package com.example.philipsremote;

import java.util.ArrayList;
import java.util.List;

/**
 * Codificadores IR puros (sem dependência de Android): geram o padrão
 * marca/espaço em microssegundos aceito por {@code ConsumerIrManager.transmit()}.
 * Por não depender do Android, podem ser testados na JVM (ver IrEncoderTest).
 */
public final class IrEncoder {
    private IrEncoder() {}

    /** Acumula marcas/espaços alternados; durações seguidas do mesmo tipo são somadas. */
    static final class Pattern {
        private final List<Integer> d = new ArrayList<>();
        private boolean lastMark;

        Pattern mark(int us) { return add(true, us); }
        Pattern space(int us) { return add(false, us); }

        Pattern add(boolean mark, int us) {
            if (us <= 0) return this;
            if (d.isEmpty()) {
                if (!mark) return this;           // silêncio inicial não é transmitido
                d.add(us); lastMark = true; return this;
            }
            if (mark == lastMark) {
                int i = d.size() - 1;
                d.set(i, d.get(i) + us);
            } else {
                d.add(us); lastMark = mark;
            }
            return this;
        }

        int total() { int t = 0; for (int v : d) t += v; return t; }

        int[] toArray() {
            int[] o = new int[d.size()];
            for (int i = 0; i < o.length; i++) o[i] = d.get(i);
            return o;
        }
    }

    private static void bytesLsb(Pattern p, int[] bytes, int markUs, int oneSpace, int zeroSpace) {
        for (int v : bytes)
            for (int m = 1; m <= 0x80; m <<= 1)
                p.mark(markUs).space((v & m) != 0 ? oneSpace : zeroSpace);
    }

    /** NEC padrão: endereço, ~endereço, comando, ~comando (LSB primeiro). 38 kHz. */
    public static int[] nec(int addr, int cmd) {
        int[] b = {addr & 255, ~addr & 255, cmd & 255, ~cmd & 255};
        Pattern p = new Pattern().mark(9000).space(4500);
        bytesLsb(p, b, 560, 1690, 560);
        return p.mark(560).space(20000).toArray();
    }

    /** Samsung32: endereço REPETIDO (não invertido), comando, ~comando. 38 kHz. */
    public static int[] samsung(int addr, int cmd) {
        int[] b = {addr & 255, addr & 255, cmd & 255, ~cmd & 255};
        Pattern p = new Pattern().mark(4500).space(4500);
        bytesLsb(p, b, 560, 1690, 560);
        return p.mark(560).space(20000).toArray();
    }

    /** Sony SIRC 12 bits: 7 de comando + 5 de endereço, LSB primeiro, bit na MARCA, 3 quadros a cada 45 ms. 40 kHz. */
    public static int[] sony(int addr, int cmd) {
        Pattern p = new Pattern();
        for (int r = 0; r < 3; r++) {
            int ini = p.total();
            p.mark(2400).space(600);
            for (int i = 0; i < 7; i++) p.mark(((cmd >> i) & 1) != 0 ? 1200 : 600).space(600);
            for (int i = 0; i < 5; i++) p.mark(((addr >> i) & 1) != 0 ? 1200 : 600).space(600);
            if (r < 2) p.space(Math.max(45000 - (p.total() - ini), 5000));
        }
        return p.toArray();
    }

    private static void rc5Bit(Pattern p, int bit) {          // 1 = espaço→marca, 0 = marca→espaço
        if (bit == 1) p.space(889).mark(889); else p.mark(889).space(889);
    }

    /** Philips RC5: 14 bits (S1, S2/campo, toggle, 5 de endereço, 6 de comando). 36 kHz. */
    public static int[] rc5(int addr, int cmd, boolean toggle) {
        Pattern p = new Pattern();
        rc5Bit(p, 1);
        rc5Bit(p, ((cmd >> 6) & 1) == 0 ? 1 : 0);              // S2 = ~bit 6 do comando (RC5 estendido)
        rc5Bit(p, toggle ? 1 : 0);
        for (int m = 16; m != 0; m >>= 1) rc5Bit(p, (addr & m) != 0 ? 1 : 0);
        for (int m = 32; m != 0; m >>= 1) rc5Bit(p, (cmd & m) != 0 ? 1 : 0);
        return p.toArray();
    }

    private static void rc6Bit(Pattern p, int bit, int half) { // 1 = marca→espaço, 0 = espaço→marca
        if (bit == 1) p.mark(half).space(half); else p.space(half).mark(half);
    }

    /** Philips RC6 modo 0: líder, start, modo 000, trailer (toggle, largura dupla), 8+8 bits. 36 kHz. */
    public static int[] rc6(int addr, int cmd, boolean toggle) {
        final int t = 444;
        Pattern p = new Pattern().mark(2666).space(889);
        rc6Bit(p, 1, t);
        rc6Bit(p, 0, t); rc6Bit(p, 0, t); rc6Bit(p, 0, t);
        rc6Bit(p, toggle ? 1 : 0, 2 * t);
        for (int m = 0x80; m != 0; m >>= 1) rc6Bit(p, (addr & m) != 0 ? 1 : 0, t);
        for (int m = 0x80; m != 0; m >>= 1) rc6Bit(p, (cmd & m) != 0 ? 1 : 0, t);
        return p.space(2666).toArray();
    }

    /**
     * Coolix (usado por muitos ARs, incl. Midea/Springer no Brasil): 3 bytes, cada um seguido do seu
     * inverso, MSB primeiro (OFF = B2 4D 7B 84 E0 1F), quadro enviado 2 vezes. 38 kHz.
     */
    public static int[] coolix(int code24) {
        int[] b = {(code24 >> 16) & 255, (code24 >> 8) & 255, code24 & 255};
        Pattern p = new Pattern();
        for (int rep = 0; rep < 2; rep++) {
            p.mark(4692).space(4416);
            for (int v : b) {
                for (int k = 0; k < 2; k++) {
                    int x = k == 0 ? v : (~v & 255);
                    for (int m = 0x80; m != 0; m >>= 1) p.mark(552).space((x & m) != 0 ? 1656 : 552);
                }
            }
            p.mark(552);
            if (rep == 0) p.space(5244);
        }
        return p.toArray();
    }

    /** Perfil legado "AC Midea": o prefixo B2 indica, na prática, o protocolo Coolix. */
    public static int[] midea(int shortCode) { return coolix(0xB20000 | (shortCode & 0xFFFF)); }

    /** Panasonic/Kaseikyo. ATENÇÃO: ordem de bits do ID do fabricante NÃO verificada contra uma TV real. */
    public static int[] panasonic(int function) {
        final int unit = 432, device = 0x01, sub = 0x00;
        int[] bytes = {0x40, 0x04, device, sub, function & 0xFF, (device ^ sub ^ function) & 0xFF};
        Pattern p = new Pattern().mark(8 * unit).space(4 * unit);
        bytesLsb(p, bytes, unit, 3 * unit, unit);
        return p.mark(unit).toArray();
    }

    /**
     * Padrões brutos (marca, espaço, marca, ...): remove zeros e, se o vetor tiver tamanho ímpar,
     * trata o último valor como ESPAÇO (intervalo final) em vez de uma marca de portadora ligada.
     */
    public static int[] raw(int[] in) {
        Pattern p = new Pattern();
        boolean impar = in.length % 2 == 1;
        for (int i = 0; i < in.length; i++) {
            boolean marca = i % 2 == 0 && !(impar && i == in.length - 1);
            p.add(marca, in[i]);
        }
        return p.toArray();
    }
}
