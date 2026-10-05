package com.example.philipsremote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import java.util.ArrayList;
import java.util.List;

/**
 * Testador experimental para encontrar o protocolo/código do PW-789 / LE-7507.
 *
 * IMPORTANTE:
 * Estes candidatos NÃO são apresentados como códigos originais confirmados.
 * Eles servem apenas para testar combinações comuns de NEC/IR no receptor.
 */
public class FanIrTeste {
    private static final int FREQ = 38000;
    private static final int UNIT = 560;

    private final Context context;
    private final ConsumerIrManager ir;
    private final List<Candidato> candidatos = new ArrayList<>();
    private int index = 0;

    public FanIrTeste(Context context, ConsumerIrManager ir) {
        this.context = context;
        this.ir = ir;
        montarCandidatos();
    }

    private void montarCandidatos() {
        // Primeira rodada: comandos mais prováveis para liga/desliga,
        // velocidades, luz, reversão e timer, em endereços NEC comuns.
        int[] enderecos = {0x00, 0x01, 0x10, 0x20};
        int[] comandos = {
                0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
                0x10, 0x11, 0x12, 0x13, 0x14, 0x15,
                0x20, 0x21, 0x22, 0x23, 0x24, 0x25,
                0x30, 0x31, 0x32, 0x33, 0x34,
                0x40, 0x41, 0x42, 0x43, 0x44
        };

        for (int endereco : enderecos) {
            for (int comando : comandos) {
                String nome = String.format(
                        "NEC 38kHz • endereço 0x%02X • comando 0x%02X",
                        endereco, comando
                );
                candidatos.add(new Candidato(nome, endereco, comando));
            }
        }
    }

    /**
     * Envia o próximo candidato e retorna seu nome.
     */
    public String next() {
        if (ir == null || !ir.hasIrEmitter()) {
            return "Emissor IR não detectado";
        }

        if (candidatos.isEmpty()) {
            return "Nenhum candidato disponível";
        }

        if (index >= candidatos.size()) {
            index = 0;
        }

        Candidato c = candidatos.get(index++);
        try {
            ir.transmit(FREQ, nec(c.endereco, c.comando));
            return c.nome + "  [" + index + "/" + candidatos.size() + "]";
        } catch (Exception e) {
            return c.nome + " • ERRO: " + e.getMessage();
        }
    }

    public void reset() {
        index = 0;
    }

    public int total() {
        return candidatos.size();
    }

    private int[] nec(int endereco, int comando) {
        int addr = endereco & 0xFF;
        int cmd = comando & 0xFF;

        ArrayList<Integer> p = new ArrayList<>();
        append(p, true, 9000);
        append(p, false, 4500);

        int[] bytes = {addr, (~addr) & 0xFF, cmd, (~cmd) & 0xFF};

        for (int b : bytes) {
            for (int mask = 1; mask <= 0x80; mask <<= 1) {
                append(p, true, UNIT);
                append(p, false, (b & mask) != 0 ? 1690 : UNIT);
            }
        }

        append(p, true, UNIT);
        append(p, false, 20000);

        int[] out = new int[p.size()];
        for (int i = 0; i < p.size(); i++) {
            out[i] = p.get(i);
        }
        return out;
    }

    private void append(ArrayList<Integer> p, boolean mark, int duration) {
        if (duration <= 0) return;

        if (p.isEmpty()) {
            if (!mark) p.add(0);
            p.add(duration);
            return;
        }

        boolean expectedMark = (p.size() % 2 == 1);
        if (expectedMark == mark) {
            int i = p.size() - 1;
            p.set(i, p.get(i) + duration);
        } else {
            p.add(duration);
        }
    }

    private static class Candidato {
        final String nome;
        final int endereco;
        final int comando;

        Candidato(String nome, int endereco, int comando) {
            this.nome = nome;
            this.endereco = endereco;
            this.comando = comando;
        }
    }
}
