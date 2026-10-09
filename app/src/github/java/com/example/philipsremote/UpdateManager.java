package com.example.philipsremote;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Verifica releases no GitHub, baixa o APK (com conferência de SHA-256) e abre o instalador do Android. */
public class UpdateManager implements Updater {
    private static final String RELEASES_URL =
        "https://api.github.com/repos/tryonBrasil/PhilipsRemoteSemAds/releases/latest";
    private static final String APK_PREFIX = "IRRemoteBR-update-";
    private static final String PREF_DOWNLOAD_ID = "download_id";
    private static final String PREF_DOWNLOAD_VERSION = "download_version";
    private static final String PREF_DOWNLOAD_URL = "download_url";
    private static final String PREF_DOWNLOAD_SHA = "download_sha256";
    private static final String PREF_LAST_CHECK = "last_silent_check";
    private static final String PREF_OPENED_VERSION = "installer_opened_version";
    private static final String PREF_OPENED_AT = "installer_opened_at";
    /** Intervalo mínimo entre verificações automáticas (a API do GitHub sem token limita a 60 req/h por IP). */
    private static final long SILENT_COOLDOWN_MS = 15L * 60L * 1000L;
    /** Depois de abrir o instalador para uma versão, não baixa a mesma versão de novo por este tempo. */
    private static final long REOFFER_MS = 24L * 60L * 60L * 1000L;
    private static final long APK_MAX_AGE_MS = 10L * 60L * 1000L;

    private static class Apk {
        final String url, sha256;
        Apk(String u, String s) { url = u; sha256 = s; }
    }

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private final BroadcastReceiver receiver;
    private boolean checking = false;
    private boolean esperandoPermissaoInstalacao = false;
    private boolean permissaoRecusadaNestaSessao = false;

