package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.ConsumerIrManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private boolean toggle = false;

    private static final int FREQ = 36000;
    private static final int UNIT = 444;
    private static final int BG = Color.rgb(12,12,12);
    private static final int PANEL = Color.rgb(27,27,27);
    private static final int KEY = Color.rgb(48,48,48);
    private static final int KEY_DARK = Color.rgb(34,34,34);
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(175,175,175);

    private static final int POWER=0x0C, MUTE=0x0D, VOL_DOWN=0x11, VOL_UP=0x10;
    private static final int CH_DOWN=0x21, CH_UP=0x20;
    private static final int UP=0x58, DOWN=0x59, LEFT=0x5A, RIGHT=0x5B, OK=0x5C;
    private static final int BACK=0x0A, MENU=0x57, HOME=0x54, SOURCE=0x38;
    private static final int INFO=0x0F, GUIDE=0xCC, NETFLIX=0x76, SETTINGS=0xBF;
    private static final int RED=0x6D, GREEN=0x6E, YELLOW=0x6F, BLUE=0x70;
    private static final int PLAY=0x2C, STOP=0x31, PAUSE=0x30, REWIND=0x2B;
    private static final int FAST_FORWARD=0x28, RECORD=0x37, SUBTITLE=0x4B, EXIT=0x9F;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        build();
    }

    private TextView label(String s,int sp){
        TextView t=new TextView(this);
        t.setText(s); t.setTextColor(WHITE); t.setTextSize(sp);
        t.setGravity(Gravity.CENTER); return t;
    }

    private Button key(String text,int cmd,int h){
        return key(text,cmd,h,KEY,10);
    }

    private Button key(String text,int cmd,int h,int color,int size){
        Button b=new Button(this);
        b.setText(text); b.setTextColor(WHITE); b.setTextSize(size);
        b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0);
        b.setMinHeight(0); b.setMinWidth(0); b.setIncludeFontPadding(false);
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(10);
        b.setBackground(g);
        b.setOnClickListener(v->send(cmd));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h,1);
        p.setMargins(3,3,3,3); b.setLayoutParams(p);
        return b;
    }

    private LinearLayout row(){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER);
        return r;
    }

    private void add(LinearLayout r,Button b){r.addView(b);}

    private void section(LinearLayout root,String title){
        TextView t=label(title,9); t.setTextColor(GRAY);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(t,new LinearLayout.LayoutParams(-1,20));
    }

    private void build(){
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false); sv.setBackgroundColor(BG);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(6,4,6,8);
        root.setBackgroundColor(BG);

        // Corpo visual do controle FBG-8049 / LE-7276
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(5,5,5,6);
        GradientDrawable bodyBg=new GradientDrawable();
        bodyBg.setColor(Color.rgb(20,20,20));
        bodyBg.setCornerRadius(22);
        body.setBackground(bodyBg);

        TextView philips=label("PHILIPS",12);
        philips.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        body.addView(philips,new LinearLayout.LayoutParams(-1,25));

        // Power
        LinearLayout r=row();
        Button power=key("⏻",POWER,46,KEY_DARK,20);
        LinearLayout.LayoutParams pp=(LinearLayout.LayoutParams)power.getLayoutParams();
        pp.weight=1; r.addView(power);
        body.addView(r);

        // Transporte: modelo original tem três grupos de teclas
        r=row();
        add(r,key("◀",REWIND,34)); add(r,key("Ⅱ",PAUSE,34)); add(r,key("●",RECORD,34));
        body.addView(r);
        r=row();
        add(r,key("◀◀",REWIND,34)); add(r,key("▶",PLAY,34)); add(r,key("▶▶",FAST_FORWARD,34));
        body.addView(r);

        r=row();
        add(r,key("TV",SOURCE,34)); add(r,key("⌕",INFO,34)); add(r,key("⚙",SETTINGS,34));
        body.addView(r);
        r=row();
        add(r,key("TV GUIDE",GUIDE,34)); add(r,key("INFO",INFO,34)); add(r,key("SOURCE",SOURCE,34));
        body.addView(r);

        section(body,"SMART TV");
        r=row();
        add(r,key("SMART",HOME,34)); add(r,key("HOME",HOME,34)); add(r,key("TV",SOURCE,34));
        body.addView(r);

        r=row();
        add(r,key("RED",RED,28,Color.rgb(125,20,20),8));
        add(r,key("GREEN",GREEN,28,Color.rgb(20,105,45),8));
        add(r,key("YELLOW",YELLOW,28,Color.rgb(150,120,10),8));
        add(r,key("BLUE",BLUE,28,Color.rgb(20,70,145),8));
        body.addView(r);

        // Navegação grande, como no controle físico
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL); nav.setGravity(Gravity.CENTER);
        nav.setPadding(22,4,22,3);
        r=row(); add(r,key("▲",UP,38,Color.rgb(90,90,90),12)); nav.addView(r);
        r=row(); add(r,key("◀",LEFT,45,Color.rgb(90,90,90),12));
        add(r,key("OK",OK,45,Color.rgb(105,105,105),11));
        add(r,key("▶",RIGHT,45,Color.rgb(90,90,90),12)); nav.addView(r);
        r=row(); add(r,key("▼",DOWN,38,Color.rgb(90,90,90),12)); nav.addView(r);
        body.addView(nav);

        r=row();
        add(r,key("↩ BACK",BACK,34)); add(r,key("☰ MENU",MENU,34)); add(r,key("▣ EXIT",EXIT,34));
        body.addView(r);

        // Volume / Netflix / Channel, mesma posição do controle
        r=row(); add(r,key("VOL +",VOL_UP,34)); add(r,key("NETFLIX",NETFLIX,34,Color.rgb(225,225,225),9)); add(r,key("CH +",CH_UP,34)); body.addView(r);
        r=row(); add(r,key("VOL −",VOL_DOWN,34)); add(r,key("🔇",MUTE,34)); add(r,key("CH −",CH_DOWN,34)); body.addView(r);

        section(body,"TECLADO");
        String[][] nums={{"1","2 ABC","3 DEF"},{"4 GHI","5 JKL","6 MNO"},{"7 PQRS","8 TUV","9 WXYZ"},{"CC","0","SUBTITLE"}};
        for(String[] a:nums){
            r=row();
            for(String s:a){
                int c;
                if(s.startsWith("1"))c=1; else if(s.startsWith("2"))c=2; else if(s.startsWith("3"))c=3;
                else if(s.startsWith("4"))c=4; else if(s.startsWith("5"))c=5; else if(s.startsWith("6"))c=6;
                else if(s.startsWith("7"))c=7; else if(s.startsWith("8"))c=8; else if(s.startsWith("9"))c=9;
                else if(s.equals("0"))c=0; else if(s.equals("SUBTITLE"))c=SUBTITLE; else c=0x3C;
                add(r,key(s,c,31,KEY_DARK,8));
            }
            body.addView(r);
        }

        boolean available=ir!=null&&ir.hasIrEmitter();
        TextView status=label(available?"●  IR disponível • RC6 36 kHz • Sem anúncios":"○  Emissor IR não detectado",9);
        status.setTextColor(GRAY);
        body.addView(status,new LinearLayout.LayoutParams(-1,28));

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        root.addView(body, bodyParams);
        sv.addView(root, new ScrollView.LayoutParams(-1, -1));
        setContentView(sv);
    }

    private void send(int command){
        if(ir==null||!ir.hasIrEmitter()){
            Toast.makeText(this,"Este celular não informou emissor IR.",Toast.LENGTH_SHORT).show(); return;
        }
        try{
            toggle=!toggle;
            ir.transmit(FREQ,rc6(0x00,command,toggle));
        }catch(Exception e){
            Toast.makeText(this,"Falha ao enviar IR: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    private int[] rc6(int address,int command,boolean tog){
        ArrayList<Integer> p=new ArrayList<>();
        append(p,true,2666); append(p,false,889);
        appendBit(p,1,UNIT); appendBit(p,0,UNIT); appendBit(p,0,UNIT); appendBit(p,0,UNIT);
        appendBit(p,tog?1:0,UNIT*2);
        for(int m=0x80;m!=0;m>>=1) appendBit(p,(address&m)!=0?1:0,UNIT);
        for(int m=0x80;m!=0;m>>=1) appendBit(p,(command&m)!=0?1:0,UNIT);
        append(p,false,2666);
        int[] out=new int[p.size()]; for(int i=0;i<p.size();i++)out[i]=p.get(i); return out;
    }

    private void appendBit(ArrayList<Integer> p,int bit,int half){
        if(bit==1){append(p,true,half);append(p,false,half);}
        else{append(p,false,half);append(p,true,half);}
    }

    private void append(ArrayList<Integer> p,boolean mark,int duration){
        if(duration<=0)return;
        if(p.isEmpty()){if(!mark)p.add(0);p.add(duration);return;}
        boolean expectedMark=(p.size()%2==1);
        if(expectedMark==mark){int i=p.size()-1;p.set(i,p.get(i)+duration);}
        else p.add(duration);
    }
}
