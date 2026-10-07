package com.example.philipsremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.ConsumerIrManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perfis de teste/transmissão IR: lista de candidatos por perfil, varredura 0x00-0xFF,
 * envio de códigos salvos e banco de códigos de teste. A geração dos sinais fica em {@link IrEncoder}.
 */
public class IrPerfilTeste {
    public static final String PERFIL_VENTILADOR = "Ventilador Universal";
    private static final int NEC = 1, SAMSUNG = 2, SONY = 3, RC5 = 4, RC6 = 5, COOLIX = 6, MIDEA = 7, PANASONIC = 8, FAN = 9;
    private static final String BANK_KEY = "codes_bank_v2", OLD_BANK_KEY = "fan_test_saved_v1";

    private static class Item {
        final String nome; final int tipo, addr, cmd, code;
        Item(String n, int t, int a, int c) { nome = n; tipo = t; addr = a; cmd = c; code = 0; }
        Item(String n, int t, int c) { nome = n; tipo = t; addr = 0; cmd = 0; code = c; }
    }

    private static class Sinal {
        final int freq; final int[] padrao;
        Sinal(int f, int[] p) { freq = f; padrao = p; }
    }

    private final ConsumerIrManager ir;
    private final SharedPreferences prefs;
    private final List<Item> itens = new ArrayList<>();
    private int pos = 0;
    private String perfil = "LG / NEC";
    private boolean rc5Toggle = false, rc6Toggle = false, varredura = false;
    private int manual = -1;
    private String descManual = "";

    public IrPerfilTeste(Context c) {
        ir = (ConsumerIrManager) c.getSystemService(Context.CONSUMER_IR_SERVICE);
        prefs = c.getSharedPreferences("ir_test_codes", Context.MODE_PRIVATE);
        selecionar("LG / NEC");
    }

    public boolean hasEmitter() { return ir != null && ir.hasIrEmitter(); }

