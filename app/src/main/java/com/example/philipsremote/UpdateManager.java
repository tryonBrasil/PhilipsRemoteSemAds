package com.example.philipsremote;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {
    private static final String RELEASES_URL =
        "https://api.github.com/repos/tryonBrasil/PhilipsRemoteSemAds/releases/latest";
    private static final String PREF_DOWNLOAD_ID = "download_id";
    private static final String PREF_DOWNLOAD_VERSION = "download_version";
    private static final String PREF_DOWNLOAD_URL = "download_url";
    private static final long SILENT_COOLDOWN_MS = 6L * 60L * 60L * 1000L;

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private BroadcastReceiver receiver;
    private boolean checking = false;

    public UpdateManager(Activity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);

        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    long savedId = prefs.getLong(PREF_DOWNLOAD_ID, -1L);
                    if (id == savedId) {
                        instalarBaixado(id);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }

        // Recupera um download iniciado antes de o processo do app ser encerrado.
        verificarDownloadPendente(false);
    }

    public void verificarSilenciosamente() { verificar(false); }

    public void verificarManualmente() {
        verificar(true);
    }

    public void verificarAoAbrir() {
        verificarDownloadPendente(false);
        verificar(false);
    }

    private void verificar(boolean manual) {
        if (!manual && !podeVerificarSilenciosamente()) return;
        synchronized (this) {
            if (checking) return;
            checking = true;
        }

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(RELEASES_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "IR-Remote-BR");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Pragma", "no-cache");

                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());

                BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) json.append(line);
                br.close();

                JSONObject release = new JSONObject(json.toString());
                String tag = release.optString("tag_name", "");
                String latest = tag.startsWith("v") ? tag.substring(1) : tag;
                String current = versaoAtual();
                String apkUrl = encontrarApk(release);

                if (!manual) {
                    prefs.edit().putLong("last_silent_check", System.currentTimeMillis()).apply();
                }

                final boolean update = compararVersoes(latest, current) > 0 && !apkUrl.isEmpty();
                final String finalLatest = latest;
                final String finalApkUrl = apkUrl;

                activity.runOnUiThread(() -> {
                    if (update) {
                        if (!manual) {
                            if (!temDownloadPendente()) {
                                baixarAutomaticamente(finalApkUrl, finalLatest);
                            }
                        } else {
                            mostrarAtualizacao(finalLatest, finalApkUrl);
                        }
                    } else if (manual) {
                        Toast.makeText(activity, "Você já está usando a versão mais recente.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (manual) {
                    activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Não foi possível verificar agora.", Toast.LENGTH_SHORT).show());
                }
            } finally {
                if (connection != null) connection.disconnect();
                synchronized (this) { checking = false; }
            }
        });
    }

    private String encontrarApk(JSONObject release) throws Exception {
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if ("app-release.apk".equalsIgnoreCase(asset.optString("name", ""))) {
                return asset.optString("browser_download_url", "");
            }
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "").toLowerCase();
            if (name.endsWith(".apk") && !name.contains("debug")) {
                return asset.optString("browser_download_url", "");
            }
        }
        return "";
    }

    private boolean podeVerificarSilenciosamente() {
        long ultima = prefs.getLong("last_silent_check", 0L);
        return System.currentTimeMillis() - ultima >= SILENT_COOLDOWN_MS;
    }

    private String versaoAtual() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName == null ? "0.0.0" : info.versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    private int compararVersoes(String a, String b) {
        try {
            String[] x = a.split("\\.");
            String[] y = b.split("\\.");
            int n = Math.max(x.length, y.length);
            for (int i = 0; i < n; i++) {
                int xi = i < x.length ? inteiroSeguro(x[i]) : 0;
                int yi = i < y.length ? inteiroSeguro(y[i]) : 0;
                if (xi != yi) return xi > yi ? 1 : -1;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private int inteiroSeguro(String valor) {
        String limpo = valor.replaceAll("[^0-9].*", "");
        return limpo.isEmpty() ? 0 : Integer.parseInt(limpo);
    }

    private boolean temDownloadPendente() {
        long id = prefs.getLong(PREF_DOWNLOAD_ID, -1L);
        if (id < 0) return false;
        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        android.database.Cursor cursor = null;
        try {
            cursor = manager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                return status == DownloadManager.STATUS_PENDING ||
                       status == DownloadManager.STATUS_RUNNING ||
                       status == DownloadManager.STATUS_PAUSED;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        prefs.edit().remove(PREF_DOWNLOAD_ID).remove(PREF_DOWNLOAD_VERSION)
            .remove(PREF_DOWNLOAD_URL).apply();
        return false;
    }

    private void verificarDownloadPendente(boolean mostrarToast) {
        long id = prefs.getLong(PREF_DOWNLOAD_ID, -1L);
        if (id < 0) return;

        DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        android.database.Cursor cursor = null;
        try {
            cursor = manager.query(query);
            if (cursor == null || !cursor.moveToFirst()) {
                limparDownload();
                return;
            }

            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                instalarBaixado(id);
            } else if (status == DownloadManager.STATUS_FAILED) {
                limparDownload();
                if (mostrarToast) {
                    Toast.makeText(activity, "O download da atualização falhou. Tentaremos novamente.", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            limparDownload();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private void limparDownload() {
        prefs.edit().remove(PREF_DOWNLOAD_ID).remove(PREF_DOWNLOAD_VERSION)
            .remove(PREF_DOWNLOAD_URL).apply();
    }

    private void mostrarAtualizacao(String versao, String apkUrl) {
        new android.app.AlertDialog.Builder(activity)
            .setTitle("Nova atualização disponível")
            .setMessage("Versão " + versao + " está disponível.\\n\\nO aplicativo pode baixar e instalar a atualização mantendo seus controles salvos.")
            .setNegativeButton("AGORA NÃO", null)
            .setPositiveButton("ATUALIZAR", (d, w) -> baixar(apkUrl, versao))
            .show();
    }

    private void baixarAutomaticamente(String apkUrl, String versao) {
        iniciarDownload(apkUrl, versao, true);
    }

    private void baixar(String apkUrl, String versao) {
        iniciarDownload(apkUrl, versao, false);
    }

    private void iniciarDownload(String apkUrl, String versao, boolean automatico) {
        if (temDownloadPendente()) return;
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("IR Remote BR " + versao);
            request.setDescription(automatico ? "Baixando atualização automaticamente..." : "Baixando atualização...");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(
                activity, Environment.DIRECTORY_DOWNLOADS, "IRRemoteBR-update-" + versao + ".apk");

            long id = manager.enqueue(request);
            prefs.edit()
                .putLong(PREF_DOWNLOAD_ID, id)
                .putString(PREF_DOWNLOAD_VERSION, versao)
                .putString(PREF_DOWNLOAD_URL, apkUrl)
                .putLong("last_download_start", System.currentTimeMillis())
                .apply();

            Toast.makeText(activity,
                automatico ? "Nova atualização encontrada. Baixando automaticamente..." :
                             "Atualização iniciada. Aguarde o download.",
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            limparDownload();
            Toast.makeText(activity, "Não foi possível iniciar a atualização.", Toast.LENGTH_SHORT).show();
        }
    }

    private void instalarBaixado(long id) {
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = manager.getUriForDownloadedFile(id);
            if (uri == null) {
                limparDownload();
                Toast.makeText(activity, "Download da atualização falhou.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new android.app.AlertDialog.Builder(activity)
                    .setTitle("Permitir atualização")
                    .setMessage("Ative a permissão para instalar aplicativos desta fonte. Depois volte ao IR Remote BR para continuar a instalação.")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("ABRIR CONFIGURAÇÕES", (d, w) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        settings.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    }).show();
                return;
            }

            // Limpa o estado antes de abrir o instalador para evitar reinstalação em loop após o update.
            limparDownload();
            Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(uri);
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
        } catch (Exception e) {
            Toast.makeText(activity, "Não foi possível abrir o instalador.", Toast.LENGTH_SHORT).show();
        }
    }

    public void destroy() {
        try { activity.unregisterReceiver(receiver); } catch (Exception ignored) {}
        executor.shutdownNow();
    }
}
