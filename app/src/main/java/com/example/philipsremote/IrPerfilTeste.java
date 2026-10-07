package com.example.philipsremote;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IrPerfilTeste {
    private static final int NEC=1, SAMSUNG=2, SONY=3, RC5=4, RC6=5, COOLIX=6, MIDEA=7, PANASONIC=8, FAN=9;
    private final ConsumerIrManager ir;
    private final List<Item> itens=new ArrayList<>();
    private int pos=0;
    private String perfil="LG / NEC";
    private Item ultimoFuncionou;
    private boolean rc6Toggle=false;
    private final android.content.SharedPreferences savedPrefs;
    private static final String SAVED_KEY="fan_test_saved_v1";


    private static class Item {
        String nome; int tipo,addr,cmd,code;
        Item(String n,int t,int a,int c){nome=n;tipo=t;addr=a;cmd=c;code=0;}
        Item(String n,int t,int c){nome=n;tipo=t;code=c;}
    }

    public IrPerfilTeste(Context c){
        ir=(ConsumerIrManager)c.getSystemService(Context.CONSUMER_IR_SERVICE);
        savedPrefs=c.getSharedPreferences("ir_test_codes",Context.MODE_PRIVATE);
        selecionar("LG / NEC");
    }

    public boolean hasEmitter(){return ir!=null&&ir.hasIrEmitter();}

    /** Usa a frequência pedida quando o emissor do aparelho suporta essa faixa.
     *  Caso contrário, aproxima para a frequência suportada mais próxima. */
    private int freq(int desejada){
        if(ir==null) return desejada;
        try{
            ConsumerIrManager.CarrierFrequencyRange[] ranges=ir.getCarrierFrequencies();
            if(ranges==null || ranges.length==0) return desejada;
            int melhor=desejada;
            long distancia=Long.MAX_VALUE;
            for(ConsumerIrManager.CarrierFrequencyRange r:ranges){
                int candidato=desejada;
                if(desejada<r.getMinFrequency()) candidato=r.getMinFrequency();
                else if(desejada>r.getMaxFrequency()) candidato=r.getMaxFrequency();
                long d=Math.abs((long)candidato-desejada);
                if(d<distancia){distancia=d;melhor=candidato;}
                if(d==0) break;
            }
            return melhor;
        }catch(Exception e){return desejada;}
    }

    private void tx(int desejada,int[] padrao){ir.transmit(freq(desejada),padrao);}

    public String[] perfis(){
        return new String[]{"LG / NEC","Samsung TV","Sony TV","Philips / RC5","Philips / RC6","Panasonic TV","AOC / NEC","TCL / NEC","Philco / NEC","Semp / NEC","Toshiba / JVC / NEC","AC Coolix" ,"AC Midea","Ventilador Universal"};
    }

    public void selecionar(String p){
        perfil=p; itens.clear(); pos=0; rc6Toggle=false; ultimoFuncionou=null;
        if(p.equals("LG / NEC")){
            int[] cs={0x08,0x09,0x02,0x03,0x00,0x01,0x40,0x41,0x07,0x06,0x44,0x28,0x43,0x7C,0x0B,0xAA,0xAB,0x72,0x71,0x63,0x61,0xB0,0xB1,0xBA,0x8F,0x8E,0x39,0x5B,0xB5,0x18};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"NEC addr 0x04 • 0x%02X",c),NEC,0x04,c));
        } else if(p.equals("Samsung TV")){
            int[] cs={0x02,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0F,0x10,0x11,0x12,0x13,0x1A,0x1F,0x58,0x60,0x61,0x62,0x65,0x68,0x79};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"Samsung32 addr 0x07 • 0x%02X",c),SAMSUNG,0x07,c));
        } else if(p.equals("Sony TV")){
            int[] cs={0x15,0x12,0x13,0x10,0x11,0x14,0x19,0x16,0x17,0x18,0x1A,0x0D,0x2A,0x74};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"SIRC addr 0x01 • 0x%02X",c),SONY,0x01,c));
        } else if(p.equals("Philips / RC5")){
            int[] cs={0x0C,0x10,0x11,0x12,0x13,0x14,0x20,0x21,0x22,0x23,0x24,0x25,0x38,0x3D};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"RC5 addr 0x00 • 0x%02X",c),RC5,0x00,c));
        } else if(p.equals("Philips / RC6")){
            int[] cs={0x0C,0x10,0x11,0x12,0x13,0x14,0x20,0x21,0x22,0x23,0x24,0x25,0x38,0x3D};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"RC6 addr 0x00 • 0x%02X",c),RC6,0x00,c));
        } else if(p.equals("Panasonic TV")){
            int[] cs={0x00,0x4C,0x04,0x84,0x2C,0xAC,0x72,0xF2,0x52,0xD2,0x92,0x4A,0x0E,0x4E,0x8E,0xCE,0xEC,0x6D,0x4F,0xF1,0x03,0x83,0x43,0xC3,0x23,0xA3,0x63,0xE3};
            for(int c:cs)itens.add(new Item(String.format(Locale.US,"Panasonic Kaseikyo • função 0x%02X",c),PANASONIC,c));
        } else if(p.equals("AOC / NEC") || p.equals("TCL / NEC") || p.equals("Philco / NEC") || p.equals("Semp / NEC")){
            int[] addrs={0x00,0x01,0x04,0x08,0x10,0x20,0x40,0x80};
            int[] cs={0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,0x18,0x19,0x1A,0x1B,0x20,0x21,0x22,0x40,0x41,0x43,0x44};
            for(int a:addrs)for(int c:cs)itens.add(new Item(String.format(Locale.US,"%s addr 0x%02X • 0x%02X",p.replace(" / NEC",""),a,c),NEC,a,c));
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
        } else if(p.equals("Ventilador Universal")){
            itens.add(new Item("Ventilador • Ligar / Desligar",FAN,1));
            itens.add(new Item("Ventilador • Oscilação",FAN,2));
            itens.add(new Item("Ventilador • Velocidade",FAN,3));
            itens.add(new Item("Ventilador • Timer",FAN,4));
            itens.add(new Item("Ventilador • Noturno",FAN,5));
        }
    }

    public String getPerfil(){return perfil;}
    public int position(){return pos;}
    public int total(){return itens.size();}
    public void reset(){pos=0; ultimoFuncionou=null; rc6Toggle=false;}
    public String previous(){
        if(itens.isEmpty()) return "Nenhum código disponível.";
        if(pos<=1) pos=0; else pos--;
        ultimoFuncionou=null;
        return pos==0 ? "Pronto para testar o primeiro código." : "Voltou para o candidato "+pos+" de "+itens.size()+".";
    }
    public String saveCurrentCode(String nome){
        int codigo=currentCode();
        if(codigo<0 || itens.isEmpty()) return "Nenhum código testado.";
        String n=(nome==null||nome.trim().isEmpty()) ? "Código "+(savedCount()+1) : nome.trim();
        String old=savedPrefs.getString(SAVED_KEY,"");
        String item=perfil+"|"+n.replace("|","/")+"|"+codigo;
        String all=old.isEmpty()?item:old+"\n"+item;
        savedPrefs.edit().putString(SAVED_KEY,all).apply();
        return "Código salvo: "+n;
    }
    public String savedCodes(){return savedPrefs.getString(SAVED_KEY,"");}
    public int savedCount(){String s=savedCodes();return s.isEmpty()?0:s.split("\n").length;}
    public void clearSavedCodes(){savedPrefs.edit().remove(SAVED_KEY).apply();}
    public boolean transmitManual(String value){
        if(!hasEmitter() || value==null) return false;
        try{
            String s=value.trim().replace("0x","").replace("0X","").replace(" ","");
            if(s.isEmpty()) return false;
            long v=Long.parseLong(s,16);
            int addr,cmd;
            if(s.length()<=2){addr=0x04;cmd=(int)v&0xFF;} else {addr=(int)((v>>8)&0xFF);cmd=(int)(v&0xFF);}
            tx(38000,nec(addr,cmd));
            return true;
        }catch(Exception e){return false;}
    }
    public String lastDescription(){
        return ultimoFuncionou==null ? "" : ultimoFuncionou.nome;
    }
    public String marcarFuncionou(){
        if(pos==0 || itens.isEmpty()) return "Nenhum código foi transmitido ainda.";
        ultimoFuncionou=itens.get(pos-1);
        return "SALVO: "+ultimoFuncionou.nome;
    }
    public void limparResultado(){ ultimoFuncionou=null; }

    public int currentCode(){
        if(pos==0 || itens.isEmpty()) return -1;
        Item x=itens.get(pos-1);
        if(x.tipo==NEC || x.tipo==SAMSUNG || x.tipo==SONY || x.tipo==RC5 || x.tipo==RC6) {
            return ((x.addr & 0xFF) << 8) | (x.cmd & 0xFF);
        }
        return x.code;
    }

    /** Transmite um código já confirmado e salvo pelo usuário. */
    public boolean transmitirSalvo(String perfilSalvo,int codigo){ return transmitirSalvo(perfilSalvo,codigo,0); }

    /** Transmite usando a frequência salva quando ela é válida; 0 usa a frequência padrão do perfil. */
    public boolean transmitirSalvo(String perfilSalvo,int codigo,int frequenciaSalva){
        if(!hasEmitter() || codigo<0) return false;
        try{
            int addr=(codigo >> 8) & 0xFF;
            int cmd=codigo & 0xFF;
            if(perfilSalvo.equals("LG / NEC")) tx(frequenciaOuPadrao(frequenciaSalva,38000),nec(codigo>255?addr:0x04,cmd));
            else if(perfilSalvo.equals("Samsung TV")) tx(frequenciaOuPadrao(frequenciaSalva,38000),samsung(codigo>255?addr:0x07,cmd));
            else if(perfilSalvo.equals("Sony TV")) tx(frequenciaOuPadrao(frequenciaSalva,40000),sony(codigo>255?addr:0x01,cmd));
            else if(perfilSalvo.equals("Philips / RC5")) tx(frequenciaOuPadrao(frequenciaSalva,36000),rc5(codigo>255?addr:0x00,cmd,false));
            else if(perfilSalvo.equals("Philips / RC6")) { rc6Toggle=!rc6Toggle; tx(frequenciaOuPadrao(frequenciaSalva,36000),rc6(codigo>255?addr:0x00,cmd,rc6Toggle)); }
            else if(perfilSalvo.equals("Panasonic TV")) tx(frequenciaOuPadrao(frequenciaSalva,37000),panasonic(codigo & 0xFF));
            else if(perfilSalvo.equals("AOC / NEC") || perfilSalvo.equals("TCL / NEC") || perfilSalvo.equals("Philco / NEC") || perfilSalvo.equals("Semp / NEC") || perfilSalvo.equals("Toshiba / JVC / NEC")) tx(frequenciaOuPadrao(frequenciaSalva,38000),nec(codigo>255?addr:0x00,cmd));
            else if(perfilSalvo.equals("AC Coolix")) tx(frequenciaOuPadrao(frequenciaSalva,38000),coolix(codigo));
            else if(perfilSalvo.equals("AC Midea")) tx(frequenciaOuPadrao(frequenciaSalva,38000),midea(codigo));
            else if(perfilSalvo.equals("Ventilador Universal")) tx(frequenciaOuPadrao(frequenciaSalva,38000),fanRaw(codigo));
            else return false;
            return true;
        }catch(Exception e){ return false; }
    }

    public boolean transmitirVentilador(int funcao){ if(!hasEmitter() || funcao<1 || funcao>5) return false; try{ tx(38000,fanRaw(funcao)); return true; }catch(Exception e){ return false; } }
    private int[] fanRaw(int f){ switch(f){
        case 1: return new int[]{1210,368,1210,368,368,1210,1210,368,1210,368,368,1210,368,1210,368,1210,368,1210,368,1210,368,1210,1210,7074};
        case 2: return new int[]{1210,368,1210,368,368,1210,1210,368,1210,368,368,1210,368,1210,1210,368,368,1210,368,1210,368,1210,368,7889};
        case 3: return new int[]{1183,368,1183,368,368,1183,1183,368,1183,368,368,1183,368,1183,368,1183,368,1183,368,1183,1183,368,368,7889};
        case 4: return new int[]{1183,368,1183,368,368,1183,1183,368,1183,368,368,1183,368,1183,368,1183,1183,368,368,1183,368,1183,368,7889};
        case 5: return new int[]{1183,368,1183,368,368,1183,1183,368,1183,368,1183,368,368,1183,1183,368,1183,368,1183,368,1183,368,1183,368,7889};
        default: return new int[]{1210,368}; } }
    private int frequenciaOuPadrao(int salva,int padrao){ return salva>0 ? salva : padrao; }

    public String next(){
        if(!hasEmitter())return "Emissor IR não detectado";
        if(pos>=itens.size())return "Fim: "+perfil;
        Item x=itens.get(pos++);
        try{
            if(x.tipo==NEC)tx(38000,nec(x.addr,x.cmd));
            else if(x.tipo==SAMSUNG)tx(38000,samsung(x.addr,x.cmd));
            else if(x.tipo==SONY)tx(40000,sony(x.addr,x.cmd));
            else if(x.tipo==RC5)tx(36000,rc5(x.addr,x.cmd,false));
            else if(x.tipo==RC6)tx(36000,rc6(x.addr,x.cmd,false));
            else if(x.tipo==COOLIX)tx(38000,coolix(x.code));
            else if(x.tipo==MIDEA)tx(38000,midea(x.code));
            else if(x.tipo==PANASONIC)tx(37000,panasonic(x.code));
            else if(x.tipo==FAN)tx(38000,fanRaw(x.code));
            return String.format(Locale.US,"%s • %d/%d",x.nome,pos,itens.size());
        }catch(Exception e){return String.format(Locale.US,"ERRO %s: %s",x.nome,e.getMessage());}
    }

    private int[] nec(int addr,int cmd){
        int[] b={addr&255,addr&255,cmd&255,(~cmd)&255}; ArrayList<Integer>p=new ArrayList<>();
        add(p,9000);add(p,4500); for(int v:b)for(int m=1;m<=128;m<<=1){add(p,560);add(p,(v&m)!=0?1690:560);} add(p,560);add(p,20000);return arr(p);
    }
    private int[] samsung(int addr,int cmd){
        int[] b={addr&255,(~addr)&255,cmd&255,(~cmd)&255}; ArrayList<Integer>p=new ArrayList<>();
        add(p,4500);add(p,4500);for(int v:b)for(int m=1;m<=128;m<<=1){add(p,560);add(p,(v&m)!=0?1600:560);}add(p,560);add(p,20000);return arr(p);
    }
    private int[] sony(int addr,int cmd){
        ArrayList<Integer>p=new ArrayList<>();for(int r=0;r<3;r++){add(p,2400);add(p,600);for(int i=0;i<7;i++){add(p,600);add(p,((cmd>>i)&1)!=0?1200:600);}for(int i=0;i<5;i++){add(p,600);add(p,((addr>>i)&1)!=0?1200:600);}if(r<2){ int last=p.size()-1; p.set(last,p.get(last)+10000); }}return arr(p);
    }
    private int[] rc5(int addr,int cmd,boolean tog){
        ArrayList<Integer>p=new ArrayList<>();
        rc5Bit(p,1,889); rc5Bit(p,1,889); rc5Bit(p,tog?1:0,889);
        for(int m=16;m!=0;m>>=1) rc5Bit(p,(addr&m)!=0?1:0,889);
        for(int m=32;m!=0;m>>=1) rc5Bit(p,(cmd&m)!=0?1:0,889);
        return arr(p);
    }
    private int[] rc6(int addr,int cmd,boolean tog){
        ArrayList<Integer>p=new ArrayList<>();
        add(p,2666); add(p,889);
        manchester(p,1,444); manchester(p,0,444); manchester(p,0,444); manchester(p,0,444);
        manchester(p,tog?1:0,888);
        for(int m=0x80;m!=0;m>>=1) manchester(p,(addr&m)!=0?1:0,444);
        for(int m=0x80;m!=0;m>>=1) manchester(p,(cmd&m)!=0?1:0,444);
        return arr(p);
    }
    private int[] coolix(int code){
        int[] bytes=interleavedBytes(code);
        return acSixBytes(bytes,4000,4000,500,1500,5000);
    }
    private int[] panasonic(int function){
        final int unit=432;
        final int device=0x01, subdevice=0x00;
        int checksum=(device ^ subdevice ^ (function & 0xFF)) & 0xFF;
        int[] bytes={0x40,0x04,device,subdevice,function & 0xFF,checksum};
        ArrayList<Integer>p=new ArrayList<>();
        add(p,8*unit); add(p,4*unit);
        for(int v:bytes) for(int m=1;m<=0x80;m<<=1){
            add(p,unit); add(p,(v&m)!=0?3*unit:unit);
        }
        add(p,unit);
        return arr(p);
    }
    private int[] midea(int shortCode){
        int[] bytes=interleavedBytes(shortCode);
        return acSixBytes(bytes,4350,4400,560,1690,5200);
    }

    private int[] interleavedBytes(int code){
        int b0=(code>>16)&0xFF;
        int b1=(code>>8)&0xFF;
        int b2=code&0xFF;
        return new int[]{b0,(~b0)&0xFF,b1,(~b1)&0xFF,b2,(~b2)&0xFF};
    }

    private int[] acSixBytes(int[] bytes,int headerMark,int headerSpace,int bitMark,int oneSpace,int gap){
        ArrayList<Integer>p=new ArrayList<>();
        for(int rep=0;rep<2;rep++){
            add(p,headerMark); add(p,headerSpace);
            for(int v:bytes){
                for(int m=0x80;m!=0;m>>=1){
                    add(p,bitMark);
                    add(p,(v&m)!=0?oneSpace:bitMark);
                }
            }
            add(p,bitMark);
            if(rep==0) add(p,gap);
        }
        return arr(p);
    }
    private void manchester(ArrayList<Integer>p,int v,int h){
        if(v==1){add(p,true,h);add(p,false,h);}else{add(p,false,h);add(p,true,h);}
    }
    private void rc5Bit(ArrayList<Integer>p,int v,int h){
        if(v==1){add(p,false,h);add(p,true,h);}else{add(p,true,h);add(p,false,h);}
    }
    private void add(ArrayList<Integer>p,int d){p.add(d);} 
    private void add(ArrayList<Integer>p,boolean mark,int d){
        if(p.isEmpty()){ if(!mark)p.add(0); p.add(d); return; }
        boolean expectedMark=(p.size()%2==1);
        if(expectedMark==mark){int i=p.size()-1;p.set(i,p.get(i)+d);}else p.add(d);
    }
    private int[] arr(ArrayList<Integer>p){int[]o=new int[p.size()];for(int i=0;i<o.length;i++)o[i]=p.get(i);return o;}
}