    /** Transmite um padrão RAW já convertido para marca/espaço em microssegundos. */
    public boolean transmitirRaw(int frequencia, int[] padrao) {
        if (!hasEmitter() || frequencia <= 0 || padrao == null || padrao.length == 0) return false;
        try {
            tx(frequencia, IrEncoder.raw(padrao));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Usa a frequência pedida se o emissor suporta; senão, a suportada mais próxima. */
    private int freq(int desejada) {
        if (ir == null) return desejada;
        try {
            ConsumerIrManager.CarrierFrequencyRange[] ranges = ir.getCarrierFrequencies();
            if (ranges == null || ranges.length == 0) return desejada;
            int melhor = desejada; long distancia = Long.MAX_VALUE;
            for (ConsumerIrManager.CarrierFrequencyRange r : ranges) {
                int candidato = desejada;
                if (desejada < r.getMinFrequency()) candidato = r.getMinFrequency();
                else if (desejada > r.getMaxFrequency()) candidato = r.getMaxFrequency();
                long d = Math.abs((long) candidato - desejada);
                if (d < distancia) { distancia = d; melhor = candidato; }
                if (d == 0) break;
            }
            return melhor;
        } catch (Exception e) { return desejada; }
    }

    private void tx(int desejada, int[] padrao) { ir.transmit(freq(desejada), padrao); }

    public String[] perfis() {
        return new String[]{"LG / NEC", "Samsung TV", "Sony TV", "Philips / RC5", "Philips / RC6", "Panasonic TV",
            "AOC / NEC", "TCL / NEC", "Philco / NEC", "Semp / NEC", "Toshiba / JVC / NEC",
            "AC Coolix", "AC Midea", PERFIL_VENTILADOR};
    }

    public static int frequenciaPadrao(String p) {
        if (p == null) return 38000;
        if (p.contains("Sony")) return 40000;
        if (p.contains("Philips")) return 36000;
        if (p.equals("Panasonic TV")) return 37000;
        return 38000;
    }

    public static int enderecoPadrao(String p) {
        if ("LG / NEC".equals(p)) return 0x04;
        if ("Samsung TV".equals(p)) return 0x07;
        if ("Sony TV".equals(p)) return 0x01;
        return 0x00;
    }

    private static boolean familiaNec(String p) {
        return "AOC / NEC".equals(p) || "TCL / NEC".equals(p) || "Philco / NEC".equals(p)
            || "Semp / NEC".equals(p) || "Toshiba / JVC / NEC".equals(p);
    }

    private void limpar(String p) { perfil = p; itens.clear(); pos = 0; rc6Toggle = false; rc5Toggle = false; manual = -1; descManual = ""; }

    public void selecionar(String p) {
        limpar(p); varredura = false;
        if (p.equals("LG / NEC")) {
            int[] cs = {0x08, 0x09, 0x02, 0x03, 0x00, 0x01, 0x40, 0x41, 0x07, 0x06, 0x44, 0x28, 0x43, 0x7C, 0x0B, 0xAA, 0xAB, 0x72, 0x71, 0x63, 0x61, 0xB0, 0xB1, 0xBA, 0x8F, 0x8E, 0x39, 0x5B, 0xB5, 0x18};
            for (int c : cs) itens.add(new Item(String.format(Locale.US, "NEC addr 0x04 • 0x%02X", c), NEC, 0x04, c));
        } else if (p.equals("Samsung TV")) {
            int[] cs = {0x02, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x1A, 0x1F, 0x58, 0x60, 0x61, 0x62, 0x65, 0x68, 0x79};
            for (int c : cs) itens.add(new Item(String.format(Locale.US, "Samsung32 addr 0x07 • 0x%02X", c), SAMSUNG, 0x07, c));
        } else if (p.equals("Sony TV")) {
            int[] cs = {0x15, 0x12, 0x13, 0x10, 0x11, 0x14, 0x19, 0x16, 0x17, 0x18, 0x1A, 0x0D, 0x2A, 0x74};
            for (int c : cs) itens.add(new Item(String.format(Locale.US, "SIRC addr 0x01 • 0x%02X", c), SONY, 0x01, c));
        } else if (p.equals("Philips / RC5")) {
            int[] cs = {0x0C, 0x10, 0x11, 0x12, 0x13, 0x14, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x38, 0x3D};
            for (int c : cs) itens.add(new Item(String.format(Locale.US, "RC5 addr 0x00 • 0x%02X", c), RC5, 0x00, c));
        } else if (p.equals("Philips / RC6")) {
            int[] cs = {0x0C, 0x10, 0x11, 0x12, 0x13, 0x14, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x38, 0x3D};
            for (int c : cs) itens.add(new Item(String.format(Locale.US, "RC6 addr 0x00 • 0x%02X", c), RC6, 0x00, c));
        } else if (p.equals("Panasonic TV")) {
            int[] cs = {0x00, 0x4C, 0x04, 0x84, 0x2C, 0xAC, 0x72, 0xF2, 0x52, 0xD2, 0x92, 0x4A, 0x0E, 0x4E, 0x8E, 0xCE, 0xEC, 0x6D, 0x4F, 0xF1, 0x03, 0x83, 0x43, 0xC3, 0x23, 0xA3, 0x63, 0xE3};
            for (int c : cs) itens.add(new Item(String.format(Locale.US, "Panasonic Kaseikyo • função 0x%02X", c), PANASONIC, c));
        } else if (p.equals("Toshiba / JVC / NEC")) {
            int[] addrs = {0x00, 0x01, 0x02, 0x04, 0x10, 0x40};
            int[] cs = {0x08, 0x02, 0x03, 0x09, 0x00, 0x01, 0x40, 0x41, 0x06, 0x07, 0x44, 0x43, 0x0B};
            for (int a : addrs) for (int c : cs)
                itens.add(new Item(String.format(Locale.US, "NEC addr 0x%02X • 0x%02X", a, c), NEC, a, c));
        } else if (familiaNec(p)) {
            int[] addrs = {0x00, 0x01, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80};
            int[] cs = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x1B, 0x20, 0x21, 0x22, 0x40, 0x41, 0x43, 0x44};
            for (int a : addrs) for (int c : cs)
                itens.add(new Item(String.format(Locale.US, "%s addr 0x%02X • 0x%02X", p.replace(" / NEC", ""), a, c), NEC, a, c));
        } else if (p.equals("AC Coolix")) {
            int[] cs = {0xB27BE0, 0xB27B00, 0xB27B20, 0xB27B40, 0xB27B60, 0xB27B80, 0xB27BA0, 0xB27BC0};
            for (int c : cs) itens.add(new Item(String.format(Locale.US, "Coolix 24-bit • 0x%06X", c), COOLIX, c));
        } else if (p.equals("AC Midea")) {
            itens.add(new Item("Midea • Power OFF", MIDEA, 0x7BE0));
            itens.add(new Item("Midea • Cool 22°C", MIDEA, 0xBF70));
            itens.add(new Item("Midea • Cool 23°C", MIDEA, 0xBF78));
            itens.add(new Item("Midea • Cool 24°C", MIDEA, 0xBF80));
            itens.add(new Item("Midea • LED/Turbo candidato", MIDEA, 0xA545));
        } else if (p.equals(PERFIL_VENTILADOR)) {
            itens.add(new Item("Ventilador • Ligar / Desligar", FAN, 1));
            itens.add(new Item("Ventilador • Oscilação", FAN, 2));
            itens.add(new Item("Ventilador • Velocidade", FAN, 3));
            itens.add(new Item("Ventilador • Timer", FAN, 4));
            itens.add(new Item("Ventilador • Noturno", FAN, 5));
        }
    }

    /**
     * Varredura completa de comandos do perfil (todas as teclas possíveis) usando um endereço fixo
     * (ex.: o endereço do controle já salvo). Perfis sem espaço de comandos numérico usam a lista normal.
     */
    public void selecionarVarredura(String p, int addrBase) {
        int addr = addrBase >= 0 ? addrBase : enderecoPadrao(p);
        int tipo = 0, max = 255;
        if (p.equals("LG / NEC") || familiaNec(p)) tipo = NEC;
        else if (p.equals("Samsung TV")) tipo = SAMSUNG;
        else if (p.equals("Sony TV")) { tipo = SONY; max = 127; addr &= 0x1F; }
        else if (p.equals("Philips / RC5")) { tipo = RC5; max = 127; addr &= 0x1F; }
        else if (p.equals("Philips / RC6")) tipo = RC6;
        if (tipo == 0 && !p.equals("Panasonic TV")) { selecionar(p); return; }
        limpar(p); varredura = true;
        for (int c = 0; c <= max; c++) {
            if (p.equals("Panasonic TV")) itens.add(new Item(String.format(Locale.US, "Panasonic • função 0x%02X", c), PANASONIC, c));
            else itens.add(new Item(String.format(Locale.US, "%s addr 0x%02X • 0x%02X", p, addr, c), tipo, addr, c));
        }
    }

    public boolean emVarredura() { return varredura; }
    public String getPerfil() { return perfil; }
    public int position() { return pos; }
    public int total() { return itens.size(); }
    public void reset() { pos = 0; rc6Toggle = false; rc5Toggle = false; manual = -1; descManual = ""; }

    /** Código do último teste (candidato transmitido ou código digitado), ou -1. */
    public int currentCode() {
        if (manual >= 0) return manual;
        if (pos == 0 || itens.isEmpty()) return -1;
        Item x = itens.get(pos - 1);
        if (x.tipo == NEC || x.tipo == SAMSUNG || x.tipo == SONY || x.tipo == RC5 || x.tipo == RC6)
            return ((x.addr & 0xFF) << 8) | (x.cmd & 0xFF);
        return x.code;
    }

    /** Descrição do último teste (vai para o campo "descrição" do controle salvo). */
    public String descricaoAtual() {
        if (manual >= 0) return descManual;
        return pos == 0 || itens.isEmpty() ? "" : itens.get(pos - 1).nome;
    }

    private Sinal sinalItem(Item x) {
        switch (x.tipo) {
            case NEC: return new Sinal(38000, IrEncoder.nec(x.addr, x.cmd));
            case SAMSUNG: return new Sinal(38000, IrEncoder.samsung(x.addr, x.cmd));
            case SONY: return new Sinal(40000, IrEncoder.sony(x.addr, x.cmd));
            case RC5: rc5Toggle = !rc5Toggle; return new Sinal(36000, IrEncoder.rc5(x.addr, x.cmd, rc5Toggle));
            case RC6: rc6Toggle = !rc6Toggle; return new Sinal(36000, IrEncoder.rc6(x.addr, x.cmd, rc6Toggle));
            case COOLIX: return new Sinal(38000, IrEncoder.coolix(x.code));
            case MIDEA: return new Sinal(38000, IrEncoder.midea(x.code));
            case PANASONIC: return new Sinal(37000, IrEncoder.panasonic(x.code));
            case FAN: return new Sinal(38000, IrEncoder.raw(fanRaw(x.code)));
            default: return null;
        }
    }

    /** Sinal de um código salvo: abaixo de 256 só vale o comando e o endereço é o padrão do perfil. */
    private Sinal sinalDe(String p, int codigo) {
        int addr = (codigo >> 8) & 0xFF, cmd = codigo & 0xFF;
        boolean comAddr = codigo > 255;
        if (p.equals("LG / NEC")) return new Sinal(38000, IrEncoder.nec(comAddr ? addr : 0x04, cmd));
        if (p.equals("Samsung TV")) return new Sinal(38000, IrEncoder.samsung(comAddr ? addr : 0x07, cmd));
        if (p.equals("Sony TV")) return new Sinal(40000, IrEncoder.sony(comAddr ? addr & 0x1F : 0x01, cmd & 0x7F));
        if (p.equals("Philips / RC5")) { rc5Toggle = !rc5Toggle; return new Sinal(36000, IrEncoder.rc5(comAddr ? addr & 0x1F : 0, cmd & 0x7F, rc5Toggle)); }
        if (p.equals("Philips / RC6")) { rc6Toggle = !rc6Toggle; return new Sinal(36000, IrEncoder.rc6(comAddr ? addr : 0, cmd, rc6Toggle)); }
        if (p.equals("Panasonic TV")) return new Sinal(37000, IrEncoder.panasonic(cmd));
        if (familiaNec(p)) return new Sinal(38000, IrEncoder.nec(comAddr ? addr : 0x00, cmd));
        if (p.equals("AC Coolix")) return new Sinal(38000, IrEncoder.coolix(codigo));
        if (p.equals("AC Midea")) return new Sinal(38000, IrEncoder.midea(codigo));
        if (p.equals(PERFIL_VENTILADOR)) return new Sinal(38000, IrEncoder.raw(fanRaw(codigo)));
        return null;
    }

    /** Transmite um código já confirmado e salvo pelo usuário (frequência padrão do perfil). */
    public boolean transmitirSalvo(String perfilSalvo, int codigo) { return transmitirSalvo(perfilSalvo, codigo, 0); }

    /** Transmite usando a frequência salva quando ela é válida; 0 usa a frequência padrão do perfil. */
    public boolean transmitirSalvo(String perfilSalvo, int codigo, int frequenciaSalva) {
        if (!hasEmitter() || codigo < 0 || perfilSalvo == null) return false;
        try {
            Sinal s = sinalDe(perfilSalvo, codigo);
            if (s == null) return false;
            tx(frequenciaSalva > 0 ? frequenciaSalva : s.freq, s.padrao);
            return true;
        } catch (Exception e) { return false; }
    }

    public boolean transmitirVentilador(int funcao) {
        return funcao >= 1 && funcao <= 5 && transmitirSalvo(PERFIL_VENTILADOR, funcao, 0);
    }

    /** Testa um código digitado (hex) usando o perfil selecionado; ele passa a ser o "código atual". */
    public boolean transmitManual(String value) {
        if (!hasEmitter() || value == null) return false;
        try {
            String s = value.trim().replace("0x", "").replace("0X", "").replace(" ", "");
            if (s.isEmpty() || s.length() > 6) return false;
            int codigo = (int) Long.parseLong(s, 16);
            if (s.length() <= 2) codigo &= 0xFF;
            Sinal sg = sinalDe(perfil, codigo);
            if (sg == null) return false;
            tx(sg.freq, sg.padrao);
            manual = codigo;
            descManual = String.format(Locale.US, "%s • manual 0x%X", perfil, codigo);
            return true;
        } catch (Exception e) { return false; }
    }

    public String next() {
        if (!hasEmitter()) return "Emissor IR não detectado";
        if (pos >= itens.size()) return "Fim: " + perfil;
        manual = -1;
        Item x = itens.get(pos++);
        try {
            Sinal s = sinalItem(x);
            if (s != null) tx(s.freq, s.padrao);
            return String.format(Locale.US, "%s • %d/%d", x.nome, pos, itens.size());
        } catch (Exception e) { return String.format(Locale.US, "ERRO %s: %s", x.nome, e.getMessage()); }
    }

    /** Volta um candidato e o retransmite (assim o código "atual" é sempre o que acabou de ser enviado). */
    public String previous() {
        if (!hasEmitter()) return "Emissor IR não detectado";
        if (itens.isEmpty()) return "Nenhum código disponível.";
        if (pos <= 1) { pos = 0; manual = -1; return "Este é o primeiro candidato."; }
        pos -= 2;
        return next();
    }

    // ---- banco de códigos de teste (linhas "perfil|nome|codigo") ----

    public String saveCurrentCode(String nome) {
        int codigo = currentCode();
        if (codigo < 0) return "Nenhum código testado.";
        String n = (nome == null || nome.trim().isEmpty()) ? "Código " + (savedCount() + 1) : nome.trim();
        String old = savedCodes();
        String item = perfil + "|" + n.replace("|", "/").replace("\n", " ") + "|" + codigo;
        prefs.edit().putString(BANK_KEY, old.isEmpty() ? item : old + "\n" + item).apply();
        return "Código salvo: " + n;
    }

    public String savedCodes() {
        String s = prefs.getString(BANK_KEY, null);
        if (s == null) {                                   // migra o formato antigo
            String old = prefs.getString(OLD_BANK_KEY, "");
            s = old.replace("\n", "\n");
            if (!old.isEmpty()) prefs.edit().putString(BANK_KEY, s).remove(OLD_BANK_KEY).apply();
        }
        return s;
    }

    public int savedCount() { String s = savedCodes(); return s.isEmpty() ? 0 : s.split("\n").length; }
    public void clearSavedCodes() { prefs.edit().remove(BANK_KEY).remove(OLD_BANK_KEY).apply(); }

    /** Código de uma linha do banco, ou -1 se a linha estiver malformada (nunca lança exceção). */
    public static int codigoDaLinha(String linha) {
        try { String[] p = linha.split("\\|"); return Integer.parseInt(p[p.length - 1].trim()); }
        catch (Exception e) { return -1; }
    }

    /** "nome • perfil • 0xHEX" para exibir uma linha do banco. */
    public static String rotuloDaLinha(String linha) {
        try {
            String[] p = linha.split("\\|");
            return p[1] + "  •  " + p[0] + "  •  0x" + Integer.toHexString(Integer.parseInt(p[p.length - 1].trim())).toUpperCase(Locale.US);
        } catch (Exception e) { return linha; }
    }

    public static String perfilDaLinha(String linha) {
        int i = linha.indexOf('|');
        return i > 0 ? linha.substring(0, i) : "";
    }

    private static int[] fanRaw(int f) {
        switch (f) {
            case 1: return new int[]{1210,368,1210,368,368,1210,1210,368,1210,368,368,1210,368,1210,368,1210,368,1210,368,1210,368,1210,1210,7074};
            case 2: return new int[]{1210,368,1210,368,368,1210,1210,368,1210,368,368,1210,368,1210,1210,368,368,1210,368,1210,368,1210,368,7889};
            case 3: return new int[]{1183,368,1183,368,368,1183,1183,368,1183,368,368,1183,368,1183,368,1183,368,1183,368,1183,1183,368,368,7889};
            case 4: return new int[]{1183,368,1183,368,368,1183,1183,368,1183,368,368,1183,368,1183,368,1183,1183,368,368,1183,368,1183,368,7889};
            case 5: return new int[]{1183,368,1183,368,368,1183,1183,368,1183,368,1183,368,368,1183,1183,368,1183,368,1183,368,1183,368,1183,368,7889};
            default: return new int[]{1210, 368};
        }
    }
}
