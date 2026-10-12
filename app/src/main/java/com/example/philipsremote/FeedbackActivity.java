package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class FeedbackActivity extends Activity {
  private static final String SERVER="https://ir-remote-br-premium-server.vercel.app";
  private final int bg=Color.rgb(12,12,12), white=Color.WHITE, gray=Color.rgb(175,175,175), red=Color.rgb(190,24,32);
  private EditText message;
  private Spinner category;
  private Button send;
  @Override public void onCreate(Bundle state){
    super.onCreate(state); getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
    ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(bg);
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20),dp(28),dp(20),dp(24));
    TextView title=text("ENVIAR FEEDBACK",24,white); title.setTypeface(null,1); root.addView(title);
    TextView hint=text("Conte para nós o que está funcionando bem ou o que precisamos melhorar.",14,gray); hint.setPadding(0,dp(8),0,dp(18)); root.addView(hint);
    TextView label=text("Tipo de mensagem",14,white); root.addView(label);
    category=new Spinner(this);
    String[] options={"Sugestão","Problema","Reclamação","Elogio","Outro"};
    category.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options)); root.addView(category);
    message=new EditText(this); message.setTextColor(white); message.setHintTextColor(gray); message.setHint("Descreva sua mensagem (5 a 3000 caracteres)"); message.setGravity(Gravity.TOP|Gravity.START); message.setMinLines(5); message.setPadding(dp(12),dp(12),dp(12),dp(12)); message.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gray));
    LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.setMargins(0,dp(14),0,dp(14));root.addView(message,mp);
    send=new Button(this);send.setText("ENVIAR");send.setTextColor(white);send.setAllCaps(false);send.setBackgroundTintList(android.content.res.ColorStateList.valueOf(red));root.addView(send,new LinearLayout.LayoutParams(-1,dp(52)));
    send.setOnClickListener(v->submit());
    Button back=new Button(this);back.setText("VOLTAR");back.setAllCaps(false);back.setTextColor(white);root.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));back.setOnClickListener(v->finish());
    scroll.addView(root);setContentView(scroll);
  }
  private void submit(){
    FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
    if(user==null){new android.app.AlertDialog.Builder(this).setMessage("Entre com Google na tela Conta/Premium antes de enviar feedback.").setPositiveButton("OK",null).show();return;}
    String msg=message.getText().toString().trim(); if(msg.length()<5){message.setError("Escreva pelo menos 5 caracteres");return;}
    send.setEnabled(false);send.setText("ENVIANDO...");
    user.getIdToken(true).addOnSuccessListener(token->new Thread(()->{
      HttpURLConnection c=null;JSONObject result=null;
      try{
        c=(HttpURLConnection)new URL(SERVER+"/api/feedback").openConnection();c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setDoOutput(true);
        c.setRequestProperty("Authorization","Bearer "+token.getToken());c.setRequestProperty("Content-Type","application/json");
        JSONObject body=new JSONObject();body.put("category",String.valueOf(category.getSelectedItem()));body.put("message",msg);
        try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        InputStream in=c.getResponseCode()<400?c.getInputStream():c.getErrorStream();
        if(in!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);result=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
      }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
      JSONObject finalResult=result;runOnUiThread(()->{
        send.setEnabled(true);send.setText("ENVIAR");
        if(finalResult!=null&&finalResult.optBoolean("ok")){new android.app.AlertDialog.Builder(this).setTitle("Obrigado!").setMessage("Seu feedback foi enviado.").setPositiveButton("OK",(d,w)->finish()).show();}
        else Toast.makeText(this,"Não foi possível enviar. Verifique a conexão e tente novamente.",Toast.LENGTH_LONG).show();
      });
    }).start()).addOnFailureListener(e->{send.setEnabled(true);send.setText("ENVIAR");Toast.makeText(this,"Não foi possível validar sua conta Google.",Toast.LENGTH_LONG).show();});
  }
  private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
  private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
