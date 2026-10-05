package com.example.philipsremote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import java.util.ArrayList;
import java.util.List;

public class FanIrTeste {
    private static final int FREQ = 38000;
    private final ConsumerIrManager ir;
    private final List<Cand> lista = new ArrayList<>();
    private int pos = 0;

    private static class Cand {
        final String nome; final int codigo;
        Cand(String nome, int codigo) { this.nome=nome; this.codigo=codigo & 0xFFF; }
    }

    public FanIrTeste(Context context) {
        ir=(ConsumerIrManager)context.getSystemService(Context.CONSUMER_IR_SERVICE);

        lista.add(new Cand("WH Luz • 0xC08",0xC08));
        lista.add(new Cand("WH Dimmer • 0xC20",0xC20));
        lista.add(new Cand("WH Desligar • 0xC10",0xC10));
        lista.add(new Cand("WH Vel 1 • 0xC00",0xC00));
        lista.add(new Cand("WH Vel 2 • 0xC04",0xC04));
        lista.add(new Cand("DEKA Desligar • 0xD80",0xD80));
        lista.add(new Cand("DEKA Vel 1 • 0xD88",0xD88));
        lista.add(new Cand("DEKA Vel 3 • 0xD82",0xD82));
        lista.add(new Cand("DEKA Timer 1h • 0xD90",0xD90));
        lista.add(new Cand("DEKA Timer 6h • 0xDA0",0xDA0));
    }

    public boolean hasEmitter() { return ir!=null && ir.hasIrEmitter(); }

    public String next() {
        if(!hasEmitter()) return "Emissor IR não detectado";
        if(pos>=lista.size()) return "Fim dos 10 testes — reinicie a sequência";
        Cand c=lista.get(pos++);
        try {
            ir.transmit(FREQ,symphony(c.codigo,4));
            return "Teste enviado: "+c.nome+" ["+pos+"/"+lista.size()+"]";
        } catch(Exception e) {
            return "Erro ao enviar "+c.nome+": "+e.getMessage();
        }
    }

    public void reset(){ pos=0; }
    public int total(){ return lista.size(); }

    private int[] symphony(int code,int repeats) {
        List<Integer> p=new ArrayList<>();
        for(int r=0;r<repeats;r++) {
            for(int i=11;i>=0;i--) {
                if(((code>>i)&1)!=0) { p.add(1250); p.add(430); }
                else { p.add(400); p.add(1270); }
            }
            if(r<repeats-1) p.add(7900);
        }
        int[] out=new int[p.size()];
        for(int i=0;i<out.length;i++) out[i]=p.get(i);
        return out;
    }
}