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

    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    private Button key(String text,int cmd,int h){
        return key(text,cmd,h,KEY,16);
    }

    private Button key(String text,int cmd,int h,int color,int size){
        Button b=new Button(this);
        b.setText(text);
        b.setTextColor(WHITE);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setMinHeight(0); b.setMinWidth(0);
        b.setIncludeFontPadding(false);
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1),Color.rgb(55,55,58));
        b.setBackground(g);
        b.setOnClickListener(v->send(cmd));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(h),1);
        p.setMargins(dp(4),dp(4),dp(4),dp(4));
        b.setLayoutParams(p);
        return b;
    }

    private TextView label(String s,int sp){
        TextView t=new TextView(this);
        t.setText(s); t.setTextColor(WHITE); t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout row(){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private void add(LinearLayout r,Button b){ r.addView(b); }

    private void section(LinearLayout root,String title){
        TextView t=label(title,13);
        t.setTextColor(GRAY);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setLetterSpacing(.03f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(34));
        p.setMargins(0,dp(7),0,0);
        root.addView(t,p);
    }

    private void build(){
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        sv.setClipToPadding(false);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(8),dp(10),dp(28));
        root.setBackgroundColor(BG);

        TextView title=label("PHILIPS",24);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));

        // Power — custom artwork
        LinearLayout r=row();
        Button powerButton=new Button(this);
        powerButton.setText("");
        powerButton.setBackgroundResource(R.drawable.power_button);
        powerButton.setPadding(0,0,0,0);
        powerButton.setContentDescription("Ligar ou desligar a TV");
        powerButton.setOnClickListener(v->send(POWER));
        LinearLayout.LayoutParams powerParams=new LinearLayout.LayoutParams(dp(82),dp(82));
        powerParams.setMargins(dp(4),dp(2),dp(4),dp(2));
        powerButton.setLayoutParams(powerParams);
        r.addView(powerButton);
        root.addView(r);

        // SOURCE / INFO / SETTINGS
        r=row();
        add(r,key("SOURCE",SOURCE,50,KEY,16));
        add(r,key("INFO",INFO,50,KEY,16));
        add(r,key("⚙",SETTINGS,50,KEY,25));
        root.addView(r);

        // GUIDE / HOME / NETFLIX
        r=row();
        add(r,key("GUIDE",GUIDE,50,KEY,16));
        add(r,key("HOME",HOME,50,KEY,16));
        add(r,key("NETFLIX",NETFLIX,50,KEY,16));
        root.addView(r);

        section(root,"NAVEGAÇÃO");

        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(42),dp(8),dp(42),dp(8));
        GradientDrawable navBg=new GradientDrawable();
        navBg.setColor(Color.rgb(26,26,28));
        navBg.setCornerRadius(dp(26));
        navBg.setStroke(dp(1),Color.rgb(55,55,58));
        nav.setBackground(navBg);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.setMargins(dp(3),dp(2),dp(3),dp(2));
        nav.setLayoutParams(np);

        r=row(); add(r,key("▲",UP,56,Color.rgb(60,61,70),22)); nav.addView(r);
        r=row();
        add(r,key("◀",LEFT,64,Color.rgb(60,61,70),22));
        add(r,key("OK",OK,64,Color.rgb(60,61,70),18));
        add(r,key("▶",RIGHT,64,Color.rgb(60,61,70),22));
        nav.addView(r);
        r=row(); add(r,key("▼",DOWN,56,Color.rgb(60,61,70),22)); nav.addView(r);
        root.addView(nav);

        r=row();
        add(r,key("↩  BACK",BACK,50,KEY,16));
        add(r,key("☰  MENU",MENU,50,KEY,16));
        add(r,key("EXIT",EXIT,50,KEY,16));
        root.addView(r);

        section(root,"VOLUME E CANAIS");
        r=row();
        add(r,key("📡 VOL +",VOL_UP,50,KEY,16));
        add(r,key("🔇",MUTE,50,KEY,20));
        add(r,key("CH +  +",CH_UP,50,KEY,16));
        root.addView(r);
        r=row();
        add(r,key("📡 VOL −",VOL_DOWN,50,KEY,16));
        add(r,key("TV",SOURCE,50,KEY,16));
        add(r,key("CH −  −",CH_DOWN,50,KEY,16));
        root.addView(r);

        section(root,"SMART TV");
        r=row();
        add(r,key("RED",RED,50,Color.rgb(145,18,18),14));
        add(r,key("GREEN",GREEN,50,Color.rgb(18,118,48),14));
        add(r,key("YELLOW",YELLOW,50,Color.rgb(166,132,8),14));
        add(r,key("BLUE",BLUE,50,Color.rgb(24,78,155),14));
        root.addView(r);

        section(root,"TECLADO");
        String[][] nums={{"1","2 ABC","3 DEF"},{"4 GHI","5 JKL","6 MNO"},{"7 PQRS","8 TUV","9 WXYZ"},{"CC","0","SUBTITLE"}};
        int[][] cmds={{1,2,3},{4,5,6},{7,8,9},{0,0x3C,SUBTITLE}};
        for(int i=0;i<nums.length;i++){
            r=row();
            for(int j=0;j<3;j++){
                int fs=(i==0&&j==0)?20:14;
                add(r,key(nums[i][j],cmds[i][j],50,KEY_DARK,fs));
            }
            root.addView(r);
        }

        section(root,"CONTROLE DE MÍDIA");
        r=row();
        add(r,key("◀◀",REWIND,50,KEY,19));
        add(r,key("▶",PLAY,50,KEY,19));
        add(r,key("Ⅱ",PAUSE,50,KEY,19));
        add(r,key("■",STOP,50,KEY,19));
        add(r,key("▶▶",FAST_FORWARD,50,KEY,19));
        root.addView(r);

        boolean available=ir!=null&&ir.hasIrEmitter();
        TextView status=label(available?"●  Emissor IR detectado  •  36 kHz":"○  Emissor IR não detectado",12);
        status.setTextColor(available?Color.rgb(75,145,95):GRAY);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));

        sv.addView(root);
        setContentView(sv);
    }

    private void send(int command){
        if(ir==null||!ir.hasIrEmitter()){
            Toast.makeText(this,"Este telemóvel não possui emissor IR.",Toast.LENGTH_SHORT).show(); return;
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
                  
