package com.example.philipsremote;

import java.util.LinkedHashMap;
import java.util.Map;

/** Automatic configuration with known tables and explicitly marked candidate tables. */
public final class AutoConfigurator {
    public static final class Result {
        public final int configurados, total;
        public final boolean suportado, candidata;
        public final String perfil;
        Result(int configurados, int total, boolean suportado, boolean candidata, String perfil) {
            this.configurados=configurados; this.total=total; this.suportado=suportado;
            this.candidata=candidata; this.perfil=perfil==null?"":perfil;
        }
    }
    private AutoConfigurator() {}

    public static Result configurar(ControleStorage.Controle controle, ControleStorage storage) {
        if(controle==null || storage==null) return new Result(0,0,false,false,"");
        String perfil=normalizar(controle.perfil);
        Map<String,Integer> comandos=tabela(perfil);
        if(IrPerfilTeste.PERFIL_VENTILADOR.equals(perfil))
            comandos=tabelaVentilador(controle.marca,controle.modelo,controle.descricao);
        boolean candidata=tabelaCandidata(perfil)
                || (IrPerfilTeste.PERFIL_VENTILADOR.equals(perfil) && !comandos.isEmpty());
        if(comandos.isEmpty()) return new Result(0,0,false,false,perfil);
        int frequencia=Math.max(0,controle.frequencia);
        if(frequencia==0) frequencia=IrPerfilTeste.frequenciaPadrao(perfil);
        int adicionados=0;
        for(Map.Entry<String,Integer> e:comandos.entrySet()){
            if(storage.possuiComando(controle,e.getKey())) continue;
            storage.salvarComando(controle,e.getKey(),e.getValue(),perfil,frequencia);
            adicionados++;
        }
        return new Result(adicionados,comandos.size(),true,candidata,perfil);
    }

    static Map<String,Integer> tabela(String perfil) {
        Map<String,Integer> out=new LinkedHashMap<>();
        perfil=normalizar(perfil);
        if("LG / NEC".equals(perfil)){
            for(int i=0;i<RemoteKeys.CHAVES.length;i++)
                out.put(RemoteKeys.CHAVES[i],(0x04<<8)|RemoteKeys.lgCode(comandoPorIndice(i)));
            return out;
        }
        // RC5 command mapping is not interchangeable with RC6; keep it guided until validated.
        if("Philips / RC6".equals(perfil)){
            for(int i=0;i<RemoteKeys.CHAVES.length;i++)
                out.put(RemoteKeys.CHAVES[i],comandoPorIndice(i)&0xFF);
            return out;
        }
        if("Samsung TV".equals(perfil)){
            // Common Samsung TV commands, address E0 repeated in Samsung32 frames.
            int[] values={
                0x40,0xF0,0xE0,0xD0,0x48,0x08,0x06,0x86,0xA6,0x46,
                0x16,0x1A,0x58,0x9E,0x80,0xF8,0xF2,-1,0xD2,0x36,0x28,0xA8,0x68,
                0xE2,0x52,0x62,0xA2,0x12,0xA4,0xB4,-1,
                0x88,0x20,0xA0,0x60,0x10,0x90,0x50,0x30,0xB0,0x70
            };
            for(int i=0;i<values.length && i<RemoteKeys.CHAVES.length;i++)
                if(values[i]>=0) out.put(RemoteKeys.CHAVES[i],(0xE0<<8)|values[i]);
            return out;
        }
        if("Sony TV".equals(perfil)){
            // Common SIRC codes; shortcuts/media functions vary by remote model.
            int[] values={
                0x15,0x14,0x12,0x13,0x10,0x11,0x74,0x75,0x34,0x33,
                0x65,0x23,0x60,0x60,0x25,0x3A,0x3D,-1,0x55,-1,-1,-1,-1,
                0x4A,0x4B,0x4C,0x48,0x49,-1,0x2F,-1,
                0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09
            };
            for(int i=0;i<values.length && i<RemoteKeys.CHAVES.length;i++)
                if(values[i]>=0) out.put(RemoteKeys.CHAVES[i],(0x01<<8)|values[i]);
            return out;
        }
        // Generic NEC, Panasonic and AC remain guided: codes/state vary by model.
        return out;
    }

    static Map<String,Integer> tabelaVentilador(String marca,String modelo,String descricao){
        Map<String,Integer> out=new LinkedHashMap<>();
        String id=(normalizar(marca)+" "+normalizar(modelo)+" "+normalizar(descricao))
                .toUpperCase(java.util.Locale.ROOT);
        // Only confirmed fan command from the user's real-device test: KXD194 power = NEC addr 04/cmd 18.
        if(id.contains("KXD194")) out.put("POWER",(0x04<<8)|0x18);
        return out;
    }

    static boolean tabelaCandidata(String perfil){
        perfil=normalizar(perfil);
        return "Samsung TV".equals(perfil)||"Sony TV".equals(perfil);
    }
    private static String normalizar(String value){return value==null?"":value.trim();}

    private static int comandoPorIndice(int i){
        if(i>=0 && i<=30) switch(i){
            case 0:return RemoteKeys.POWER; case 1:return RemoteKeys.MUTE;
            case 2:return RemoteKeys.VOL_UP; case 3:return RemoteKeys.VOL_DOWN;
            case 4:return RemoteKeys.CH_UP; case 5:return RemoteKeys.CH_DOWN;
            case 6:return RemoteKeys.UP; case 7:return RemoteKeys.DOWN;
            case 8:return RemoteKeys.LEFT; case 9:return RemoteKeys.RIGHT;
            case 10:return RemoteKeys.OK; case 11:return RemoteKeys.BACK;
            case 12:return RemoteKeys.MENU; case 13:return RemoteKeys.HOME;
            case 14:return RemoteKeys.SOURCE; case 15:return RemoteKeys.INFO;
            case 16:return RemoteKeys.GUIDE; case 17:return RemoteKeys.NETFLIX;
            case 18:return RemoteKeys.SETTINGS; case 19:return RemoteKeys.RED;
            case 20:return RemoteKeys.GREEN; case 21:return RemoteKeys.YELLOW;
            case 22:return RemoteKeys.BLUE; case 23:return RemoteKeys.PLAY;
            case 24:return RemoteKeys.PAUSE; case 25:return RemoteKeys.STOP;
            case 26:return RemoteKeys.REWIND; case 27:return RemoteKeys.FAST_FORWARD;
            case 28:return RemoteKeys.SUBTITLE; case 29:return RemoteKeys.EXIT;
            case 30:return RemoteKeys.CC; default:break;
        }
        return i-31;
    }
}
