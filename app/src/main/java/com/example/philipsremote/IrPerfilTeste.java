package com.example.philipsremote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class IrPerfilTeste {
    private static final int NEC=1, SAMSUNG=2, SONY=3, RC5=4, RC6=5, COOLIX=6, MIDEA=7;
    private final ConsumerIrManager ir;
    private final List<Item> itens=new ArrayList<>();
    private int pos=0;
    private String perfil="LG / NEC";

    private static class Item {
        String nome; int tipo,addr,cmd,code;
        Item(String n,int t,int a,int c){nome=n;tipo=t;addr=a;cmd=c;code=0;}
        Item(String n,int t,int c){nome=n;tipo=t;code=c;}
    }

    public IrPerfilTeste(Context c){ir=(ConsumerIrManager)c.getSystemService(Context.CONSUMER_IR_SERVICE); selecionar("LG / NEC");}

    public boolean hasEmitter(){return ir!=null&&ir.hasIrEmitter();}

    public String[] perfis(){
        return new String[]{"LG / NEC","Samsung TV","Sony TV","Philips / RC5","Philips / RC6","Toshiba / JVC / NEC","AC Coolix","AC Midea"};
    }

    public void selecionar(String p){
        perfil=p; itens.clear(); pos=0;
        if(p.equals("LG / NEC")){
            int[] cs={0x18,0x08,0x09,0x02,0x03,0x00,0x01,0x40,0x41,0x07,0x06,0x44,0x28,0x43,0x7C,0x0B,0xAA,0xAB,0x72,0x71,0x63,0x61,0xB0,0xB1,0xBA,0x8F,0x8E,0x39,0x5B,0xB5};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"NEC addr 0x04 • 0x%02X",c),NEC,0x04,c));
        } else if(p.equals("Samsung TV")){
            int[] cs={0x02,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0F,0x10,0x11,0x12,0x13,0x1A,0x1F,0x58,0x60,0x61,0x62,0x65,0x68,0x79};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"Samsung32 addr 0x07 • 0x%02X",c),SAMSUNG,0x07,c));
        } else if(p.equals("Sony TV")){
            int[] cs={0x15,0x19,0x10,0x11,0x12,0x13,0x14,0x16,0x17,0x18,0x1A,0x0D,0x2A,0x74};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"SIRC addr 0x01 • 0x%02X",c),SONY,0x01,c));
        } else if(p.equals("Philips / RC5")){
            int[] cs={0x0C,0x10,0x11,0x12,0x13,0x14,0x20,0x21,0x22,0x23,0x24,0x25,0x38,0x3D};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"RC5 addr 0x00 • 0x%02X",c),RC5,0x00,c));
        } else if(p.equals("Philips / RC6")){
            int[] cs={0x0C,0x10,0x11,0x12,0x13,0x14,0x20,0x21,0x22,0x23,0x24,0x25,0x38,0x3D};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"RC6 addr 0x00 • 0x%02X",c),RC6,0x00,c));
        } else if(p.equals("Toshiba / JVC / NEC")){
            int[] addrs={0x00,0x01,0x02,0x04,0x10,0x40};
            int[] cs={0x08,0x02,0x03,0x09,0x00,0x01,0x40,0x41,0x06,0x07,0x44,0x43,0x0B};
            for(int a:addrs)for(int c:cs)itens.add(new Item(String.format(Locale.US,"NEC addr 0x%02X • 0x%02X",a,c),NEC,a,c));
        } else if(p.equals("AC Coolix")){
            int[] cs={0xB27BE0,0xB27B00,0xB27B20,0xB27B40,0xB27B60,0xB27B80,0xB27BA0,0xB27BC0};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"Coolix 24-bit • 0x%06X",c),COOLIX,c));
        } else if(p.equals("AC Midea")){
            itens.add(new Item("Midea • Power OFF",MIDEA,0x7BE0));
            itens.add(new Item("Midea • Cool 22°C",MIDEA,0xBF70));
            itens.add(new Item("Midea • Cool 23°C",MIDEA,0xBF78));
            itens.add(new Item("Midea • Cool 24°C",MIDEA,0xBF80));
            itens.add(new Item("Midea • LED/Turbo candidato",MIDEA,0xA545));
        }
    }

    public String getPerfil(){return perfil;}
    public int position(){return pos;}
    public int total(){return itens.size();}
    public void reset(){pos=0;}
    public int currentCode(){return pos==0?-1:itens.get(pos-1).code;}

    public String next(){
        if(!hasEmitter())return "Emissor IR não detectado";
        if(pos>=itens.size())return "Fim: "+perfil;
        Item x=itens.get(pos++);
        try{
            if(x.tipo==NEC)ir.transmit(38000,nec(x.addr,x.cmd));
            else if(x.tipo==SAMSUNG)ir.transmit(38000,samsung(x.addr,x.cmd));
            else if(x.tipo==SONY)ir.transmit(40000,sony(x.addr,x.cmd));
            else if(x.tipo==RC5)ir.transmit(36000,rc5(x.addr,x.cmd,false));
            else if(x.tipo==RC6)ir.transmit(36000,rc6(x.addr,x.cmd,false));
            else if(x.tipo==COOLIX)ir.transmit(38000,coolix(x.code));
            else if(x.tipo==MIDEA)ir.transmit(38000,midea(x.code));
            return String.format(Locale.US,"%s • %d/%d",x.nome,pos,itens.size());
        }catch(Exception e){return String.format(Locale.US,"ERRO %s: %s",x.nome,e.getMessage());}
    }

    private int[] nec(int addr,int cmd){
        int[] b={addr&255,(~addr)&255,cmd&255,(~cmd)&255}; ArrayList<Integer>p=new ArrayList<>();
        add(p,9000);add(p,4500); for(int v:b)for(int m=1;m<=128;m<<=1){add(p,560);add(p,(v&m)!=0?1690:560);} add(p,560);add(p,20000);return arr(p);
    }
    private int[] samsung(int addr,int cmd){
        int[] b={addr&255,cmd&255,(~cmd)&255,addr&255}; ArrayList<Integer>p=new ArrayList<>();
        add(p,4500);add(p,4500);for(int v:b)for(int m=1;m<=128;m<<=1){add(p,560);add(p,(v&m)!=0?1600:560);}add(p,560);add(p,20000);return arr(p);
    }
    private int[] sony(int addr,int cmd){
        ArrayList<Integer>p=new ArrayList<>();for(int r=0;r<3;r++){add(p,2400);add(p,600);for(int i=0;i<7;i++){add(p,600);add(p,((cmd>>i)&1)!=0?1200:600);}for(int i=0;i<5;i++){add(p,600);add(p,((addr>>i)&1)!=0?1200:600);}if(r<2)add(p,10000);}return arr(p);
    }
    private int[] rc5(int addr,int cmd,boolean tog){
        ArrayList<Integer>p=new ArrayList<>(); bit(p,1,889);bit(p,1,889);bit(p,tog?1:0,889);for(int m=16;m!=0;m>>=1)bit(p,(addr&m)!=0?1:0,889);for(int m=64;m!=0;m>>=1)bit(p,(cmd&m)!=0?1:0,889);return arr(p);
    }
    private int[] rc6(int addr,int cmd,boolean tog){
        ArrayList<Integer>p=new ArrayList<>();add(p,2666);add(p,889);bit(p,1,444);bit(p,0,444);bit(p,0,444);bit(p,0,444);bit(p,tog?1:0,888);for(int m=128;m!=0;m>>=1)bit(p,(addr&m)!=0?1:0,444);for(int m=128;m!=0;m>>=1)bit(p,(cmd&m)!=0?1:0,444);add(p,2666);return arr(p);
    }
    private int[] coolix(int code){
        ArrayList<Integer>p=new ArrayList<>();add(p,4000);add(p,4000);for(int i=0;i<24;i++){add(p,500);add(p,((code>>i)&1)!=0?1500:500);}add(p,500);return arr(p);
    }
    private int[] midea(int shortCode){
        int a=(shortCode>>8)&255,b=shortCode&255;int[] bytes={0xB2,a,b,(~0xB2)&255,(~a)&255,(~b)&255};ArrayList<Integer>p=new ArrayList<>();
        add(p,4350);add(p,4400);for(int rep=0;rep<2;rep++){for(int v:bytes)for(int m=1;m<=128;m<<=1){add(p,560);add(p,(v&m)!=0?1690:560);}add(p,560);if(rep==0){add(p,4400);}}return arr(p);
    }
    private void bit(ArrayList<Integer>p,int v,int h){if(v==1){add(p,h);add(p,h);}else{add(p,h);add(p,h);}}
    private void add(ArrayList<Integer>p,int d){p.add(d);}
    private int[] arr(ArrayList<Integer>p){int[]o=new int[p.size()];for(int i=0;i<o.length;i++)o[i]=p.get(i);return o;}
}