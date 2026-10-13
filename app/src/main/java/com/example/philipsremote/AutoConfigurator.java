package com.example.philipsremote;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Preenche automaticamente funções com tabelas de comandos conhecidas ou
 * tabelas candidatas explicitamente sinalizadas para teste no aparelho real.
 */
public final class AutoConfigurator {
    public static final class Result {
        public final int configurados;
        public final int total;
        public final boolean suportado;
        public final boolean candidata;
        public final String perfil;

        Result(int configurados, int total, boolean suportado, boolean candidata, String perfil) {
            this.configurados = configurados;
            this.total = total;
            this.suportado = suportado;
            this.candidata = candidata;
            this.perfil = perfil == null ? "" : perfil;
        }
    }

    private AutoConfigurator() {}

    public static Result configurar(ControleStorage.Controle controle, ControleStorage storage) {
        if (controle == null || storage == null) return new Result(0, 0, false, false, "");

        String perfil = normalizar(controle.perfil);
        Map<String, Integer> comandos = tabela(perfil);
        boolean candidata = tabelaCandidata(perfil);
        if (comandos.isEmpty()) return new Result(0, 0, false, false, perfil);

        int frequencia = Math.max(0, controle.frequencia);
        if (frequencia == 0) frequencia = IrPerfilTeste.frequenciaPadrao(perfil);

        int adicionados = 0;
        for (Map.Entry<String, Integer> e : comandos.entrySet()) {
            if (storage.possuiComando(controle, e.getKey())) continue;
            storage.salvarComando(controle, e.getKey(), e.getValue(), perfil, frequencia);
            adicionados++;
        }
        return new Result(adicionados, comandos.size(), true, candidata, perfil);
    }

    static Map<String, Integer> tabela(String perfil) {
        Map<String, Integer> out = new LinkedHashMap<>();
        perfil = normalizar(perfil);

        if ("LG / NEC".equals(perfil)) {
            for (int i = 0; i < RemoteKeys.CHAVES.length; i++) {
                out.put(RemoteKeys.CHAVES[i], (0x04 << 8) | RemoteKeys.lgCode(comandoPorIndice(i)));
            }
            return out;
        }

        if ("Philips / RC6".equals(perfil) || "Philips / RC5".equals(perfil)) {
            for (int i = 0; i < RemoteKeys.CHAVES.length; i++) {
                out.put(RemoteKeys.CHAVES[i], comandoPorIndice(i) & 0xFF);
            }
            return out;
        }

        if ("Samsung TV".equals(perfil)) {
            // Códigos Samsung32 usados como candidatos iniciais; variantes de modelo podem diferir.
            int[] codes = new int[RemoteKeys.CHAVES.length];
            java.util.Arrays.fill(codes, -1);
            int[] values = {
                0x02, 0x0F, 0x07, 0x0B, 0x12, 0x10, 0x60, 0x61, 0x65, 0x62,
                0x68, 0x58, 0x1A, 0x79, 0x1F, 0x16, 0x1F,
                -1, 0x1A, 0x71, 0x72, 0x73, 0x74,
                0x47, 0x4B, 0x46, 0x45, 0x48, -1, 0x2D, -1,
                0x11, 0x04, 0x05, 0x06, 0x08, 0x09, 0x0A, 0x0C, 0x0D, 0x0E
            };
            System.arraycopy(values, 0, codes, 0, Math.min(values.length, codes.length));
            for (int i = 0; i < codes.length; i++) {
                if (codes[i] >= 0) out.put(RemoteKeys.CHAVES[i], (0x07 << 8) | codes[i]);
            }
            return out;
        }

        if ("Sony TV".equals(perfil)) {
            // SIRC (12-bit) códigos comuns; os botões multimídia/atalhos variam por controle.
            int[] codes = new int[RemoteKeys.CHAVES.length];
            java.util.Arrays.fill(codes, -1);
            int[] values = {
                0x15, 0x14, 0x12, 0x13, 0x10, 0x11, 0x74, 0x75, 0x34, 0x33,
                0x65, 0x23, 0x60, 0x60, 0x25, 0x3A, 0x3D,
                -1, 0x55, -1, -1, -1, -1,
                0x4A, 0x4B, 0x4C, 0x48, 0x49, -1, 0x2F, -1,
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09
            };
            System.arraycopy(values, 0, codes, 0, Math.min(values.length, codes.length));
            for (int i = 0; i < codes.length; i++) {
                if (codes[i] >= 0) out.put(RemoteKeys.CHAVES[i], (0x01 << 8) | codes[i]);
            }
            return out;
        }

        if (IrPerfilTeste.PERFIL_VENTILADOR.equals(perfil)) {
            for (int i = 0; i < RemoteKeys.FAN_CHAVES.length; i++) {
                out.put(RemoteKeys.FAN_CHAVES[i], i + 1);
            }
            return out;
        }

        // NEC genérico, Panasonic e ar-condicionado permanecem guiados:
        // não há uma tabela universal segura para todas as marcas/modelos.
        return out;
    }

    static boolean tabelaCandidata(String perfil) {
        perfil = normalizar(perfil);
        return "Samsung TV".equals(perfil) || "Sony TV".equals(perfil)
                || IrPerfilTeste.PERFIL_VENTILADOR.equals(perfil);
    }

    private static String normalizar(String value) {
        return value == null ? "" : value.trim();
    }

    private static int comandoPorIndice(int i) {
        if (i >= 0 && i <= 30) {
            switch (i) {
                case 0: return RemoteKeys.POWER;
                case 1: return RemoteKeys.MUTE;
                case 2: return RemoteKeys.VOL_UP;
                case 3: return RemoteKeys.VOL_DOWN;
                case 4: return RemoteKeys.CH_UP;
                case 5: return RemoteKeys.CH_DOWN;
                case 6: return RemoteKeys.UP;
                case 7: return RemoteKeys.DOWN;
                case 8: return RemoteKeys.LEFT;
                case 9: return RemoteKeys.RIGHT;
                case 10: return RemoteKeys.OK;
                case 11: return RemoteKeys.BACK;
                case 12: return RemoteKeys.MENU;
                case 13: return RemoteKeys.HOME;
                case 14: return RemoteKeys.SOURCE;
                case 15: return RemoteKeys.INFO;
                case 16: return RemoteKeys.GUIDE;
                case 17: return RemoteKeys.NETFLIX;
                case 18: return RemoteKeys.SETTINGS;
                case 19: return RemoteKeys.RED;
                case 20: return RemoteKeys.GREEN;
                case 21: return RemoteKeys.YELLOW;
                case 22: return RemoteKeys.BLUE;
                case 23: return RemoteKeys.PLAY;
                case 24: return RemoteKeys.PAUSE;
                case 25: return RemoteKeys.STOP;
                case 26: return RemoteKeys.REWIND;
                case 27: return RemoteKeys.FAST_FORWARD;
                case 28: return RemoteKeys.SUBTITLE;
                case 29: return RemoteKeys.EXIT;
                case 30: return RemoteKeys.CC;
                default: break;
            }
        }
        return i - 31;
    }
}
