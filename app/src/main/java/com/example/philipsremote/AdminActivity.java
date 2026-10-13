package com.example.philipsremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AdminActivity extends Activity {
  private static final String SERVER = "https://ir-remote-br-premium-server.vercel.app";
  private final int bg = Color.rgb(12,12,12), card = Color.rgb(26,26,30), white = Color.WHITE, gray = Color.rgb(175,175,175), red = Color.rgb(190,24,32), green = Color.rgb(80,170,100);
  private LinearLayout root, content;
  private TextView summary;
  private EditText premiumEmail;

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    if (FirebaseAuth.getInstance().getCurrentUser() == null) { finish(); return; }
    getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
    ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
    root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(24));
    TextView title = text("CENTRAL ADM",25,white); title.setTypeface(null,Typeface.BOLD); root.addView(title);
    TextView sub = text("Clientes, Premium e feedbacks",14,gray); root.addView(sub);
    summary = text("Carregando dados...",14,gray); summary.setPadding(0,dp(14),0,dp(12)); root.addView(summary);
    Button refresh = button("ATUALIZAR",red); root.addView(refresh); refresh.setOnClickListener(v -> load());
    content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); root.addView(content);
    Button back = button("VOLTAR",card); back.setOnClickListener(v -> finish()); root.addView(back);
    scroll.addView(root); setContentView(scroll); load();
  }

  private void load() {
    summary.setText("Carregando clientes e feedbacks...");
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) { summary.setText("Entre com a conta Google autorizada."); return; }
    user.getIdToken(true).addOnSuccessListener(token -> new Thread(() -> {
      JSONObject data = request("GET", "/api/admin", token.getToken(), null);
      runOnUiThread(() -> render(data));
    }).start()).addOnFailureListener(e -> summary.setText("Falha ao validar a conta. Entre novamente."));
  }

  private void render(JSONObject data) {
    content.removeAllViews();
    if (data == null) { summary.setText("Não foi possível carregar. Confira se o servidor já foi publicado."); return; }
    if (data.has("error")) { String code=data.optString("error","erro_desconhecido"); int status=data.optInt("_httpStatus",0); String detail="Falha na Central ADM: "+code+(status>0?" (HTTP "+status+")":"")+"."; if ("admin_only".equals(code)) detail="A conta conectada não está autorizada como administradora ou o e-mail não está verificado (HTTP "+status+")."; else if ("unauthorized".equals(code) || "invalid_firebase_token".equals(code)) detail="O servidor não aceitou o token do Firebase. Saia da conta Google no app e entre novamente. (HTTP "+status+")"; else if ("admin_request_failed".equals(code)) detail="O servidor recebeu a solicitação, mas falhou ao consultar os dados. Confira os logs da Vercel. (HTTP "+status+")"; summary.setText(detail); return; }
    JSONObject totals = data.optJSONObject("totals");
    summary.setText("Clientes: " + (totals == null ? 0 : totals.optInt("users")) +
      "   •   Premium: " + (totals == null ? 0 : totals.optInt("premium")) +
      "   •   Feedbacks pendentes: " + (totals == null ? 0 : totals.optInt("feedbackOpen")));

    addHeading("ADICIONAR / LIBERAR PREMIUM");
    TextView instruction = text("Informe o e-mail de uma conta já cadastrada no aplicativo.",13,gray);
    instruction.setPadding(0,0,0,dp(8)); content.addView(instruction);
    premiumEmail = new EditText(this);
    premiumEmail.setSingleLine(true);
    premiumEmail.setHint("E-mail do cliente");
    premiumEmail.setHintTextColor(gray);
    premiumEmail.setTextColor(white);
    premiumEmail.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
    premiumEmail.setPadding(dp(12),dp(10),dp(12),dp(10));
    premiumEmail.setBackgroundTintList(android.content.res.ColorStateList.valueOf(gray));
    content.addView(premiumEmail);
    Button grantByEmail = button("LIBERAR PREMIUM PARA ESTE E-MAIL",green); content.addView(grantByEmail);
    grantByEmail.setOnClickListener(v -> {
      String target = premiumEmail.getText().toString().trim();
      if (target.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches()) {
        Toast.makeText(this,"Informe um e-mail válido.",Toast.LENGTH_LONG).show(); return;
      }
      new AlertDialog.Builder(this).setTitle("Liberar Premium")
        .setMessage("Deseja liberar o Premium para "+target+"? A conta precisa já existir no aplicativo.")
        .setNegativeButton("Cancelar",null)
        .setPositiveButton("Liberar", (dialog,which) -> managePremium("",target,true)).show();
    });

    addHeading("CLIENTES");
    JSONArray users = data.optJSONArray("users");
    if (users == null || users.length() == 0) addNote("Nenhum cliente encontrado.");
    else for (int i=0;i<users.length();i++) {
      JSONObject u=users.optJSONObject(i); if(u==null) continue;
      final String uid=u.optString("uid","");
      final String email=u.optString("email","Sem e-mail");
      final boolean hasPremium=u.optBoolean("premium");
      String name=u.optString("displayName","");
      String line=(name.isEmpty()?email:name+"\n"+email)+"\n"+(hasPremium?"⭐ Premium ativo":"Plano gratuito")+(u.optBoolean("disabled")?" • conta desativada":"");
      LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(10),dp(12),dp(10)); box.setBackgroundColor(card);
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(3),0,dp(3)); content.addView(box,p);
      box.addView(text(line,14,white));
      Button premiumButton=button(hasPremium?"REVOGAR PREMIUM":"LIBERAR PREMIUM",hasPremium?red:green);
      box.addView(premiumButton);
      premiumButton.setOnClickListener(v -> new AlertDialog.Builder(this)
        .setTitle(hasPremium?"Revogar Premium":"Liberar Premium")
        .setMessage((hasPremium?"Deseja remover o acesso Premium de ":"Deseja liberar o Premium para ")+email+"?")
        .setNegativeButton("Cancelar",null)
        .setPositiveButton("Confirmar",(dialog,which)->managePremium(uid,"",!hasPremium)).show());
    }

    addHeading("FEEDBACKS");
    JSONArray feedback = data.optJSONArray("feedback");
    if (feedback == null || feedback.length()==0) addNote("Ainda não há feedbacks recebidos.");
    else for (int i=0;i<feedback.length();i++) {
      JSONObject f=feedback.optJSONObject(i); if(f==null) continue;
      final String id=f.optString("id"); final String status=f.optString("status","novo");
      LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),dp(10),dp(12),dp(10)); box.setBackgroundColor(card);
      LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(4),0,dp(4)); content.addView(box,p);
      TextView meta=text(f.optString("category","Geral")+" • "+f.optString("email","") +"\nStatus: "+status,12,gray); box.addView(meta);
      TextView msg=text(f.optString("message",""),14,white); msg.setPadding(0,dp(7),0,dp(8)); box.addView(msg);
      if(!"resolvido".equals(status)){
        Button done=button("MARCAR COMO RESOLVIDO",green); box.addView(done);
        done.setOnClickListener(v -> updateStatus(id, "resolvido"));
      }
    }
  }

  private void managePremium(String uid, String email, boolean grant) {
    FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser(); if(user==null) return;
    user.getIdToken(true).addOnSuccessListener(token -> new Thread(() -> {
      JSONObject body=new JSONObject();
      try {
        body.put("action",grant?"grant_premium":"revoke_premium");
        if(uid!=null && !uid.isEmpty()) body.put("uid",uid);
        if(email!=null && !email.isEmpty()) body.put("email",email);
      } catch(Exception ignored){}
      JSONObject result=request("POST","/api/admin",token.getToken(),body);
      runOnUiThread(() -> {
        if(result!=null && result.optBoolean("ok")) {
          Toast.makeText(this,grant?"Premium liberado com sucesso.":"Premium revogado.",Toast.LENGTH_LONG).show();
          load();
        } else {
          String error=result==null?"erro_de_rede":result.optString("error","falha");
          String message="Não foi possível alterar o Premium: "+error;
          if("user_not_found".equals(error)) message="Não existe uma conta cadastrada com esse e-mail.";
          Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        }
      });
    }).start()).addOnFailureListener(e -> Toast.makeText(this,"Não foi possível validar a conta administrativa.",Toast.LENGTH_LONG).show());
  }

  private void updateStatus(String id, String status) {
    FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser(); if(user==null) return;
    user.getIdToken(true).addOnSuccessListener(token -> new Thread(() -> {
      JSONObject body=new JSONObject(); try { body.put("feedbackId",id); body.put("status",status); } catch(Exception ignored){}
      JSONObject result=request("POST","/api/admin",token.getToken(),body);
      runOnUiThread(() -> { if(result!=null && result.optBoolean("ok")) load(); else Toast.makeText(this,"Não foi possível atualizar o feedback.",Toast.LENGTH_LONG).show(); });
    }).start());
  }

  private JSONObject request(String method,String path,String token,JSONObject body) {
    HttpURLConnection c=null;
    try {
      c=(HttpURLConnection)new URL(SERVER+path).openConnection(); c.setRequestMethod(method); c.setConnectTimeout(12000); c.setReadTimeout(12000);
      c.setRequestProperty("Authorization","Bearer "+token); c.setRequestProperty("Accept","application/json");
      if(body!=null){ c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json"); try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));} }
      int status=c.getResponseCode(); InputStream in=(status<400)?c.getInputStream():c.getErrorStream(); if(in==null){ JSONObject empty=new JSONObject(); empty.put("error","empty_server_response"); empty.put("_httpStatus",status); return empty; }
      ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[4096]; int n; while((n=in.read(b))!=-1)out.write(b,0,n);
      JSONObject result=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)); result.put("_httpStatus",status); return result;
    } catch(Exception e){ JSONObject failure=new JSONObject(); try { failure.put("error","network_error"); failure.put("detail",e.getClass().getSimpleName()); } catch(Exception ignored){} return failure; } finally {if(c!=null)c.disconnect();}
  }
  private void addHeading(String s){TextView t=text(s,16,red);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(18),0,dp(8));content.addView(t);}
  private void addNote(String s){TextView t=text(s,14,white);t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setBackgroundColor(card);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(3),0,dp(3));content.addView(t,p);}
  private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
  private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(white);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(5),0,dp(5));b.setLayoutParams(p);return b;}
  private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
}
