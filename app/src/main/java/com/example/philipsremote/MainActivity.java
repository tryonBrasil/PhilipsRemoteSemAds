package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.hardware.ConsumerIrManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private boolean toggle = false;

    private static final int FREQ = 36000;
    private static final int UNIT = 444;
    private static final int BG = Color.rgb(15,17,21);
    private static final int PANEL = Color.rgb(20,22,27);
    private static final int KEY = Color.rgb(38,40,46);
    private static final int KEY_DARK = Color.rgb(30,32,38);
    private static final int KEY_ACTIVE = Color.rgb(55,58,66);
    private static final int BORDER = Color.rgb(55,57,64);
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(160,164,172);

    private static final int POWER=0x0C, MUTE=0x0D, VOL_DOWN=0x11, VOL_UP=0x10;
    private static final int CH_DOWN=0x21, CH_UP=0x20;
    private static final int UP=0x58, DOWN=0x59, LEFT=0x5A, RIGHT=0x5B, OK=0x5C;
    private static final int BACK=0x0A, MENU=0x57, HOME=0x54, SOURCE=0x38;
    private static final int INFO=0x0F, GUIDE=0xCC, NETFLIX=0x76, SETTINGS=0xBF;
    private static final int RED=0x6D, GREEN=0x6E, YELLOW=0x6F, BLUE=0x70;
    private static final int PLAY=0x2C, STOP=0x31, PAUSE=0x30, REWIND=0x2B;
    private static final int FAST_FORWARD=0x28, RECORD=0x37, SUBTITLE=0x4B, EXIT=0x9F;

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        w.getDecorView().setSystemUiVisibility(0);
        ir = (ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        build();
    }

    private GradientDrawable bg(int color, int radius, boolean border) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (border) g.setStroke(dp(1), BORDER);
        return g;
    }

    private TextView label(String text, float size) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(WHITE);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        return t;
    }

    private Button key(String text, int cmd, int height) {
        return key(text, cmd, height, KEY, 17);
    }

    private Button key(String text, int cmd, int height, int color, float size) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(WHITE);
        b.setTextSize(size);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        b.setIncludeFontPadding(false);
        b.setStateListAnimator(null);
        b.setBackground(bg(color, 18, true));
        b.setOnClickListener(v -> send(cmd));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(height), 1f);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private void add(LinearLayout r, Button b) {
        r.addView(b);
    }

    private void section(LinearLayout root, String title) {
        TextView t = label(title, 10);
        t.setTextColor(GRAY);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(28));
        p.setMargins(dp(4), dp(8), dp(4), dp(2));
        root.addView(t, p);
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(10), dp(8), dp(10), dp(12));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(8), dp(6), dp(8), dp(8));
        body.setBackground(bg(PANEL, 22, true));

        TextView logo = label("PHILIPS", 15);
        logo.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        body.addView(logo, new LinearLayout.LayoutParams(-1, dp(34)));

        LinearLayout r = row();
        Button power = key("⏻", POWER, 64, KEY_DARK, 30);
        LinearLayout.LayoutParams powerP = (LinearLayout.LayoutParams) power.getLayoutParams();
        powerP.weight = 1;
        r.addView(power);
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(72)));

        r = row();
        add(r,key("SOURCE",SOURCE,56,KEY,12));
        add(r,key("INFO",INFO,56,KEY,15));
        add(r,key("⚙",SETTINGS,56,KEY,23));
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(64)));

        r = row();
        add(r,key("GUIDE",GUIDE,56,KEY,11));
        add(r,key("HOME",HOME,56,KEY,13));
        add(r,key("NETFLIX",NETFLIX,56,KEY,10));
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(64)));

        section(body, "NAVEGAÇÃO");
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(24), dp(2), dp(24), dp(2));
        nav.setBackground(bg(KEY_DARK, 26, true));

        r = row();
        add(r,key("▲",UP,64,KEY_ACTIVE,25));
        nav.addView(r, new LinearLayout.LayoutParams(-1, dp(72)));

        r = row();
        add(r,key("◀",LEFT,70,KEY_ACTIVE,25));
        add(r,key("OK",OK,70,KEY_ACTIVE,19));
        add(r,key("▶",RIGHT,70,KEY_ACTIVE,25));
        nav.addView(r, new LinearLayout.LayoutParams(-1, dp(78)));

        r = row();
        add(r,key("▼",DOWN,64,KEY_ACTIVE,25));
        nav.addView(r, new LinearLayout.LayoutParams(-1, dp(72)));
        body.addView(nav, new LinearLayout.LayoutParams(-1, dp(230)));

        r = row();
        add(r,key("↩  BACK",BACK,58,KEY,13));
        add(r,key("☰  MENU",MENU,58,KEY,13));
        add(r,key("EXIT",EXIT,58,KEY,13));
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(66)));

        section(body, "VOLUME E CANAIS");
        r = row();
        add(r,key("🔊  VOL +",VOL_UP,66,KEY,16));
        add(r,key("🔇",MUTE,66,KEY,22));
        add(r,key("CH +  ＋",CH_UP,66,KEY,16));
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(74)));

        r = row();
        add(r,key("🔉  VOL −",VOL_DOWN,66,KEY,16));
        add(r,key("TV",SOURCE,66,KEY,13));
        add(r,key("CH −  −",CH_DOWN,66,KEY,16));
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(74)));

        section(body, "SMART TV");
        r = row();
        add(r,key("RED",RED,54,Color.rgb(125,20,20),11));
        add(r,key("GREEN",GREEN,54,Color.rgb(20,105,45),11));
        add(r,key("YELLOW",YELLOW,54,Color.rgb(150,120,10),11));
        add(r,key("BLUE",BLUE,54,Color.rgb(20,70,145),11));
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(62)));

        section(body, "TECLADO");
        String[][] nums = {
            {"1","2 ABC","3 DEF"},
            {"4 GHI","5 JKL","6 MNO"},
            {"7 PQRS","8 TUV","9 WXYZ"},
            {"CC","0","SUBTITLE"}
        };

        for (String[] a : nums) {
            r = row();
            for (String s : a) {
                int c;
                if (s.startsWith("1")) c=1;
                else if (s.startsWith("2")) c=2;
                else if (s.startsWith("3")) c=3;
                else if (s.startsWith("4")) c=4;
                else if (s.startsWith("5")) c=5;
                else if (s.startsWith("6")) c=6;
                else if (s.startsWith("7")) c=7;
                else if (s.startsWith("8")) c=8;
                else if (s.startsWith("9")) c=9;
                else if (s.equals("0")) c=0;
                else if (s.equals("SUBTITLE")) c=SUBTITLE;
                else c=0x3C;
                add(r,key(s,c,62,KEY_DARK,s.length() > 3 ? 10 : 20));
            }
            body.addView(r, new LinearLayout.LayoutParams(-1, dp(70)));
        }

        section(body, "CONTROLE DE MÍDIA");
        r = row();
        add(r,key("◀◀",REWIND,60,KEY,18));
        add(r,key("▶",PLAY,60,KEY,21));
        add(r,key("Ⅱ",PAUSE,60,KEY,18));
        add(r,key("■",STOP,60,KEY,18));
        add(r,key("▶▶",FAST_FORWARD,60,KEY,18));
        body.addView(r, new LinearLayout.LayoutParams(-1, dp(68)));

        boolean available = ir != null && ir.hasIrEmitter();
        TextView status = label(
            available ? "●  Emissor IR detectado • 36 kHz" : "○  Emissor IR não detectado",
            10
        );
        status.setTextColor(available ? Color.rgb(130,190,140) : Color.rgb(220,110,110));
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(-1, dp(32));
        statusP.setMargins(0, dp(8), 0, 0);
        body.addView(status, statusP);

        root.addView(body, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));
        setContentView(scroll);
    }

    private void send(int command) {
        if (ir == null || !ir.hasIrEmitter()) {
            Toast.makeText(this, "Este celular não possui emissor IR disponível.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            toggle = !toggle;
            ir.transmit(FREQ, rc6(0x00, command, toggle));
        } catch (SecurityException e) {
            Toast.makeText(this, "Acesso ao emissor IR foi negado pelo Android. Verifique TRANSMIT_IR.", Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Sinal IR inválido para este emissor.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao enviar IR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int[] rc6(int address,int command,boolean tog) {
        ArrayList<Integer> p=new ArrayList<>();
        append(p,true,2666);
        append(p,false,889);
        appendBit(p,1,UNIT);
        appendBit(p,0,UNIT);
        appendBit(p,0,UNIT);
        appendBit(p,0,UNIT);
        appendBit(p,tog?1:0,UNIT*2);
        for(int m=0x80;m!=0;m>>=1) appendBit(p,(address&m)!=0?1:0,UNIT);
        for(int m=0x80;m!=0;m>>=1) appendBit(p,(command&m)!=0?1:0,UNIT);
        append(p,false,2666);
        int[] out=new int[p.size()];
        for(int i=0;i<p.size();i++) out[i]=p.get(i);
        return out;
    }

    private void appendBit(ArrayList<Integer> p,int bit,int half) {
        if(bit==1) {
            append(p,true,half);
            append(p,false,half);
        } else {
            append(p,false,half);
            append(p,true,half);
        }
    }

    private void append(ArrayList<Integer> p,boolean mark,int duration) {
        if(duration<=0) return;
        if(p.isEmpty()) {
            if(!mark) p.add(0);
            p.add(duration);
            return;
        }
        boolean expectedMark=(p.size()%2==1);
        if(expectedMark==mark) {
            int i=p.size()-1;
            p.set(i,p.get(i)+duration);
        } else {
            p.add(duration);
        }
    }
}