    public UpdateManager(Activity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);

        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == prefs.getLong(PREF_DOWNLOAD_ID, -1L)) instalarBaixado(id, false);
                }
            }
        };
        // ACTION_DOWNLOAD_COMPLETE é um broadcast protegido (só o sistema envia); o DownloadManager o envia
        // de outro processo, então o receiver precisa ser EXPORTED para recebê-lo no Android 13+.
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        else activity.registerReceiver(receiver, filter);

        apagarApksAntigos();
        verificarDownloadPendente(false);   // recupera download iniciado antes de o processo ser encerrado
    }

    public void verificarManualmente() { verificar(true); }

    /** Chamado ao abrir/voltar para o app: recupera download pendente e verifica (respeitando o intervalo mínimo). */
    public void verificarAoAbrir() {
        verificarDownloadPendente(false);
        verificar(false);
    }

    private boolean ativa() { return !activity.isFinishing() && !activity.isDestroyed(); }

    private void verificar(boolean manual) {
        if (!manual && !podeVerificarSilenciosamente()) return;
        synchronized (this) {
            if (checking) return;
            checking = true;
        }
        if (!manual) prefs.edit().putLong(PREF_LAST_CHECK, System.currentTimeMillis()).apply();

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "IR-Remote-BR");
                connection.setRequestProperty("Cache-Control", "no-cache");

                int code = connection.getResponseCode();
                if (code == 403 || code == 429) throw new LimiteException();
                if (code != 200) throw new Exception("HTTP " + code);

                StringBuilder json = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) json.append(line);
                }

                JSONObject release = new JSONObject(json.toString());
                String tag = release.optString("tag_name", "");
                final String latest = tag.startsWith("v") ? tag.substring(1) : tag;
                final Apk apk = encontrarApk(release);
                final boolean update = !latest.isEmpty() && compararVersoes(latest, versaoAtual()) > 0 && apk != null;

                activity.runOnUiThread(() -> {
                    if (!ativa()) return;
                    if (update) {
                        if (manual) mostrarAtualizacao(latest, apk);
                        else if (!temDownloadPendente() && !jaOfereceuRecentemente(latest)) iniciarDownload(apk, latest, true);
                    } else if (manual) {
                        Toast.makeText(activity, "Você já está usando a versão mais recente.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (manual) {
                    final String msg = e instanceof LimiteException
                        ? "Limite de consultas do GitHub atingido. Tente novamente em alguns minutos."
                        : "Não foi possível verificar agora.";
                    activity.runOnUiThread(() -> { if (ativa()) Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show(); });
                }
            } finally {
                if (connection != null) connection.disconnect();
                synchronized (this) { checking = false; }
            }
        });
    }

    private static class LimiteException extends Exception {}

    /** Escolhe o APK de release (preferindo app-release.apk) e, se o GitHub informar, o SHA-256 dele. */
    private Apk encontrarApk(JSONObject release) throws Exception {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return null;
        JSONObject escolhido = null;
        for (int i = 0; i < assets.length() && escolhido == null; i++) {
            JSONObject a = assets.getJSONObject(i);
            if ("app-release.apk".equalsIgnoreCase(a.optString("name", ""))) escolhido = a;
        }
        for (int i = 0; i < assets.length() && escolhido == null; i++) {
            JSONObject a = assets.getJSONObject(i);
            String n = a.optString("name", "").toLowerCase(Locale.ROOT);
            if (n.endsWith(".apk") && !n.contains("debug")) escolhido = a;
        }
        if (escolhido == null) return null;
        String url = escolhido.optString("browser_download_url", "");
        if (url.isEmpty()) return null;
        String digest = escolhido.optString("digest", "");
        String sha = digest.toLowerCase(Locale.ROOT).startsWith("sha256:") ? digest.substring(7).trim() : "";
        return new Apk(url, sha);
    }

    private boolean podeVerificarSilenciosamente() {
        return System.currentTimeMillis() - prefs.getLong(PREF_LAST_CHECK, 0L) >= SILENT_COOLDOWN_MS;
    }

    private boolean jaOfereceuRecentemente(String versao) {
        return versao.equals(prefs.getString(PREF_OPENED_VERSION, ""))
            && System.currentTimeMillis() - prefs.getLong(PREF_OPENED_AT, 0L) < REOFFER_MS;
    }

    private String versaoAtual() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "0.0.0" : info.versionName;
        } catch (Exception e) { return "0.0.0"; }
    }

    static int compararVersoes(String a, String b) { return VersionUtil.comparar(a, b); }

    // ---------- estado do download ----------

    /** Status do DownloadManager para o id (ou -1 se o id não existe mais). */
    private int statusDownload(long id) {
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        android.database.Cursor cursor = null;
        try {
            cursor = manager.query(new DownloadManager.Query().setFilterById(id));
            if (cursor != null && cursor.moveToFirst())
                return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        } catch (Exception ignored) {
        } finally { if (cursor != null) cursor.close(); }
        return -1;
    }

    private boolean temDownloadPendente() {
        long id = prefs.getLong(PREF_DOWNLOAD_ID, -1L);
        if (id < 0) return false;
        int s = statusDownload(id);
        if (s == DownloadManager.STATUS_PENDING || s == DownloadManager.STATUS_RUNNING || s == DownloadManager.STATUS_PAUSED) return true;
        if (s == -1) limparDownload();
        return false;
    }

    private void verificarDownloadPendente(boolean mostrarToast) {
        long id = prefs.getLong(PREF_DOWNLOAD_ID, -1L);
        if (id < 0) return;
        int s = statusDownload(id);
        if (s == DownloadManager.STATUS_SUCCESSFUL) {
            instalarBaixado(id, false);
        } else if (s == DownloadManager.STATUS_FAILED || s == -1) {
            limparDownload();
            if (mostrarToast) Toast.makeText(activity, "O download da atualização falhou. Tentaremos novamente.", Toast.LENGTH_SHORT).show();
        }
    }

    private void limparDownload() {
        prefs.edit().remove(PREF_DOWNLOAD_ID).remove(PREF_DOWNLOAD_VERSION)
            .remove(PREF_DOWNLOAD_URL).remove(PREF_DOWNLOAD_SHA).apply();
    }

    /** Remove APKs de atualização antigos (exceto o do download em andamento e os muito recentes). */
    private void apagarApksAntigos() {
        try {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File[] arquivos = dir == null ? null : dir.listFiles();
            if (arquivos == null) return;
            boolean emAndamento = prefs.getLong(PREF_DOWNLOAD_ID, -1L) >= 0;
            long agora = System.currentTimeMillis();
            for (File f : arquivos) {
                if (!f.getName().startsWith(APK_PREFIX) || !f.getName().endsWith(".apk")) continue;
                if (emAndamento || agora - f.lastModified() < APK_MAX_AGE_MS) continue;
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Exception ignored) {}
    }

    // ---------- diálogos e download ----------

    private void mostrarAtualizacao(String versao, Apk apk) {
        new android.app.AlertDialog.Builder(activity)
            .setTitle("Nova atualização disponível")
            .setMessage("Versão " + versao + " está disponível.\n\nO aplicativo baixa a atualização e abre o instalador do Android. Seus controles salvos são mantidos.")
            .setNegativeButton("AGORA NÃO", null)
            .setPositiveButton("ATUALIZAR", (d, w) -> { permissaoRecusadaNestaSessao = false; iniciarDownload(apk, versao, false); })
            .show();
    }

    private void iniciarDownload(Apk apk, String versao, boolean automatico) {
        if (temDownloadPendente()) return;
        // Já baixado e íntegro para esta versão? Só instala, sem baixar de novo.
        long existente = prefs.getLong(PREF_DOWNLOAD_ID, -1L);
        if (existente >= 0 && versao.equals(prefs.getString(PREF_DOWNLOAD_VERSION, ""))
                && statusDownload(existente) == DownloadManager.STATUS_SUCCESSFUL) {
            instalarBaixado(existente, !automatico);
            return;
        }
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apk.url));
            request.setTitle("IR Remote BR " + versao);
            request.setDescription(automatico ? "Baixando atualização automaticamente..." : "Baixando atualização...");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            // A atualização automática também pode usar rede móvel. O usuário continua recebendo a notificação do download.
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, APK_PREFIX + versao + ".apk");

            long id = manager.enqueue(request);
            prefs.edit()
                .putLong(PREF_DOWNLOAD_ID, id)
                .putString(PREF_DOWNLOAD_VERSION, versao)
                .putString(PREF_DOWNLOAD_URL, apk.url)
                .putString(PREF_DOWNLOAD_SHA, apk.sha256)
                .putLong("last_download_start", System.currentTimeMillis())
                .apply();

            Toast.makeText(activity,
                automatico ? "Nova atualização encontrada. Baixando automaticamente..." : "Atualização iniciada. Aguarde o download.",
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            limparDownload();
            Toast.makeText(activity, "Não foi possível iniciar a atualização.", Toast.LENGTH_SHORT).show();
        }
    }

    /** @param explicito true quando o usuário acabou de pedir (aí o aviso de permissão pode reaparecer) */
    private void instalarBaixado(long id, boolean explicito) {
        if (!ativa()) return;
        if (explicito) permissaoRecusadaNestaSessao = false;
        final String esperado = prefs.getString(PREF_DOWNLOAD_SHA, "");
        final String versao = prefs.getString(PREF_DOWNLOAD_VERSION, "");
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            final Uri uri = manager.getUriForDownloadedFile(id);
            if (uri == null) {
                limparDownload();
                Toast.makeText(activity, "Download da atualização falhou.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (esperado.isEmpty()) { continuarInstalacao(uri, versao); return; }
            executor.execute(() -> {
                final boolean ok = esperado.equalsIgnoreCase(sha256(uri));
                activity.runOnUiThread(() -> {
                    if (!ativa()) return;
                    if (ok) { continuarInstalacao(uri, versao); return; }
                    try { ((DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE)).remove(id); } catch (Exception ignored) {}
                    limparDownload();
                    Toast.makeText(activity, "O arquivo baixado não confere com o publicado (SHA-256). Atualização cancelada.", Toast.LENGTH_LONG).show();
                });
            });
        } catch (Exception e) {
            Toast.makeText(activity, "Não foi possível abrir o instalador.", Toast.LENGTH_SHORT).show();
        }
    }

    private String sha256(Uri uri) {
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private void continuarInstalacao(Uri uri, String versao) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            if (permissaoRecusadaNestaSessao) return;
            esperandoPermissaoInstalacao = true;
            new android.app.AlertDialog.Builder(activity)
                .setTitle("Pronto para instalar")
                .setMessage("A atualização já foi baixada. Só falta permitir que o IR Remote BR instale atualizações. Toque em CONFIGURAÇÕES e ative \"Permitir desta fonte\". Ao voltar, a instalação continuará automaticamente.")
                .setOnCancelListener(d -> { esperandoPermissaoInstalacao = false; permissaoRecusadaNestaSessao = true; })
                .setNegativeButton("CANCELAR", (d, w) -> { esperandoPermissaoInstalacao = false; permissaoRecusadaNestaSessao = true; })
                .setPositiveButton("CONFIGURAÇÕES", (d, w) -> {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    settings.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(settings);
                }).show();
            return;
        }
        abrirInstalador(uri, versao);
    }

    @SuppressWarnings("deprecation")
    private void abrirInstalador(Uri uri, String versao) {
        try {
            limparDownload();
            prefs.edit().putString(PREF_OPENED_VERSION, versao).putLong(PREF_OPENED_AT, System.currentTimeMillis()).apply();
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(uri);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
        } catch (Exception e) {
            Toast.makeText(activity, "Não foi possível abrir o instalador.", Toast.LENGTH_SHORT).show();
        }
    }

    public void aoRetornarDoSistema() {
        if (esperandoPermissaoInstalacao) {
            esperandoPermissaoInstalacao = false;
            activity.runOnUiThread(() -> verificarDownloadPendente(false));
        }
    }

    public void destroy() {
        try { activity.unregisterReceiver(receiver); } catch (Exception ignored) {}
        executor.shutdownNow();
    }
}
