package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.ConsumerIrManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private boolean toggle = false;

    private static final int FREQ = 36000;
    private static final int UNIT = 444; // RC6: 36 kHz, ~444 us
    private static final int BG = Color.rgb(22,22,22);
    private static final int BTN = Color.rgb(43,43,43);
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(170,170,170);

    // Philips TV RC6 command set, address/system 0x00.
    // These are the common Philips TV codes used by working RC6 remotes.
    private static final int POWER = 0x0C;
    private static final int MUTE = 0x0D;
    private static final int VOL_DOWN = 0x11;
    private static final int VOL_UP = 0x10;
    private static final int CH_DOWN = 0x21;
    private static final int CH_UP = 0x20;
    private static final int UP = 0x58;
    private static final int DOWN = 0x59;
    private static final int LEFT = 0x5A;
    private static final int RIGHT = 0x5B;
    private static final int OK = 0x5C;
    private static final int BACK = 0x0A;
    private static final int MENU = 0x57;
    private static final int HOME = 0x54;
    private static final int SOURCE = 0x38;
    private static final int INFO = 0x0F;
    private static final int GUIDE = 0xCC;
    private static final int NETFLIX = 0x76;
    private static final int SETTINGS = 0xBF;
    private static final int RED = 0x6D;
    private static final int GREEN = 0x6E;
    private static final int YELLOW = 0x6F;
    private static final int BLUE = 0x70;
    private static final int PLAY = 0x2C;
    private static final int STOP = 0x31;
    private static final int PAUSE = 0x30;
    private static final int REWIND = 0x2B;
    private static final int FAST_FORWARD = 0x28;
    private static final int RECORD = 0x37;
    private static final int SUBTITLE = 0x4B;
    private static final int EXIT = 0x9F;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ir = (ConsumerIrManager) getSystemService(CONSUMER_IR_SERVICE);
        build();
    }

    private TextView txt(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(WHITE);
        t.setTextSize(sp);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button key(String s, int cmd, int h) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(WHITE);
        b.setTextSize(10);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0,0,0,0);
        b.setMinHeight(0);
        b.setMinWidth(0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BTN);
        g.setCornerRadius(22);
        b.setBackground(g);
        b.setOnClickListener(v -> send(cmd));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,h,1);
        p.setMargins(3,3,3,3);
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private void add(LinearLayout r, Button b) { r.addView(b); }

    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(14,8,14,18);
        root.setBackgroundColor(BG);

        TextView brand = txt("PHILIPS",17);
        brand.setTypeface(null,1);
        root.addView(brand, new LinearLayout.LayoutParams(-1,34));

        LinearLayout r = row();
        add(r,key("⏻",POWER,42)); add(r,key("SOURCE",SOURCE,42)); add(r,key("⚙",SETTINGS,42));
        root.addView(r);

        r=row(); add(r,key("⏮",REWIND,38)); add(r,key("▶",PLAY,38)); add(r,key("⏸",PAUSE,38)); add(r,key("⏭",FAST_FORWARD,38)); root.addView(r);
        r=row(); add(r,key("■",STOP,38)); add(r,key("● REC",RECORD,38)); add(r,key("GUIDE",GUIDE,38)); add(r,key("INFO",INFO,38)); root.addView(r);

        TextView section=txt("SMART TV",11);
        section.setTextColor(GRAY);
        root.addView(section,new LinearLayout.LayoutParams(-1,25));

        r=row(); add(r,key("HOME",HOME,40)); add(r,key("BACK",BACK,40)); add(r,key("MENU",MENU,40)); add(r,key("EXIT",EXIT,40)); root.addView(r);

        r=row(); add(r,key("●",RED,30)); add(r,key("●",GREEN,30)); add(r,key("●",YELLOW,30)); add(r,key("●",BLUE,30)); root.addView(r);

        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(35,4,35,4);

        r=row(); add(r,key("▲",UP,43)); nav.addView(r);
        r=row(); add(r,key("◀",LEFT,48)); add(r,key("OK",OK,48)); add(r,key("▶",RIGHT,48)); nav.addView(r);
        r=row(); add(r,key("▼",DOWN,43)); nav.addView(r);
        root.addView(nav);

        r=row(); add(r,key("VOL −",VOL_DOWN,43)); add(r,key("MUTE",MUTE,43)); add(r,key("VOL +",VOL_UP,43)); root.addView(r);
        r=row(); add(r,key("CH −",CH_DOWN,43)); add(r,key("NETFLIX",NETFLIX,43)); add(r,key("CH +",CH_UP,43)); root.addView(r);

        TextView numTitle=txt("TECLADO",10);
        numTitle.setTextColor(GRAY);
        root.addView(numTitle,new LinearLayout.LayoutParams(-1,22));

        String[][] n={{"1","2 ABC","3 DEF"},{"4 GHI","5 JKL","6 MNO"},{"7 PQRS","8 TUV","9 WXYZ"},{"SUBTITLE","0","TEXT"}};
        for(String[] a:n){
            r=row();
            for(String s:a){
                int cmd;
                if(s.startsWith("1")) cmd=1;
                else if(s.startsWith("2")) cmd=2;
                else if(s.startsWith("3")) cmd=3;
                else if(s.startsWith("4")) cmd=4;
                else if(s.startsWith("5")) cmd=5;
                else if(s.startsWith("6")) cmd=6;
                else if(s.startsWith("7")) cmd=7;
                else if(s.startsWith("8")) cmd=8;
                else if(s.startsWith("9")) cmd=9;
                else if(s.equals("0")) cmd=0;
                else if(s.equals("SUBTITLE")) cmd=SUBTITLE;
                else cmd=0x3C; // Teletext for TEXT
                add(r,key(s,cmd,40));
            }
            root.addView(r);
        }

        boolean available = ir != null && ir.hasIrEmitter();
        TextView status=txt(available ? "●  IR disponível  •  RC6 36 kHz  •  Sem anúncios"
                                      : "○  Emissor IR não detectado",11);
        status.setTextColor(GRAY);
        root.addView(status,new LinearLayout.LayoutParams(-1,38));

        sv.addView(root);
        setContentView(sv);
    }

    private void send(int command) {
        if (ir == null || !ir.hasIrEmitter()) {
            Toast.makeText(this,"Este celular não informou emissor IR.",Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            toggle = !toggle;
            int[] pattern = rc6(0x00, command, toggle);
            ir.transmit(FREQ, pattern);
        } catch (Exception e) {
            Toast.makeText(this,"Falha ao enviar IR: " + e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    /*
     * Philips RC6 Mode 0:
     * header 2666/889 us
     * start bit 1
     * mode 000
     * toggle (double width)
     * address 8 bits
     * command 8 bits
     *
     * RC6 Manchester polarity:
     * 1 = mark -> space
     * 0 = space -> mark
     */
    private int[] rc6(int address, int command, boolean tog) {
        ArrayList<Integer> p = new ArrayList<>();
        boolean markState = true;

        append(p, true, 2666);
        append(p, false, 889);

        appendBit(p, 1, UNIT);
        appendBit(p, 0, UNIT);
        appendBit(p, 0, UNIT);
        appendBit(p, 0, UNIT);

        appendBit(p, tog ? 1 : 0, UNIT * 2);

        for (int mask = 0x80; mask != 0; mask >>= 1)
            appendBit(p, (address & mask) != 0 ? 1 : 0, UNIT);

        for (int mask = 0x80; mask != 0; mask >>= 1)
            appendBit(p, (command & mask) != 0 ? 1 : 0, UNIT);

        append(p, false, 2666);

        int[] out = new int[p.size()];
        for (int i=0;i<p.size();i++) out[i]=p.get(i);
        return out;
    }

    private void appendBit(ArrayList<Integer> p, int bit, int half) {
        if (bit == 1) {
            append(p,true,half);
            append(p,false,half);
        } else {
            append(p,false,half);
            append(p,true,half);
        }
    }

    private void append(ArrayList<Integer> p, boolean mark, int duration) {
        if (duration <= 0) return;
        if (p.isEmpty()) {
            // ConsumerIrManager patterns start with an ON/mark duration.
            if (!mark) {
                p.add(0);
            }
            p.add(duration);
            return;
        }

        // Pattern entries alternate mark/space. Merge equal polarity entries.
        boolean expectedMark = (p.size() % 2 == 1);
        if (expectedMark == mark) {
            int i = p.size()-1;
            p.set(i, p.get(i)+duration);
        } else {
            p.add(duration);
        }
    }
}
