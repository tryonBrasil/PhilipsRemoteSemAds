package com.example.philipsremote;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuração automática dos principais botões.
 *
 * Só grava tabelas quando o mapeamento do protocolo é conhecido com segurança
 * no aplicativo. Para perfis sem uma tabela confiável, retorna suporte parcial
 * e deixa a configuração guiada disponível.
 */
public final class AutoConfigurator {
    public static final class Result {
        public final int configurados;
        public final int total;
        public final boolean suportado;
        public final String perfil;

        Result(int configurados, int total, boolean suportado, String perfil) {
            this.configurados = configurados;
            this.total = total;
            this.suportado = suportado;
            this.perfil = perfil == null ? "" : perfil;
        }
    }

    private AutoConfigurator() {}

    public static Result configurar(ControleStorage.Controle controle, ControleStorage storage) {
        if (controle == null || storage == null) return new Result(0, 0, false, "");

        String perfil = controle.perfil == null ? "" : controle.perfil.trim();
        Map<String, Integer> comandos = tabela(perfil);
        if (comandos.isEmpty()) return new Result(0, 0, false, perfil);

        int frequencia = Math.max(0, controle.frequencia);
        if (frequencia == 0) frequencia = IrPerfilTeste.frequenciaPadrao(perfil);

        int adicionados = 0;
        for (Map.Entry<String, Integer> e : comandos.entrySet()) {
            if (storage.possuiComando(controle, e.getKey())) continue;
            storage.salvarComando(controle, e.getKey(), e.getValue(), perfil, frequencia);
            adicionados++;
        }
        return new Result(adicionados, comandos.size(), true, perfil);
    }

    private static Map<String, Integer> tabela(String perfil) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (perfil == null) return out;

        if ("LG / NEC".equals(profile(perfil))) {
            for (int i = 0; i < RemoteKeys.CHAVES.length; i++) {
                int command = comandoPorIndice(i);
                out.put(RemoteKeys.CHAVES[i], (0x04 << 8) | RemoteKeys.lgCode(command));
            }
            return out;
        }

        if ("Philips / RC6".equals(profile(perfil)) || "Philips / RC5".equals(profile(perfil))) {
            for (int i = 0; i < RemoteKeys.CHAVES.length; i++) {
                int command = comandoPorIndice(i);
                out.put(RemoteKeys.CHAVES[i], command & 0xFF);
            }
            return out;
        }

        if (IrPerfilTeste.PERFIL_VENTILADOR.equals(profile(perfil))) {
            for (int i = 0; i < RemoteKeys.FAN_CHAVES.length; i++) {
                out.put(RemoteKeys.FAN_CHAVES[i], i + 1);
            }
            return out;
        }

        return out;
    }

    private static String profile(String value) {
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
