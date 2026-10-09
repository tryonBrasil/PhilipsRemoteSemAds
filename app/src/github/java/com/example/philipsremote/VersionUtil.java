package com.example.philipsremote;

/** Comparação de versões no formato 1.2.3 (sufixos como "-beta" são ignorados). */
public final class VersionUtil {
    private VersionUtil() {}

    /** @return >0 se a > b, <0 se a < b, 0 se iguais */
    public static int comparar(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? inteiro(x[i]) : 0;
            int yi = i < y.length ? inteiro(y[i]) : 0;
            if (xi != yi) return xi > yi ? 1 : -1;
        }
        return 0;
    }

    private static int inteiro(String valor) {
        String limpo = valor.replaceAll("[^0-9].*", "");
        if (limpo.isEmpty()) return 0;
        try { return Integer.parseInt(limpo); } catch (NumberFormatException e) { return 0; }
    }
}