package com.example.philipsremote;

public final class UpdateIntegrity {
    private UpdateIntegrity() {}

    public static String extrairSha256(String digest) {
        if (digest == null) return "";
        String value = digest.trim();
        if (!value.regionMatches(true, 0, "sha256:", 0, 7)) return "";
        value = value.substring(7).trim().toLowerCase(java.util.Locale.ROOT);
        return value.matches("[0-9a-f]{64}") ? value : "";
    }

    public static boolean valido(String sha256) {
        return sha256 != null && sha256.matches("(?i)[0-9a-f]{64}");
    }

    public static boolean corresponde(String esperado, String calculado) {
        return valido(esperado) && valido(calculado) && esperado.equalsIgnoreCase(calculado);
    }
}
