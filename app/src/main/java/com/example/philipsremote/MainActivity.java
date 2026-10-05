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
    private boolean toggle=false;
    private final int BG=Color.rgb(22,22,22), BTN=Color.rgb(43,43,43), WHITE=Color.WHITE, GRAY=Color.rgb(170,170,170);

    public void onCreate(Bundle b){super.onCreate(b); ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE); build();}

    TextView txt(String s,int sp){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(WHITE); t.setTextSize(sp);
        t.setGravity(Gravity.CENTER); return t;
    }
    Button key(String s,int cmd,int h){
        Button b=new Button(this); b.setText(s); b.setTextColor(WHITE); b.setTextSize(10);
        b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0); b.setMinHeight(0); b.setMinWidth(0);
        GradientDrawable g=new GradientDrawable(); g.setColor(BTN); g.setCornerRadius(22); b.setBackground(g);
        b.setOnClickListener(v->send(cmd));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,h,1); p.setMargins(3,3,3,3); b.setLayoutParams(p);
        return b;
    }
    LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER);return r;}
    void add(LinearLayout r,Button b){r.addView(b);}

    void build(){
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(14,8,14,18); root.setBackgroundColor(BG);

        TextView brand=txt("PHILIPS",17); brand.setTypeface(null,1); root.addView(brand,new LinearLayout.LayoutParams(-1,34));

        // Silhouette/order follows the physical Philips remote: media keys -> navigation -> volume/channel -> keypad.
        LinearLayout r=row(); add(r,key("⏮",59,38)); add(r,key("▶",48,38)); add(r,key("⏭",58,38)); root.addView(r);
        r=row(); add(r,key("■",59,38)); add(r,key("⏸",48,38)); add(r,key("●",59,38)); root.addView(r);

        r=row(); add(r,key("GUIDE",35,38)); add(r,key("SEARCH",20,38)); add(r,key("SOURCE",36,38)); root.addView(r);
        r=row(); add(r,key("HOME",50,40)); add(r,key("⚙",15,40)); add(r,key("LIST",15,40)); root.addView(r);

        TextView section=txt("SMART TV",11); section.setTextColor(GRAY); root.addView(section,new LinearLayout.LayoutParams(-1,25));
        r=row(); add(r,key("INFO",15,38)); add(r,key("BACK",28,38)); add(r,key("OPTIONS",15,38)); add(r,key("EXIT",20,38)); root.addView(r);

        // Four compact colour keys
        r=row(); add(r,key("●",12,30)); add(r,key("●",17,30)); add(r,key("●",16,30)); add(r,key("●",23,30)); root.addView(r);

        // Large circular-looking navigation cluster
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.VERTICAL);nav.setGravity(Gravity.CENTER);nav.setPadding(35,4,35,4);
        r=row(); add(r,key("▲",32,43)); nav.addView(r);
        r=row(); add(r,key("◀",21,48)); add(r,key("OK",32,48)); add(r,key("▶",22,48)); nav.addView(r);
        r=row(); add(r,key("▼",33,43)); nav.addView(r); root.addView(nav);

        r=row(); add(r,key("VOL −",17,43)); add(r,key("MUTE",13,43)); add(r,key("CH +",23,43)); root.addView(r);
        r=row(); add(r,key("VOL +",16,43)); add(r,key("NETFLIX",59,43)); add(r,key("CH −",33,43)); root.addView(r);

        TextView numTitle=txt("TECLADO",10);numTitle.setTextColor(GRAY);root.addView(numTitle,new LinearLayout.LayoutParams(-1,22));
        String[][] n={{"1","2 ABC","3 DEF"},{"4 GHI","5 JKL","6 MNO"},{"7 PQRS","8 TUV","9 WXYZ"},{"SUBTITLE","0","TEXT"}};
        for(String[] a:n){r=row();for(String s:a){int cmd=0;if(s.startsWith("1"))cmd=1;else if(s.startsWith("2"))cmd=2;else if(s.startsWith("3"))cmd=3;else if(s.startsWith("4"))cmd=4;else if(s.startsWith("5"))cmd=5;else if(s.startsWith("6"))cmd=6;else if(s.startsWith("7"))cmd=7;else if(s.startsWith("8"))cmd=8;else if(s.startsWith("9"))cmd=9;else if(s.equals("0"))cmd=0;else cmd=15;add(r,key(s,cmd,40));}root.addView(r);}

        TextView status=txt(ir!=null&&ir.hasIrEmitter()?"●  IR disponível  •  Sem anúncios":"○  Emissor IR não detectado",11);
        status.setTextColor(GRAY);root.addView(status,new LinearLayout.LayoutParams(-1,38));
        sv.addView(root);setContentView(sv);
    }

    void send(int command){
        if(ir==null||!ir.hasIrEmitter()){Toast.makeText(this,"Este celular não informou emissor IR.",Toast.LENGTH_SHORT).show();return;}
        toggle=!toggle;try{ir.transmit(36000,rc5(0,command,toggle));}catch(Exception e){Toast.makeText(this,"Falha IR: "+e.getMessage(),Toast.LENGTH_SHORT).show();}
    }
    int[] rc5(int addr,int cmd,boolean tog){
        int bits=(1<<13)|(1<<12)|((tog?1:0)<<11)|((addr&31)<<6)|(cmd&63);ArrayList<Integer> p=new ArrayList<>();int mask=1<<13;
        for(int i=0;i<14;i++){boolean one=(bits&mask)!=0;p.add(one?889:0);p.add(889);mask>>=1;}
        int[] out=new int[p.size()];for(int i=0;i<out.length;i++)out[i]=p.get(i);return out;
    }
}