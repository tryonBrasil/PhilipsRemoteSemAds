package com.example.philipsremote;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Testes dos codificadores IR contra vetores conhecidos de cada protocolo. */
public class IrEncoderTest {

    /** Decodifica pares marca/espaço (bit = espaço longo) a partir de 'ini'. */
    private static int[] bytesPorEspaco(int[] p, int ini, int nBytes, int oneSpace, int zeroSpace, boolean msb) {
        int[] out = new int[nBytes];
        int lim = (oneSpace + zeroSpace) / 2;
        for (int b = 0; b < nBytes; b++) {
            int v = 0;
            for (int i = 0; i < 8; i++) {
                int bit = p[ini + 2 * (b * 8 + i) + 1] > lim ? 1 : 0;
                if (msb) v = (v << 1) | bit; else v |= bit << i;
            }
            out[b] = v;
        }
        return out;
    }

    @Test public void necLgPower() {
        int[] p = IrEncoder.nec(0x04, 0x08);
        assertEquals(9000, p[0]); assertEquals(4500, p[1]);
        assertArrayEquals(new int[]{0x04, 0xFB, 0x08, 0xF7}, bytesPorEspaco(p, 2, 4, 1690, 560, false));
        assertEquals(68, p.length);
    }

    @Test public void samsungRepeteEndereco() {   // Samsung power = 0xE0E040BF (MSB), endereço 0x07 repetido
        int[] p = IrEncoder.samsung(0x07, 0x02);
        assertEquals(4500, p[0]); assertEquals(4500, p[1]);
        assertArrayEquals(new int[]{0x07, 0x07, 0x02, 0xFD}, bytesPorEspaco(p, 2, 4, 1690, 560, false));
    }

    @Test public void sonyBitNaMarcaE45ms() {
        int addr = 0x01, cmd = 0x15;                // Sony TV power
        int[] p = IrEncoder.sony(addr, cmd);
        List<Integer> inicios = new ArrayList<>();
        for (int i = 0; i < p.length; i++) if (p[i] == 2400) inicios.add(i);
        assertEquals(3, inicios.size());
        int t = inicios.get(0);
        int c = 0, a = 0;
        for (int i = 0; i < 7; i++) if (p[t + 2 + 2 * i] == 1200) c |= 1 << i;
        for (int i = 0; i < 5; i++) if (p[t + 2 + 14 + 2 * i] == 1200) a |= 1 << i;
        assertEquals(cmd, c); assertEquals(addr, a);
        for (int i = 0; i < 11; i++) assertEquals(600, p[t + 3 + 2 * i]);      // espaços de dados sempre 600
        assertEquals(25800, p[t + 3 + 2 * 11]);                                 // último espaço = 600 + intervalo até 45 ms
        int soma = 0;
        for (int i = inicios.get(0); i < inicios.get(1); i++) soma += p[i];
        assertEquals(45000, soma);                                              // período do quadro
    }

    /** Expande o padrão em níveis (1=portadora) numa grade de 'unidade' µs. */
    private static List<Integer> niveis(int[] p, int ini, int unidade, int primeiroNivel) {
        List<Integer> l = new ArrayList<>();
        for (int i = ini; i < p.length; i++) {
            int n = Math.round(p[i] / (float) unidade);
            int nivel = (i - ini) % 2 == 0 ? primeiroNivel : 1 - primeiroNivel;
            for (int k = 0; k < n; k++) l.add(nivel);
        }
        return l;
    }

    @Test public void rc5Standby() {               // RC5 endereço 0, comando 12, toggle 0 → 11 0 00000 001100
        int[] p = IrEncoder.rc5(0, 12, false);
        List<Integer> h = niveis(p, 0, 889, 1);
        h.add(0, 0);                                // meio-bit de espaço inicial do bit '1' (não transmitido)
        StringBuilder bits = new StringBuilder();
        for (int i = 0; i + 1 < h.size(); i += 2) bits.append(h.get(i) == 0 && h.get(i + 1) == 1 ? '1' : '0');
        assertEquals("11000000001100", bits.toString());
    }

    @Test public void rc6Modo0() {                 // endereço 0x00, comando 0x0C (power), toggle 1
        int[] p = IrEncoder.rc6(0x00, 0x0C, true);
        assertEquals(2666, p[0]); assertEquals(889, p[1]);
        List<Integer> u = niveis(p, 2, 444, 1);
        // esperado bit a bit: start=1, modo=000, trailer=1, addr=0x00, cmd=0x0C
        StringBuilder exp = new StringBuilder("10");                      // start (1 → marca,espaço)
        for (int i = 0; i < 3; i++) exp.append("01");                     // modo 0
        exp.append("1100");                                               // toggle 1 (largura dupla)
        for (int i = 0; i < 8; i++) exp.append("01");                     // endereço 0x00
        int cmd = 0x0C;
        for (int m = 0x80; m != 0; m >>= 1) exp.append((cmd & m) != 0 ? "10" : "01");
        StringBuilder got = new StringBuilder();
        for (int i = 0; i < exp.length(); i++) got.append(u.get(i));
        assertEquals(exp.toString(), got.toString());
    }

    @Test public void coolixOffEQuadroDuplicado() {
        int[] p = IrEncoder.coolix(0xB27BE0);
        assertEquals(4692, p[0]); assertEquals(4416, p[1]);
        assertArrayEquals(new int[]{0xB2, 0x4D, 0x7B, 0x84, 0xE0, 0x1F}, bytesPorEspaco(p, 2, 6, 1656, 552, true));
        assertEquals(552, p[98]); assertEquals(5244, p[99]);               // rodapé + intervalo
        assertEquals(4692, p[100]);                                         // 2º quadro
        assertEquals(199, p.length);
    }

    @Test public void mideaLegadoUsaQuadroCoolix() {
        assertArrayEquals(IrEncoder.coolix(0xB27BE0), IrEncoder.midea(0x7BE0));
    }

    @Test public void panasonicEstrutura() {
        int[] p = IrEncoder.panasonic(0x3D);
        assertEquals(99, p.length);
        assertEquals(3456, p[0]); assertEquals(1728, p[1]);
    }

    @Test public void rawTrataUltimoImparComoEspaco() {
        assertArrayEquals(new int[]{1, 5}, IrEncoder.raw(new int[]{1, 2, 3}));
        assertArrayEquals(new int[]{10, 20, 30, 40}, IrEncoder.raw(new int[]{10, 20, 30, 40}));
    }

    @Test public void patternNaoGeraZerosNemComecaPorEspaco() {
        int[] p = new IrEncoder.Pattern().space(500).mark(100).space(0).space(50).mark(10).toArray();
        assertArrayEquals(new int[]{100, 50, 10}, p);
    }

    @Test public void intervalosLongosSaoSempreEspacos() {
        int[][] todos = {
            IrEncoder.nec(4, 8), IrEncoder.samsung(7, 2), IrEncoder.sony(1, 0x15), IrEncoder.rc5(0, 12, true),
            IrEncoder.rc6(0, 12, false), IrEncoder.coolix(0xB27BE0), IrEncoder.panasonic(0x3D)
        };
        for (int[] p : todos) {
            for (int i = 1; i < p.length; i++) {
                assertTrue("duração inválida", p[i] > 0);
                if (p[i] >= 5000) assertTrue("intervalo longo caiu numa marca (índice " + i + ")", i % 2 == 1);
            }
        }
    }
}
