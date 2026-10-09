package com.example.philipsremote;

import android.app.Activity;
import android.os.Bundle;
import android.hardware.ConsumerIrManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Build;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import static com.example.philipsremote.RemoteKeys.*;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private IrPerfilTeste irPerfilTeste;
    private boolean lgMode = false;
    private boolean showingSelector = true;
    private boolean fanMode = false;
    private SharedPreferences prefs;
    private ControleStorage controleStorage;
    private ControleStorage.Controle controleAtivo;
    private MonetizationManager monetizacao;
    private Updater updater;
    private static final String PRIVACY_URL="https://github.com/tryonBrasil/PhilipsRemoteSemAds/blob/main/PRIVACY.md";
    private int aprenderFuncaoPos = 0;
    private static final int REQ_EXPORT_BACKUP = 4101;
    private static final int REQ_IMPORT_BACKUP = 4102;


    private static final int BG = Color.rgb(12,12,12);
    private static final int KEY = Color.rgb(48,48,48);
    private static final int KEY_DARK = Color.rgb(34,34,34);
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(175,175,175);
private static final int ACCENT = Color.rgb(210,30,38);
private static final int CARD = Color.rgb(24,24,28);
private static final int CARD_2 = Color.rgb(31,31,36);
    private static final int BORDER = Color.rgb(52,52,58);
    private static final int SUCCESS = Color.rgb(82,170,102);



    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        ir=(ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        irPerfilTeste=new IrPerfilTeste(this);
        prefs=getSharedPreferences("remote_prefs",MODE_PRIVATE);
        controleStorage=new ControleStorage(this);
        long activeId=prefs.getLong("active_control_id",-1L);
        if(activeId>0){
            for(ControleStorage.Controle item:controleStorage.listar()){
                if(item.id==activeId){ controleAtivo=item; break; }
            }
        }
        lgMode=prefs.getBoolean("lg_mode",false);
        if(controleAtivo!=null){
            lgMode="LG".equalsIgnoreCase(controleAtivo.marca);
            prefs.edit().putBoolean("lg_mode",lgMode).apply();
        }
        updater=UpdaterFactory.criar(this);
        monetizacao=new MonetizationManager(this,()->showSelector());
        registrarVoltar();
        if(!prefs.getBoolean("initial_screen_seen",false)) showInitialScreen();
        else showSelector();
    }

    /** Executa na thread de UI só se a tela ainda existe (evita crash ao abrir diálogo depois do onDestroy). */
    private void ui(Runnable r){
        runOnUiThread(()->{ if(!isFinishing()&&!isDestroyed()) r.run(); });
    }

    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

    /** Exibe a tela; no Android 15+ (edge-to-edge forçado com targetSdk 35) respeita as barras do sistema. */
    private void mostrar(View v){
        if(Build.VERSION.SDK_INT>=35){
            v.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                view.setPadding(b.left,b.top,b.right,b.bottom);
                return WindowInsets.CONSUMED;
            });
            v.requestApplyInsets();
        }
        setContentView(v);
    }

    private ScrollView wrapScroll(View v){ ScrollView sv=new ScrollView(this); sv.addView(v); return sv; }

    /** Permite que uma lista com altura fixa role dentro de um diálogo que também rola. */
    private void permitirRolagemInterna(ScrollView lista){
        lista.setOnTouchListener((view,ev)->{ view.getParent().requestDisallowInterceptTouchEvent(true); return false; });
    }

    private Button botaoAcao(String text,int color,int sp){
        Button b=new Button(this); b.setText(text); b.setTextColor(WHITE); b.setTextSize(sp); b.setAllCaps(false);
        b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0);
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(14)); g.setStroke(dp(1),BORDER);
        b.setBackground(g); actionFeedback(b); return b;
    }
    private LinearLayout.LayoutParams lpPeso(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1); p.setMargins(dp(3),dp(3),dp(3),dp(3)); return p; }
    private LinearLayout.LayoutParams lpFixa(int wDp){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(wDp),dp(44)); p.setMargins(dp(3),dp(3),dp(3),dp(3)); return p; }

    /** Releitura do controle ativo no banco (os objetos de listar() são cópias; compare sempre por id). */
    private void recarregarAtivo(){
        long id=prefs.getLong("active_control_id",-1L);
        controleAtivo=id>0?controleStorage.buscar(id):null;
    }

    private interface OnPick { void on(int i); }

    /** Lista de opções em botões. (AlertDialog.setMessage + setItems não exibe a lista no AOSP.) */
    // Sobrecarga para telas que não precisam de uma ação específica de VOLTAR.
    private void escolher(String titulo,String mensagem,String[] itens,OnPick ok){
        escolher(titulo,mensagem,itens,ok,null);
    }

    private void escolher(String titulo,String mensagem,String[] itens,OnPick ok,Runnable voltar){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(6),dp(18),dp(6));
        if(mensagem!=null && !mensagem.isEmpty()){
            TextView m=label(mensagem,13); m.setTextColor(GRAY); m.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); m.setPadding(0,0,0,dp(8));
            box.addView(m,new LinearLayout.LayoutParams(-1,-2));
        }
        final android.app.AlertDialog[] dlg=new android.app.AlertDialog[1];
        for(int i=0;i<itens.length;i++){
            final int idx=i;
            Button b=new Button(this); b.setText(itens[i]); b.setTextColor(WHITE); b.setTextSize(15); b.setAllCaps(false);
            b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); b.setPadding(dp(14),0,dp(10),0);
            GradientDrawable g=new GradientDrawable(); g.setColor(CARD_2); g.setCornerRadius(dp(12)); g.setStroke(dp(1),BORDER); b.setBackground(g);
            b.setOnClickListener(v->{ if(dlg[0]!=null) dlg[0].dismiss(); ok.on(idx); });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(46)); lp.setMargins(0,dp(3),0,dp(3)); box.addView(b,lp);
        }
        android.app.AlertDialog.Builder bd=new android.app.AlertDialog.Builder(this).setTitle(titulo).setView(wrapScroll(box));
        if(voltar!=null) bd.setNegativeButton("VOLTAR",(d,w)->voltar.run()); else bd.setNegativeButton("CANCELAR",null);
        dlg[0]=bd.create(); dlg[0].show();
    }

    private void actionFeedback(View v){
        v.setHapticFeedbackEnabled(true);
        v.setOnTouchListener((view,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            return false;
        });
    }

    private Button key(String text,int cmd,int h){ return key(text,cmd,h,KEY,16); }

    private Button key(String text,int cmd,int h,int color,int size){
        Button b=new Button(this);
        b.setText(text); b.setTextColor(WHITE); b.setTextSize(size); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setAllCaps(false); b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0);
        b.setMinHeight(0); b.setMinWidth(0); b.setIncludeFontPadding(false);
        GradientDrawable g=new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(16));
        g.setStroke(dp(1),BORDER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.rgb(85,85,90)),g,null));
        b.setHapticFeedbackEnabled(true);
        b.setOnClickListener(v->{ v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); send(cmd); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(h),1);
        p.setMargins(dp(3),dp(3),dp(3),dp(3)); b.setLayoutParams(p);
        return b;
    }

    private TextView label(String s,int sp){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(WHITE);
        t.setTextSize(sp); t.setGravity(Gravity.CENTER); t.setIncludeFontPadding(false);
        return t;
    }

    private LinearLayout row(){
        LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER); return r;
    }

    private void add(LinearLayout r,Button b){ r.addView(b); }

    private void section(LinearLayout root,String title){
        TextView t=label(title,13); t.setTextColor(GRAY);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setLetterSpacing(.05f);
        t.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(34));
        p.setMargins(dp(4),dp(8),dp(4),0); root.addView(t,p);
    }

    private TextView badge(String text, int color){
        TextView b=label(text,11);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(WHITE);
        b.setPadding(dp(10),0,dp(10),0);
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        return b;
    }

    private void showInitialScreen() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24),dp(56),dp(24),dp(30));

        TextView logo=label("IR",50);
        logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        GradientDrawable logoBg=new GradientDrawable();
        logoBg.setColor(ACCENT);
        logoBg.setCornerRadius(dp(28));
        logo.setBackground(logoBg);
        root.addView(logo,new LinearLayout.LayoutParams(dp(104),dp(104)));

        TextView brand=label("IR Remote BR",16);
        brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        brand.setLetterSpacing(.1f);
        brand.setTextColor(ACCENT);
        LinearLayout.LayoutParams brandP=new LinearLayout.LayoutParams(-1,-2);
        brandP.setMargins(0,dp(22),0,0);
        root.addView(brand,brandP);

        TextView title=label("Controle seus aparelhos de um jeito simples",26);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(-1,-2);
        titleP.setMargins(dp(8),dp(10),dp(8),0);
        root.addView(title,titleP);

        TextView sub=label("Comece agora. Não é preciso criar conta para usar os recursos gratuitos.",14);
        sub.setTextColor(GRAY);
        LinearLayout.LayoutParams subP=new LinearLayout.LayoutParams(-1,-2);
        subP.setMargins(dp(8),dp(8),dp(8),dp(28));
        root.addView(sub,subP);

        root.addView(botaoLargo(0,"Começar",58,ACCENT,v->{
            prefs.edit().putBoolean("initial_screen_seen",true).apply();
            showSelector();
        }));
        root.addView(botaoLargo(R.drawable.ic_settings,"Conta e privacidade",52,KEY_DARK,v->showPremiumAccountScreen()));

        TextView note=label("O Premium (sem anúncios e controles ilimitados) é vinculado à sua conta do Google Play.",12);
        note.setTextColor(GRAY);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.setMargins(dp(8),dp(16),dp(8),0);
        root.addView(note,np);

        TextView version=label("",11);
        version.setTextColor(GRAY);
        try{
            version.setText("Versão "+getPackageManager().getPackageInfo(getPackageName(),0).versionName);
        }catch(Exception ignored){}
        LinearLayout.LayoutParams vlp=new LinearLayout.LayoutParams(-1,-2); vlp.setMargins(0,dp(14),0,0);
        root.addView(version,vlp);

        mostrar(telaRolavel(root));
    }

    private void showPremiumAccountScreen(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(20),dp(18),dp(30));

        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconBtn(R.drawable.ic_back,46,24,CARD_2,true,"Voltar",v->{ if(prefs.getBoolean("initial_screen_seen",false)) showSelector(); else showInitialScreen(); }),new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1); tp.setMargins(dp(12),0,0,0);
        top.addView(textoEsq("Conta e Premium",22,WHITE,true),tp);
        root.addView(top);

        TextView info=subtitulo("A compra do Premium é associada à conta do Google Play usada na compra. Você não precisa criar uma senha separada para usar o aplicativo.");
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);
        ip.setMargins(0,dp(14),0,dp(14));
        root.addView(info,ip);

        boolean ativo=monetizacao.isPremium();
        TextView status=label(ativo ? "✓  Premium ativo" : "○  Premium não ativo",16);
        status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        status.setTextColor(ativo?SUCCESS:WHITE);
        GradientDrawable statusBg=new GradientDrawable();
        statusBg.setColor(ativo?Color.rgb(38,70,48):CARD);
        statusBg.setCornerRadius(dp(16));
        statusBg.setStroke(dp(1),BORDER);
        status.setBackground(statusBg);
        status.setPadding(dp(12),dp(16),dp(12),dp(16));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout.LayoutParams espaco=new LinearLayout.LayoutParams(1,dp(8));
        root.addView(new Space(this),espaco);

        root.addView(botaoLargo(R.drawable.ic_auto,"Restaurar ou verificar Premium",52,Color.rgb(55,55,62),v->{
            monetizacao.restaurarCompra();
            v.postDelayed(()->{ if(!isFinishing()&&!isDestroyed()) showPremiumAccountScreen(); },1200L);
        }));
        root.addView(botaoLargo(R.drawable.ic_star,ativo?"Premium ativo":"Conhecer o Premium",52,Color.rgb(70,55,20),v->monetizacao.showPremiumDialog()));
        root.addView(botaoLargo(R.drawable.ic_info,"Política de privacidade",52,Color.rgb(55,55,62),v->{
            try{ startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(PRIVACY_URL))); }
            catch(Exception e){ Toast.makeText(this,"Não foi possível abrir o link.",Toast.LENGTH_SHORT).show(); }
        }));
        if(monetizacao.precisaOpcoesPrivacidade()){
            root.addView(botaoLargo(R.drawable.ic_settings,"Opções de privacidade dos anúncios",52,Color.rgb(55,55,62),v->monetizacao.mostrarOpcoesPrivacidade()));
        }

        mostrar(telaRolavel(root));
    }

    private void showSelector(){
        showingSelector=true; fanMode=false;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(22),dp(16),dp(28));

        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL);
        TextView brand=textoEsq("IR Remote BR",13,ACCENT,true); brand.setLetterSpacing(.1f);
        head.addView(brand,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2); tlp.setMargins(0,dp(2),0,0);
        head.addView(textoEsq("Escolha seu controle",26,WHITE,true),tlp);
        top.addView(head,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(iconBtn(R.drawable.ic_settings,48,24,CARD_2,true,"Conta e privacidade",v->showPremiumAccountScreen()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        root.addView(top);

        TextView sub=subtitulo("Controle TV, ar-condicionado e ventilador por infravermelho.");
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2); slp.setMargins(0,dp(6),0,dp(10));
        root.addView(sub,slp);

        List<ControleStorage.Controle> salvosHome=controleStorage.listar();
        if(controleAtivo!=null){
            root.addView(cartaoLinha(R.drawable.ic_check,"Controle ativo",controleAtivo.nome,Color.rgb(38,70,48),v->{ showingSelector=false; build(); }));
        } else {
            section(root,"TVs prontas");
            LinearLayout cards=row();
            cards.addView(tvCard("Philips","50PUG6513/7",!lgMode,v->{lgMode=false;}));
            cards.addView(tvCard("LG","32LB620B",lgMode,v->{lgMode=true;}));
            root.addView(cards);
            root.addView(botaoLargo(0,"Continuar",56,ACCENT,v->{prefs.edit().putBoolean("lg_mode",lgMode).apply();showingSelector=false;build();}));
        }

        root.addView(cartaoLinha(R.drawable.ic_star,"Meus controles",
            salvosHome.isEmpty()?"Nenhum controle salvo ainda":salvosHome.size()+(salvosHome.size()==1?" controle salvo":" controles salvos"),
            salvosHome.isEmpty()?CARD:Color.rgb(34,52,40),v->showMeusControles()));

        root.addView(cartaoLinha(R.drawable.ic_search,"Controle universal",
            "TV, ar-condicionado e ventilador: pesquise a marca, teste códigos e salve o que funcionar.",CARD,v->{
            v.post(()->{
                try{
                    Toast.makeText(this,"Abrindo Controle Universal...",Toast.LENGTH_SHORT).show();
                    showingSelector=false;
                    // Não depende do controle ativo: o Universal deve sempre abrir seu próprio seletor.
                    showUniversalScanner("Universal","Universal",null);
                }catch(Throwable ex){
                    showingSelector=true;
                    String msg=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
                    Toast.makeText(this,"Erro no Controle Universal: "+msg,Toast.LENGTH_LONG).show();
                    android.util.Log.e("IR_REMOTE","Falha ao abrir Controle Universal",ex);
                }
            });
        }));

        LinearLayout chips=new LinearLayout(this); chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(R.drawable.ic_star,monetizacao.isPremium()?"Premium ativo":"Premium",v->{
            if(monetizacao.isPremium()) Toast.makeText(this,"⭐ Premium já está ativo neste aparelho.",Toast.LENGTH_SHORT).show();
            else monetizacao.showPremiumDialog();
        }));
        if(BuildConfig.ATUALIZACAO_POR_APK) chips.addView(chip(R.drawable.ic_auto,"Atualizar",v->updater.verificarManualmente()));
        LinearLayout.LayoutParams chp=new LinearLayout.LayoutParams(-1,-2); chp.setMargins(0,dp(10),0,0);
        root.addView(chips,chp);

        monetizacao.addBanner(root);
        mostrar(telaRolavel(root));
    }
    /** Banco online: consulta modelos do Flipper-IRDB sob demanda e importa somente os sinais escolhidos. */
    private void showBancoOnline(String categoriaInicial, String marcaInicial){
        final LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(8),dp(18),dp(8));

        TextView info=label("Escolha a categoria e a marca. O app baixa apenas o modelo selecionado; a base não fica embutida no APK.",13);
        info.setTextColor(GRAY);
        box.addView(info,new LinearLayout.LayoutParams(-1,dp(62)));

        final String[] cats={"FAN","AC"};
        final String[] catLabels={"🌀 VENTILADORES","❄️ AR-CONDICIONADO"};
        final String[] cat={"FAN"};
        Button categoria=new Button(this);
        categoria.setText(catLabels[0]); categoria.setAllCaps(false); categoria.setTextColor(WHITE);
        box.addView(categoria,new LinearLayout.LayoutParams(-1,dp(48)));

        EditText marca=new EditText(this);
        marca.setHint("Marca (ex.: Arno, Mondial, Midea)");
        marca.setSingleLine(true); marca.setTextColor(WHITE); marca.setHintTextColor(GRAY);
        box.addView(marca,new LinearLayout.LayoutParams(-1,dp(52)));

        Button buscar=new Button(this);
        buscar.setText("🔎  BUSCAR MODELOS");
        buscar.setAllCaps(false); buscar.setTextColor(WHITE);
        box.addView(buscar,new LinearLayout.LayoutParams(-1,dp(50)));

        TextView status=label("Pronto para pesquisar.",12); status.setTextColor(GRAY);
        box.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));

        final String[] selectedCat={"FAN"};
        categoria.setOnClickListener(v->{
            selectedCat[0]=selectedCat[0].equals("FAN")?"AC":"FAN";
            categoria.setText(selectedCat[0].equals("FAN")?catLabels[0]:catLabels[1]);
        });

        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
            .setTitle("BANCO ONLINE DE CONTROLES")
            .setView(wrapScroll(box))
            .setNegativeButton("FECHAR",null).create();

        buscar.setOnClickListener(v->{
            String b=marca.getText().toString().trim();
            if(b.isEmpty()){ marca.setError("Digite uma marca"); return; }
            status.setText("⏳ Buscando modelos de "+b+"...");
            buscar.setEnabled(false);
            new Thread(()->{
                try{
            if("AC".equals(selectedCat[0])){
                        java.util.List<SmartIrDatabase.Model> models=SmartIrDatabase.listarModelos(b);
                        ui(()->{
                            buscar.setEnabled(true);
                            if(models.isEmpty()){ status.setText("Nenhum modelo encontrado. Tente outra marca."); return; }
                            String[] nomes=new String[models.size()];
                            for(int i=0;i<models.size();i++) nomes[i]=models.get(i).manufacturer+" • "+models.get(i).model;
                            escolher("AR-CONDICIONADO • MODELOS ("+models.size()+")","Escolha o modelo exato sempre que possível.",nomes,w->abrirSmartIrClimate(models.get(w)));
                            status.setText("✓ "+models.size()+" modelo(s) encontrado(s).");
                        });
                    }else{
                        java.util.List<IrRemoteDatabase.RemoteFile> files=IrRemoteDatabase.listar(selectedCat[0],b);
                        ui(()->{
                        buscar.setEnabled(true);
                        if(files.isEmpty()){ status.setText("Nenhum modelo encontrado. Tente outra marca."); return; }
                        final String[] nomes=new String[files.size()];
                        for(int i=0;i<files.size();i++) nomes[i]=files.get(i).model;
                        escolher("MODELOS ENCONTRADOS ("+files.size()+")","Toque em um modelo para carregar os códigos.",nomes,w->abrirRemoteOnline(files.get(w)));
                        status.setText("✓ "+files.size()+" modelo(s) encontrado(s).");
                        });
                    }
                }catch(Exception e){
                    ui(()->{ buscar.setEnabled(true); status.setText("✕ Não foi possível acessar o banco online."); Toast.makeText(this,e.getMessage()==null?"Erro de conexão":e.getMessage(),Toast.LENGTH_LONG).show(); });
                }
            }).start();
        });

        d.show();
    }

    private void abrirSmartIrMarca(String marca){
        Toast.makeText(this,"⏳ Buscando modelos de "+marca+"...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                java.util.List<SmartIrDatabase.Model> models=SmartIrDatabase.listarModelos(marca);
                ui(()->{
                    if(models.isEmpty()){
                        Toast.makeText(this,"Nenhum modelo SmartIR encontrado para "+marca+". Use o banco online ou outro perfil.",Toast.LENGTH_LONG).show();
                        return;
                    }
                    String[] nomes=new String[models.size()];
                    for(int i=0;i<models.size();i++){
                        SmartIrDatabase.Model m=models.get(i);
                        nomes[i]=m.manufacturer+" • "+m.model;
                    }
                    escolher("AR-CONDICIONADO • "+marca+" ("+models.size()+")",
                            "Escolha o modelo do aparelho. O código completo será carregado do SmartIR.",
                            nomes,idx->abrirSmartIrClimate(models.get(idx)),null);
                });
            }catch(Exception e){
                ui(()->Toast.makeText(this,
                        e.getMessage()==null?"Não foi possível acessar o banco SmartIR.":e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void abrirSmartIrClimate(SmartIrDatabase.Model model){
        if(model==null){
            Toast.makeText(this,"Modelo de ar-condicionado inválido.",Toast.LENGTH_LONG).show();
            return;
        }
        final android.app.AlertDialog carregando=new android.app.AlertDialog.Builder(this)
            .setTitle("❄️ AR-CONDICIONADO")
            .setMessage("Carregando o controle de "+model.manufacturer+" • "+model.model+"...\\n\\nAguarde enquanto os códigos do modelo são preparados para teste.")
            .setNegativeButton("CANCELAR",null)
            .create();
        carregando.setOnShowListener(v->{
            Button cancelar=carregando.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            cancelar.setOnClickListener(x->carregando.dismiss());
        });
        carregando.show();
        new Thread(()->{ try{
            SmartIrDatabase.Climate climate=SmartIrDatabase.carregar(model);
            ui(()->{
                carregando.dismiss();
                showAcRemote(climate,model,null);
            });
        }catch(Exception e){
            ui(()->{
                carregando.dismiss();
                new android.app.AlertDialog.Builder(this)
                    .setTitle("NÃO FOI POSSÍVEL CARREGAR")
                    .setMessage("O modelo foi selecionado, mas os códigos não puderam ser carregados.\\n\\n"+(e.getMessage()==null?"Verifique a internet e tente novamente.":e.getMessage()))
                    .setNegativeButton("VOLTAR",null)
                    .setPositiveButton("TENTAR NOVAMENTE",(d,w)->abrirSmartIrClimate(model))
                    .show();
            });
        } }).start();
    }

    private void abrirSmartIrSalvo(ControleStorage.Controle controle){
        if(controle==null || controle.descricao==null || !controle.descricao.startsWith("SMARTIR|")){ build(); return; }
        String url=controle.descricao.substring("SMARTIR|".length());
        new Thread(()->{ try{
            SmartIrDatabase.Climate climate=SmartIrDatabase.carregarPorUrl(url);
            SmartIrDatabase.Model model=new SmartIrDatabase.Model(controle.marca,extrairCodigoSmartIr(url),controle.modelo);
            ui(()->showAcRemote(climate,model,controle));
        }catch(Exception e){ ui(()->new android.app.AlertDialog.Builder(this).setTitle("CONTROLE NÃO CARREGADO").setMessage("Não foi possível atualizar os códigos deste ar-condicionado. Verifique a internet.").setPositiveButton("TENTAR", (d,w)->abrirSmartIrSalvo(controle)).setNegativeButton("VOLTAR",null).show()); } }).start();
    }

    private String extrairCodigoSmartIr(String url){
        int a=url.lastIndexOf('/'), b=url.lastIndexOf('.');
        return a>=0&&b>a?url.substring(a+1,b):"";
    }

    private void showAcRemote(SmartIrDatabase.Climate climate, SmartIrDatabase.Model model, ControleStorage.Controle existing){
        showingSelector=false; fanMode=false;
        final String[] mode={climate.modes.isEmpty()?"cool":climate.modes.get(0)};
        final String[] fan={climate.fans.isEmpty()?"auto":climate.fans.get(0)};
        final String[] swing={climate.swings.isEmpty()?null:climate.swings.get(0)};
        final int[] temp={Math.max(climate.minTemp,Math.min(climate.maxTemp,24))};
        final boolean[] ligado={true};
        final ControleStorage.Controle[] saved={existing};
        // v1.6.4: restaura o último estado usado do ar-condicionado salvo.
        if(existing!=null){
            String base="ac_state_"+existing.id+"_";
            String sm=prefs.getString(base+"mode","");
            String sf=prefs.getString(base+"fan","");
            String ss=prefs.getString(base+"swing","");
            if(!sm.isEmpty() && climate.modes.contains(sm)) mode[0]=sm;
            if(!sf.isEmpty() && climate.fans.contains(sf)) fan[0]=sf;
            if(!ss.isEmpty() && climate.swings.contains(ss)) swing[0]=ss;
            int st=prefs.getInt(base+"temp",temp[0]);
            temp[0]=Math.max(climate.minTemp,Math.min(climate.maxTemp,st));
            ligado[0]=prefs.getBoolean(base+"on",true);
        }
        final java.util.List<View> modeButtons=new ArrayList<>();
        final java.util.List<View> fanButtons=new ArrayList<>();
        final java.util.List<View> swingButtons=new ArrayList<>();
        final int verdeLigado=Color.rgb(45,105,58), cinzaDesligado=Color.rgb(72,72,78);
        final int selecionado=Color.rgb(65,85,105);

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(12),dp(14),dp(26));

        // Cabeçalho: título, modelo e liga/desliga.
        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL);
        head.addView(textoEsq("Ar-condicionado",22,WHITE,true),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams sublp=new LinearLayout.LayoutParams(-1,-2); sublp.setMargins(0,dp(2),0,0);
        head.addView(textoEsq(model.manufacturer+" · "+model.model,12,GRAY,false),sublp);
        top.addView(head,new LinearLayout.LayoutParams(0,-2,1));
        final ImageButton powerBtn=iconBtn(R.drawable.ic_power,58,28,verdeLigado,true,"Ligar ou desligar",null);
        LinearLayout.LayoutParams pbp=new LinearLayout.LayoutParams(dp(58),dp(58)); pbp.setMargins(dp(10),0,0,0);
        top.addView(powerBtn,pbp);
        root.addView(top);

        final TextView state=label("",13);
        state.setTextColor(Color.rgb(105,190,125));
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable stateBg=new GradientDrawable(); stateBg.setColor(Color.rgb(22,42,28)); stateBg.setCornerRadius(dp(14)); stateBg.setStroke(dp(1),Color.rgb(65,110,75));
        state.setBackground(stateBg);
        LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2); stp.setMargins(0,dp(12),0,0);
        root.addView(state,stp);

        // Temperatura em destaque.
        LinearLayout tempCard=new LinearLayout(this); tempCard.setOrientation(LinearLayout.VERTICAL); tempCard.setGravity(Gravity.CENTER);
        tempCard.setPadding(dp(14),dp(14),dp(14),dp(14));
        GradientDrawable tempBg=new GradientDrawable(); tempBg.setColor(CARD); tempBg.setCornerRadius(dp(28)); tempBg.setStroke(dp(1),BORDER);
        tempCard.setBackground(tempBg);
        TextView tempCaption=label("Temperatura",12); tempCaption.setTextColor(GRAY);
        tempCard.addView(tempCaption,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout tr=new LinearLayout(this); tr.setOrientation(LinearLayout.HORIZONTAL); tr.setGravity(Gravity.CENTER_VERTICAL);
        final ImageButton menos=iconBtn(R.drawable.ic_minus,68,32,KEY,true,"Diminuir temperatura",null);
        final ImageButton mais=iconBtn(R.drawable.ic_plus,68,32,KEY,true,"Aumentar temperatura",null);
        final TextView tv=label(temp[0]+"°",58); tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        tr.addView(menos,new LinearLayout.LayoutParams(dp(68),dp(68)));
        tr.addView(tv,new LinearLayout.LayoutParams(0,-2,1));
        tr.addView(mais,new LinearLayout.LayoutParams(dp(68),dp(68)));
        LinearLayout.LayoutParams trp=new LinearLayout.LayoutParams(-1,-2); trp.setMargins(0,dp(8),0,dp(4));
        tempCard.addView(tr,trp);
        TextView limite=label(climate.minTemp+" °C a "+climate.maxTemp+" °C",11); limite.setTextColor(GRAY);
        tempCard.addView(limite,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams tcp=new LinearLayout.LayoutParams(-1,-2); tcp.setMargins(0,dp(10),0,dp(4)); root.addView(tempCard,tcp);

        Runnable refresh=()->{
            if(saved[0]!=null){
                String base="ac_state_"+saved[0].id+"_";
                prefs.edit().putString(base+"mode",mode[0]).putString(base+"fan",fan[0])
                    .putString(base+"swing",swing[0]==null?"":swing[0]).putInt(base+"temp",temp[0])
                    .putBoolean(base+"on",ligado[0]).apply();
            }
            tv.setText(temp[0]+"°");
            String st=(ligado[0]?"● Ligado":"○ Desligado")+" · "+modoTexto(mode[0])+" · "+temp[0]+" °C · "+fanTexto(fan[0]);
            if(swing[0]!=null) st+=" · Oscilação "+modoTexto(swing[0]);
            state.setText(st);
            state.setTextColor(ligado[0]?Color.rgb(105,190,125):GRAY);
            powerBtn.setBackground(ripple(ligado[0]?verdeLigado:cinzaDesligado,true,false));
            for(View b:modeButtons){ String v=String.valueOf(b.getTag()); pintar(b,v.equals(mode[0])?corModo(v):KEY); }
            for(View b:fanButtons){ String v=String.valueOf(b.getTag()); pintar(b,v.equals(fan[0])?selecionado:KEY); }
            for(View b:swingButtons){ String v=String.valueOf(b.getTag()); pintar(b,v.equals(swing[0])?selecionado:KEY); }
        };

        powerBtn.setOnClickListener(v->{
            haptic(v);
            if(ligado[0]){
                String cmd=climate.offCommand();
                if(cmd.isEmpty()){Toast.makeText(this,"Este modelo não possui código OFF.",Toast.LENGTH_SHORT).show();return;}
                ligado[0]=false;
                enviarBase64Smart(cmd,climate,state,"Desligado");
            } else {
                ligado[0]=true;
                enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);
            }
            refresh.run();
        });

        section(root,"Modo");
        LinearLayout mr=row();
        int mc=0;
        for(String m:climate.modes){
            final String value=m;
            LinearLayout mb=tile(modoIcone(m),modoTexto(m),66,KEY,v->{mode[0]=value;ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
            mb.setTag(value);
            modeButtons.add(mb);
            mr.addView(mb); mc++;
            if(mc%3==0 && mc<climate.modes.size()){root.addView(mr);mr=row();}
        }
        if(mc>0) root.addView(mr);

        section(root,"Velocidade do ventilador");
        LinearLayout fr=row();
        int fc=0;
        for(String f:climate.fans){
            final String value=f;
            LinearLayout fb=tile(0,fanTexto(f),48,KEY,v->{fan[0]=value;ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
            fb.setTag(value);
            fanButtons.add(fb);
            fr.addView(fb); fc++;
            if(fc%3==0 && fc<climate.fans.size()){root.addView(fr);fr=row();}
        }
        if(fc>0) root.addView(fr);

        if(!climate.swings.isEmpty()){
            section(root,"Oscilação");
            LinearLayout sr=row();
            int sc=0;
            for(String sw:climate.swings){
                final String value=sw;
                LinearLayout sb=tile(0,modoTexto(sw),48,KEY,v->{swing[0]=value;ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
                sb.setTag(value);
                swingButtons.add(sb);
                sr.addView(sb); sc++;
                if(sc%3==0 && sc<climate.swings.size()){root.addView(sr);sr=row();}
            }
            if(sc>0) root.addView(sr);
        }

        section(root,"Atalhos");
        LinearLayout quick=row();
        quick.addView(tile(0,"Conforto 24°",48,KEY_DARK,v->{temp[0]=Math.max(climate.minTemp,Math.min(climate.maxTemp,24));ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();}));
        quick.addView(tile(0,"Economia 26°",48,KEY_DARK,v->{temp[0]=Math.max(climate.minTemp,Math.min(climate.maxTemp,26));ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();}));
        quick.addView(tile(0,"Auto",48,KEY_DARK,v->{
            for(String m:climate.modes) if("auto".equalsIgnoreCase(m)||"heat_cool".equalsIgnoreCase(m)){mode[0]=m;break;}
            for(String f:climate.fans) if("auto".equalsIgnoreCase(f)){fan[0]=f;break;}
            ligado[0]=true; enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state); refresh.run();
        }));
        root.addView(quick);

        LinearLayout actions=row();
        actions.addView(tile(saved[0]==null?R.drawable.ic_star:R.drawable.ic_check,saved[0]==null?"Salvar controle":"Controle salvo",58,ACCENT,v->{
            if(saved[0]==null){
                String url="https://raw.githubusercontent.com/smartHomeHub/SmartIR/master/codes/climate/"+model.code+".json";
                long id=controleStorage.salvar(model.manufacturer+" "+model.model,"AR-CONDICIONADO",model.manufacturer,model.model,"AC SmartIR","SMARTIR|"+url,-1,38000);
                if(id>0){saved[0]=controleStorage.buscar(id);}
            }
            if(saved[0]!=null){
                int[] p=smartRaw(climate.command(mode[0],fan[0],swing[0],temp[0]),climate.encoding);
                if(p.length>0) controleStorage.salvarComandoRaw(saved[0],"ESTADO ATUAL",38000,p);
                String base="ac_state_"+saved[0].id+"_";
                prefs.edit().putString(base+"mode",mode[0]).putString(base+"fan",fan[0])
                    .putString(base+"swing",swing[0]==null?"":swing[0]).putInt(base+"temp",temp[0])
                    .putBoolean(base+"on",ligado[0]).apply();
                Toast.makeText(this,"✓ Controle de ar-condicionado salvo.",Toast.LENGTH_SHORT).show();
                build();
            }
        }));
        actions.addView(tile(R.drawable.ic_back,"Voltar",58,KEY_DARK,v->showSelector()));
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2); alp.setMargins(0,dp(14),0,0);
        root.addView(actions,alp);

        TextView foot=label("Banco SmartIR · "+climate.models.size()+" modelo(s) · "+climate.modes.size()+" modos · "+climate.fans.size()+" velocidades",11);
        foot.setTextColor(GRAY); foot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,-2); flp.setMargins(0,dp(14),0,0);
        root.addView(foot,flp);

        menos.setOnClickListener(v->{
            haptic(v);
            temp[0]=Math.max(climate.minTemp,temp[0]-climate.precision);
            ligado[0]=true; enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state); refresh.run();
        });
        mais.setOnClickListener(v->{
            haptic(v);
            temp[0]=Math.min(climate.maxTemp,temp[0]+climate.precision);
            ligado[0]=true; enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state); refresh.run();
        });
        refresh.run();
        mostrar(telaRolavel(root));
    }

    private String modoTexto(String value){
        if(value==null) return "";
        String v=value.trim().toLowerCase(java.util.Locale.ROOT);
        if(v.equals("cool")) return "Frio";
        if(v.equals("heat")) return "Quente";
        if(v.equals("auto") || v.equals("heat_cool")) return "Auto";
        if(v.equals("dry")) return "Seco";
        if(v.equals("fan_only") || v.equals("fan")) return "Ventilar";
        if(v.equals("off")) return "Desligado";
        String s=value.replace("_"," ").trim();
        return s.isEmpty()?s:s.substring(0,1).toUpperCase(java.util.Locale.ROOT)+s.substring(1);
    }

    private String fanTexto(String value){
        if(value==null) return "";
        String v=value.trim().toLowerCase(java.util.Locale.ROOT);
        if(v.equals("auto")) return "Auto";
        if(v.equals("low") || v.equals("low-low")) return "Baixa";
        if(v.equals("medium") || v.equals("med")) return "Média";
        if(v.equals("high")) return "Alta";
        if(v.equals("turbo") || v.equals("max")) return "Turbo";
        if(v.equals("off")) return "Off";
        String s=value.replace("_"," ").trim();
        return s.isEmpty()?s:s.substring(0,1).toUpperCase(java.util.Locale.ROOT)+s.substring(1);
    }

    private void enviarEstadoAc(SmartIrDatabase.Climate c,String mode,String fan,String swing,int temp,TextView status){
        String command=c.command(mode,fan,swing,temp);
        if(command.isEmpty()){status.setText("✕ Estado não disponível para este modelo.");status.setTextColor(ACCENT);return;}
        enviarBase64Smart(command,c,status,modoTexto(mode)+" • "+temp+" °C");
    }

    private void enviarBase64Smart(String value,SmartIrDatabase.Climate climate,TextView status,String texto){
        try{
            int[] p=SmartIrDatabase.decodeCommand(value,climate.encoding);
            if(p.length==0){status.setText("✕ Código indisponível");status.setTextColor(ACCENT);return;}
            boolean ok=irPerfilTeste.transmitirRaw(38000,p);
            status.setText(ok?"✓ "+texto:"✕ Emissor IR indisponível");
            status.setTextColor(ok?Color.rgb(105,190,125):ACCENT);
        }catch(Exception e){status.setText("✕ Código IR inválido");status.setTextColor(ACCENT);}
    }

    private int[] smartRaw(String value,String encoding){
        try{return SmartIrDatabase.decodeCommand(value,encoding);}catch(Exception e){return new int[0];}
    }

    private void abrirRemoteOnline(IrRemoteDatabase.RemoteFile remote){
        Toast.makeText(this,"⏳ Carregando "+remote.model+"...",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                java.util.List<IrRemoteDatabase.Signal> sinais=IrRemoteDatabase.baixarSinais(remote);
                ui(()->mostrarSinaisOnline(remote,sinais));
            }catch(Exception e){
                ui(()->Toast.makeText(this,e.getMessage()==null?"Falha ao carregar o controle.":e.getMessage(),Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void mostrarSinaisOnline(IrRemoteDatabase.RemoteFile remote, java.util.List<IrRemoteDatabase.Signal> sinais){
        if(sinais==null || sinais.isEmpty()){
            new android.app.AlertDialog.Builder(this).setTitle(remote.model).setMessage("Este arquivo não possui sinais transmitíveis compatíveis com o app.").setPositiveButton("OK",null).show();
            return;
        }
        final ControleStorage.Controle[] salvo={null};
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14),dp(6),dp(14),dp(6));
        TextView info=label("Teste cada função. Quando uma responder, toque em ADICIONAR. O controle fica salvo no aparelho e pode ser editado depois.",12);
        info.setTextColor(GRAY); box.addView(info,new LinearLayout.LayoutParams(-1,dp(60)));
        TextView count=label("0 funções salvas • "+sinais.size()+" encontradas",12); count.setTextColor(SUCCESS); box.addView(count,new LinearLayout.LayoutParams(-1,dp(34)));

        for(int i=0;i<sinais.size();i++){
            final IrRemoteDatabase.Signal s=sinais.get(i);
            LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView n=label(s.name,13); n.setTextColor(WHITE); row.addView(n,new LinearLayout.LayoutParams(0,dp(50),1));
            Button test=new Button(this); test.setText("TESTAR"); test.setTextSize(10); test.setAllCaps(false); test.setTextColor(WHITE);
            test.setOnClickListener(v->{
                boolean ok=irPerfilTeste.transmitirRaw(s.frequency,s.pattern);
                Toast.makeText(this,ok?"✓ IR enviado: "+s.name:"✕ Falha no emissor IR",Toast.LENGTH_SHORT).show();
            });
            row.addView(test,new LinearLayout.LayoutParams(dp(78),dp(44)));
            Button add=new Button(this); add.setText("ADICIONAR"); add.setTextSize(9); add.setAllCaps(false); add.setTextColor(WHITE);
            add.setOnClickListener(v->{
                if(salvo[0]==null){
                    if(!monetizacao.podeSalvarControle(controleStorage.listar().size())){
                        Toast.makeText(this,"Limite gratuito atingido (3 controles). Desbloqueie o Premium para salvar ilimitados.",Toast.LENGTH_LONG).show();
                        monetizacao.showPremiumDialog();
                        return;
                    }
                    long id=controleStorage.salvar(remote.model,remote.category,remote.brand,remote.model,"RAW","Importado do banco online", -1,s.frequency);
                    if(id<0){ Toast.makeText(this,"Não foi possível criar o controle.",Toast.LENGTH_SHORT).show(); return; }
                    salvo[0]=controleStorage.buscar(id);
                }
                controleStorage.salvarComandoRaw(salvo[0],s.name,s.frequency,s.pattern);
                salvo[0]=controleStorage.buscar(salvo[0].id);
                count.setText(controleStorage.quantidadeComandos(salvo[0])+" funções salvas • "+sinais.size()+" encontradas");
                add.setText("✓ SALVO"); add.setEnabled(false);
            });
            row.addView(add,new LinearLayout.LayoutParams(dp(86),dp(44)));
            box.addView(row);
        }
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this)
            .setTitle("IR • "+remote.brand+" • "+remote.model)
            .setView(wrapScroll(box))
            .setPositiveButton("ABRIR CONTROLE",null)
            .setNegativeButton("FECHAR",null).create();
        d.setOnShowListener(x->d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(salvo[0]!=null){
                controleAtivo=controleStorage.buscar(salvo[0].id);
                if(controleAtivo!=null){
                    lgMode="LG".equalsIgnoreCase(controleAtivo.marca);
                    prefs.edit().putLong("active_control_id",controleAtivo.id).putBoolean("lg_mode",lgMode).apply();
                }
                d.dismiss();
                build();
            } else {
                Toast.makeText(this,"Adicione pelo menos uma função primeiro.",Toast.LENGTH_SHORT).show();
            }
        }));
        d.show();
    }

    private LinearLayout tvCard(String brand,String model,boolean selected,View.OnClickListener click){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14),dp(12),dp(14),dp(12));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(selected?Color.rgb(42,42,48):Color.rgb(27,27,30)); bg.setCornerRadius(dp(18));
        bg.setStroke(dp(2),selected?ACCENT:Color.rgb(55,55,58));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.rgb(85,85,90)),bg,null));
        card.setClickable(true); card.setFocusable(true); card.setContentDescription(brand+" "+model+(selected?", selecionada":""));
        card.addView(textoEsq(brand,18,WHITE,true),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-1,-2); mlp.setMargins(0,dp(2),0,dp(8));
        card.addView(textoEsq(model,13,GRAY,false),mlp);
        LinearLayout st=new LinearLayout(this); st.setOrientation(LinearLayout.HORIZONTAL); st.setGravity(Gravity.CENTER_VERTICAL);
        if(selected){
            LinearLayout.LayoutParams ci=new LinearLayout.LayoutParams(dp(14),dp(14)); ci.setMargins(0,0,dp(6),0);
            st.addView(icone(R.drawable.ic_check,14,SUCCESS),ci);
        }
        st.addView(textoEsq(selected?"Selecionada":"Toque para escolher",12,selected?SUCCESS:GRAY,false),new LinearLayout.LayoutParams(0,-2,1));
        card.addView(st,new LinearLayout.LayoutParams(-1,-2));
        card.setOnClickListener(v->{ haptic(v); click.onClick(v); showSelector(); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1); p.setMargins(dp(3),dp(4),dp(3),dp(4));
        card.setLayoutParams(p);
        return card;
    }

    @Override protected void onResume(){
        super.onResume();
        recarregarAtivo();
        if(updater!=null){
            updater.aoRetornarDoSistema();
            updater.verificarAoAbrir();
        }
    }

    @Override protected void onDestroy(){
        if(updater!=null) updater.destroy();
        if(monetizacao!=null) monetizacao.destroy();
        super.onDestroy();
    }

    /** Android 13+ (obrigatório com targetSdk 36): voltar via OnBackInvokedCallback. */
    private void registrarVoltar(){
        if(Build.VERSION.SDK_INT>=33){
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                ()->{ if(!showingSelector) showSelector(); else finish(); });
        }
    }

    /** Android 12 e anteriores. */
    @Override public void onBackPressed(){
        if(!showingSelector){ showSelector(); } else { super.onBackPressed(); }
    }

    private boolean ehMarcaArCondicionado(String marca){
        if(marca==null) return false;
        String m=marca.trim().toLowerCase(java.util.Locale.ROOT);
        return m.equals("midea") || m.equals("comfee") || m.equals("kaysun") || m.equals("mrcool")
            || m.equals("pioneer") || m.equals("lennox") || m.equals("keystone") || m.equals("beko")
            || m.equals("airwell") || m.equals("bosch") || m.equals("fisher") || m.equals("carrier")
            || m.equals("springer") || m.equals("consul") || m.equals("elgin") || m.equals("electrolux")
            || m.equals("philco") || m.equals("agratto") || m.equals("eos") || m.equals("gree")
            || m.equals("daikin") || m.equals("fujitsu") || m.equals("mitsubishi") || m.equals("whirlpool");
    }

    private int perfilInicialPara(String marca,String modelo,String[] perfis){
        if(marca==null) marca="";
        String alvo="";
        if("LG".equalsIgnoreCase(marca)) alvo="LG / NEC";
        else if("Ventilador".equalsIgnoreCase(marca)) alvo="Ventilador Universal";
        else if("Samsung".equalsIgnoreCase(marca)) alvo="Samsung TV";
        else if("Sony".equalsIgnoreCase(marca)) alvo="Sony TV";
        else if("Philips".equalsIgnoreCase(marca)) alvo="Philips / RC6";
        else if("Panasonic".equalsIgnoreCase(marca)) alvo="Panasonic TV";
        else if("AOC".equalsIgnoreCase(marca)) alvo="AOC / NEC";
        else if("TCL".equalsIgnoreCase(marca)) alvo="TCL / NEC";
        else if("Philco".equalsIgnoreCase(marca)) alvo="Philco / NEC";
        else if("Semp".equalsIgnoreCase(marca)) alvo="Semp / NEC";
        for(int i=0;i<perfis.length;i++) if(perfis[i].equals(alvo)) return i;
        return 0;
    }


    private int frequenciaPerfil(String perfil){
        if(perfil==null) return 38000;
        if(perfil.contains("Sony")) return 40000;
        if(perfil.contains("Philips")) return 36000;
        if(perfil.equals("Panasonic TV")) return 37000;
        if(perfil.equals("Ventilador Universal")) return 38000;
        return 38000;
    }

    /** Teste fácil: reduz a configuração a aparelho → função → testar → funcionou. */
    private void showUniversalScanner(String marca,String modelo){ showUniversalScanner(marca,modelo,null); }

    private void showUniversalScanner(String marca,String modelo,String categoriaForcada){
        final String[] perfis=irPerfilTeste.perfis();
        final IrCatalog catalog=new IrCatalog(this);
        final java.util.List<IrCatalog.Device> catalogDevices=catalog.all();

        final String[] nomes;
        final String[] marcas;
        final String[] modelos;
        final String[] perfisMapa;
        final String[] tipos;

        if(catalogDevices.isEmpty()){
            nomes=new String[]{"Philips TV","LG TV","Samsung TV","Sony TV","Panasonic TV","AOC TV","TCL TV","Philco TV","Semp TV","Toshiba / JVC TV","Ar-condicionado Coolix","Ar-condicionado Midea","Ventilador Universal"};
            marcas=new String[]{"Philips","LG","Samsung","Sony","Panasonic","AOC","TCL","Philco","Semp","Toshiba","Coolix","Midea","Ventilador"};
            modelos=new String[]{"Smart TV / RC6","Smart TV / NEC","Smart TV / Samsung","TV / SIRC","TV / Kaseikyo","Smart TV / NEC","Smart TV / NEC","TV / NEC","TV / NEC","TV / NEC","Linha compatível","Linha compatível","Universal"};
            perfisMapa=new String[]{"Philips / RC6","LG / NEC","Samsung TV","Sony TV","Panasonic TV","AOC / NEC","TCL / NEC","Philco / NEC","Semp / NEC","Toshiba / JVC / NEC","AC Coolix","AC Midea","Ventilador Universal"};
            tipos=new String[]{"TV","TV","TV","TV","TV","TV","TV","TV","TV","TV","AC","AC","FAN"};
        }else{
            nomes=new String[catalogDevices.size()];
            marcas=new String[catalogDevices.size()];
            modelos=new String[catalogDevices.size()];
            perfisMapa=new String[catalogDevices.size()];
            tipos=new String[catalogDevices.size()];
            for(int i=0;i<catalogDevices.size();i++){
                IrCatalog.Device d=catalogDevices.get(i);
                nomes[i]=d.label();
                marcas[i]=d.brand;
                modelos[i]=d.model;
                perfisMapa[i]=d.profile;
                tipos[i]=d.type;
            }
        }
        final String[] marcaSelecionada={marca};
        final String[] perfilSelecionado={perfis[perfilInicialPara(marca,modelo,perfis)]};
        final String[] modeloSelecionado={modelo};
        final String[] nomeSelecionado={marca+" "+modelo};

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(6),dp(18),dp(6));

        TextView etapa=label("PASSO 3 DE 3  •  ESCOLHA O DISPOSITIVO",12);
        etapa.setTextColor(ACCENT); etapa.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(etapa,new LinearLayout.LayoutParams(-1,dp(30)));

        TextView aparelho=label(nomeSelecionado[0],18);
        aparelho.setTextColor(WHITE); aparelho.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        aparelho.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(aparelho,new LinearLayout.LayoutParams(-1,dp(36)));

        TextView subtitulo=label("Escolha o tipo de aparelho e a marca. Você também pode pesquisar pelo nome da marca.",12);
        subtitulo.setTextColor(GRAY); subtitulo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(subtitulo,new LinearLayout.LayoutParams(-1,dp(42)));

        EditText buscaMarca=new EditText(this);
        buscaMarca.setHint("🔎  Pesquisar marca...");
        buscaMarca.setHintTextColor(Color.rgb(120,120,125)); buscaMarca.setTextColor(WHITE);
        buscaMarca.setSingleLine(true); buscaMarca.setTextSize(14);
        buscaMarca.setPadding(dp(14),0,dp(14),0);
        GradientDrawable buscaBg=new GradientDrawable(); buscaBg.setColor(CARD_2); buscaBg.setCornerRadius(dp(12)); buscaBg.setStroke(dp(1),BORDER);
        buscaMarca.setBackground(buscaBg);
        box.addView(buscaMarca,new LinearLayout.LayoutParams(-1,dp(48)));

        // Começa na categoria do aparelho de entrada; TV, AR e ventilador ficam separados.
        final String categoriaInicial = categoriaForcada != null ? categoriaForcada :
                ("Ventilador".equalsIgnoreCase(marca) ? "FAN" : "");
        final String[] categoriaFiltro={categoriaInicial};
        LinearLayout filtros=row();
        Button filtroTodos=botaoAcao("TODOS",KEY_DARK,11);
        Button filtroTv=botaoAcao("📺 TV",KEY_DARK,11);
        Button filtroAc=botaoAcao("❄️ AR",KEY_DARK,11);
        Button filtroFan=botaoAcao("🌀 VENT.",KEY_DARK,11);
        filtros.addView(filtroTodos,lpPeso()); filtros.addView(filtroTv,lpPeso());
        filtros.addView(filtroAc,lpPeso()); filtros.addView(filtroFan,lpPeso());
        box.addView(filtros,new LinearLayout.LayoutParams(-1,dp(46)));

        LinearLayout lista=new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0,dp(2),0,dp(2));

        ScrollView listaScroll=new ScrollView(this);
        listaScroll.setFillViewport(false);
        listaScroll.setBackgroundColor(Color.TRANSPARENT);
        listaScroll.addView(lista); permitirRolagemInterna(listaScroll);
        box.addView(listaScroll,new LinearLayout.LayoutParams(-1,dp(210)));

        TextView perfilInfo=label("Perfil: "+perfilSelecionado[0],12);
        perfilInfo.setTextColor(GRAY); perfilInfo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(perfilInfo,new LinearLayout.LayoutParams(-1,dp(32)));

        TextView status=label(irPerfilTeste.diagnosticoEmissor(),15);
        status.setTextColor(GRAY); status.setGravity(Gravity.CENTER_VERTICAL); status.setPadding(dp(14),0,dp(14),0);
        GradientDrawable statusBg=new GradientDrawable(); statusBg.setColor(CARD); statusBg.setCornerRadius(dp(14)); statusBg.setStroke(dp(1),BORDER); status.setBackground(statusBg);
        box.addView(status,new LinearLayout.LayoutParams(-1,dp(56)));

        // Ações principais sempre visíveis: não dependem da barra de botões do AlertDialog,
        // que pode ficar escondida quando o conteúdo do testador é rolado.
        LinearLayout acoesPrincipais=row();
        Button cancelarTeste=botaoAcao("CANCELAR",KEY_DARK,11);
        Button testarProximoVisivel=botaoAcao("TESTAR PRÓXIMO ▶",ACCENT,11);
        Button funcionouSalvarVisivel=botaoAcao("✓ FUNCIONOU / SALVAR",Color.rgb(45,110,65),11);
        acoesPrincipais.addView(cancelarTeste,lpPeso());
        acoesPrincipais.addView(testarProximoVisivel,lpPeso());
        acoesPrincipais.addView(funcionouSalvarVisivel,lpPeso());
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(52));
        ap.setMargins(0,dp(8),0,dp(6));
        box.addView(acoesPrincipais,ap);

        LinearLayout navegacao=row();
        Button anterior=botaoAcao("◀ ANTERIOR",KEY_DARK,13);
        Button proximo=botaoAcao("PRÓXIMO ▶",KEY_DARK,13);
        navegacao.addView(anterior,lpPeso());
        navegacao.addView(proximo,lpPeso());
        box.addView(navegacao,new LinearLayout.LayoutParams(-1,dp(50)));

        LinearLayout manual=row();
        EditText codigoManual=new EditText(this);
        codigoManual.setHint("Código hexadecimal (ex.: 0x08 ou 0x0408)");
        codigoManual.setHintTextColor(Color.rgb(120,120,125)); codigoManual.setTextColor(WHITE);
        codigoManual.setSingleLine(true); codigoManual.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        manual.addView(codigoManual,new LinearLayout.LayoutParams(0,dp(48),1));
        Button testarManual=botaoAcao("TESTAR",KEY_DARK,12);
        manual.addView(testarManual,lpFixa(92));
        box.addView(manual,new LinearLayout.LayoutParams(-1,dp(54)));

        LinearLayout acoesCodigo=row();
        Button salvarCodigo=botaoAcao("💾 SALVAR CÓDIGO",Color.rgb(45,75,52),12);
        Button listaCodigos=botaoAcao("📋 SALVOS",KEY_DARK,12);
        acoesCodigo.addView(salvarCodigo,lpPeso());
        acoesCodigo.addView(listaCodigos,lpFixa(105));
        box.addView(acoesCodigo,new LinearLayout.LayoutParams(-1,dp(50)));

        TextView detalhe=label("Aponte o celular para o aparelho e toque em TESTAR PRÓXIMO.",13);
        detalhe.setTextColor(GRAY); detalhe.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(detalhe,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView progresso=label("Candidato 0",12); progresso.setTextColor(GRAY); progresso.setGravity(Gravity.CENTER);
        box.addView(progresso,new LinearLayout.LayoutParams(-1,dp(28)));

        final TextView[] itens=new TextView[nomes.length];
        final int[] selecionado={-1};
        int inicial=0;
        for(int i=0;i<perfisMapa.length;i++){
            if(perfisMapa[i].equals(perfilSelecionado[0])){ inicial=i; break; }
        }
        selecionado[0]=inicial;

        for(int i=0;i<nomes.length;i++){
            final int pos=i;
            TextView item=label(nomes[i],15);
            item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            item.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            item.setPadding(dp(14),0,dp(10),0);
            item.setTextColor(WHITE);
            GradientDrawable itemBg=new GradientDrawable();
            itemBg.setCornerRadius(dp(12));
            itemBg.setStroke(dp(1),i==inicial?ACCENT:BORDER);
            itemBg.setColor(i==inicial?Color.rgb(48,30,32):CARD);
            item.setBackground(itemBg);
            item.setOnClickListener(v->{
                // Para ar-condicionado, a marca do catálogo é apenas a porta de entrada.
                // O controle real vem do SmartIR, que precisa do modelo/protocolo exato.
                if("AC".equals(tipos[pos])){
                    abrirSmartIrMarca(marcas[pos]);
                    return;
                }
                selecionado[0]=pos;
                marcaSelecionada[0]=marcas[pos];
                perfilSelecionado[0]=perfisMapa[pos];
                modeloSelecionado[0]=modelos[pos];
                nomeSelecionado[0]=marcas[pos]+" "+modelos[pos];
                aparelho.setText(nomeSelecionado[0]);
                perfilInfo.setText("Perfil: "+perfilSelecionado[0]);
                irPerfilTeste.selecionar(perfilSelecionado[0]);
                
                status.setText(irPerfilTeste.diagnosticoEmissor());
                status.setTextColor(irPerfilTeste.hasEmitter()?GRAY:ACCENT);
                progresso.setText("Candidato 0");
                detalhe.setText("Aponte o celular para o aparelho e toque em TESTAR PRÓXIMO.");
                for(int j=0;j<itens.length;j++){
                    GradientDrawable g=new GradientDrawable();
                    g.setCornerRadius(dp(12));
                    g.setStroke(dp(1),j==selecionado[0]?ACCENT:BORDER);
                    g.setColor(j==selecionado[0]?Color.rgb(48,30,32):CARD);
                    itens[j].setBackground(g);
                }
            });
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(44));
            ip.setMargins(0,dp(3),0,dp(3));
            lista.addView(item,ip);
            itens[i]=item;
        }

        final Runnable aplicarFiltroMarcas=()->{
            String busca=buscaMarca.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            String categoria=categoriaFiltro[0];
            int visiveis=0;
            for(int i=0;i<itens.length;i++){
                boolean categoriaOk=categoria.isEmpty() || categoria.equals(tipos[i]);
                String texto=(tipos[i]+" "+nomes[i]+" "+marcas[i]+" "+modelos[i]+" "+perfisMapa[i]).toLowerCase(java.util.Locale.ROOT);
                boolean buscaOk=busca.isEmpty() || texto.contains(busca);
                itens[i].setVisibility(categoriaOk && buscaOk?View.VISIBLE:View.GONE);
                if(categoriaOk && buscaOk) visiveis++;
            }
            if(visiveis==0){
                status.setText("●  Nenhuma marca encontrada");
                status.setTextColor(ACCENT);
            } else {
                status.setText(irPerfilTeste.diagnosticoEmissor());
                status.setTextColor(irPerfilTeste.hasEmitter()?GRAY:ACCENT);
            }
        };
        buscaMarca.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){ aplicarFiltroMarcas.run(); }
            public void afterTextChanged(android.text.Editable s){}
        });        View.OnClickListener aplicarCategoria=v->{
            if(v==filtroTv) categoriaFiltro[0]="TV";
            else if(v==filtroAc) categoriaFiltro[0]="AC";
            else if(v==filtroFan) categoriaFiltro[0]="FAN";
            else categoriaFiltro[0]="";
            aplicarFiltroMarcas.run();
        };
        filtroTodos.setOnClickListener(aplicarCategoria);
        filtroTv.setOnClickListener(aplicarCategoria);
        filtroAc.setOnClickListener(aplicarCategoria);
        filtroFan.setOnClickListener(aplicarCategoria);
        aplicarFiltroMarcas.run();

        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("TESTE UNIVERSAL")
            .setView(wrapScroll(box))
            .create();

        dialog.setOnShowListener(x->{
            irPerfilTeste.selecionar(perfilSelecionado[0]);
            Button testar=testarProximoVisivel;
            Button salvar=funcionouSalvarVisivel;
            cancelarTeste.setOnClickListener(v->{
                dialog.dismiss();
                showMeusControles();
            });
            anterior.setOnClickListener(v->{
                String resultado=irPerfilTeste.previous();
                status.setText("●  "+resultado); status.setTextColor(GRAY);
                int atual=irPerfilTeste.position(); progresso.setText("Candidato "+atual+" de "+irPerfilTeste.total());
            });
            testarManual.setOnClickListener(v->{
                String valor=codigoManual.getText().toString().trim();
                if(valor.isEmpty()){ codigoManual.setError("Digite o código"); return; }
                boolean ok=irPerfilTeste.transmitManual(valor);
                status.setText(ok?"●  Código manual enviado":"●  Código inválido ou falha no emissor IR");
                status.setTextColor(ok?SUCCESS:ACCENT);
            });
            salvarCodigo.setOnClickListener(v->{
                int codigo=irPerfilTeste.currentCode();
                if(codigo<0){ Toast.makeText(this,"Teste um código antes de salvar.",Toast.LENGTH_SHORT).show(); return; }
                EditText nome=new EditText(this); nome.setHint("Ex.: Liga / Desliga"); nome.setSingleLine(true); nome.setTextColor(WHITE); nome.setHintTextColor(GRAY);
                new android.app.AlertDialog.Builder(this).setTitle("Salvar código").setView(nome)
                    .setNegativeButton("CANCELAR",null)
                    .setPositiveButton("SALVAR",(d,w)->{ String msg=irPerfilTeste.saveCurrentCode(nome.getText().toString()); status.setText("●  "+msg); status.setTextColor(SUCCESS); })
                    .show();
            });
            listaCodigos.setOnClickListener(v->{
                String dados=irPerfilTeste.savedCodes();
                if(dados.isEmpty()){ Toast.makeText(this,"Nenhum código salvo ainda.",Toast.LENGTH_SHORT).show(); return; }
                final String[] linhas=dados.split("\\n");
                final String[] rotulos=new String[linhas.length];
                for(int i=0;i<linhas.length;i++) rotulos[i]=IrPerfilTeste.rotuloDaLinha(linhas[i]);
                escolher("CÓDIGOS SALVOS ("+linhas.length+")", "Toque em um código para carregar e testar imediatamente.", rotulos, w->{
                    int cod=IrPerfilTeste.codigoDaLinha(linhas[w]);
                    String pf=IrPerfilTeste.perfilDaLinha(linhas[w]);
                    if(cod<0 || pf.isEmpty()){ Toast.makeText(this,"Registro inválido.",Toast.LENGTH_SHORT).show(); return; }
                    codigoManual.setText("0x"+Integer.toHexString(cod).toUpperCase(java.util.Locale.ROOT));
                    codigoManual.setSelection(codigoManual.length());
                    irPerfilTeste.selecionar(pf);
                    boolean ok=irPerfilTeste.transmitManual("0x"+Integer.toHexString(cod));
                    status.setText(ok
                        ?"✓  Código salvo enviado • "+pf+" • 0x"+Integer.toHexString(cod).toUpperCase(java.util.Locale.ROOT)
                        :"✕  Falha ao enviar o código salvo • "+pf);
                    status.setTextColor(ok?SUCCESS:ACCENT);
                    detalhe.setText(ok
                        ?"Código salvo testado. Se o aparelho respondeu, use FUNCIONOU / SALVAR para gravá-lo no controle."
                        :"Não foi possível enviar este código. Verifique o emissor IR e tente novamente.");
                },null);
            });
            proximo.setOnClickListener(v->testar.performClick());
            testar.setOnClickListener(v->{
                String resultado=irPerfilTeste.next();
                status.setText("●  "+resultado); status.setTextColor(SUCCESS);
                int atual=irPerfilTeste.position(); int total=irPerfilTeste.total();
                progresso.setText("Candidato "+atual+" de "+total);
                if(atual>=total) detalhe.setText("Fim dos candidatos. Escolha outro aparelho na lista ou cancele e tente novamente.");
                else detalhe.setText("Código enviado.\\nSe respondeu, toque em FUNCIONOU / SALVAR.");
            });
            salvar.setOnClickListener(v->{
                int codigo=irPerfilTeste.currentCode(); String perfil=irPerfilTeste.getPerfil();
                if(codigo<0 || perfil==null || perfil.isEmpty()){
                    Toast.makeText(this,"Teste pelo menos um código antes de salvar.",Toast.LENGTH_SHORT).show(); return;
                }
                int freq=frequenciaPerfil(perfil);
                String nome=nomeSelecionado[0]; String modeloSalvo=modeloSelecionado[0]; String descricao=irPerfilTeste.descricaoAtual();
                String categoria=perfil.startsWith("AC ")?"AR-CONDICIONADO":(perfil.equals("Ventilador Universal")?"VENTILADOR":"TV");
                if(!monetizacao.podeSalvarControle(controleStorage.listar().size())){
                    Toast.makeText(this,"Limite gratuito atingido (3 controles). Desbloqueie o Premium para salvar ilimitados.",Toast.LENGTH_LONG).show();
                    monetizacao.showPremiumDialog();
                    return;
                }
                long novoId=controleStorage.salvar(nome,categoria,marcaSelecionada[0],modeloSalvo,perfil,descricao,codigo,freq);
                if(novoId<0){
                    Toast.makeText(this,"Não foi possível salvar este controle. Tente novamente.",Toast.LENGTH_LONG).show();
                    return;
                }
                dialog.dismiss();
                controleAtivo=controleStorage.buscar(novoId);
                if(controleAtivo!=null) prefs.edit().putLong("active_control_id",controleAtivo.id).apply();
                lgMode="LG".equalsIgnoreCase(marcaSelecionada[0]);
                prefs.edit().putBoolean("lg_mode",lgMode).apply();
                Toast.makeText(this,"✓ Controle salvo e definido como ativo.",Toast.LENGTH_SHORT).show();
                oferecerConfiguracaoAutomatica(controleAtivo);
            });
        });
    }

    private void oferecerConfiguracaoAutomatica(ControleStorage.Controle controle){
        if(controle==null){ showMeusControles(); return; }

        AutoConfigurator.Result resultado=AutoConfigurator.configurar(controle,controleStorage);
        final int totalFuncoes=IrPerfilTeste.PERFIL_VENTILADOR.equals(controle.perfil)
                ? RemoteKeys.FAN_FUNCOES.length : RemoteKeys.FUNCOES.length;
        int qtd=controleStorage.quantidadeComandos(controle);

        if(resultado.suportado && resultado.configurados>0){
            String mensagem="⚡ O aplicativo pode montar automaticamente os principais botões deste controle.\n\n"
                    +"✓ "+resultado.configurados+" funções configuradas agora\n"
                    +"✓ "+qtd+" de "+totalFuncoes+" botões prontos\n\n"
                    +"Você pode abrir o controle e testar. Se alguma função não responder, ela poderá ser ajustada depois em CONFIGURAR.";

            new android.app.AlertDialog.Builder(this)
                .setTitle("CONFIGURAÇÃO AUTOMÁTICA")
                .setMessage(mensagem)
                .setNegativeButton("CONFIGURAR MANUALMENTE",(d,w)->showAprenderComandos(controle))
                .setPositiveButton("ABRIR CONTROLE",(d,w)->{
                    controleAtivo=controleStorage.buscar(controle.id);
                    prefs.edit().putLong("active_control_id",controle.id).apply();
                    lgMode="LG".equalsIgnoreCase(controle.marca);
                    prefs.edit().putBoolean("lg_mode",lgMode).apply();
                    build();
                }).show();
        } else if(resultado.suportado){
            new android.app.AlertDialog.Builder(this)
                .setTitle("CONTROLE JÁ CONFIGURADO")
                .setMessage("✓ Os botões automáticos disponíveis já estão configurados.\n\nVocê pode abrir o controle ou ajustar alguma função manualmente.")
                .setNegativeButton("CONFIGURAR",(d,w)->showAprenderComandos(controle))
                .setPositiveButton("ABRIR",(d,w)->{
                    controleAtivo=controleStorage.buscar(controle.id);
                    prefs.edit().putLong("active_control_id",controle.id).apply();
                    build();
                }).show();
        } else {
            new android.app.AlertDialog.Builder(this)
                .setTitle("CONFIGURAÇÃO GUIADA")
                .setMessage("Este perfil não possui uma tabela automática confiável no aplicativo ainda.\n\nEm vez de gravar códigos que podem não corresponder ao seu aparelho, vamos usar a configuração guiada para confirmar cada função.\n\nO código que você encontrou continua salvo como base.")
                .setNegativeButton("AGORA NÃO",(d,w)->showMeusControles())
                .setPositiveButton("CONFIGURAR",(d,w)->showAprenderComandos(controle)).show();
        }
    }

    private void showAprenderComandos(ControleStorage.Controle controle){
        if(controle==null){ showMeusControles(); return; }

        final boolean isFan=IrPerfilTeste.PERFIL_VENTILADOR.equals(controle.perfil);
        final String[] funcoes=isFan?RemoteKeys.FAN_FUNCOES:RemoteKeys.FUNCOES;
        final String[] chaves=isFan?RemoteKeys.FAN_CHAVES:RemoteKeys.CHAVES;
        final int[] pos={Math.max(0,Math.min(aprenderFuncaoPos,funcoes.length-1))};
        final String[] perfis=irPerfilTeste.perfis();
        final int configuradosInicial=controleStorage.quantidadeComandos(controle);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(4),dp(18),dp(4));

        TextView intro=label("A configuração automática já preenche o que é conhecido. Use esta tela para corrigir ou adicionar funções.",13);
        intro.setTextColor(GRAY);
        intro.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams introP=new LinearLayout.LayoutParams(-1,-2); introP.setMargins(0,0,0,dp(8)); box.addView(intro,introP);

        LinearLayout device=row();
        TextView deviceName=label(controle.nome,18);
        deviceName.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        deviceName.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        device.addView(deviceName,new LinearLayout.LayoutParams(0,dp(38),1));
        TextView profileBadge=badge(controle.perfil==null||controle.perfil.isEmpty()?"IR":controle.perfil,Color.rgb(60,60,68));
        device.addView(profileBadge,new LinearLayout.LayoutParams(-2,dp(28)));
        box.addView(device,new LinearLayout.LayoutParams(-1,dp(42)));

        LinearLayout progressRow=row();
        TextView progress=label("",12);
        progress.setTextColor(Color.rgb(105,175,115));
        progress.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        progressRow.addView(progress,new LinearLayout.LayoutParams(0,dp(30),1));
        Button proximoNaoConfigurado=smallAction("Próximo pendente",Color.rgb(55,65,80));
        progressRow.addView(proximoNaoConfigurado,new LinearLayout.LayoutParams(dp(132),dp(32)));
        box.addView(progressRow,new LinearLayout.LayoutParams(-1,dp(34)));
        final ProgressBar barraProgresso=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        barraProgresso.setMax(100);
        barraProgresso.setProgressTintList(ColorStateList.valueOf(Color.rgb(105,190,125)));
        barraProgresso.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(48,48,54)));
        LinearLayout.LayoutParams barraP=new LinearLayout.LayoutParams(-1,dp(6)); barraP.setMargins(0,0,0,dp(10));
        box.addView(barraProgresso,barraP);

        TextView selectedTitle=label("",16);
        selectedTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        selectedTitle.setTextColor(WHITE);
        selectedTitle.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(selectedTitle,new LinearLayout.LayoutParams(-1,dp(34)));

        final TextView status=label("Aguardando teste",13);
        status.setTextColor(GRAY);
        status.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        GradientDrawable statusBg=new GradientDrawable();
        statusBg.setColor(CARD);
        statusBg.setCornerRadius(dp(12));
        statusBg.setStroke(dp(1),BORDER);
        status.setBackground(statusBg);
        status.setPadding(dp(12),dp(10),dp(12),dp(10));
        box.addView(status,new LinearLayout.LayoutParams(-1,-2));

        TextView listaTitulo=label("Botões do controle",12);
        listaTitulo.setTextColor(GRAY);
        listaTitulo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        listaTitulo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lt=new LinearLayout.LayoutParams(-1,dp(30));
        lt.setMargins(0,dp(8),0,0);
        box.addView(listaTitulo,lt);

        ScrollView listaScroll=new ScrollView(this);
        listaScroll.setFillViewport(false);
        LinearLayout lista=new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        listaScroll.addView(lista); permitirRolagemInterna(listaScroll);
        LinearLayout.LayoutParams lsp=new LinearLayout.LayoutParams(-1,Math.min(dp(300),(int)(getResources().getDisplayMetrics().heightPixels*0.34f)));
        lsp.setMargins(0,dp(2),0,dp(2));
        box.addView(listaScroll,lsp);

        LinearLayout hexRow=row();
        final EditText hex=new EditText(this);
        hex.setHint("Ir direto a um código (hex, ex.: 0x5C)"); hex.setHintTextColor(Color.rgb(120,120,125));
        hex.setTextColor(WHITE); hex.setTextSize(13); hex.setSingleLine(true);
        hexRow.addView(hex,new LinearLayout.LayoutParams(0,dp(46),1));
        final Button hexBtn=botaoAcao("Testar hex",KEY_DARK,12);
        hexRow.addView(hexBtn,lpFixa(96));
        box.addView(hexRow,new LinearLayout.LayoutParams(-1,dp(50)));

        final Button[] botoes=new Button[funcoes.length];

        Runnable atualizarLista=()->{
            int qtd=controleStorage.quantidadeComandos(controle);
            int percentual=funcoes.length<=0?0:Math.min(100,(qtd*100)/funcoes.length);
            progress.setText(qtd+" de "+funcoes.length+" botões • "+percentual+"%");
            barraProgresso.setProgress(percentual);
            proximoNaoConfigurado.setText(qtd>=funcoes.length?"✓ Concluído":"Próximo pendente");
            selectedTitle.setText(qtd>=funcoes.length
                ?"✓ Controle totalmente configurado"
                :"Configurando: "+funcoes[pos[0]]);
            for(int i=0;i<botoes.length;i++){
                if(botoes[i]==null) continue;
                boolean selecionado=i==pos[0];
                boolean feito=controleStorage.possuiComando(controle,chaves[i]);
                botoes[i].setText((selecionado?"●  ":"")+(feito?"✓  ":"")+funcoes[i]);
                botoes[i].setTextColor(selecionado?WHITE:(feito?Color.rgb(120,190,135):GRAY));
                GradientDrawable bg=new GradientDrawable();
                bg.setColor(selecionado?Color.rgb(58,58,68):CARD_2);
                bg.setCornerRadius(dp(11));
                bg.setStroke(dp(1),selecionado?ACCENT:(feito?Color.rgb(65,105,75):BORDER));
                botoes[i].setBackground(bg);
            }
        };

        proximoNaoConfigurado.setOnClickListener(v->{
            int inicio=pos[0];
            for(int passo=1;passo<=funcoes.length;passo++){
                int candidato=(inicio+passo)%funcoes.length;
                if(!controleStorage.possuiComando(controle,chaves[candidato])){
                    pos[0]=candidato;
                    aprenderFuncaoPos=candidato;
                    irPerfilTeste.reset();
                    status.setText("Aguardando teste para "+funcoes[candidato]);
                    status.setTextColor(GRAY);
                    atualizarLista.run();
                    return;
                }
            }
            status.setText("✓ Todos os botões já estão configurados.");
            status.setTextColor(Color.rgb(105,190,125));
        });
        for(int i=0;i<funcoes.length;i++){
            final int indice=i;
            Button item=new Button(this);
            item.setTextSize(12);
            item.setAllCaps(false);
            item.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            item.setPadding(dp(12),0,dp(10),0);
            item.setMinHeight(0);
            item.setMinWidth(0);
            item.setOnClickListener(v->{
                pos[0]=indice;
                aprenderFuncaoPos=indice;
                irPerfilTeste.reset();
                status.setText("Aguardando teste para "+funcoes[indice]);
                status.setTextColor(GRAY);
                atualizarLista.run();
            });
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(46));
            ip.setMargins(0,dp(2),0,dp(2));
            lista.addView(item,ip);
            botoes[i]=item;
        }

        atualizarLista.run();

        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("Configurar botões")
            .setView(wrapScroll(box))
            .setNegativeButton("Fechar",(d,w)->showMeusControles())
            .setNeutralButton("Testar código",null)
            .setPositiveButton("Funcionou, salvar",null)
            .create();

        dialog.setOnShowListener(x->{
            Button testar=dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
            Button salvar=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);

            final String[] preparado={""};
            final Runnable preparar=()->{
                String perfil=controle.perfil;
                if(perfil==null || perfil.isEmpty()) perfil=perfis[perfilInicialPara(controle.marca,controle.modelo,perfis)];
                if(!perfil.equals(preparado[0]) || !perfil.equals(irPerfilTeste.getPerfil())){
                    // varredura completa de comandos usando o endereço do controle já salvo
                    irPerfilTeste.selecionarVarredura(perfil,controle.codigo>255?(controle.codigo>>8)&0xFF:-1);
                    preparado[0]=perfil;
                }
            };
            testar.setOnClickListener(v->{
                preparar.run();
                String resultado=irPerfilTeste.next();
                status.setText("●  "+resultado+"  •  "+funcoes[pos[0]]);
                status.setTextColor(Color.rgb(205,180,90));
            });
            hexBtn.setOnClickListener(v->{
                preparar.run();
                boolean ok=irPerfilTeste.transmitManual(hex.getText().toString());
                status.setText(ok?"●  Código enviado • "+funcoes[pos[0]]+"  (se respondeu, toque em Funcionou, salvar)":"●  Código inválido ou falha no emissor IR");
                status.setTextColor(ok?Color.rgb(205,180,90):ACCENT);
            });

            salvar.setOnClickListener(v->{
                int codigo=irPerfilTeste.currentCode();
                String perfil=irPerfilTeste.getPerfil();
                if(codigo<0 || perfil==null || perfil.isEmpty()){
                    status.setText("Faça pelo menos um teste antes de salvar.");
                    status.setTextColor(ACCENT);
                    return;
                }
                String funcao=chaves[pos[0]];
                controleStorage.salvarComando(controle,funcao,codigo,perfil,frequenciaPerfil(perfil));
                status.setText("✓  "+funcoes[pos[0]]+" configurado com sucesso");
                status.setTextColor(Color.rgb(105,190,125));
                atualizarLista.run();
                Toast.makeText(this,"✓ "+funcoes[pos[0]]+" configurado",Toast.LENGTH_SHORT).show();

                // Avança automaticamente para o próximo botão ainda pendente.
                for(int passo=1;passo<=funcoes.length;passo++){
                    int candidato=(pos[0]+passo)%funcoes.length;
                    if(!controleStorage.possuiComando(controle,chaves[candidato])){
                        pos[0]=candidato;
                        aprenderFuncaoPos=candidato;
                        irPerfilTeste.reset();
                        status.setText("Próximo: "+funcoes[candidato]+" • pronto para testar");
                        status.setTextColor(GRAY);
                        atualizarLista.run();
                        break;
                    }
                }
            });
        });

        dialog.show();
    }

    private void exportarBackup() {
        if (controleStorage.quantidadeControles() == 0) {
            Toast.makeText(this,"Não há controles salvos para exportar.",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"ir-remote-backup.json");
        startActivityForResult(i,REQ_EXPORT_BACKUP);
    }

    private void importarBackup() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("application/json");
        startActivityForResult(i,REQ_IMPORT_BACKUP);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,android.content.Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null || data.getData()==null) return;
        android.net.Uri uri=data.getData();
        if(requestCode==REQ_EXPORT_BACKUP) {
            try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)) {
                String json=controleStorage.exportarJson();
                if(json==null || out==null) throw new java.io.IOException("backup vazio");
                out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)); out.flush();
                Toast.makeText(this,"✓ Backup exportado com sucesso.",Toast.LENGTH_LONG).show();
            } catch(Exception e) {
                Toast.makeText(this,"Não foi possível exportar o backup.",Toast.LENGTH_LONG).show();
            }
        } else if(requestCode==REQ_IMPORT_BACKUP) {
            try(java.io.InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null) throw new java.io.IOException("arquivo indisponível");
                java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
                byte[] dataBuf=new byte[8192]; int n;
                while((n=in.read(dataBuf))!=-1) buffer.write(dataBuf,0,n);
                String json=new String(buffer.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                final String backup=json;
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Restaurar backup")
                    .setMessage("Escolha como importar os controles encontrados neste backup.")
                    .setNegativeButton("ADICIONAR",(d,w)->finalizarImportacao(backup,false))
                    .setPositiveButton("SUBSTITUIR",(d,w)->finalizarImportacao(backup,true))
                    .show();
            } catch(Exception e) {
                Toast.makeText(this,"Arquivo de backup inválido ou inacessível.",Toast.LENGTH_LONG).show();
            }
        }
    }

    private void finalizarImportacao(String json, boolean substituir) {
        int qtd=controleStorage.importarJson(json,substituir);
        if(qtd<0) {
            Toast.makeText(this,"Não foi possível importar: backup inválido ou incompatível.",Toast.LENGTH_LONG).show();
            return;
        }
        controleAtivo=null; prefs.edit().remove("active_control_id").apply();
        Toast.makeText(this,"✓ "+qtd+" controle"+(qtd==1?"":"s")+" restaurado"+(qtd==1?"":"s")+".",Toast.LENGTH_LONG).show();
        showMeusControles();
    }

    private void showMeusControles(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(28));

        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconBtn(R.drawable.ic_back,46,24,CARD_2,true,"Voltar",v->showSelector()),new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL);
        head.addView(textoEsq("Meus controles",22,WHITE,true),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams hs=new LinearLayout.LayoutParams(-1,-2); hs.setMargins(0,dp(2),0,0);
        head.addView(textoEsq("Salvos neste aparelho",12,GRAY,false),hs);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1); hp.setMargins(dp(12),0,0,0);
        top.addView(head,hp);
        LinearLayout.LayoutParams topP=new LinearLayout.LayoutParams(-1,-2); topP.setMargins(0,0,0,dp(8));
        root.addView(top,topP);

        final boolean premiumAtivo=monetizacao.isPremium();
        root.addView(cartaoLinha(R.drawable.ic_star,premiumAtivo?"Premium ativo":"Plano gratuito",
            monetizacao.resumoLimite(controleStorage.quantidadeControles())+" · "+(premiumAtivo?"sem anúncios":"desbloqueie controles ilimitados"),
            premiumAtivo?Color.rgb(28,55,36):Color.rgb(48,40,24),v->monetizacao.showPremiumDialog()));

        LinearLayout backup=new LinearLayout(this); backup.setOrientation(LinearLayout.HORIZONTAL);
        backup.addView(chip(R.drawable.ic_upload,"Exportar backup",v->exportarBackup()));
        backup.addView(chip(R.drawable.ic_download,"Importar backup",v->importarBackup()));
        LinearLayout.LayoutParams bkp=new LinearLayout.LayoutParams(-1,-2); bkp.setMargins(0,dp(6),0,dp(4));
        root.addView(backup,bkp);

        final EditText busca=new EditText(this);
        busca.setSingleLine(true); busca.setHint("Pesquisar marca, modelo ou nome");
        busca.setHintTextColor(Color.rgb(125,125,130)); busca.setTextColor(WHITE); busca.setTextSize(14); busca.setPadding(dp(14),0,dp(14),0);
        GradientDrawable searchBg=new GradientDrawable(); searchBg.setColor(CARD); searchBg.setCornerRadius(dp(15)); searchBg.setStroke(dp(1),BORDER); busca.setBackground(searchBg);
        LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(48)); searchP.setMargins(0,dp(8),0,dp(6)); root.addView(busca,searchP);

        final TextView resumo=label("",12); resumo.setTextColor(GRAY); resumo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams resP=new LinearLayout.LayoutParams(-1,-2); resP.setMargins(dp(4),dp(4),0,dp(4));
        root.addView(resumo,resP);
        final LinearLayout listaBox=new LinearLayout(this); listaBox.setOrientation(LinearLayout.VERTICAL); root.addView(listaBox,new LinearLayout.LayoutParams(-1,-2));

        final Runnable[] render=new Runnable[1];
        render[0]=()->{
            listaBox.removeAllViews();
            String filtro=busca.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            List<ControleStorage.Controle> todos=controleStorage.listar();
            List<ControleStorage.Controle> lista=new ArrayList<>();
            for(ControleStorage.Controle c:todos){
                String alvo=(c.nome+" "+c.marca+" "+c.modelo+" "+c.categoria).toLowerCase(java.util.Locale.ROOT);
                if(filtro.isEmpty()||alvo.contains(filtro)) lista.add(c);
            }
            resumo.setText(filtro.isEmpty() ? monetizacao.resumoLimite(todos.size()) : lista.size() + " resultado" + (lista.size()==1 ? "" : "s") + " para \"" + filtro + "\"");
            if(lista.isEmpty()){
                if(filtro.isEmpty()){
                    LinearLayout vazioBox=new LinearLayout(this);
                    vazioBox.setOrientation(LinearLayout.VERTICAL);
                    vazioBox.setGravity(Gravity.CENTER_HORIZONTAL);
                    vazioBox.setPadding(dp(18),dp(24),dp(18),dp(18));
                    GradientDrawable vazioBg=new GradientDrawable();
                    vazioBg.setColor(CARD);
                    vazioBg.setCornerRadius(dp(20));
                    vazioBg.setStroke(dp(1),BORDER);
                    vazioBox.setBackground(vazioBg);

                    LinearLayout.LayoutParams iv=new LinearLayout.LayoutParams(dp(40),dp(40));
                    vazioBox.addView(icone(R.drawable.ic_tv,40,GRAY),iv);

                    TextView tituloVazio=label("Nenhum controle salvo ainda",18);
                    tituloVazio.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                    LinearLayout.LayoutParams tv1=new LinearLayout.LayoutParams(-1,-2); tv1.setMargins(0,dp(12),0,dp(6));
                    vazioBox.addView(tituloVazio,tv1);

                    TextView textoVazio=label("Use o Controle universal para encontrar seu aparelho, testar os códigos e salvar o que funcionar. Depois de salvo, ele aparece aqui para acesso rápido.",13);
                    textoVazio.setTextColor(GRAY);
                    LinearLayout.LayoutParams tv2=new LinearLayout.LayoutParams(-1,-2); tv2.setMargins(0,0,0,dp(14));
                    vazioBox.addView(textoVazio,tv2);

                    vazioBox.addView(botaoLargo(R.drawable.ic_search,"Encontrar meu controle",52,ACCENT,v->showUniversalScanner("Universal","Universal")));

                    LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,-2);
                    vp.setMargins(0,dp(4),0,dp(8));
                    listaBox.addView(vazioBox,vp);
                }else{
                    TextView vazio=label("Nenhum controle encontrado para \""+filtro+"\".",15);
                    vazio.setTextColor(GRAY);
                    vazio.setPadding(0,dp(30),0,dp(30));
                    listaBox.addView(vazio,new LinearLayout.LayoutParams(-1,-2));
                }
                return;
            }
            for(ControleStorage.Controle c:lista){
                final boolean ativoAtual=controleAtivo!=null&&controleAtivo.id==c.id;
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(14),dp(14),dp(14),dp(10));
                GradientDrawable cb=new GradientDrawable(); cb.setColor(ativoAtual?Color.rgb(28,55,36):CARD); cb.setCornerRadius(dp(18));
                cb.setStroke(dp(1),ativoAtual?Color.rgb(80,160,100):Color.rgb(55,55,60)); card.setBackground(cb);

                // Cabeçalho: ícone da categoria, nome, marca/modelo e selo.
                LinearLayout line=new LinearLayout(this); line.setOrientation(LinearLayout.HORIZONTAL); line.setGravity(Gravity.CENTER_VERTICAL);
                int catIcon=IrPerfilTeste.PERFIL_VENTILADOR.equals(c.perfil)?R.drawable.ic_air:("AC SmartIR".equals(c.perfil)?R.drawable.ic_snow:R.drawable.ic_tv);
                LinearLayout circulo=new LinearLayout(this); circulo.setGravity(Gravity.CENTER);
                GradientDrawable cg=new GradientDrawable(); cg.setShape(GradientDrawable.OVAL); cg.setColor(KEY); circulo.setBackground(cg);
                circulo.addView(icone(catIcon,22,WHITE),new LinearLayout.LayoutParams(dp(22),dp(22)));
                line.addView(circulo,new LinearLayout.LayoutParams(dp(42),dp(42)));
                LinearLayout nomes=new LinearLayout(this); nomes.setOrientation(LinearLayout.VERTICAL);
                nomes.addView(textoEsq(c.nome,16,WHITE,true),new LinearLayout.LayoutParams(-1,-2));
                LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2); ml.setMargins(0,dp(2),0,0);
                nomes.addView(textoEsq(c.marca+" · "+c.modelo,12,GRAY,false),ml);
                LinearLayout.LayoutParams nl=new LinearLayout.LayoutParams(0,-2,1); nl.setMargins(dp(12),0,dp(8),0);
                line.addView(nomes,nl);
                line.addView(badge(ativoAtual?"Ativo":"IR",ativoAtual?Color.rgb(55,110,65):Color.rgb(60,60,68)),new LinearLayout.LayoutParams(-2,dp(26)));
                card.addView(line);

                // Progresso de configuração dos botões.
                int qtd=controleStorage.quantidadeComandos(c);
                int totalFuncoes=(IrPerfilTeste.PERFIL_VENTILADOR.equals(c.perfil)?RemoteKeys.FAN_FUNCOES.length:RemoteKeys.FUNCOES.length);
                int percentual=totalFuncoes<=0?0:Math.min(100,(qtd*100)/totalFuncoes);
                LinearLayout prog=new LinearLayout(this); prog.setOrientation(LinearLayout.HORIZONTAL); prog.setGravity(Gravity.CENTER_VERTICAL);
                prog.addView(textoEsq((c.perfil==null?"":c.perfil)+" · "+qtd+" de "+totalFuncoes+" botões",12,GRAY,false),new LinearLayout.LayoutParams(0,-2,1));
                prog.addView(textoEsq(percentual+"%",12,Color.rgb(105,190,125),true),new LinearLayout.LayoutParams(-2,-2));
                LinearLayout.LayoutParams pgl=new LinearLayout.LayoutParams(-1,-2); pgl.setMargins(0,dp(12),0,dp(6));
                card.addView(prog,pgl);
                ProgressBar barra=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                barra.setMax(100); barra.setProgress(percentual);
                barra.setProgressTintList(ColorStateList.valueOf(Color.rgb(105,190,125)));
                barra.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(48,48,54)));
                card.addView(barra,new LinearLayout.LayoutParams(-1,dp(6)));

                // Ação principal e ações secundárias.
                card.addView(botaoLargo(R.drawable.ic_right,"Abrir",48,Color.rgb(55,110,65),v->{
                    controleAtivo=c;lgMode="LG".equalsIgnoreCase(c.marca);
                    prefs.edit().putLong("active_control_id",c.id).putBoolean("lg_mode",lgMode).apply();
                    showingSelector=false;build();
                    Toast.makeText(this,"✓ "+c.nome+" em uso",Toast.LENGTH_SHORT).show();
                }));

                LinearLayout acoes=row();
                acoes.addView(tile(R.drawable.ic_auto,"Auto",54,KEY,v->{
                    controleAtivo=c;lgMode="LG".equalsIgnoreCase(c.marca);
                    prefs.edit().putLong("active_control_id",c.id).putBoolean("lg_mode",lgMode).apply();
                    oferecerConfiguracaoAutomatica(c);
                },11));
                acoes.addView(tile(R.drawable.ic_play,"Testar",54,KEY,v->showComandosConfigurados(c),11));
                acoes.addView(tile(R.drawable.ic_copy,"Duplicar",54,KEY,v->{
                    int totalAtual=controleStorage.listar().size();
                    if(!monetizacao.podeSalvarControle(totalAtual)){
                        Toast.makeText(this,"Limite gratuito de "+monetizacao.limiteGratuito()+" controles atingido.",Toast.LENGTH_LONG).show();
                        monetizacao.showPremiumDialog();
                        return;
                    }
                    ControleStorage.Controle novo=controleStorage.duplicar(c,c.nome+" (cópia)");
                    if(novo!=null) Toast.makeText(this,"✓ Controle duplicado e salvo",Toast.LENGTH_SHORT).show();
                    else Toast.makeText(this,"Não foi possível duplicar o controle.",Toast.LENGTH_SHORT).show();
                    render[0].run();
                },11));
                acoes.addView(tile(R.drawable.ic_edit,"Nome",54,KEY,v->{
                    final EditText campo=new EditText(this); campo.setSingleLine(true); campo.setText(c.nome); campo.setSelectAllOnFocus(true); campo.setHint("Nome do controle");
                    new android.app.AlertDialog.Builder(this).setTitle("Renomear controle").setView(campo)
                        .setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",(d,w)->{
                            String novoNome=campo.getText().toString().trim();
                            if(!novoNome.isEmpty()){controleStorage.renomear(c,novoNome);if(controleAtivo!=null&&controleAtivo.id==c.id) controleAtivo.nome=c.nome;render[0].run();}
                        }).show();
                },11));
                acoes.addView(tile(R.drawable.ic_delete,"Excluir",54,Color.rgb(90,40,40),v->new android.app.AlertDialog.Builder(this).setTitle("Excluir controle?")
                    .setMessage("Remover \""+c.nome+"\" deste aparelho?").setNegativeButton("CANCELAR",null)
                    .setPositiveButton("EXCLUIR",(d,w)->{controleStorage.excluir(c);if(controleAtivo!=null&&controleAtivo.id==c.id){controleAtivo=null;prefs.edit().remove("active_control_id").apply();}render[0].run();}).show(),11));
                card.addView(acoes);

                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(0,dp(6),0,dp(6)); listaBox.addView(card,cp);
            }
        };
        busca.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){render[0].run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        render[0].run();

        root.addView(botaoLargo(R.drawable.ic_plus,"Adicionar outro controle",52,ACCENT,v->showAddControlWizard()));
        root.addView(botaoLargo(R.drawable.ic_info,"Sobre o aplicativo",48,CARD_2,v->showSobre()));
        mostrar(telaRolavel(root));
    }

    private void showComandosConfigurados(ControleStorage.Controle controle){
        if(controle==null || controle.comandos==null || controle.comandos.length()==0){
            Toast.makeText(this,"Nenhum botão foi configurado ainda.",Toast.LENGTH_SHORT).show();
            return;
        }
        JSONArray nomes=controle.comandos.names();
        if(nomes==null || nomes.length()==0){
            Toast.makeText(this,"Nenhum botão foi configurado ainda.",Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] funcoes=new String[nomes.length()];
        for(int i=0;i<nomes.length();i++) funcoes[i]=nomes.optString(i,"");
        escolher("TESTAR BOTÕES • "+controle.nome,
            "Selecione um botão configurado para enviar o código salvo.",
            funcoes,w->{
                String funcao=funcoes[w];
                int codigo=controleStorage.codigoComando(controle,funcao);
                String perfil=controleStorage.perfilComando(controle,funcao);
                int freq=controleStorage.frequenciaComando(controle,funcao);
                if(codigo<0 || perfil.isEmpty()){
                    Toast.makeText(this,"Código salvo inválido para "+funcao+".",Toast.LENGTH_SHORT).show();
                    return;
                }
                irPerfilTeste.selecionar(perfil);
                boolean ok=irPerfilTeste.transmitirSalvo(perfil,codigo,freq);
                Toast.makeText(this,ok?"✓ "+funcao+" enviado":"✕ Falha ao enviar "+funcao+" • "+freq+" Hz",Toast.LENGTH_SHORT).show();
            },null);
    }

    private Button smallAction(String text,int color){
        Button b=new Button(this); b.setText(text); b.setTextColor(WHITE); b.setTextSize(11); b.setAllCaps(false); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0);
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(11)); g.setStroke(dp(1),Color.rgb(70,70,75)); b.setBackground(g); actionFeedback(b); return b;
    }

    private void showFanRemote(){
        showingSelector=false; fanMode=true;
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(12),dp(14),dp(26));
        final int[] fanFunc={0};
        final java.util.List<View> fanActionButtons=new ArrayList<>();

        LinearLayout top=new LinearLayout(this); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL);
        head.addView(textoEsq("Ventilador",24,WHITE,true),new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams sublp=new LinearLayout.LayoutParams(-1,-2); sublp.setMargins(0,dp(2),0,0);
        head.addView(textoEsq(controleAtivo!=null?controleAtivo.marca+" · "+controleAtivo.modelo:"Controle universal · 38 kHz",12,GRAY,false),sublp);
        top.addView(head,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=badge(controleAtivo!=null?"Salvo":"Universal",controleAtivo!=null?Color.rgb(55,110,65):Color.rgb(60,60,68));
        top.addView(badge,new LinearLayout.LayoutParams(-2,dp(26)));
        root.addView(top);

        LinearLayout statusCard=new LinearLayout(this); statusCard.setGravity(Gravity.CENTER_VERTICAL); statusCard.setPadding(dp(14),dp(12),dp(14),dp(12));
        GradientDrawable sb=new GradientDrawable(); sb.setColor(CARD); sb.setCornerRadius(dp(18)); sb.setStroke(dp(1),BORDER); statusCard.setBackground(sb);
        final TextView status=textoEsq("● Pronto para enviar comandos",13,SUCCESS,false);
        statusCard.addView(status,new LinearLayout.LayoutParams(0,-2,1));
        TextView freq=label("38 kHz",11); freq.setTextColor(GRAY); statusCard.addView(freq,new LinearLayout.LayoutParams(-2,-2));
        LinearLayout.LayoutParams scp=new LinearLayout.LayoutParams(-1,-2); scp.setMargins(0,dp(12),0,0);
        root.addView(statusCard,scp);

        // Liga/desliga grande no centro.
        ImageButton power=iconBtn(R.drawable.ic_power,112,54,Color.rgb(125,35,40),true,"Ligar ou desligar",v->{
            enviarVentilador(1); fanFunc[0]=1; status.setText("✓ Liga/desliga enviado");
            for(View b:fanActionButtons) pintar(b,KEY);
        });
        LinearLayout.LayoutParams pwp=new LinearLayout.LayoutParams(dp(112),dp(112)); pwp.gravity=Gravity.CENTER_HORIZONTAL; pwp.setMargins(0,dp(18),0,dp(10));
        root.addView(power,pwp);
        TextView pwl=label("Liga / desliga",12); pwl.setTextColor(GRAY);
        root.addView(pwl,new LinearLayout.LayoutParams(-1,-2));

        section(root,"Funções");
        String[] nomes={"Oscilação","Velocidade","Timer","Noturno"};
        int[] icones={R.drawable.ic_swap_vert,R.drawable.ic_air,R.drawable.ic_timer,R.drawable.ic_moon};
        LinearLayout fr=row();
        for(int i=0;i<4;i++){
            final int func=i+2;
            final String nome=nomes[i];
            final LinearLayout[] self=new LinearLayout[1];
            self[0]=tile(icones[i],nome,88,KEY,v->{
                enviarVentilador(func);
                fanFunc[0]=func;
                status.setText("✓ "+nome+" enviado");
                for(View x:fanActionButtons) pintar(x,x==self[0]?Color.rgb(65,85,105):KEY);
            });
            fanActionButtons.add(self[0]);
            fr.addView(self[0]);
            if(i==1){ root.addView(fr); fr=row(); }
        }
        root.addView(fr);

        TextView dica=label("Toque numa função para enviar. Se o seu modelo não responder, use Testar e aprender códigos.",12);
        dica.setTextColor(GRAY); dica.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2); dp1.setMargins(dp(6),dp(10),dp(6),dp(4));
        root.addView(dica,dp1);

        LinearLayout acoes=row();
        acoes.addView(tile(R.drawable.ic_search,"Testar e aprender códigos",58,ACCENT,v->showUniversalScanner("Ventilador","Universal","FAN")));
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2); alp.setMargins(0,dp(10),0,0);
        root.addView(acoes,alp);
        LinearLayout acoes2=row();
        acoes2.addView(tile(R.drawable.ic_star,"Meus controles",54,KEY_DARK,v->showMeusControles()));
        acoes2.addView(tile(R.drawable.ic_back,"Voltar",54,KEY_DARK,v->showSelector()));
        root.addView(acoes2);

        TextView aviso=label("Ventiladores variam entre marcas e modelos. Se uma função não responder, não significa defeito no emissor do celular.",11);
        aviso.setTextColor(Color.rgb(190,170,110)); aviso.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(-1,-2); avp.setMargins(dp(6),dp(12),dp(6),0);
        root.addView(aviso,avp);

        mostrar(telaRolavel(root));
    }

    private void showSobre(){
        String versao="?";
        try{
            android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
            if(info.versionName!=null) versao=info.versionName;
        }catch(Exception ignored){}
        new android.app.AlertDialog.Builder(this)
            .setTitle("IR Remote BR")
            .setMessage("Controle remoto por infravermelho\n\nVersão "+versao+"\n\nControle TVs compatíveis usando o emissor infravermelho do celular.\n\nSeus controles e configurações são armazenados localmente no aparelho.\n\nPara transmitir IR, o celular precisa possuir emissor infravermelho compatível.")
            .setPositiveButton("OK",null).show();
    }

    private android.app.AlertDialog.Builder wizardDialog(String titulo,String mensagem){
        return new android.app.AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage(mensagem);
    }

    private void showAddControlWizard(){
        wizardDialog("ADICIONAR CONTROLE","PASSO 1 → MARCA\nPASSO 2 → MODELO\nPASSO 3 → TESTE IR\n\nO controle só será salvo depois que você confirmar que a TV respondeu. Seus controles existentes não serão alterados.")
            .setPositiveButton("COMEÇAR",(d,w)->showBrandWizard())
            .setNegativeButton("CANCELAR",(d,w)->showMeusControles()).show();
    }

    private void showBrandWizard(){
        final String[] marcas={"Philips","LG","Samsung","Sony","Panasonic","AOC","TCL","Philco","Semp"};
        escolher("PASSO 1 DE 3 • MARCA","Escolha a marca da TV. O aplicativo usará automaticamente o perfil IR mais provável.",
            marcas,i->showModelWizard(marcas[i]),()->showMeusControles());
    }

    private void showModelWizard(String marca){
        final String[] modelos;
        if("Philips".equals(marca)) modelos=new String[]{"50PUG6513/7","Outro modelo"};
        else if("LG".equals(marca)) modelos=new String[]{"32LB620B","Outro modelo"};
        else modelos=new String[]{"Smart TV","Outro modelo"};
        escolher("PASSO 2 DE 3 • MODELO","Marca selecionada: "+marca+"\n\nEscolha um modelo conhecido ou informe o modelo manualmente.",modelos,w->{
            if(w==modelos.length-1){
                final EditText input=new EditText(this);
                input.setSingleLine(true); input.setHint("Ex.: 50PUG6513/7");
                input.setTextColor(WHITE); input.setHintTextColor(GRAY); input.setTextSize(16); input.setPadding(dp(12),0,dp(12),0);
                GradientDrawable inputBg=new GradientDrawable(); inputBg.setColor(CARD); inputBg.setCornerRadius(dp(12)); inputBg.setStroke(dp(1),BORDER);
                input.setBackground(inputBg);
                android.widget.FrameLayout box=new android.widget.FrameLayout(this);
                box.setPadding(dp(22),dp(4),dp(22),0);
                box.addView(input,new android.widget.FrameLayout.LayoutParams(-1,dp(52)));
                new android.app.AlertDialog.Builder(this)
                    .setTitle("MODELO • "+marca)
                    .setMessage("Digite o modelo da TV para identificar melhor os códigos.")
                    .setView(box)
                    .setNegativeButton("VOLTAR",(x,y)->showModelWizard(marca))
                    .setPositiveButton("CONTINUAR",(x,y)->{
                        String modelo=input.getText().toString().trim();
                        if(modelo.isEmpty()) modelo="Modelo não informado";
                        prepararNovoControle(marca,modelo);
                    }).show();
            } else {
                prepararNovoControle(marca,modelos[w]);
            }
        },()->showBrandWizard());
    }

    private void prepararNovoControle(String marca,String modelo){
        lgMode="LG".equals(marca);
        controleAtivo=null;
        prefs.edit().remove("active_control_id").putBoolean("lg_mode",lgMode).apply();
        showTvSetup(marca,modelo);
    }

    private void showTvSetup(String marca,String modelo){
        wizardDialog("3 de 3 • Teste IR","TV: "+marca+" "+modelo+"\n\n1. Aponte o celular para a TV.\n2. Toque em TESTAR PRÓXIMO.\n3. Quando a TV responder, toque em FUNCIONOU / SALVAR.\n\nO primeiro código confirmado será usado como base do controle.")
            .setNegativeButton("CANCELAR",(d,w)->showMeusControles())
            .setPositiveButton("INICIAR TESTE",(d,w)->showUniversalScanner(marca,modelo,"TV")).show();
    }

    // ===== Componentes visuais v2: ícones vetoriais, botões e coluna central =====

    private RippleDrawable ripple(int cor,boolean oval,boolean borda){
        GradientDrawable g=new GradientDrawable();
        g.setShape(oval?GradientDrawable.OVAL:GradientDrawable.RECTANGLE);
        g.setColor(cor);
        if(!oval) g.setCornerRadius(dp(16));
        if(borda) g.setStroke(dp(1),BORDER);
        GradientDrawable mask=new GradientDrawable();
        mask.setShape(oval?GradientDrawable.OVAL:GradientDrawable.RECTANGLE);
        mask.setColor(Color.WHITE);
        if(!oval) mask.setCornerRadius(dp(16));
        return new RippleDrawable(ColorStateList.valueOf(Color.rgb(95,95,102)),g,mask);
    }

    private void pintar(View v,int cor){ v.setBackground(ripple(cor,false,true)); }

    private void haptic(View v){ v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); }

    private ImageView icone(int res,int dpSize,int cor){
        ImageView iv=new ImageView(this);
        iv.setImageResource(res);
        iv.setColorFilter(cor);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(dpSize),dp(dpSize)));
        iv.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return iv;
    }

    /** Botão só com ícone (redondo ou arredondado). viewDp = tamanho do botão; iconDp = tamanho do ícone. */
    private ImageButton iconBtn(int res,int viewDp,int iconDp,int cor,boolean oval,String desc,View.OnClickListener acao){
        ImageButton b=new ImageButton(this);
        b.setImageResource(res);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int p=dp((viewDp-iconDp)/2f);
        b.setPadding(p,p,p,p);
        b.setBackground(ripple(cor,oval,cor!=Color.TRANSPARENT));
        b.setContentDescription(desc);
        b.setHapticFeedbackEnabled(true);
        b.setOnClickListener(v->{ haptic(v); if(acao!=null) acao.onClick(v); });
        return b;
    }

    /** Botão com ícone em cima e texto embaixo (texto vazio = só ícone). Ocupa uma fatia da linha. */
    private LinearLayout tile(int icon,String texto,int hDp,int cor,View.OnClickListener acao){ return tile(icon,texto,hDp,cor,acao,12); }

    private LinearLayout tile(int icon,String texto,int hDp,int cor,View.OnClickListener acao,int sp){
        LinearLayout t=new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL); t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4),dp(4),dp(4),dp(4));
        t.setBackground(ripple(cor,false,true));
        t.setClickable(true); t.setFocusable(true);
        if(texto!=null && !texto.isEmpty()) t.setContentDescription(texto);
        if(icon!=0) t.addView(icone(icon,hDp>=72?26:22,WHITE));
        if(texto!=null && !texto.isEmpty()){
            TextView tv=new TextView(this);
            tv.setText(texto); tv.setTextColor(WHITE); tv.setTextSize(sp); tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            tv.setGravity(Gravity.CENTER); tv.setSingleLine(true); tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setIncludeFontPadding(false);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
            lp.setMargins(0,icon!=0?dp(4):0,0,0);
            t.addView(tv,lp);
        }
        t.setOnClickListener(v->{ haptic(v); if(acao!=null) acao.onClick(v); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(hDp),1);
        p.setMargins(dp(3),dp(3),dp(3),dp(3));
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout cmdTile(int icon,String texto,int cmd,int hDp){
        return tile(icon,texto,hDp,KEY,v->send(cmd));
    }

    /** Botão pequeno (ícone + texto na mesma linha) para as ações do cabeçalho. */
    private LinearLayout chip(int icon,String texto,View.OnClickListener acao){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER);
        c.setPadding(dp(8),0,dp(8),0);
        c.setBackground(ripple(CARD_2,false,true));
        c.setClickable(true); c.setFocusable(true);
        c.setContentDescription(texto);
        c.addView(icone(icon,16,WHITE));
        TextView tv=new TextView(this);
        tv.setText(texto); tv.setTextColor(WHITE); tv.setTextSize(12); tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END); tv.setIncludeFontPadding(false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2); lp.setMargins(dp(6),0,0,0);
        c.addView(tv,lp);
        c.setOnClickListener(v->{ haptic(v); if(acao!=null) acao.onClick(v); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);
        p.setMargins(dp(3),0,dp(3),0);
        c.setLayoutParams(p);
        return c;
    }

    /** Coluna central com largura máxima de 420 dp (em telas largas o controle não estica). */
    private ScrollView telaRolavel(LinearLayout root){
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG); sv.setClipToPadding(false);
        FrameLayout f=new FrameLayout(this);
        int largura=Math.min(getResources().getDisplayMetrics().widthPixels,dp(420));
        f.addView(root,new FrameLayout.LayoutParams(largura,-2,Gravity.CENTER_HORIZONTAL));
        sv.addView(f);
        return sv;
    }

    /** Botão largo (ícone + texto na mesma linha), ocupa a largura toda. */
    private LinearLayout botaoLargo(int icon,String texto,int hDp,int cor,View.OnClickListener acao){
        LinearLayout b=new LinearLayout(this); b.setOrientation(LinearLayout.HORIZONTAL); b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14),0,dp(14),0);
        b.setBackground(ripple(cor,false,cor!=ACCENT));
        b.setClickable(true); b.setFocusable(true); b.setContentDescription(texto);
        if(icon!=0){
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(20),dp(20)); ip.setMargins(0,0,dp(10),0);
            b.addView(icone(icon,20,WHITE),ip);
        }
        TextView tv=new TextView(this); tv.setText(texto); tv.setTextColor(WHITE); tv.setTextSize(15);
        tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD); tv.setSingleLine(true); tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setIncludeFontPadding(false);
        b.addView(tv,new LinearLayout.LayoutParams(-2,-2));
        b.setOnClickListener(v->{ haptic(v); if(acao!=null) acao.onClick(v); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(hDp)); p.setMargins(0,dp(5),0,dp(5));
        b.setLayoutParams(p);
        return b;
    }

    /** Cartão em linha: ícone, título, detalhe e seta. */
    private LinearLayout cartaoLinha(int icon,String titulo,String detalhe,int cor,View.OnClickListener acao){
        LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.HORIZONTAL); c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(14),dp(12),dp(10),dp(12));
        c.setBackground(ripple(cor,false,true));
        c.setClickable(true); c.setFocusable(true); c.setContentDescription(titulo+". "+detalhe);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(22),dp(22)); ip.setMargins(0,0,dp(12),0);
        c.addView(icone(icon,22,WHITE),ip);
        LinearLayout t=new LinearLayout(this); t.setOrientation(LinearLayout.VERTICAL);
        t.addView(textoEsq(titulo,15,WHITE,true),new LinearLayout.LayoutParams(-1,-2));
        TextView d=label(detalhe,12); d.setTextColor(GRAY); d.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2); dlp.setMargins(0,dp(2),0,0);
        t.addView(d,dlp);
        c.addView(t,new LinearLayout.LayoutParams(0,-2,1));
        c.addView(icone(R.drawable.ic_right,20,GRAY),new LinearLayout.LayoutParams(dp(20),dp(20)));
        c.setOnClickListener(v->{ haptic(v); if(acao!=null) acao.onClick(v); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(5),0,dp(5));
        c.setLayoutParams(p);
        return c;
    }

    private TextView subtitulo(String s){
        TextView t=label(s,13); t.setTextColor(GRAY); t.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView textoEsq(String s,int sp,int cor,boolean negrito){
        TextView t=label(s,sp); t.setTextColor(cor); t.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        if(negrito) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setSingleLine(true); t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private LinearLayout.LayoutParams lpChip(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
        p.setMargins(dp(3),dp(3),dp(3),dp(3)); return p;
    }

    private int modoIcone(String value){
        if(value==null) return 0;
        String v=value.trim().toLowerCase(java.util.Locale.ROOT);
        if(v.equals("cool")) return R.drawable.ic_snow;
        if(v.equals("heat")) return R.drawable.ic_sun;
        if(v.equals("dry")) return R.drawable.ic_drop;
        if(v.equals("fan_only")||v.equals("fan")) return R.drawable.ic_air;
        if(v.equals("auto")||v.equals("heat_cool")) return R.drawable.ic_auto;
        return 0;
    }

    private int corModo(String value){
        String v=value==null?"":value.trim().toLowerCase(java.util.Locale.ROOT);
        if(v.equals("cool")) return Color.rgb(36,98,168);
        if(v.equals("heat")) return Color.rgb(184,92,28);
        if(v.equals("dry")) return Color.rgb(36,124,118);
        if(v.equals("fan_only")||v.equals("fan")) return Color.rgb(78,88,108);
        if(v.equals("auto")||v.equals("heat_cool")) return Color.rgb(96,80,150);
        return Color.rgb(65,85,105);
    }

    private int tvTab=0;

    private void build(){
        fanMode=false;
        if(controleAtivo!=null && "Ventilador Universal".equals(controleAtivo.perfil)){ showFanRemote(); return; }
        if(controleAtivo!=null && "AC SmartIR".equals(controleAtivo.perfil)){ abrirSmartIrSalvo(controleAtivo); return; }

        final boolean temEmissor=ir!=null&&ir.hasIrEmitter();
        final String nome=controleAtivo!=null ? controleAtivo.nome : (lgMode?"LG 32LB620B":"Philips 50PUG6513/7");
        final String perfil=controleAtivo!=null ? (controleAtivo.perfil==null?"":controleAtivo.perfil) : (lgMode?"LG / NEC":"Philips / RC6");
        final int hz=controleAtivo!=null ? controleAtivo.frequencia : (lgMode?38000:36000);
        final int botoes=controleAtivo!=null ? controleStorage.quantidadeComandos(controleAtivo) : 0;
        final int salvos=controleStorage.listar().size();

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(12),dp(14),dp(24));

        // Cabeçalho único: nome, detalhes, status do emissor e liga/desliga.
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info=new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL);
        info.addView(textoEsq(nome,17,WHITE,true),new LinearLayout.LayoutParams(-1,-2));
        String det=controleAtivo!=null ? controleAtivo.marca+" · "+perfil+" · "+botoes+" botões configurados" : perfil+" · controle padrão";
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,-2); dlp.setMargins(0,dp(2),0,0);
        info.addView(textoEsq(det,12,GRAY,false),dlp);
        String st=temEmissor ? "● Emissor IR detectado · "+(hz/1000)+" kHz" : "○ Emissor IR não detectado";
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2); slp.setMargins(0,dp(3),0,0);
        info.addView(textoEsq(st,11,temEmissor?SUCCESS:GRAY,false),slp);
        head.addView(info,new LinearLayout.LayoutParams(0,-2,1));

        Button power=new Button(this); power.setText("");
        power.setBackgroundResource(R.drawable.power_button); power.setPadding(0,0,0,0);
        power.setContentDescription("Ligar ou desligar a TV");
        power.setHapticFeedbackEnabled(true);
        power.setOnClickListener(v->{ haptic(v); send(POWER); });
        LinearLayout.LayoutParams pwp=new LinearLayout.LayoutParams(dp(60),dp(60)); pwp.setMargins(dp(10),0,0,0);
        head.addView(power,pwp);
        root.addView(head);

        // Ações: trocar, editar (controle salvo) e meus controles.
        LinearLayout chips=new LinearLayout(this); chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(R.drawable.ic_swap,"Trocar",v->showSelector()));
        if(controleAtivo!=null) chips.addView(chip(R.drawable.ic_edit,"Editar",v->showAprenderComandos(controleAtivo)));
        chips.addView(chip(R.drawable.ic_star,"Meus ("+salvos+")",v->showMeusControles()));
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,-2); clp.setMargins(0,dp(10),0,0);
        root.addView(chips,clp);

        if(!temEmissor){
            TextView aviso=label("Este celular não tem emissor infravermelho. Os botões não vão enviar nada.",12);
            aviso.setTextColor(Color.rgb(230,190,120)); aviso.setPadding(dp(12),dp(8),dp(12),dp(8));
            GradientDrawable ab=new GradientDrawable(); ab.setColor(Color.rgb(48,40,24)); ab.setCornerRadius(dp(12)); aviso.setBackground(ab);
            LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,-2); alp.setMargins(dp(3),dp(10),dp(3),0);
            root.addView(aviso,alp);
        }

        // Abas: principal, números e mídia.
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setPadding(dp(3),dp(3),dp(3),dp(3));
        GradientDrawable tabsBg=new GradientDrawable(); tabsBg.setColor(CARD); tabsBg.setCornerRadius(dp(16)); tabsBg.setStroke(dp(1),BORDER);
        tabs.setBackground(tabsBg);
        String[] nomesAbas={"Principal","Números","Mídia"};
        for(int i=0;i<nomesAbas.length;i++){
            final int idx=i;
            TextView t=label(nomesAbas[i],13); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            t.setTextColor(i==tvTab?WHITE:GRAY);
            t.setBackground(ripple(i==tvTab?KEY:Color.TRANSPARENT,false,false));
            t.setClickable(true); t.setFocusable(true); t.setContentDescription(nomesAbas[i]);
            t.setOnClickListener(v->{ haptic(v); tvTab=idx; build(); });
            tabs.addView(t,new LinearLayout.LayoutParams(0,dp(42),1));
        }
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2); tlp.setMargins(dp(3),dp(12),dp(3),dp(4));
        root.addView(tabs,tlp);

        if(tvTab==1) painelTvNumeros(root);
        else if(tvTab==2) painelTvMidia(root);
        else painelTvPrincipal(root);

        mostrar(telaRolavel(root));
    }

    private void painelTvPrincipal(LinearLayout root){
        LinearLayout r=row();
        r.addView(cmdTile(R.drawable.ic_tv,"Fonte",SOURCE,56));
        r.addView(cmdTile(R.drawable.ic_list,"Guia",GUIDE,56));
        r.addView(cmdTile(R.drawable.ic_home,"Home",HOME,56));
        r.addView(cmdTile(R.drawable.ic_settings,"Ajustes",SETTINGS,56));
        root.addView(r);

        // D-pad circular com OK no centro.
        FrameLayout pad=new FrameLayout(this);
        GradientDrawable padBg=new GradientDrawable(); padBg.setShape(GradientDrawable.OVAL);
        padBg.setColor(Color.rgb(26,26,28)); padBg.setStroke(dp(1),Color.rgb(55,55,58));
        pad.setBackground(padBg);
        int tam=dp(58), m=dp(6);
        FrameLayout.LayoutParams up=new FrameLayout.LayoutParams(tam,tam,Gravity.TOP|Gravity.CENTER_HORIZONTAL); up.setMargins(0,m,0,0);
        FrameLayout.LayoutParams dn=new FrameLayout.LayoutParams(tam,tam,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL); dn.setMargins(0,0,0,m);
        FrameLayout.LayoutParams lf=new FrameLayout.LayoutParams(tam,tam,Gravity.LEFT|Gravity.CENTER_VERTICAL); lf.setMargins(m,0,0,0);
        FrameLayout.LayoutParams rt=new FrameLayout.LayoutParams(tam,tam,Gravity.RIGHT|Gravity.CENTER_VERTICAL); rt.setMargins(0,0,m,0);
        pad.addView(iconBtn(R.drawable.ic_up,58,44,Color.TRANSPARENT,true,"Cima",v->send(UP)),up);
        pad.addView(iconBtn(R.drawable.ic_down,58,44,Color.TRANSPARENT,true,"Baixo",v->send(DOWN)),dn);
        pad.addView(iconBtn(R.drawable.ic_left,58,44,Color.TRANSPARENT,true,"Esquerda",v->send(LEFT)),lf);
        pad.addView(iconBtn(R.drawable.ic_right,58,44,Color.TRANSPARENT,true,"Direita",v->send(RIGHT)),rt);
        Button ok=new Button(this); ok.setText("OK"); ok.setTextColor(WHITE); ok.setTextSize(18); ok.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        ok.setAllCaps(false); ok.setMinHeight(0); ok.setMinWidth(0); ok.setPadding(0,0,0,0); ok.setIncludeFontPadding(false);
        ok.setBackground(ripple(Color.rgb(60,61,70),true,false)); ok.setHapticFeedbackEnabled(true);
        ok.setOnClickListener(v->{ haptic(v); send(OK); });
        pad.addView(ok,new FrameLayout.LayoutParams(dp(86),dp(86),Gravity.CENTER));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(210),dp(210)); pp.gravity=Gravity.CENTER_HORIZONTAL; pp.setMargins(0,dp(14),0,dp(10));
        root.addView(pad,pp);

        r=row();
        r.addView(cmdTile(R.drawable.ic_back,"Voltar",BACK,54));
        r.addView(cmdTile(R.drawable.ic_menu,"Menu",MENU,54));
        r.addView(cmdTile(R.drawable.ic_exit,"Sair",EXIT,54));
        root.addView(r);

        // Volume e canais em bastões, com o mudo no meio.
        LinearLayout vc=new LinearLayout(this); vc.setOrientation(LinearLayout.HORIZONTAL); vc.setGravity(Gravity.CENTER);
        vc.addView(bastao("Vol",VOL_UP,VOL_DOWN,"Volume mais","Volume menos"));
        LinearLayout meio=new LinearLayout(this); meio.setOrientation(LinearLayout.VERTICAL); meio.setGravity(Gravity.CENTER);
        meio.addView(iconBtn(R.drawable.ic_volume_off,56,26,KEY,true,"Mudo",v->send(MUTE)),new LinearLayout.LayoutParams(dp(56),dp(56)));
        TextView mudo=label("Mudo",11); mudo.setTextColor(GRAY);
        LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-2,-2); mlp.setMargins(0,dp(4),0,0);
        meio.addView(mudo,mlp);
        LinearLayout.LayoutParams melp=new LinearLayout.LayoutParams(-2,-2); melp.setMargins(dp(22),0,dp(22),0);
        vc.addView(meio,melp);
        vc.addView(bastao("Canal",CH_UP,CH_DOWN,"Canal mais","Canal menos"));
        LinearLayout.LayoutParams vclp=new LinearLayout.LayoutParams(-1,-2); vclp.setMargins(0,dp(14),0,dp(4));
        root.addView(vc,vclp);

        // Teclas coloridas.
        LinearLayout cores=new LinearLayout(this); cores.setOrientation(LinearLayout.HORIZONTAL); cores.setGravity(Gravity.CENTER);
        int[] corv={Color.rgb(170,28,28),Color.rgb(24,130,56),Color.rgb(190,150,12),Color.rgb(30,92,175)};
        int[] cmdc={RED,GREEN,YELLOW,BLUE};
        String[] nomec={"Vermelho","Verde","Amarelo","Azul"};
        for(int i=0;i<4;i++){
            final int cmd=cmdc[i];
            Button b=new Button(this); b.setText(""); b.setContentDescription(nomec[i]);
            b.setBackground(ripple(corv[i],true,false)); b.setHapticFeedbackEnabled(true);
            b.setOnClickListener(v->{ haptic(v); send(cmd); });
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(44),dp(44)); bp.setMargins(dp(10),0,dp(10),0);
            cores.addView(b,bp);
        }
        LinearLayout.LayoutParams colp=new LinearLayout.LayoutParams(-1,-2); colp.setMargins(0,dp(14),0,0);
        root.addView(cores,colp);
    }

    /** Bastão vertical (+ em cima, rótulo no meio, − embaixo) para volume e canais. */
    private LinearLayout bastao(String rotulo,int cmdMais,int cmdMenos,String descMais,String descMenos){
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setGravity(Gravity.CENTER);
        GradientDrawable g=new GradientDrawable(); g.setColor(KEY); g.setCornerRadius(dp(34)); g.setStroke(dp(1),BORDER);
        p.setBackground(g);
        p.addView(iconBtn(R.drawable.ic_plus,64,28,Color.TRANSPARENT,true,descMais,v->send(cmdMais)),new LinearLayout.LayoutParams(dp(64),dp(56)));
        TextView l=label(rotulo,12); l.setTextColor(GRAY);
        p.addView(l,new LinearLayout.LayoutParams(-2,dp(22)));
        p.addView(iconBtn(R.drawable.ic_minus,64,28,Color.TRANSPARENT,true,descMenos,v->send(cmdMenos)),new LinearLayout.LayoutParams(dp(64),dp(56)));
        p.setLayoutParams(new LinearLayout.LayoutParams(dp(64),dp(136)));
        return p;
    }

    private void painelTvNumeros(LinearLayout root){
        String[][] nums={{"1","2","3"},{"4","5","6"},{"7","8","9"}};
        String[][] letras={{"","ABC","DEF"},{"GHI","JKL","MNO"},{"PQRS","TUV","WXYZ"}};
        LinearLayout.LayoutParams topo=new LinearLayout.LayoutParams(-1,-2); topo.setMargins(0,dp(8),0,0);
        for(int i=0;i<3;i++){
            LinearLayout r=row();
            for(int j=0;j<3;j++){
                Button b=key(nums[i][j],i*3+j+1,64,KEY_DARK,22);
                String d=nums[i][j], l=letras[i][j];
                if(!l.isEmpty()){
                    android.text.SpannableString sp=new android.text.SpannableString(d+"\n"+l);
                    sp.setSpan(new android.text.style.RelativeSizeSpan(.5f),d.length()+1,sp.length(),0);
                    b.setText(sp);
                }
                r.addView(b);
            }
            if(i==0) root.addView(r,topo); else root.addView(r);
        }
        LinearLayout r=row();
        r.addView(key("CC",CC,64,KEY_DARK,16));
        r.addView(key("0",0,64,KEY_DARK,22));
        r.addView(key("Legenda",SUBTITLE,64,KEY_DARK,14));
        root.addView(r);
    }

    private void painelTvMidia(LinearLayout root){
        LinearLayout.LayoutParams topo=new LinearLayout.LayoutParams(-1,-2); topo.setMargins(0,dp(8),0,0);
        LinearLayout r=row();
        r.addView(cmdTile(R.drawable.ic_rewind,"",REWIND,60));
        r.addView(cmdTile(R.drawable.ic_play,"",PLAY,60));
        r.addView(cmdTile(R.drawable.ic_pause,"",PAUSE,60));
        r.addView(cmdTile(R.drawable.ic_stop,"",STOP,60));
        r.addView(cmdTile(R.drawable.ic_forward,"",FAST_FORWARD,60));
        root.addView(r,topo);
        String[] desc={"Retroceder","Reproduzir","Pausar","Parar","Avançar"};
        for(int i=0;i<r.getChildCount();i++) r.getChildAt(i).setContentDescription(desc[i]);

        LinearLayout r2=row();
        r2.addView(cmdTile(R.drawable.ic_info,"Info",INFO,60));
        r2.addView(cmdTile(R.drawable.ic_tv,lgMode?"Smart":"Netflix",NETFLIX,60));
        LinearLayout.LayoutParams l2=new LinearLayout.LayoutParams(-1,-2); l2.setMargins(0,dp(8),0,0);
        root.addView(r2,l2);
    }

    private boolean enviarComandoSalvo(String funcao){
        if(controleAtivo==null || funcao.isEmpty()) return false;
        int codigo=controleStorage.codigoComando(controleAtivo,funcao);
        String perfil=controleStorage.perfilComando(controleAtivo,funcao);
        if(codigo<0 || perfil.isEmpty()) return false;
        return irPerfilTeste.transmitirSalvo(perfil,codigo,controleStorage.frequenciaComando(controleAtivo,funcao));
    }

    /** Tecla n (1..5) do ventilador: usa o código aprendido para a função, ou o código padrão n. */
    private void enviarVentilador(int n){
        if(!irPerfilTeste.hasEmitter()){
            Toast.makeText(this,"Este celular não possui emissor IR.",Toast.LENGTH_SHORT).show(); return;
        }
        int codigo=n;
        if(controleAtivo!=null && IrPerfilTeste.PERFIL_VENTILADOR.equals(controleAtivo.perfil)){
            int salvo=controleStorage.codigoComando(controleAtivo,RemoteKeys.FAN_CHAVES[n-1]);
            if(salvo>=1 && salvo<=5) codigo=salvo;
        }
        if(!irPerfilTeste.transmitirVentilador(codigo))
            Toast.makeText(this,"Falha ao enviar IR.",Toast.LENGTH_SHORT).show();
    }

    private void send(int command){
        if(fanMode && command>=1 && command<=5){ enviarVentilador(command); return; }
        if(!irPerfilTeste.hasEmitter()){
            Toast.makeText(this,"Este celular não possui emissor IR.",Toast.LENGTH_SHORT).show(); return;
        }
        if(enviarComandoSalvo(funcao(command))) return;
        if(command==POWER && controleAtivo!=null && controleAtivo.codigo>=0){
            if(!irPerfilTeste.transmitirSalvo(controleAtivo.perfil,controleAtivo.codigo,controleAtivo.frequencia))
                Toast.makeText(this,"Não foi possível enviar o código salvo",Toast.LENGTH_SHORT).show();
            return;
        }
        // Tabelas embutidas só valem para LG/NEC e Philips/RC6; em outros perfis o botão precisa ser aprendido.
        String perfil=controleAtivo!=null?(controleAtivo.perfil==null?"":controleAtivo.perfil):(lgMode?"LG / NEC":"Philips / RC6");
        boolean ok;
        if("LG / NEC".equals(perfil)) ok=irPerfilTeste.transmitirSalvo(perfil,(0x04<<8)|lgCode(command),0);
        else if("Philips / RC6".equals(perfil)) ok=irPerfilTeste.transmitirSalvo(perfil,command&0xFF,0);
        else { Toast.makeText(this,"Este botão ainda não foi configurado. Toque em EDITAR para aprendê-lo.",Toast.LENGTH_SHORT).show(); return; }
        if(!ok) Toast.makeText(this,"Falha ao enviar IR.",Toast.LENGTH_SHORT).show();
    }
}