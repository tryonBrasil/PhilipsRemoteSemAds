package com.example.philipsremote;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local-network remote protocols for the user's Philips SAPHI and legacy LG NetCast TVs.
 * All methods are synchronous and MUST be called from a worker thread.
 */
public final class WifiTvProtocol {
    private static final int TIMEOUT_MS = 3500;
    private final SharedPreferences prefs;

    public WifiTvProtocol(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("wifi_tv_remote", Context.MODE_PRIVATE);
    }

    public void saveHost(String brand, String host) {
        prefs.edit().putString(key(brand, "host"), host.trim()).apply();
    }

    public String getHost(String brand) {
        return prefs.getString(key(brand, "host"), "");
    }

    public boolean isLgPaired() {
        return !prefs.getString(key("LG", "session"), "").isEmpty();
    }

    public void clearLgSession() {
        prefs.edit().remove(key("LG", "session")).remove(key("LG", "pairing_key")).apply();
    }

    /** Philips JointSpace API test; tries the common HTTP endpoint used by this TV generation. */
    public String testPhilips(String host) throws Exception {
        String body = request("GET", "http://" + cleanHost(host) + ":1925/6/system", null,
                "application/json");
        JSONObject json = new JSONObject(body);
        String name = json.optString("name", "Philips TV");
        return name.isEmpty() ? "Philips TV respondeu à API JointSpace." : name;
    }

    public void sendPhilipsKey(String host, String key) throws Exception {
        JSONObject body = new JSONObject();
        body.put("key", key);
        request("POST", "http://" + cleanHost(host) + ":1925/6/input/key",
                body.toString(), "application/json");
    }

    /** Requests that a compatible LG NetCast TV display its pairing key. */
    public String requestLgPairKey(String host) throws Exception {
        String body = request("POST", "http://" + cleanHost(host) + ":8080/roap/api/auth",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthKeyReq</type></auth>",
                "application/atom+xml");
        prefs.edit().putString(key("LG", "host"), cleanHost(host)).apply();
        if (body == null || body.trim().isEmpty()) {
            throw new Exception("A TV não retornou a solicitação de emparelhamento.");
        }
        return body;
    }

    public void pairLg(String host, String pairingKey) throws Exception {
        if (pairingKey == null || !pairingKey.trim().matches("\\d{4,8}")) {
            throw new Exception("Digite a chave numérica mostrada na TV.");
        }
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthReq</type><value>"
                + pairingKey.trim() + "</value></auth>";
        String response = request("POST", "http://" + cleanHost(host) + ":8080/roap/api/auth",
                body, "application/atom+xml");
        Matcher matcher = Pattern.compile("<session>(.*?)</session>", Pattern.DOTALL).matcher(response);
        if (!matcher.find()) {
            throw new Exception("A TV não aceitou o emparelhamento. Confira a chave e tente novamente.");
        }
        prefs.edit().putString(key("LG", "host"), cleanHost(host))
                .putString(key("LG", "pairing_key"), pairingKey.trim())
                .putString(key("LG", "session"), matcher.group(1)).apply();
    }

    public void sendLgKey(String keyName) throws Exception {
        String host = getHost("LG");
        String session = prefs.getString(key("LG", "session"), "");
        if (host.isEmpty() || session.isEmpty()) throw new Exception("Emparelhe a TV LG primeiro.");
        int code = lgKeyCode(keyName);
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?><command><session>"
                + session + "</session><type>HandleKeyInput</type><value>" + code + "</value></command>";
        request("POST", "http://" + cleanHost(host) + ":8080/roap/api/command",
                body, "application/atom+xml");
    }

    private static int lgKeyCode(String key) throws Exception {
        switch (key) {
            case "POWER": return 1;
            case "0": return 2; case "1": return 3; case "2": return 4; case "3": return 5;
            case "4": return 6; case "5": return 7; case "6": return 8; case "7": return 9;
            case "8": return 10; case "9": return 11;
            case "UP": return 12; case "DOWN": return 13; case "LEFT": return 14; case "RIGHT": return 15;
            case "OK": return 20; case "HOME": return 21; case "MENU": return 22; case "BACK": return 23;
            case "VOLUP": return 24; case "VOLDOWN": return 25; case "MUTE": return 26;
            case "CHUP": return 27; case "CHDOWN": return 28;
            case "RED": return 31; case "GREEN": return 30; case "YELLOW": return 32; case "BLUE": return 29;
            case "PLAY": return 33; case "PAUSE": return 34; case "STOP": return 35;
            case "REW": return 37; case "FF": return 36; case "INPUT": return 47; case "INFO": return 45;
            case "EXIT": return 412; case "GUIDE": return 44; case "LIVE_TV": return 43;
            default: throw new Exception("Tecla não suportada para LG NetCast: " + key);
        }
    }

    private static String cleanHost(String host) throws Exception {
        if (host == null) throw new Exception("Informe o IP da TV.");
        String value = host.trim();
        if (value.startsWith("http://")) value = value.substring(7);
        if (value.startsWith("https://")) value = value.substring(8);
        int slash = value.indexOf('/');
        if (slash >= 0) value = value.substring(0, slash);
        if (value.contains(":")) throw new Exception("Digite somente o IP ou nome local, sem porta.");
        if (value.isEmpty() || !value.matches("[A-Za-z0-9.-]+")) {
            throw new Exception("Endereço da TV inválido.");
        }
        return value;
    }

    private static String request(String method, String address, String payload, String contentType) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "*/*");
            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", contentType);
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : readAll(stream);
            if (status < 200 || status >= 300) {
                throw new Exception("A TV respondeu HTTP " + status + ". Verifique IP, Wi-Fi e compatibilidade do protocolo.");
            }
            return body;
        } catch (java.net.SocketTimeoutException e) {
            throw new Exception("Tempo esgotado. Confira se a TV está ligada e na mesma rede Wi-Fi.");
        } catch (java.net.ConnectException e) {
            throw new Exception("Não foi possível conectar. Confira o IP e se o controle pela rede está disponível na TV.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return out.toString("UTF-8");
        }
    }

    private static String key(String brand, String field) {
        return brand.toUpperCase(java.util.Locale.ROOT) + "_" + field;
    }
}
