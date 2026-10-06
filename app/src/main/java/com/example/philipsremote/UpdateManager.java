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

    private final Activity activity;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long downloadId = -1;
    private BroadcastReceiver receiver;
    private final SharedPreferences prefs;
    private static final long SILENT_COOLDOWN_MS = 6L * 60L * 60L * 1000L;

    public UpdateManager(Activity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id == downloadId) instalarBaixado(id);
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
    }

    public void verificarSilenciosamente() {
        verificar(false);
    }

    public void verificarManualmente() {
        verificar(true);
    }

    /** Verifica atualizações ao retornar para o aplicativo. */
    public void verificarAoAbrir() {
        verificar(false);
    }

    private void verificar(boolean manual) {
        if (!manual && !podeVerificarSilenciosamente()) return;
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
                int code = connection.getResponseCode();
                if (code != 200) throw new Exception("HTTP " + code);

                InputStream in = connection.getInputStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) json.append(line);
                br.close();

                JSONObject release = new JSONObject(json.toString());
                String tag = release.optString("tag_name", "");
                String latest = tag.startsWith("v") ? tag.substring(1) : tag;
                String current = versaoAtual();

                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = "";
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase().endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", "");
                            break;
                        }
                    }
                }

                final boolean update = compararVersoes(latest, current) > 0 && !apkUrl.isEmpty();
                final String finalLatest = latest;
                final String finalApkUrl = apkUrl;

                activity.runOnUiThread(() -> {
                    if (update) {
                        if (!manual) marcarPrompt(finalLatest);
                        mostrarAtualizacao(finalLatest, finalApkUrl);
                    } else if (manual) {
                        Toast.makeText(activity, "Você já está usando a versão mais recente.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (manual) {
                    activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Não foi possível verificar agora.", Toast.LENGTH_SHORT).show()
                    );
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private boolean podeVerificarSilenciosamente() {
        long ultima = prefs.getLong("last_silent_check", 0L);
        return System.currentTimeMillis() - ultima >= SILENT_COOLDOWN_MS;
    }

    private void marcarPrompt(String versao) {
        prefs.edit().putLong("last_silent_check", System.currentTimeMillis())
            .putString("last_prompt_version", versao).apply();
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
                int xi = i < x.length ? Integer.parseInt(x[i].replaceAll("[^0-9].*", "")) : 0;
                int yi = i < y.length ? Integer.parseInt(y[i].replaceAll("[^0-9].*", "")) : 0;
                if (xi != yi) return xi > yi ? 1 : -1;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private void mostrarAtualizacao(String versao, String apkUrl) {
        new android.app.AlertDialog.Builder(activity)
            .setTitle("Nova atualização disponível")
            .setMessage("Versão " + versao + " está disponível.\n\nO aplicativo pode baixar e instalar a atualização mantendo seus controles salvos.")
            .setNegativeButton("AGORA NÃO", null)
            .setPositiveButton("ATUALIZAR", (d, w) -> baixar(apkUrl, versao))
            .show();
    }

    private void baixar(String apkUrl, String versao) {
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("IR Remote BR " + versao);
            request.setDescription("Baixando atualização...");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "IRRemoteBR-update-" + versao + ".apk");
            downloadId = manager.enqueue(request);
            Toast.makeText(activity, "Atualização iniciada. Aguarde o download.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Não foi possível iniciar a atualização.", Toast.LENGTH_SHORT).show();
        }
    }

    private void instalarBaixado(long id) {
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = manager.getUriForDownloadedFile(id);
            if (uri == null) {
                Toast.makeText(activity, "Download da atualização falhou.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                new android.app.AlertDialog.Builder(activity)
                    .setTitle("Permitir atualização")
                    .setMessage("O Android precisa permitir que o IR Remote BR instale atualizações baixadas por ele. Ative a permissão e toque novamente em Atualizar.")
                    .setNegativeButton("CANCELAR", null)
                    .setPositiveButton("ABRIR CONFIGURAÇÕES", (d, w) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        settings.setData(Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(settings);
                    }).show();
                return;
            }

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
