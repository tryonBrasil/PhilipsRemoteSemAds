package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.hardware.ConsumerIrManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private boolean toggle = false;
    private static final int FREQ = 36000;
    private static final int UNIT = 444;

    private static final int BG_COLOR = Color.parseColor("#0F1115");
    private static final int BTN_COLOR = Color.parseColor("#26282E");
    private static final int BTN_BORDER = Color.parseColor("#333333");
    private static final int TEXT_COLOR = Color.WHITE;
    private static final int TEXT_RED = Color.parseColor("#E53935");

    private static final int POWER=0x0C,MUTE=0x0D,VOL_DOWN=0x11,VOL_UP=0x10;
    private static final int CH_DOWN=0x21,CH_UP=0x20;
    private static final int UP=0x58,DOWN=0x59,LEFT=0x5A,RIGHT=0x5B,OK=0x5C;
    private static final int BACK=0x0A,MENU=0x57,HOME=0x54,SOURCE=0x38;
    private static final int INFO=0x0F,GUIDE=0xCC,NETFLIX=0x76,SETTINGS=0xBF;
    private static final int RED=0x6D,GREEN=0x6E,YELLOW=0x6F,BLUE=0x70;
    private static final int PLAY=0x2C,STOP=0x31,PAUSE=0x30,REWIND=0x2B;
    private static final int FAST_FORWARD=0x28,RECORD=0x37,SUBTITLE=0x4B,EXIT=0x9F;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(BG_COLOR);
        w.setNavigationBarColor(BG_COLOR);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        ir = (ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        buildUi();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, int radius, boolean border) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (border) g.setStroke(dp(1), BTN_BORDER);
        return g;
    }

    private Button createBtn(String text, int cmd, int radius, int bgColor, int textColor, int textSizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(textSizeSp);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setIncludeFontPadding(false);
        b.setStateListAnimator(null);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinimumWidth(0);
        b.setBackground(bg(bgColor, radius, true));
        b.setOnClickListener(v -> send(cmd));
        return b;
    }

    private Button createPowerBtn() {
        Button b = createBtn("⏻", POWER, 34, BTN_COLOR, TEXT_RED, 27);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BTN_COLOR);
        g.setStroke(dp(2), TEXT_RED);
        g.setCornerRadius(dp(34));
        b.setBackground(g);
        return b;
    }

    private void addDpadBtn(FrameLayout fl, Button b, int transX, int transY, int sizeX, int sizeY) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(sizeX), dp(sizeY));
        p.gravity = Gravity.CENTER;
        b.setLayoutParams(p);
        b.setTranslationX(dp(transX));
        b.setTranslationY(dp(transY));
        fl.addView(b);
    }

    private void addWeightedBtn(LinearLayout parent, String text, int cmd, int textSize) {
        Button b = createBtn(text, cmd, 30, BTN_COLOR, TEXT_COLOR, textSize);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.setMargins(dp(2),dp(2),dp(2),dp(2));
        parent.addView(b,p);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG_COLOR);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5),dp(5),dp(5),dp(10));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(BG_COLOR);
        scroll.addView(root,new ScrollView.LayoutParams(-1,-1));

        FrameLayout dpadArea = new FrameLayout(this);
        dpadArea.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(365)));

        View diamond = new View(this);
        GradientDrawable dBg = new GradientDrawable();
        dBg.setColor(Color.TRANSPARENT);
        dBg.setStroke(dp(1),BTN_BORDER);
        dBg.setCornerRadius(dp(32));
        diamond.setBackground(dBg);
        diamond.setRotation(45);
        FrameLayout.LayoutParams dpParams = new FrameLayout.LayoutParams(dp(220),dp(220));
        dpParams.gravity = Gravity.CENTER;
        dpadArea.addView(diamond,dpParams);

        addDpadBtn(dpadArea,createBtn("OK",OK,44,BTN_COLOR,TEXT_COLOR,20),0,0,92,92);
        addDpadBtn(dpadArea,createBtn("∧",UP,32,BTN_COLOR,TEXT_COLOR,25),0,-98,66,66);
        addDpadBtn(dpadArea,createBtn("∨",DOWN,32,BTN_COLOR,TEXT_COLOR,25),0,98,66,66);
        addDpadBtn(dpadArea,createBtn("＜",LEFT,32,BTN_COLOR,TEXT_COLOR,25),-98,0,66,66);
        addDpadBtn(dpadArea,createBtn("＞",RIGHT,32,BTN_COLOR,TEXT_COLOR,25),98,0,66,66);
        addDpadBtn(dpadArea,createPowerBtn(),0,-165,68,68);
        addDpadBtn(dpadArea,createBtn("⎘",SOURCE,32,BTN_COLOR,TEXT_COLOR,22),-130,-112,62,62);
        addDpadBtn(dpadArea,createBtn("▦",MENU,32,BTN_COLOR,TEXT_COLOR,22),130,-112,62,62);
        addDpadBtn(dpadArea,createBtn("←",BACK,32,BTN_COLOR,TEXT_COLOR,24),-130,112,62,62);
        addDpadBtn(dpadArea,createBtn("⌂",HOME,32,BTN_COLOR,TEXT_COLOR,27),130,112,62,62);
        root.addView(dpadArea);

        LinearLayout midBar = new LinearLayout(this);
        midBar.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable midBg = new GradientDrawable();
        midBg.setColor(Color.TRANSPARENT);
        midBg.setStroke(dp(1),BTN_BORDER);
        midBg.setCornerRadius(dp(20));
        midBar.setBackground(midBg);
        midBar.setPadding(dp(4),dp(4),dp(4),dp(4));
        LinearLayout.LayoutParams midParams = new LinearLayout.LayoutParams(-1,dp(64));
        midParams.setMargins(0,dp(7),0,dp(12));
        root.addView(midBar,midParams);
        addWeightedBtn(midBar,"⋯",SETTINGS,19);
        addWeightedBtn(midBar,"🔢",GUIDE,19);
        addWeightedBtn(midBar,"▶/II",PLAY,15);
        addWeightedBtn(midBar,"🎨",NETFLIX,15);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        TextView volLabel = new TextView(this);
        volLabel.setText("Volume");
        volLabel.setTextColor(TEXT_COLOR);
        volLabel.setGravity(Gravity.CENTER);
        TextView chLabel = new TextView(this);
        chLabel.setText("Canais");
        chLabel.setTextColor(TEXT_COLOR);
        chLabel.setGravity(Gravity.CENTER);
        labels.addView(volLabel,new LinearLayout.LayoutParams(0,-2,1f));
        labels.addView(new Space(this),new LinearLayout.LayoutParams(dp(64),-2));
        labels.addView(chLabel,new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(labels,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setPadding(0,dp(5),0,dp(2));
        addWeightedBtn(row1,"🔊",VOL_UP,19);
        Button exitBtn = createBtn("TV\nEXIT",EXIT,32,BTN_COLOR,TEXT_COLOR,11);
        row1.addView(exitBtn,new LinearLayout.LayoutParams(dp(68),dp(68)));
        addWeightedBtn(row1,"＋",CH_UP,24);
        root.addView(row1,new LinearLayout.LayoutParams(-1,dp(76)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        row2.setPadding(0,dp(2),0,dp(10));
        addWeightedBtn(row2,"🔉",VOL_DOWN,19);
        Button muteBtn = createBtn("🔇",MUTE,32,BTN_COLOR,TEXT_COLOR,20);
        row2.addView(muteBtn,new LinearLayout.LayoutParams(dp(68),dp(68)));
        addWeightedBtn(row2,"－",CH_DOWN,24);
        root.addView(row2,new LinearLayout.LayoutParams(-1,dp(76)));

        boolean available = ir != null && ir.hasIrEmitter();
        TextView status = new TextView(this);
        status.setText(available ? "● Emissor IR detectado • 36 kHz" : "○ Emissor IR não detectado");
        status.setTextColor(available ? Color.rgb(130,190,140) : Color.rgb(220,110,110));
        status.setGravity(Gravity.CENTER);
        status.setTextSize(10);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));

        setContentView(scroll);
    }

    private void send(int command) {
        if (ir == null || !ir.hasIrEmitter()) {
            Toast.makeText(this,"Este celular não possui emissor IR disponível.",Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            toggle = !toggle;
            ir.transmit(FREQ,rc6(0x00,command,toggle));
        } catch (SecurityException e) {
            Toast.makeText(this,"Acesso ao emissor IR foi negado pelo Android. TRANSMIT_IR precisa estar declarado.",Toast.LENGTH_LONG).show();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this,"Sinal IR inválido para este emissor.",Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this,"Falha ao enviar IR: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    private int[] rc6(int address,int command,boolean tog) {
        ArrayList<Integer> p = new ArrayList<>();
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
        if(bit==1){append(p,true,half);append(p,false,half);}
        else{append(p,false,half);append(p,true,half);}
    }

    private void append(ArrayList<Integer> p,boolean mark,int duration) {
        if(duration<=0)return;
        if(p.isEmpty()){if(!mark)p.add(0);p.add(duration);return;}
        boolean expectedMark=(p.size()%2==1);
        if(expectedMark==mark){int i=p.size()-1;p.set(i,p.get(i)+duration);}
        else p.add(duration);
    }
}