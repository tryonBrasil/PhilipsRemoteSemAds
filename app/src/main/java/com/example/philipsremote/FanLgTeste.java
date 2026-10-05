package com.example.philipsremote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FanLgTeste {
    private static final int FREQ = 38000;
    private static final int UNIT = 560;
    private final ConsumerIrManager ir;
    private final List<Integer> candidatos = new ArrayList<>();
    private int pos = 0;

    public FanLgTeste(Context context) {
        ir = (ConsumerIrManager) context.getSystemService(Context.CONSUMER_IR_SERVICE);
        int[] prioritarios = {
            0x08,0x09,0x02,0x03,0x00,0x01,0x40,0x41,0x07,0x06,
            0x44,0x28,0x43,0x7C,0x0B,0xAA,0xAB,0x72,0x71,0x63,
            0x61,0xB0,0xB1,0xBA,0x8F,0x8E,0x39,0x5B,0xB5
        };
        for (int c : prioritarios) add(c);
        for (int c = 0; c <= 0xFF; c++) if (!candidatos.contains(c)) candidatos.add(c);
    }

    private void add(int code) {
        code &= 0xFF;
        if (!candidatos.contains(code)) candidatos.add(code);
    }

    public boolean hasEmitter() { return ir != null && ir.hasIrEmitter(); }

    public String next() {
        if (!hasEmitter()) return "Emissor IR não detectado";
        if (pos >= candidatos.size()) return "Fim da varredura LG — 256 códigos testados";
        int code = candidatos.get(pos);
        int numero = pos + 1;
        try {
            ir.transmit(FREQ, lgNec(code));
            pos++;
            return String.format(Locale.US, "LG 0x%02X • teste %d/%d", code, numero, candidatos.size());
        } catch (Exception e) {
            pos++;
            return String.format(Locale.US, "ERRO LG 0x%02X • %s", code, e.getMessage());
        }
    }

    public int currentCodeValue() { return pos == 0 ? -1 : candidatos.get(pos - 1); }
    public int position() { return pos; }
    public int total() { return candidatos.size(); }
    public void reset() { pos = 0; }

    private int[] lgNec(int data) {
        int[] bytes = {0x04, 0xFB, data & 0xFF, (~data) & 0xFF};
        ArrayList<Integer> p = new ArrayList<>();
        append(p, true, 9000); append(p, false, 4500);
        for (int b : bytes) for (int m = 1; m <= 0x80; m <<= 1) {
            append(p, true, UNIT);
            append(p, false, (b & m) != 0 ? 1690 : 560);
        }
        append(p, true, UNIT); append(p, false, 20000);
        int[] out = new int[p.size()];
        for (int i = 0; i < p.size(); i++) out[i] = p.get(i);
        return out;
    }

    private void append(ArrayList<Integer> p, boolean mark, int duration) {
        if (duration <= 0) return;
        if (p.isEmpty()) { if (!mark) p.add(0); p.add(duration); return; }
        boolean expectedMark = (p.size() % 2 == 1);
        if (expectedMark == mark) {
            int i = p.size() - 1;
            p.set(i, p.get(i) + duration);
        } else p.add(duration);
    }
}
