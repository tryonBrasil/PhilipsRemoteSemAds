package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.ConsumerIrManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private boolean toggle = false;
    private int white = Color.rgb(245,245,245);
    private int dark = Color.rgb(25,25,25);
    private int key = Color.rgb(48,48,48);
    private int key2 = Color.rgb(60,60,60);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ir = (ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        build();
    }

    TextView label(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(white); t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    Button btn(String text, int command, int h) {
        Button b = new Button(this);
        b.setText(text); b.setTextColor(white); b.setTextSize(11);
        b.setAllCaps(false); b.setPadding(2,0,2,0);
        b.setMinHeight(0); b.setMinWidth(0); b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(key); bg.setCornerRadius(28); b.setBackground(bg);
        b.setOnClickListener(v -> send(command));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,h,1);
        p.setMargins(3,3,3,3); b.setLayoutParams(p);
        return b;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setPadding(3,0,3,0);
        return r;
    }

    void add(LinearLayout r, Button b) { r.addView(b); }

    Button round(String text, int command, int size) {
        Button b = btn(text, command, size);
        b.setTextSize(12);
        return b;
    }

    void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(12,10,12,14);
        root.setBackgroundColor(dark);

        TextView title = label("PHILIPS", 18);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1,38));

        // Top of the original remote
        LinearLayout r = row();
        add(r, btn("⏪", 59, 42));
        add(r, btn("▶", 48, 42));
        add(r, btn("⏩", 58, 42));
        root.addView(r);

        r = row();
        add(r, btn("■", 59, 42));
        add(r, btn("⏸", 48, 42));
        add(r, btn("●", 59, 42));
        root.addView(r);

        r = row();
        add(r, btn("MEDIA", 59, 42));
        add(r, btn("GUIDE", 35, 42));
        add(r, btn("SEARCH", 20, 42));
        root.addView(r);

        r = row();
        add(r, btn("⚙", 15, 42));
        add(r, btn("SOURCE", 36, 42));
        add(r, btn("LIST", 15, 42));
        root.addView(r);

        r = row();
        add(r, btn("HOME", 50, 44));
        add(r, btn("SMART TV", 50, 44));
        root.addView(r);

        // Color keys
        r = row();
        add(r, btn("●", 12, 36)); add(r, btn("●", 17, 36));
        add(r, btn("●", 16, 36)); add(r, btn("●", 23, 36));
        root.addView(r);

        r = row();
        add(r, btn("INFO", 15, 40)); add(r, btn("BACK", 28, 40));
        add(r, btn("EXIT", 20, 40)); add(r, btn("OPTIONS", 15, 40));
        root.addView(r);

        // Direction pad, like the physical remote
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(18,5,18,5);

        r = row();
        add(r, btn("▲", 32, 48));
        nav.addView(r);

        r = row();
        add(r, btn("◀", 21, 52)); add(r, btn("OK", 32, 52)); add(r, btn("▶", 22, 52));
        nav.addView(r);

        r = row();
        add(r, btn("▼", 33, 48));
        nav.addView(r);
        root.addView(nav);

        r = row();
        add(r, btn("↩ BACK", 28, 42)); add(r, btn("FORMAT", 15, 42)); add(r, btn("TV", 50, 42));
        root.addView(r);

        // Volume / Netflix / mute / channel block
        r = row();
        add(r, btn("VOL −", 17, 44));
        add(r, btn("NETFLIX", 59, 44));
        add(r, btn("MUTE", 13, 44));
        add(r, btn("CH +", 23, 44));
        root.addView(r);
        r = row();
        add(r, btn("VOL +", 16, 44));
        add(r, btn("YouTube", 58, 44));
        add(r, btn("CH −", 33, 44));
        root.addView(r);

        // Numeric keypad
        String[][] nums = {
            {"1", "2 ABC", "3 DEF"},
            {"4 GHI", "5 JKL", "6 MNO"},
            {"7 PQRS", "8 TUV", "9 WXYZ"},
            {"SUBTITLE", "0", "TEXT"}
        };
        for (String[] n : nums) {
            r = row();
            for (String s : n) {
                int cmd = 0;
                if (s.startsWith("1")) cmd=1; else if (s.startsWith("2")) cmd=2; else if (s.startsWith("3")) cmd=3;
                else if (s.startsWith("4")) cmd=4; else if (s.startsWith("5")) cmd=5; else if (s.startsWith("6")) cmd=6;
                else if (s.startsWith("7")) cmd=7; else if (s.startsWith("8")) cmd=8; else if (s.startsWith("9")) cmd=9; else if (s.equals("0")) cmd=0;
                else cmd=15;
                add(r, btn(s, cmd, 42));
            }
            root.addView(r);
        }

        TextView status = label(ir != null && ir.hasIrEmitter() ? "● IR disponível  •  Sem anúncios" : "○ Emissor IR não detectado", 12);
        status.setTextColor(Color.LTGRAY);
        status.setPadding(0,10,0,0);
        root.addView(status, new LinearLayout.LayoutParams(-1,40));

        scroll.addView(root);
        setContentView(scroll);
    }

    void send(int command) {
        if (ir == null || !ir.hasIrEmitter()) {
            Toast.makeText(this, "Este celular não informou emissor IR.", Toast.LENGTH_SHORT).show();
            return;
        }
        toggle = !toggle;
        try {
            ir.transmit(36000, rc5(0, command, toggle));
        } catch (Exception e) {
            Toast.makeText(this, "Falha IR: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    int[] rc5(int addr, int cmd, boolean tog) {
        int bits = (1<<13) | (1<<12) | ((tog?1:0)<<11) | ((addr&31)<<6) | (cmd&63);
        ArrayList<Integer> p = new ArrayList<>();
        int mask = 1<<13;
        for (int i=0;i<14;i++) {
            boolean one = (bits & mask) != 0;
            p.add(one ? 889 : 0);
            p.add(889);
            mask >>= 1;
        }
        int[] out = new int[p.size()];
        for (int i=0;i<out.length;i++) out[i] = p.get(i);
        return out;
    }
}
