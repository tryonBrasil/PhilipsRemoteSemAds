package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.ConsumerIrManager;
import android.graphics.Color;
import android.widget.*;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private boolean toggle=false;
    @Override public void onCreate(Bundle b){ super.onCreate(b); ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE); build(); }
    Button btn(String s, final int cmd){ Button b=new Button(this); b.setText(s); b.setOnClickListener(v->send(cmd)); return b; }
    void build(){ LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(12,12,12,12); root.setBackgroundColor(Color.rgb(18,18,18)); TextView title=new TextView(this); title.setText("PHILIPS 50PUG6513/7"); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setGravity(17); root.addView(title,new LinearLayout.LayoutParams(-1,60)); TextView status=new TextView(this); status.setText(ir!=null&&ir.hasIrEmitter()?"Infravermelho disponivel":"Sem emissor IR detectado"); status.setTextColor(Color.LTGRAY); status.setGravity(17); root.addView(status,new LinearLayout.LayoutParams(-1,40)); GridLayout g=new GridLayout(this); g.setColumnCount(3); String[][] keys={{"POWER","VOL-","VOL+"},{"MUDO","CH-","CH+"},{"LEFT","OK","RIGHT"},{"BACK","HOME","MENU"},{"INFO","SOURCE","GUIDE"},{"LIST","NETFLIX","PLAY"}}; int[][] cmds={{12,17,16},{13,33,23},{21,32,22},{28,50,20},{15,36,35},{59,58,48}}; for(int r=0;r<keys.length;r++) for(int c=0;c<3;c++){ Button b=btn(keys[r][c],cmds[r][c]); GridLayout.LayoutParams p=new GridLayout.LayoutParams(); p.width=0; p.height=64; p.columnSpec=GridLayout.spec(c,1f); p.setMargins(4,4,4,4); g.addView(b,p);} root.addView(g,new LinearLayout.LayoutParams(-1,0,1)); TextView foot=new TextView(this); foot.setText("Sem anuncios - RC5"); foot.setTextColor(Color.GRAY); foot.setGravity(17); root.addView(foot,new LinearLayout.LayoutParams(-1,36)); setContentView(root); }
    void send(int command){ if(ir==null||!ir.hasIrEmitter()){Toast.makeText(this,"Este celular nao informou emissor IR.",Toast.LENGTH_SHORT).show(); return;} toggle=!toggle; try{ir.transmit(36000,rc5(0,command,toggle));}catch(Exception e){Toast.makeText(this,"Falha IR: "+e.getMessage(),Toast.LENGTH_SHORT).show();} }
    int[] rc5(int addr,int cmd,boolean tog){ int bits=(1<<13)|(1<<12)|((tog?1:0)<<11)|((addr&31)<<6)|(cmd&63); ArrayList<Integer> p=new ArrayList<>(); int mask=1<<13; for(int i=0;i<14;i++){ boolean one=(bits&mask)!=0; p.add(one?889:0); p.add(889); mask>>=1; } int[] out=new int[p.size()]; for(int i=0;i<out.length;i++) out[i]=p.get(i); return out; }
}