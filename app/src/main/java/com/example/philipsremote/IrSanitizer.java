package com.example.philipsremote;

/**
 * Limites de segurança para sinais IR vindos de fora (bancos online, arquivos importados).
 * Puro Java, sem Android, para poder ser testado na JVM.
 */
public final class IrSanitizer {
    public static final int FREQ_MIN = 15000;
    public static final int FREQ_MAX = 60000;
    public static final int MAX_DURACOES = 2000;
    public static final int DURACAO_MAX_US = 1_000_000;

    private IrSanitizer() {}

    public static boolean frequenciaValida(int hz) {
        return hz >= FREQ_MIN && hz <= FREQ_MAX;
    }

    public static boolean padraoValido(int[] padrao) {
        if (padrao == null || padrao.length == 0 || padrao.length > MAX_DURACOES) return false;
        for (int d : padrao) if (d < 0 || d > DURACAO_MAX_US) return false;
        return true;
    }

    public static boolean valido(int hz, int[] padrao) {
        return frequenciaValida(hz) && padraoValido(padrao);
    }
}