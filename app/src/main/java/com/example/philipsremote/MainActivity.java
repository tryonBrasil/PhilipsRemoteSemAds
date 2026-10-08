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
    private UpdateManager updateManager;
    private MonetizationManager monetizacao;
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
        updateManager=new UpdateManager(this);
        monetizacao=new MonetizationManager(this,()->showSelector());
        if(!prefs.getBoolean("initial_screen_seen",false)) showInitialScreen();
        else showSelector();
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
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24),dp(42),dp(24),dp(30));

        Space top=new Space(this);
        root.addView(top,new LinearLayout.LayoutParams(1,dp(28)));

        TextView logo=label("IR",56);
        logo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        logo.setTextColor(WHITE);
        GradientDrawable logoBg=new GradientDrawable();
        logoBg.setColor(ACCENT);
        logoBg.setCornerRadius(dp(28));
        logo.setBackground(logoBg);
        logo.setGravity(Gravity.CENTER);
        root.addView(logo,new LinearLayout.LayoutParams(dp(112),dp(112)));

        TextView brand=label("IR REMOTE BR",18);
        brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        brand.setLetterSpacing(.12f);
        brand.setTextColor(ACCENT);
        LinearLayout.LayoutParams brandP=new LinearLayout.LayoutParams(-1,dp(34));
        brandP.setMargins(0,dp(22),0,0);
        root.addView(brand,brandP);

        TextView title=label("Controle seus aparelhos\nde um jeito simples",27);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(-1,dp(78));
        titleP.setMargins(0,dp(8),0,0);
        root.addView(title,titleP);

        TextView sub=label("Comece agora. Não é necessário criar uma conta para usar os recursos gratuitos.",14);
        sub.setTextColor(GRAY);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subP=new LinearLayout.LayoutParams(-1,dp(60));
        subP.setMargins(0,dp(4),0,dp(22));
        root.addView(sub,subP);

        Button comecar=botaoAcao("COMEÇAR",Color.rgb(190,24,32),18);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(58));
        cp.setMargins(0,dp(4),0,dp(10));
        root.addView(comecar,cp);
        comecar.setOnClickListener(v->{
            prefs.edit().putBoolean("initial_screen_seen",true).apply();
            showSelector();
        });

        Button conta=new Button(this);
        conta.setText("ENTRAR / CONTA PREMIUM");
        conta.setTextColor(WHITE);
        conta.setTextSize(14);
        conta.setAllCaps(false);
        GradientDrawable contaBg=new GradientDrawable();
        contaBg.setColor(KEY_DARK);
        contaBg.setCornerRadius(dp(16));
        contaBg.setStroke(dp(1),BORDER);
        conta.setBackground(contaBg);
        actionFeedback(conta);
        conta.setOnClickListener(v->showPremiumAccountScreen());
        root.addView(conta,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView note=label("⭐ O Premium é vinculado à sua compra no Google Play.",12);
        note.setTextColor(GRAY);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(48));
        np.setMargins(0,dp(16),0,dp(0));
        root.addView(note,np);

        TextView version=label("",11);
        version.setTextColor(GRAY);
        try{
            version.setText("Versão "+getPackageManager().getPackageInfo(getPackageName(),0).versionName);
        }catch(Exception ignored){}
        root.addView(version,new LinearLayout.LayoutParams(-1,dp(28)));

        sv.addView(root);
        mostrar(sv);
    }

    private void showPremiumAccountScreen(){
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20),dp(28),dp(20),dp(30));

        TextView title=label("CONTA E PREMIUM",26);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView info=label("A compra do Premium é associada à conta do Google Play usada na compra.\n\nVocê não precisa criar uma senha separada para usar o aplicativo.",14);
        info.setTextColor(GRAY);
        info.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(92));
        ip.setMargins(0,dp(8),0,dp(18));
        root.addView(info,ip);

        TextView status=label(monetizacao.isPremium() ? "✓ PREMIUM ATIVO" : "○ PREMIUM NÃO ATIVO",16);
        status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        status.setTextColor(monetizacao.isPremium()?SUCCESS:WHITE);
        GradientDrawable statusBg=new GradientDrawable();
        statusBg.setColor(monetizacao.isPremium()?Color.rgb(38,70,48):CARD);
        statusBg.setCornerRadius(dp(16));
        statusBg.setStroke(dp(1),BORDER);
        status.setBackground(statusBg);
        status.setGravity(Gravity.CENTER);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(54)));

        Button restaurar=botaoAcao("RESTAURAR / VERIFICAR PREMIUM",Color.rgb(55,55,62),14);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));
        rp.setMargins(0,dp(12),0,dp(8));
        root.addView(restaurar,rp);
        restaurar.setOnClickListener(v->{
            monetizacao.restaurarCompra();
            v.postDelayed(()->showPremiumAccountScreen(),1200L);
        });

        Button premium=botaoAcao(monetizacao.isPremium()?"PREMIUM ATIVO":"CONHECER O PREMIUM",Color.rgb(70,55,20),14);
        root.addView(premium,new LinearLayout.LayoutParams(-1,dp(52)));
        premium.setOnClickListener(v->monetizacao.showPremiumDialog());

        Button voltar=botaoAcao("VOLTAR",KEY_DARK,14);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(48));
        vp.setMargins(0,dp(18),0,dp(0));
        root.addView(voltar,vp);
        voltar.setOnClickListener(v->showInitialScreen());

        sv.addView(root);
        mostrar(sv);
    }

    private void showSelector(){
        showingSelector=true; fanMode=false;
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(dp(18),dp(28),dp(18),dp(30));
        TextView brand=label("IR REMOTE BR",13); brand.setTextColor(ACCENT);
        brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD); brand.setLetterSpacing(.14f);
        root.addView(brand,new LinearLayout.LayoutParams(-1,dp(24)));
        TextView title=label("Escolha seu controle",28); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        Button inicio=new Button(this);
        inicio.setText("⌂  TELA INICIAL / CONTA");
        inicio.setTextColor(WHITE);
        inicio.setTextSize(13);
        inicio.setAllCaps(false);
        GradientDrawable inicioBg=new GradientDrawable();
        inicioBg.setColor(KEY_DARK);
        inicioBg.setCornerRadius(dp(16));
        inicioBg.setStroke(dp(1),BORDER);
        inicio.setBackground(inicioBg);
        actionFeedback(inicio);
        inicio.setOnClickListener(v->showInitialScreen());
        LinearLayout.LayoutParams inicioP=new LinearLayout.LayoutParams(-1,dp(48));
        inicioP.setMargins(0,dp(8),0,dp(6));
        root.addView(inicio,inicioP);

        TextView sub=label("Infravermelho • rápido • sem anúncios",14); sub.setTextColor(GRAY);
        sub.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        root.addView(sub,new LinearLayout.LayoutParams(-1,dp(30)));
        List<ControleStorage.Controle> salvosHome=controleStorage.listar();
        boolean temControlesSalvos=!salvosHome.isEmpty();
        Button meusControlesHome=new Button(this);
        meusControlesHome.setText("★  MEUS CONTROLES  •  "+salvosHome.size());
        meusControlesHome.setTextColor(WHITE);
        meusControlesHome.setTextSize(15);
        meusControlesHome.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        meusControlesHome.setAllCaps(false);
        GradientDrawable meusHomeBg=new GradientDrawable();
        meusHomeBg.setColor(salvosHome.isEmpty()?Color.rgb(45,45,50):Color.rgb(55,75,60));
        meusHomeBg.setCornerRadius(dp(17));
        meusHomeBg.setStroke(dp(1),salvosHome.isEmpty()?BORDER:Color.rgb(85,145,95));
        meusControlesHome.setBackground(meusHomeBg);
        actionFeedback(meusControlesHome);
        meusControlesHome.setOnClickListener(v->showMeusControles());
        LinearLayout.LayoutParams meusHomeP=new LinearLayout.LayoutParams(-1,dp(58));
        meusHomeP.setMargins(0,dp(10),0,dp(6));
        root.addView(meusControlesHome,meusHomeP);

        LinearLayout bancoCard=new LinearLayout(this); bancoCard.setOrientation(LinearLayout.HORIZONTAL); bancoCard.setGravity(Gravity.CENTER_VERTICAL);
        bancoCard.setPadding(dp(14),0,dp(14),0);
        GradientDrawable bancoBg=new GradientDrawable(); bancoBg.setColor(CARD); bancoBg.setCornerRadius(dp(15)); bancoBg.setStroke(dp(1),Color.rgb(55,55,60)); bancoCard.setBackground(bancoBg);
        TextView bancoTitulo=label("●  BANCO LOCAL",12); bancoTitulo.setTextColor(Color.rgb(105,190,125)); bancoTitulo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        bancoCard.addView(bancoTitulo,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView bancoQtd=label(salvosHome.size()+" "+(salvosHome.size()==1?"controle":"controles"),12); bancoQtd.setTextColor(GRAY);
        bancoCard.addView(bancoQtd,new LinearLayout.LayoutParams(-2,dp(42)));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(44)); bp.setMargins(0,dp(4),0,dp(4)); root.addView(bancoCard,bp);
        if(controleAtivo!=null){
            TextView ativo=label("CONTROLE ATIVO  •  "+controleAtivo.nome,13);
            ativo.setTextColor(WHITE);
            GradientDrawable ativoBg=new GradientDrawable(); ativoBg.setColor(Color.rgb(38,70,48)); ativoBg.setCornerRadius(dp(14)); ativo.setBackground(ativoBg);
            ativo.setPadding(dp(14),0,dp(14),0);
            root.addView(ativo,new LinearLayout.LayoutParams(-1,dp(42)));
        }
        if(controleAtivo==null){
            LinearLayout philips=tvCard("PHILIPS","50PUG6513/7",!lgMode,v->{lgMode=false;});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(125)); cp.setMargins(0,dp(28),0,dp(10)); root.addView(philips,cp);
            LinearLayout lg=tvCard("LG","32LB620B",lgMode,v->{lgMode=true;});
            LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,dp(125)); cl.setMargins(0,dp(10),0,dp(24)); root.addView(lg,cl);
            TextView chosen=label(lgMode?"✓ LG 32LB620B":"✓ Philips 50PUG6513/7",15); chosen.setTextColor(Color.rgb(75,145,95));
            root.addView(chosen,new LinearLayout.LayoutParams(-1,dp(38)));
        } else {
            TextView acesso=label("Há um controle ativo. Toque em CONTINUAR para abri-lo ou em MEUS CONTROLES para trocar.",14);
            acesso.setTextColor(GRAY); acesso.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(70)); ap.setMargins(0,dp(18),0,dp(4)); root.addView(acesso,ap);
        }
        Button continueBtn=new Button(this); continueBtn.setText("CONTINUAR"); continueBtn.setTextColor(WHITE); continueBtn.setTextSize(17); continueBtn.setAllCaps(false);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.rgb(190,24,32)); bg.setCornerRadius(dp(18)); continueBtn.setBackground(bg); continueBtn.setOnClickListener(v->{prefs.edit().putBoolean("lg_mode",lgMode).apply();showingSelector=false;build();});
        root.addView(continueBtn,new LinearLayout.LayoutParams(-1,dp(58)));
        Button universal=new Button(this);
        universal.setText("🔎  CONTROLE UNIVERSAL");
        universal.setTextColor(WHITE); universal.setTextSize(15); universal.setAllCaps(false);
        GradientDrawable universalBg=new GradientDrawable(); universalBg.setColor(ACCENT); universalBg.setCornerRadius(dp(16)); universal.setBackground(universalBg); actionFeedback(universal);
        universal.setOnClickListener(v->showUniversalScanner(lgMode?"LG":"Philips",lgMode?"32LB620B":"50PUG6513/7","TV"));
        LinearLayout.LayoutParams universalParams=new LinearLayout.LayoutParams(-1,dp(58));
        universalParams.setMargins(0,dp(10),0,dp(0)); root.addView(universal,universalParams);
        TextView universalInfo=label("TV • AR-CONDICIONADO • VENTILADOR  •  pesquise a marca, teste códigos e salve o que funcionar.",12);
        universalInfo.setTextColor(GRAY); universalInfo.setGravity(Gravity.CENTER);
        root.addView(universalInfo,new LinearLayout.LayoutParams(-1,dp(44)));

        Button premium=new Button(this);
        premium.setText(monetizacao.isPremium()?"⭐  PREMIUM ATIVO":"⭐  IR REMOTE PREMIUM");
        premium.setTextColor(WHITE); premium.setTextSize(13); premium.setAllCaps(false);
        GradientDrawable premiumBg=new GradientDrawable();
        premiumBg.setColor(monetizacao.isPremium()?Color.rgb(55,105,65):Color.rgb(70,55,20));
        premiumBg.setCornerRadius(dp(16)); premium.setBackground(premiumBg); actionFeedback(premium);
        premium.setOnClickListener(v->{ if(monetizacao.isPremium()) Toast.makeText(this,"⭐ Premium já está ativo neste aparelho.",Toast.LENGTH_SHORT).show(); else monetizacao.showPremiumDialog(); });
        LinearLayout.LayoutParams premiumParams=new LinearLayout.LayoutParams(-1,dp(50));
        premiumParams.setMargins(0,dp(8),0,0); root.addView(premium,premiumParams);

        Button atualizar=new Button(this); atualizar.setText("↻  VERIFICAR ATUALIZAÇÃO"); atualizar.setTextColor(WHITE); atualizar.setTextSize(13); atualizar.setAllCaps(false);
        GradientDrawable atualizarBg=new GradientDrawable(); atualizarBg.setColor(KEY_DARK); atualizarBg.setCornerRadius(dp(16)); atualizar.setBackground(atualizarBg); actionFeedback(atualizar); atualizar.setOnClickListener(v->updateManager.verificarManualmente());
        LinearLayout.LayoutParams atualizarParams=new LinearLayout.LayoutParams(-1,dp(50)); atualizarParams.setMargins(0,dp(8),0,0); root.addView(atualizar,atualizarParams);
        monetizacao.addBanner(root);
        sv.addView(root); mostrar(sv);
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
                        runOnUiThread(()->{
                            buscar.setEnabled(true);
                            if(models.isEmpty()){ status.setText("Nenhum modelo encontrado. Tente outra marca."); return; }
                            String[] nomes=new String[models.size()];
                            for(int i=0;i<models.size();i++) nomes[i]=models.get(i).manufacturer+" • "+models.get(i).model;
                            escolher("AR-CONDICIONADO • MODELOS ("+models.size()+")","Escolha o modelo exato sempre que possível.",nomes,w->abrirSmartIrClimate(models.get(w)));
                            status.setText("✓ "+models.size()+" modelo(s) encontrado(s).");
                        });
                    }else{
                        java.util.List<IrRemoteDatabase.RemoteFile> files=IrRemoteDatabase.listar(selectedCat[0],b);
                        runOnUiThread(()->{
                        buscar.setEnabled(true);
                        if(files.isEmpty()){ status.setText("Nenhum modelo encontrado. Tente outra marca."); return; }
                        final String[] nomes=new String[files.size()];
                        for(int i=0;i<files.size();i++) nomes[i]=files.get(i).model;
                        escolher("MODELOS ENCONTRADOS ("+files.size()+")","Toque em um modelo para carregar os códigos.",nomes,w->abrirRemoteOnline(files.get(w)));
                        status.setText("✓ "+files.size()+" modelo(s) encontrado(s).");
                        });
                    }
                }catch(Exception e){
                    runOnUiThread(()->{ buscar.setEnabled(true); status.setText("✕ Não foi possível acessar o banco online."); Toast.makeText(this,e.getMessage()==null?"Erro de conexão":e.getMessage(),Toast.LENGTH_LONG).show(); });
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
                runOnUiThread(()->{
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
                runOnUiThread(()->Toast.makeText(this,
                        e.getMessage()==null?"Não foi possível acessar o banco SmartIR.":e.getMessage(),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void abrirSmartIrClimate(SmartIrDatabase.Model model){
        Toast.makeText(this,"⏳ Carregando "+model.model+"...",Toast.LENGTH_SHORT).show();
        new Thread(()->{ try{
            SmartIrDatabase.Climate climate=SmartIrDatabase.carregar(model);
            runOnUiThread(()->showAcRemote(climate,model,null));
        }catch(Exception e){ runOnUiThread(()->Toast.makeText(this,e.getMessage()==null?"Falha ao carregar.":e.getMessage(),Toast.LENGTH_LONG).show()); } }).start();
    }

    private void abrirSmartIrSalvo(ControleStorage.Controle controle){
        if(controle==null || controle.descricao==null || !controle.descricao.startsWith("SMARTIR|")){ build(); return; }
        String url=controle.descricao.substring("SMARTIR|".length());
        new Thread(()->{ try{
            SmartIrDatabase.Climate climate=SmartIrDatabase.carregarPorUrl(url);
            SmartIrDatabase.Model model=new SmartIrDatabase.Model(controle.marca,extrairCodigoSmartIr(url),controle.modelo);
            runOnUiThread(()->showAcRemote(climate,model,controle));
        }catch(Exception e){ runOnUiThread(()->new android.app.AlertDialog.Builder(this).setTitle("CONTROLE NÃO CARREGADO").setMessage("Não foi possível atualizar os códigos deste ar-condicionado. Verifique a internet.").setPositiveButton("TENTAR", (d,w)->abrirSmartIrSalvo(controle)).setNegativeButton("VOLTAR",null).show()); } }).start();
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
        final java.util.List<Button> modeButtons=new ArrayList<>();
        final java.util.List<Button> fanButtons=new ArrayList<>();
        final java.util.List<Button> swingButtons=new ArrayList<>();
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(14),dp(14),dp(26));
        LinearLayout top=row();
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL);
        TextView title=label("❄️  AR-CONDICIONADO",22); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        head.addView(title,new LinearLayout.LayoutParams(-1,dp(32)));
        TextView sub=label(model.manufacturer+"  •  "+model.model,12); sub.setTextColor(GRAY);
        sub.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        head.addView(sub,new LinearLayout.LayoutParams(-1,dp(24)));
        top.addView(head,new LinearLayout.LayoutParams(0,dp(58),1));
        TextView smart=badge("SMARTIR",Color.rgb(55,75,95));
        top.addView(smart,new LinearLayout.LayoutParams(-2,dp(28)));
        root.addView(top);

        final TextView state=label("",13);
        state.setTextColor(Color.rgb(105,190,125));
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(10),0,dp(10),0);
        GradientDrawable stateBg=new GradientDrawable(); stateBg.setColor(Color.rgb(22,42,28)); stateBg.setCornerRadius(dp(14)); stateBg.setStroke(dp(1),Color.rgb(65,110,75));
        state.setBackground(stateBg);
        root.addView(state,new LinearLayout.LayoutParams(-1,dp(44)));

        LinearLayout tempCard=new LinearLayout(this); tempCard.setOrientation(LinearLayout.VERTICAL); tempCard.setGravity(Gravity.CENTER);
        tempCard.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable tempBg=new GradientDrawable(); tempBg.setColor(CARD); tempBg.setCornerRadius(dp(24)); tempBg.setStroke(dp(1),BORDER);
        tempCard.setBackground(tempBg);

        TextView tempCaption=label("TEMPERATURA",11); tempCaption.setTextColor(GRAY);
        tempCard.addView(tempCaption,new LinearLayout.LayoutParams(-1,dp(24)));
        LinearLayout tr=row();
        Button menos=botaoAcao("−",KEY_DARK,25);
        Button mais=botaoAcao("+",KEY_DARK,25);
        TextView tv=label(temp[0]+"°",40); tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        tr.addView(menos,lpFixa(72)); tr.addView(tv,new LinearLayout.LayoutParams(0,dp(72),1)); tr.addView(mais,lpFixa(72));
        tempCard.addView(tr,new LinearLayout.LayoutParams(-1,dp(76)));
        TextView limite=label(climate.minTemp+"°C  —  "+climate.maxTemp+"°C",11); limite.setTextColor(GRAY);
        tempCard.addView(limite,new LinearLayout.LayoutParams(-1,dp(22)));
        LinearLayout.LayoutParams tcp=new LinearLayout.LayoutParams(-1,dp(158)); tcp.setMargins(0,dp(8),0,dp(8)); root.addView(tempCard,tcp);

        Runnable refresh=()->{
            if(saved[0]!=null){
                String base="ac_state_"+saved[0].id+"_";
                prefs.edit().putString(base+"mode",mode[0]).putString(base+"fan",fan[0])
                    .putString(base+"swing",swing[0]==null?"":swing[0]).putInt(base+"temp",temp[0])
                    .putBoolean(base+"on",ligado[0]).apply();
            }
            tv.setText(temp[0]+"°");
            String st=(ligado[0]?"● LIGADO":"○ DESLIGADO")+"  •  "+modoTexto(mode[0])+"  •  "+temp[0]+" °C  •  "+fanTexto(fan[0]);
            if(swing[0]!=null) st+="  •  SWING "+modoTexto(swing[0]);
            state.setText(st);
            state.setTextColor(ligado[0]?Color.rgb(105,190,125):GRAY);
            for(Button b:modeButtons){
                String v=String.valueOf(b.getTag());
                GradientDrawable g=new GradientDrawable();
                g.setColor(v.equals(mode[0])?ACCENT:KEY);
                g.setCornerRadius(dp(14));
                g.setStroke(dp(1),BORDER);
                b.setBackground(g);
            }
            for(Button b:fanButtons){
                String v=String.valueOf(b.getTag());
                GradientDrawable g=new GradientDrawable();
                g.setColor(v.equals(fan[0])?Color.rgb(65,85,105):KEY);
                g.setCornerRadius(dp(14));
                g.setStroke(dp(1),BORDER);
                b.setBackground(g);
            }
            for(Button b:swingButtons){
                String v=String.valueOf(b.getTag());
                GradientDrawable g=new GradientDrawable();
                g.setColor(v.equals(swing[0])?Color.rgb(65,85,105):KEY);
                g.setCornerRadius(dp(14));
                g.setStroke(dp(1),BORDER);
                b.setBackground(g);
            }
        };

        Button powerOn=botaoAcao("⏻  LIGAR",Color.rgb(45,105,58),12);
        Button powerOff=botaoAcao("⏻  DESLIGAR",Color.rgb(90,40,40),12);
        powerOn.setOnClickListener(v->{
            ligado[0]=true;
            enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);
            refresh.run();
        });
        powerOff.setOnClickListener(v->{
            String cmd=climate.offCommand();
            if(cmd.isEmpty()){Toast.makeText(this,"Este modelo não possui código OFF.",Toast.LENGTH_SHORT).show();return;}
            ligado[0]=false;
            enviarBase64Smart(cmd,climate,state,"Desligado");
            refresh.run();
        });
        LinearLayout powerRow=row(); powerRow.addView(powerOn,lpPeso()); powerRow.addView(powerOff,lpPeso());
        root.addView(powerRow,new LinearLayout.LayoutParams(-1,dp(50)));

        section(root,"MODO DE OPERAÇÃO");
        LinearLayout mr=row();
        int mc=0;
        for(String m:climate.modes){
            final String value=m;
            Button mb=botaoAcao(modoTexto(m),m.equals(mode[0])?ACCENT:KEY,10);
            mb.setTag(value);
            modeButtons.add(mb);
            mb.setOnClickListener(v->{mode[0]=value;ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
            mr.addView(mb,lpPeso()); mc++;
            if(mc%3==0 && mc<climate.modes.size()){root.addView(mr);mr=row();}
        }
        if(mc>0) root.addView(mr);

        section(root,"VELOCIDADE DO VENTILADOR");
        LinearLayout fr=row();
        int fc=0;
        for(String f:climate.fans){
            final String value=f;
            Button fb=botaoAcao(fanTexto(f),f.equals(fan[0])?Color.rgb(65,85,105):KEY,10);
            fb.setTag(value);
            fanButtons.add(fb);
            fb.setOnClickListener(v->{fan[0]=value;ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
            fr.addView(fb,lpPeso()); fc++;
            if(fc%3==0 && fc<climate.fans.size()){root.addView(fr);fr=row();}
        }
        if(fc>0) root.addView(fr);

        if(!climate.swings.isEmpty()){
            section(root,"OSCILAÇÃO");
            LinearLayout sr=row();
            int sc=0;
            for(String sw:climate.swings){
                final String value=sw;
                Button sb=botaoAcao(modoTexto(sw),sw.equals(swing[0])?Color.rgb(65,85,105):KEY,10);
                sb.setTag(value);
                swingButtons.add(sb);
                sb.setOnClickListener(v->{swing[0]=value;ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
                sr.addView(sb,lpPeso()); sc++;
                if(sc%3==0 && sc<climate.swings.size()){root.addView(sr);sr=row();}
            }
            if(sc>0) root.addView(sr);
        }

        section(root,"ATALHOS");
        LinearLayout quick=row();
        Button q24=botaoAcao("❄️ CONFORTO 24°",KEY_DARK,10);
        Button q26=botaoAcao("🌿 ECONOMIA 26°",KEY_DARK,10);
        Button qAuto=botaoAcao("AUTO",KEY_DARK,10);
        quick.addView(q24,lpPeso()); quick.addView(q26,lpPeso()); quick.addView(qAuto,lpPeso());
        root.addView(quick,new LinearLayout.LayoutParams(-1,dp(50)));
        q24.setOnClickListener(v->{temp[0]=Math.max(climate.minTemp,Math.min(climate.maxTemp,24));ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
        q26.setOnClickListener(v->{temp[0]=Math.max(climate.minTemp,Math.min(climate.maxTemp,26));ligado[0]=true;enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state);refresh.run();});
        qAuto.setOnClickListener(v->{
            for(String m:climate.modes) if("auto".equalsIgnoreCase(m)||"heat_cool".equalsIgnoreCase(m)){mode[0]=m;break;}
            for(String f:climate.fans) if("auto".equalsIgnoreCase(f)){fan[0]=f;break;}
            ligado[0]=true; enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state); refresh.run();
        });

        LinearLayout actions=row();
        Button save=botaoAcao(saved[0]==null?"💾 SALVAR CONTROLE":"✓ CONTROLE SALVO",ACCENT,12);
        save.setOnClickListener(v->{
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
        });
        actions.addView(save,lpPeso());
        Button back=botaoAcao("← VOLTAR",KEY_DARK,12);
        back.setOnClickListener(v->showSelector());
        actions.addView(back,lpPeso());
        root.addView(actions,new LinearLayout.LayoutParams(-1,dp(50)));

        TextView foot=label("Banco SmartIR • "+climate.models.size()+" modelo(s) • "+climate.modes.size()+" modos • "+climate.fans.size()+" velocidades",11);
        foot.setTextColor(GRAY); foot.setGravity(Gravity.CENTER);
        root.addView(foot,new LinearLayout.LayoutParams(-1,dp(36)));

        menos.setOnClickListener(v->{
            temp[0]=Math.max(climate.minTemp,temp[0]-climate.precision);
            ligado[0]=true; enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state); refresh.run();
        });
        mais.setOnClickListener(v->{
            temp[0]=Math.min(climate.maxTemp,temp[0]+climate.precision);
            ligado[0]=true; enviarEstadoAc(climate,mode[0],fan[0],swing[0],temp[0],state); refresh.run();
        });
        refresh.run();
        sv.addView(root); mostrar(sv);
    }

    private String modoTexto(String value){
        if(value==null) return "";
        String v=value.trim().toLowerCase(java.util.Locale.ROOT);
        if(v.equals("cool")) return "❄️ FRIO";
        if(v.equals("heat")) return "☀️ QUENTE";
        if(v.equals("auto") || v.equals("heat_cool")) return "AUTO";
        if(v.equals("dry")) return "💧 SECO";
        if(v.equals("fan_only") || v.equals("fan")) return "🌀 VENT.";
        if(v.equals("off")) return "DESL.";
        return value.replace("_"," ").toUpperCase(java.util.Locale.ROOT);
    }

    private String fanTexto(String value){
        if(value==null) return "";
        String v=value.trim().toLowerCase(java.util.Locale.ROOT);
        if(v.equals("auto")) return "AUTO";
        if(v.equals("low") || v.equals("low-low")) return "BAIXA";
        if(v.equals("medium") || v.equals("med")) return "MÉDIA";
        if(v.equals("high")) return "ALTA";
        if(v.equals("turbo") || v.equals("max")) return "TURBO";
        if(v.equals("off")) return "OFF";
        return value.replace("_"," ").toUpperCase(java.util.Locale.ROOT);
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
                runOnUiThread(()->mostrarSinaisOnline(remote,sinais));
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,e.getMessage()==null?"Falha ao carregar o controle.":e.getMessage(),Toast.LENGTH_LONG).show());
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
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(20),dp(10),dp(20),dp(10));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(selected?Color.rgb(42,42,48):Color.rgb(27,27,30)); bg.setCornerRadius(dp(20)); bg.setStroke(dp(2),selected?Color.rgb(210,30,38):Color.rgb(55,55,58)); card.setBackground(bg);
        TextView b=label(brand,18); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); card.addView(b,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView m=label(model,16); m.setTextColor(GRAY); card.addView(m,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView s=label(selected?"✓ SELECIONADA":"TOQUE PARA SELECIONAR",12); s.setTextColor(selected?Color.rgb(75,145,95):GRAY); card.addView(s,new LinearLayout.LayoutParams(-1,dp(28)));
        card.setOnClickListener(v->{ click.onClick(v); showSelector(); });
        return card;
    }

    @Override protected void onResume(){
        super.onResume();
        recarregarAtivo();
        if(updateManager!=null){
            updateManager.aoRetornarDoSistema();
            updateManager.verificarAoAbrir();
        }
    }

    @Override protected void onDestroy(){
        if(updateManager!=null) updateManager.destroy();
        super.onDestroy();
    }

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
                ("Ventilador".equalsIgnoreCase(marca) ? "FAN" :
                ("Universal".equalsIgnoreCase(marca) ? "" : "TV"));
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

        TextView status=label("●  Pronto para testar",15);
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
                
                status.setText("●  Pronto para testar");
                status.setTextColor(GRAY);
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
                status.setText("●  Pronto para testar");
                status.setTextColor(GRAY);
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

        TextView intro=label("⚡ A configuração automática já preenche o que é conhecido. Use esta tela apenas para corrigir ou adicionar funções.",13);
        intro.setTextColor(GRAY);
        intro.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(intro,new LinearLayout.LayoutParams(-1,dp(54)));

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
        Button proximoNaoConfigurado=smallAction("PRÓXIMO PENDENTE",Color.rgb(55,65,80));
        progressRow.addView(proximoNaoConfigurado,new LinearLayout.LayoutParams(dp(132),dp(32)));
        box.addView(progressRow,new LinearLayout.LayoutParams(-1,dp(34)));

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
        status.setPadding(dp(12),0,dp(12),0);
        box.addView(status,new LinearLayout.LayoutParams(-1,dp(44)));

        TextView listaTitulo=label("BOTÕES DO CONTROLE",11);
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
        LinearLayout.LayoutParams lsp=new LinearLayout.LayoutParams(-1,dp(260));
        lsp.setMargins(0,dp(2),0,dp(2));
        box.addView(listaScroll,lsp);

        LinearLayout hexRow=row();
        final EditText hex=new EditText(this);
        hex.setHint("Ir direto a um código (hex, ex.: 0x5C)"); hex.setHintTextColor(Color.rgb(120,120,125));
        hex.setTextColor(WHITE); hex.setTextSize(13); hex.setSingleLine(true);
        hexRow.addView(hex,new LinearLayout.LayoutParams(0,dp(46),1));
        final Button hexBtn=botaoAcao("TESTAR HEX",KEY_DARK,11);
        hexRow.addView(hexBtn,lpFixa(96));
        box.addView(hexRow,new LinearLayout.LayoutParams(-1,dp(50)));

        final Button[] botoes=new Button[funcoes.length];

        Runnable atualizarLista=()->{
            int qtd=controleStorage.quantidadeComandos(controle);
            int percentual=funcoes.length<=0?0:Math.min(100,(qtd*100)/funcoes.length);
            progress.setText(qtd+" de "+funcoes.length+" botões • "+percentual+"%");
            proximoNaoConfigurado.setText(qtd>=funcoes.length?"✓ CONCLUÍDO":"PRÓXIMO PENDENTE");
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
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(42));
            ip.setMargins(0,dp(2),0,dp(2));
            lista.addView(item,ip);
            botoes[i]=item;
        }

        atualizarLista.run();

        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("Configurar botões")
            .setView(wrapScroll(box))
            .setNegativeButton("FECHAR",(d,w)->showMeusControles())
            .setNeutralButton("TESTAR CÓDIGO",null)
            .setPositiveButton("FUNCIONOU / SALVAR",null)
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
                status.setText(ok?"●  Código enviado • "+funcoes[pos[0]]+"  (se respondeu, toque em FUNCIONOU / SALVAR)":"●  Código inválido ou falha no emissor IR");
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
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(16),dp(14),dp(28)); root.setBackgroundColor(BG);

        LinearLayout top=row();
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Meus controles",24); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        head.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView headSub=label("Seus controles • salvos neste aparelho",12); headSub.setTextColor(GRAY); headSub.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        head.addView(headSub,new LinearLayout.LayoutParams(-1,dp(20)));
        top.addView(head,new LinearLayout.LayoutParams(0,dp(52),1));
        Button voltar=new Button(this); voltar.setText("VOLTAR"); voltar.setTextColor(WHITE); voltar.setTextSize(12); voltar.setAllCaps(false);
        GradientDrawable vb=new GradientDrawable(); vb.setColor(CARD_2); vb.setCornerRadius(dp(14)); vb.setStroke(dp(1),Color.rgb(60,60,66)); voltar.setBackground(vb);
        actionFeedback(voltar); voltar.setOnClickListener(v->showSelector()); top.addView(voltar,new LinearLayout.LayoutParams(dp(90),dp(44))); root.addView(top);

        LinearLayout premiumCard=new LinearLayout(this); premiumCard.setOrientation(LinearLayout.HORIZONTAL); premiumCard.setGravity(Gravity.CENTER_VERTICAL); premiumCard.setPadding(dp(14),0,dp(10),0);
        boolean premiumAtivo=monetizacao.isPremium();
        GradientDrawable premiumCardBg=new GradientDrawable(); premiumCardBg.setColor(premiumAtivo?Color.rgb(28,55,36):Color.rgb(48,40,24)); premiumCardBg.setCornerRadius(dp(15)); premiumCardBg.setStroke(dp(1),premiumAtivo?Color.rgb(80,160,100):Color.rgb(100,78,40)); premiumCard.setBackground(premiumCardBg);
        LinearLayout premiumInfo=new LinearLayout(this); premiumInfo.setOrientation(LinearLayout.VERTICAL); premiumInfo.setGravity(Gravity.CENTER_VERTICAL);
        TextView premiumTitle=label(premiumAtivo?"⭐  PREMIUM ATIVO":"⭐  PLANO GRATUITO",14); premiumTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); premiumTitle.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        premiumInfo.addView(premiumTitle,new LinearLayout.LayoutParams(-1,dp(24)));
        TextView premiumSub=label(monetizacao.resumoLimite(controleStorage.quantidadeControles())+" • "+(premiumAtivo?"sem anúncios":"desbloqueie controles ilimitados"),11); premiumSub.setTextColor(GRAY); premiumSub.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        premiumInfo.addView(premiumSub,new LinearLayout.LayoutParams(-1,dp(20)));
        premiumCard.addView(premiumInfo,new LinearLayout.LayoutParams(0,dp(48),1));
        Button premiumAction=new Button(this); premiumAction.setText(premiumAtivo?"VERIFICAR":"PREMIUM"); premiumAction.setTextColor(WHITE); premiumAction.setTextSize(11); premiumAction.setAllCaps(false); premiumAction.setMinHeight(0); premiumAction.setMinWidth(0); premiumAction.setPadding(dp(10),0,dp(10),0);
        GradientDrawable premiumActionBg=new GradientDrawable(); premiumActionBg.setColor(premiumAtivo?Color.rgb(55,110,65):ACCENT); premiumActionBg.setCornerRadius(dp(11)); premiumAction.setBackground(premiumActionBg); actionFeedback(premiumAction);
        premiumAction.setOnClickListener(v->monetizacao.showPremiumDialog());
        premiumCard.addView(premiumAction,new LinearLayout.LayoutParams(dp(92),dp(40)));
        LinearLayout.LayoutParams premiumCardP=new LinearLayout.LayoutParams(-1,dp(60)); premiumCardP.setMargins(0,dp(4),0,dp(8)); root.addView(premiumCard,premiumCardP);
        LinearLayout backupRow=row();
        Button exportar=smallAction("EXPORTAR BACKUP",Color.rgb(55,85,65));
        Button importar=smallAction("IMPORTAR BACKUP",Color.rgb(65,70,90));
        backupRow.addView(exportar,new LinearLayout.LayoutParams(0,dp(40),1));
        backupRow.addView(importar,new LinearLayout.LayoutParams(0,dp(40),1));
        exportar.setOnClickListener(v->exportarBackup());
        importar.setOnClickListener(v->importarBackup());
        LinearLayout.LayoutParams backupP=new LinearLayout.LayoutParams(-1,dp(48));
        backupP.setMargins(0,0,0,dp(4)); root.addView(backupRow,backupP);


        final EditText busca=new EditText(this);
        busca.setSingleLine(true); busca.setHint("🔎  Pesquisar marca, modelo ou nome...");
        busca.setHintTextColor(Color.rgb(125,125,130)); busca.setTextColor(WHITE); busca.setTextSize(14); busca.setPadding(dp(14),0,dp(14),0);
        GradientDrawable searchBg=new GradientDrawable(); searchBg.setColor(CARD); searchBg.setCornerRadius(dp(15)); searchBg.setStroke(dp(1),BORDER); busca.setBackground(searchBg);
        LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(48)); searchP.setMargins(0,dp(10),0,dp(8)); root.addView(busca,searchP);

        final TextView resumo=label("",12); resumo.setTextColor(GRAY); resumo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        root.addView(resumo,new LinearLayout.LayoutParams(-1,dp(30)));
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
                    vazioBox.setPadding(dp(18),dp(22),dp(18),dp(22));
                    GradientDrawable vazioBg=new GradientDrawable();
                    vazioBg.setColor(CARD);
                    vazioBg.setCornerRadius(dp(20));
                    vazioBg.setStroke(dp(1),BORDER);
                    vazioBox.setBackground(vazioBg);

                    TextView emoji=label("📭",38);
                    emoji.setGravity(Gravity.CENTER);
                    vazioBox.addView(emoji,new LinearLayout.LayoutParams(-1,dp(48)));

                    TextView tituloVazio=label("Nenhum controle salvo ainda",18);
                    tituloVazio.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                    tituloVazio.setGravity(Gravity.CENTER);
                    tituloVazio.setTextColor(WHITE);
                    vazioBox.addView(tituloVazio,new LinearLayout.LayoutParams(-1,dp(34)));

                    TextView textoVazio=label("🔎 Use o CONTROLE UNIVERSAL para encontrar seu aparelho, testar os códigos e salvar o controle que funcionar.",13);
                    textoVazio.setTextColor(GRAY);
                    textoVazio.setGravity(Gravity.CENTER);
                    vazioBox.addView(textoVazio,new LinearLayout.LayoutParams(-1,dp(68)));

                    TextView dicaVazia=label("💡 Depois de salvar, ele aparecerá aqui para acesso rápido.",12);
                    dicaVazia.setTextColor(Color.rgb(155,155,160));
                    dicaVazia.setGravity(Gravity.CENTER);
                    vazioBox.addView(dicaVazia,new LinearLayout.LayoutParams(-1,dp(42)));

                    Button addVazio=new Button(this);
                    addVazio.setText("🔎  ENCONTRAR MEU CONTROLE");
                    addVazio.setTextColor(WHITE);
                    addVazio.setTextSize(13);
                    addVazio.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                    addVazio.setAllCaps(false);
                    GradientDrawable av=new GradientDrawable();
                    av.setColor(ACCENT);
                    av.setCornerRadius(dp(15));
                    addVazio.setBackground(av);
                    actionFeedback(addVazio);
                    addVazio.setOnClickListener(v->showUniversalScanner("Universal","Universal"));
                    vazioBox.addView(addVazio,new LinearLayout.LayoutParams(-1,dp(52)));

                    LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(296));
                    vp.setMargins(0,dp(4),0,dp(8));
                    listaBox.addView(vazioBox,vp);
                }else{
                    TextView vazio=label("🔎 Nenhum controle encontrado para \""+filtro+"\".",15);
                    vazio.setTextColor(GRAY);
                    vazio.setGravity(Gravity.CENTER);
                    listaBox.addView(vazio,new LinearLayout.LayoutParams(-1,dp(100)));
                }
                return;
            }
            for(ControleStorage.Controle c:lista){
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(15),dp(12),dp(15),dp(12));
                boolean ativoAtual=controleAtivo!=null&&controleAtivo.id==c.id;
                GradientDrawable cb=new GradientDrawable(); cb.setColor(ativoAtual?Color.rgb(28,55,36):CARD); cb.setCornerRadius(dp(18)); cb.setStroke(dp(1),ativoAtual?Color.rgb(80,160,100):Color.rgb(55,55,60)); card.setBackground(cb);

                LinearLayout line=row();
                TextView n=label((ativoAtual?"✓  ":"")+c.nome,17); n.setTypeface(Typeface.DEFAULT,Typeface.BOLD); n.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
                line.addView(n,new LinearLayout.LayoutParams(0,dp(32),1));
                TextView badge=badge(ativoAtual?"ATIVO":"IR",ativoAtual?Color.rgb(55,110,65):Color.rgb(60,60,68));
                line.addView(badge,new LinearLayout.LayoutParams(-2,dp(26))); card.addView(line);

                int qtd=controleStorage.quantidadeComandos(c);
                int totalFuncoes=(IrPerfilTeste.PERFIL_VENTILADOR.equals(c.perfil)?RemoteKeys.FAN_FUNCOES.length:RemoteKeys.FUNCOES.length);
                int percentual=totalFuncoes<=0?0:Math.min(100,(qtd*100)/totalFuncoes);
                TextView detail=label(c.marca+"  •  "+c.modelo+"\n"+c.perfil+"  •  "+qtd+" de "+totalFuncoes+" botões configurados  •  "+percentual+"%",12);
                detail.setTextColor(GRAY); detail.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); card.addView(detail,new LinearLayout.LayoutParams(-1,dp(54)));

                LinearLayout actions=row();
                Button abrir=smallAction("ABRIR",Color.rgb(55,110,65));
                Button config=smallAction("⚡ AUTOMÁTICO",Color.rgb(65,85,70));
                Button testar=smallAction("TESTAR",Color.rgb(55,65,80));
                Button copiar=smallAction("DUPLICAR",Color.rgb(75,60,45));
                actions.addView(abrir,new LinearLayout.LayoutParams(0,dp(42),1));
                actions.addView(config,new LinearLayout.LayoutParams(0,dp(42),1));
                actions.addView(testar,new LinearLayout.LayoutParams(0,dp(42),1));
                card.addView(actions);
                LinearLayout copyRow=row();
                copyRow.addView(copiar,new LinearLayout.LayoutParams(-1,dp(38)));
                card.addView(copyRow);
                abrir.setOnClickListener(v->{controleAtivo=c;lgMode="LG".equalsIgnoreCase(c.marca);prefs.edit().putLong("active_control_id",c.id).putBoolean("lg_mode",lgMode).apply();showingSelector=false;build();Toast.makeText(this,"✓ "+c.nome+" está ativo",Toast.LENGTH_SHORT).show();});
                config.setOnClickListener(v->{controleAtivo=c;lgMode="LG".equalsIgnoreCase(c.marca);prefs.edit().putLong("active_control_id",c.id).putBoolean("lg_mode",lgMode).apply();oferecerConfiguracaoAutomatica(c);});
                testar.setOnClickListener(v->showComandosConfigurados(c));
                copiar.setOnClickListener(v->{
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
                });

                LinearLayout editRow=row();
                Button renomear=smallAction("RENOMEAR",Color.rgb(55,65,80));
                Button excluir=smallAction("🗑  EXCLUIR",Color.rgb(105,45,45));
                editRow.addView(renomear,new LinearLayout.LayoutParams(0,dp(38),1));
                editRow.addView(excluir,new LinearLayout.LayoutParams(0,dp(38),1));
                card.addView(editRow);
                renomear.setOnClickListener(v->{                    final EditText campo=new EditText(this); campo.setSingleLine(true); campo.setText(c.nome); campo.setSelectAllOnFocus(true); campo.setHint("Nome do controle");
                    new android.app.AlertDialog.Builder(this).setTitle("Renomear controle").setView(campo)
                        .setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",(d,w)->{
                            String novoNome=campo.getText().toString().trim();
                            if(!novoNome.isEmpty()){controleStorage.renomear(c,novoNome);if(controleAtivo!=null&&controleAtivo.id==c.id) controleAtivo.nome=c.nome;render[0].run();}
                        }).show();
                });
                excluir.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Excluir controle?")
                    .setMessage("Remover \""+c.nome+"\" deste aparelho?").setNegativeButton("CANCELAR",null)
                    .setPositiveButton("EXCLUIR",(d,w)->{controleStorage.excluir(c);if(controleAtivo!=null&&controleAtivo.id==c.id){controleAtivo=null;prefs.edit().remove("active_control_id").apply();}render[0].run();}).show());

                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(276)); cp.setMargins(0,dp(6),0,dp(6)); listaBox.addView(card,cp);
            }
        };
        busca.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){render[0].run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        render[0].run();

        Button add=new Button(this); add.setText("+  ADICIONAR OUTRO CONTROLE"); add.setTextColor(WHITE); add.setTextSize(13); add.setAllCaps(false);
        GradientDrawable addBg=new GradientDrawable(); addBg.setColor(ACCENT); addBg.setCornerRadius(dp(16)); addBg.setStroke(dp(1),Color.rgb(240,70,76)); add.setBackground(addBg); actionFeedback(add); add.setOnClickListener(v->showAddControlWizard());
        LinearLayout.LayoutParams addP=new LinearLayout.LayoutParams(-1,dp(52)); addP.setMargins(0,dp(12),0,dp(6)); root.addView(add,addP);
        Button sobre=new Button(this); sobre.setText("ⓘ  SOBRE O APLICATIVO"); sobre.setTextColor(WHITE); sobre.setTextSize(13); sobre.setAllCaps(false);
        GradientDrawable sobreBg=new GradientDrawable(); sobreBg.setColor(CARD_2); sobreBg.setCornerRadius(dp(16)); sobre.setBackground(sobreBg); actionFeedback(sobre); sobre.setOnClickListener(v->showSobre()); root.addView(sobre,new LinearLayout.LayoutParams(-1,dp(48)));
        sv.addView(root); mostrar(sv);
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
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(14),dp(14),dp(14),dp(26));
        final int[] fanFunc={0};
        final java.util.List<Button> fanActionButtons=new ArrayList<>();

        LinearLayout top=row();
        LinearLayout head=new LinearLayout(this); head.setOrientation(LinearLayout.VERTICAL);
        TextView title=label("🌀  VENTILADOR",24); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        head.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
        TextView sub=label(controleAtivo!=null?controleAtivo.marca+" • "+controleAtivo.modelo:"Controle universal • 38 kHz",12);
        sub.setTextColor(GRAY); sub.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        head.addView(sub,new LinearLayout.LayoutParams(-1,dp(24)));
        top.addView(head,new LinearLayout.LayoutParams(0,dp(58),1));
        TextView badge=badge(controleAtivo!=null?"SALVO":"UNIVERSAL",controleAtivo!=null?Color.rgb(55,110,65):Color.rgb(60,60,68));
        top.addView(badge,new LinearLayout.LayoutParams(-2,dp(28)));
        root.addView(top);

        LinearLayout statusCard=new LinearLayout(this); statusCard.setGravity(Gravity.CENTER_VERTICAL); statusCard.setPadding(dp(14),0,dp(14),0);
        GradientDrawable sb=new GradientDrawable(); sb.setColor(CARD); sb.setCornerRadius(dp(18)); sb.setStroke(dp(1),BORDER); statusCard.setBackground(sb);
        TextView status=label("●  Pronto para enviar comandos",13); status.setTextColor(SUCCESS); status.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        statusCard.addView(status,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView freq=label("38 kHz",11); freq.setTextColor(GRAY); statusCard.addView(freq,new LinearLayout.LayoutParams(-2,dp(48)));
        root.addView(statusCard,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout powerCard=new LinearLayout(this); powerCard.setGravity(Gravity.CENTER); powerCard.setPadding(dp(12),dp(14),dp(12),dp(14));
        GradientDrawable pc=new GradientDrawable(); pc.setColor(Color.rgb(27,27,30)); pc.setCornerRadius(dp(24)); pc.setStroke(dp(1),BORDER); powerCard.setBackground(pc);
        Button power=new Button(this); power.setText("⏻"); power.setTextColor(WHITE); power.setTextSize(34); power.setGravity(Gravity.CENTER);
        power.setAllCaps(false); power.setMinHeight(0); power.setMinWidth(0);
        GradientDrawable pg=new GradientDrawable(); pg.setColor(Color.rgb(125,35,40)); pg.setCornerRadius(dp(48)); pg.setStroke(dp(1),Color.rgb(185,60,65)); power.setBackground(pg);
        actionFeedback(power);
        power.setOnClickListener(v->{
            enviarVentilador(1); fanFunc[0]=1; status.setText("✓  Liga / desliga enviado");
            for(Button b:fanActionButtons){ b.setBackgroundColor(KEY); }
            power.setAlpha(.95f);
        });
        powerCard.addView(power,new LinearLayout.LayoutParams(dp(104),dp(104)));
        LinearLayout.LayoutParams pcp=new LinearLayout.LayoutParams(-1,dp(132)); pcp.setMargins(0,dp(8),0,dp(8)); root.addView(powerCard,pcp);

        section(root,"CONTROLE PRINCIPAL");
        LinearLayout r=row();
        Button osc=key("↕\nOSCILAÇÃO",2,70,KEY,13);
        Button vel=key("≋\nVELOCIDADE",3,70,Color.rgb(55,65,80),13);
        Button timer=key("⏱\nTIMER",4,70,KEY,13);
        Button sleep=key("☾\nNOTURNO",5,70,KEY,13);
        fanActionButtons.add(osc); fanActionButtons.add(vel); fanActionButtons.add(timer); fanActionButtons.add(sleep);
        r.addView(osc); r.addView(vel); root.addView(r);
        r=row();
        r.addView(timer); r.addView(sleep); root.addView(r);

        LinearLayout infoCard=new LinearLayout(this); infoCard.setOrientation(LinearLayout.VERTICAL); infoCard.setPadding(dp(14),dp(12),dp(14),dp(12));
        GradientDrawable ib=new GradientDrawable(); ib.setColor(CARD); ib.setCornerRadius(dp(18)); ib.setStroke(dp(1),BORDER); infoCard.setBackground(ib);
        TextView infoTitle=label("CONTROLE INTELIGENTE",13); infoTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); infoTitle.setGravity(Gravity.LEFT);
        infoCard.addView(infoTitle,new LinearLayout.LayoutParams(-1,dp(26)));
        TextView info=label("Toque em uma função para enviar o comando. A função usada fica destacada e o status acima confirma o último envio. Para modelos diferentes, use TESTAR / APRENDER CÓDIGOS.",11);
        info.setTextColor(GRAY); info.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        infoCard.addView(info,new LinearLayout.LayoutParams(-1,dp(52)));
        root.addView(infoCard,new LinearLayout.LayoutParams(-1,dp(96)));
        View.OnClickListener fanClick=v->{
            Button b=(Button)v;
            int func=fanActionButtons.indexOf(b)+2;
            if(func<2 || func>5) return;
            enviarVentilador(func);
            fanFunc[0]=func;
            status.setText("✓  "+(func==2?"Oscilação":func==3?"Velocidade":func==4?"Timer":"Modo noturno")+" enviado");
            for(Button x:fanActionButtons){
                GradientDrawable g=new GradientDrawable();
                g.setColor(x==b?Color.rgb(65,85,105):KEY);
                g.setCornerRadius(dp(16)); g.setStroke(dp(1),BORDER);
                x.setBackground(g);
            }
        };
        osc.setOnClickListener(fanClick); vel.setOnClickListener(fanClick);
        timer.setOnClickListener(fanClick); sleep.setOnClickListener(fanClick);

        Button testar=new Button(this); testar.setText("🔎  TESTAR / APRENDER CÓDIGOS"); testar.setTextColor(WHITE); testar.setTextSize(13); testar.setAllCaps(false);
        GradientDrawable tb=new GradientDrawable(); tb.setColor(ACCENT); tb.setCornerRadius(dp(15)); testar.setBackground(tb); actionFeedback(testar);
        testar.setOnClickListener(v->showUniversalScanner("Ventilador","Universal","FAN"));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(52)); tp.setMargins(0,dp(10),0,dp(6)); root.addView(testar,tp);

        Button meus=new Button(this); meus.setText("★  MEUS CONTROLES"); meus.setTextColor(WHITE); meus.setTextSize(13); meus.setAllCaps(false);
        GradientDrawable mb=new GradientDrawable(); mb.setColor(KEY_DARK); mb.setCornerRadius(dp(15)); meus.setBackground(mb); actionFeedback(meus);
        meus.setOnClickListener(v->showMeusControles()); root.addView(meus,new LinearLayout.LayoutParams(-1,dp(48)));

        TextView aviso=label("⚠️ Ventiladores variam bastante entre marcas e modelos. Se uma função não responder, não significa que o emissor do celular esteja com defeito.",11);
        aviso.setTextColor(Color.rgb(190,170,110)); aviso.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(58)); ap.setMargins(0,dp(8),0,dp(2)); root.addView(aviso,ap);

        Button voltar=botaoAcao("← VOLTAR",KEY_DARK,12); voltar.setOnClickListener(v->showSelector());
        root.addView(voltar,new LinearLayout.LayoutParams(-1,dp(48)));

        sv.addView(root); mostrar(sv);
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

    private void build(){
        fanMode=false;
        if(controleAtivo!=null && "Ventilador Universal".equals(controleAtivo.perfil)){ showFanRemote(); return; }
        if(controleAtivo!=null && "AC SmartIR".equals(controleAtivo.perfil)){ abrirSmartIrSalvo(controleAtivo); return; }
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        sv.setBackgroundColor(BG); sv.setClipToPadding(false);

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(8),dp(12),dp(22)); root.setBackgroundColor(BG);

        LinearLayout modelRow=row();
        LinearLayout brandBox=new LinearLayout(this); brandBox.setOrientation(LinearLayout.VERTICAL); brandBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("IR REMOTE BR",22); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        brandBox.addView(title,new LinearLayout.LayoutParams(-1,dp(28)));
        TextView subtitle=label(controleAtivo!=null ? controleAtivo.nome : (lgMode?"LG 32LB620B":"Philips 50PUG6513/7"),12);
        subtitle.setTextColor(GRAY); subtitle.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        brandBox.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(20)));
        modelRow.addView(brandBox,new LinearLayout.LayoutParams(0,dp(50),1));

        Button change= new Button(this);
        change.setText("TROCAR"); change.setTextColor(WHITE); change.setTextSize(12); change.setAllCaps(false);
        GradientDrawable changeBg=new GradientDrawable(); changeBg.setColor(CARD_2); changeBg.setCornerRadius(dp(14)); changeBg.setStroke(dp(1),Color.rgb(60,60,66));
        change.setBackground(changeBg); actionFeedback(change); change.setOnClickListener(v->showSelector());
        modelRow.addView(change,new LinearLayout.LayoutParams(dp(88),dp(44)));
        root.addView(modelRow);

        LinearLayout deviceCard=new LinearLayout(this);
        deviceCard.setOrientation(LinearLayout.VERTICAL);
        deviceCard.setPadding(dp(14),dp(8),dp(14),dp(8));
        GradientDrawable deviceBg=new GradientDrawable();
        deviceBg.setColor(controleAtivo!=null?Color.rgb(28,55,36):CARD);
        deviceBg.setCornerRadius(dp(16));
        deviceBg.setStroke(dp(1),controleAtivo!=null?Color.rgb(80,160,100):Color.rgb(55,55,60));
        deviceCard.setBackground(deviceBg);

        LinearLayout deviceTop=row();
        TextView selected=label(controleAtivo!=null ? controleAtivo.nome : (lgMode?"LG 32LB620B":"Philips 50PUG6513/7"),16);
        selected.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        selected.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        selected.setTextColor(WHITE);
        deviceTop.addView(selected,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView ativoBadge=badge(controleAtivo!=null?"ATIVO":"PADRÃO",controleAtivo!=null?Color.rgb(55,110,65):Color.rgb(65,65,72));
        deviceTop.addView(ativoBadge,new LinearLayout.LayoutParams(-2,dp(28)));
        deviceCard.addView(deviceTop);

        TextView selectedDetail=label(controleAtivo!=null
                ? controleAtivo.marca+" "+controleAtivo.modelo+"  •  "+controleStorage.quantidadeComandos(controleAtivo)+" botões configurados"
                : (lgMode?"LG • perfil NEC":"Philips • perfil RC6"),12);
        selectedDetail.setTextColor(controleAtivo!=null?Color.rgb(175,205,180):GRAY);
        selectedDetail.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        deviceCard.addView(selectedDetail,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout.LayoutParams deviceParams=new LinearLayout.LayoutParams(-1,dp(92));
        deviceParams.setMargins(dp(2),0,dp(2),dp(5));
        root.addView(deviceCard,deviceParams);

        LinearLayout r=row();
        Button powerButton=new Button(this); powerButton.setText("");
        powerButton.setBackgroundResource(R.drawable.power_button); powerButton.setPadding(0,0,0,0);
        powerButton.setContentDescription("Ligar ou desligar a TV");
        powerButton.setHapticFeedbackEnabled(true);
        powerButton.setOnTouchListener((view,event)->{
            if(event.getAction()==android.view.MotionEvent.ACTION_DOWN)
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            return false;
        });
        powerButton.setOnClickListener(v->{ v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); send(POWER); });
        LinearLayout.LayoutParams powerParams=new LinearLayout.LayoutParams(dp(88),dp(88));
        powerParams.setMargins(dp(4),dp(0),dp(4),dp(0)); powerButton.setLayoutParams(powerParams);
        r.addView(powerButton); root.addView(r);

        TextView hint=label(controleAtivo!=null
                ? "Controle ativo • "+controleStorage.quantidadeComandos(controleAtivo)+" botões personalizados"
                : "Controle padrão • você pode salvar seu próprio controle",11);
        hint.setTextColor(GRAY);
        root.addView(hint,new LinearLayout.LayoutParams(-1,dp(24)));

        r=row();
        add(r,key("SOURCE",SOURCE,50,KEY,16)); add(r,key("INFO",INFO,50,KEY,16));
        add(r,key("⚙",SETTINGS,50,KEY,25)); root.addView(r);

        r=row();
        add(r,key("GUIDE",GUIDE,50,KEY,16)); add(r,key("HOME",HOME,50,KEY,16));
        add(r,key(lgMode?"SMART":"NETFLIX",NETFLIX,50,KEY,16)); root.addView(r);

        section(root,"NAVEGAÇÃO");
        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.CENTER); nav.setPadding(dp(34),dp(6),dp(34),dp(6));
        GradientDrawable navBg=new GradientDrawable(); navBg.setColor(Color.rgb(26,26,28));
        navBg.setCornerRadius(dp(26)); navBg.setStroke(dp(1),Color.rgb(55,55,58));
        nav.setBackground(navBg); LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.setMargins(dp(3),dp(2),dp(3),dp(2)); nav.setLayoutParams(np);

        r=row(); add(r,key("▲",UP,56,Color.rgb(60,61,70),22)); nav.addView(r);
        r=row(); add(r,key("◀",LEFT,64,Color.rgb(60,61,70),22));
        add(r,key("OK",OK,64,Color.rgb(60,61,70),18));
        add(r,key("▶",RIGHT,64,Color.rgb(60,61,70),22)); nav.addView(r);
        r=row(); add(r,key("▼",DOWN,56,Color.rgb(60,61,70),22)); nav.addView(r);
        root.addView(nav);

        r=row(); add(r,key("↩  BACK",BACK,50,KEY,16));
        add(r,key("☰  MENU",MENU,50,KEY,16)); add(r,key("EXIT",EXIT,50,KEY,16)); root.addView(r);

        section(root,"VOLUME E CANAIS");
        r=row(); add(r,key("📡 VOL +",VOL_UP,50,KEY,16)); add(r,key("🔇",MUTE,50,KEY,20));
        add(r,key("CH +  +",CH_UP,50,KEY,16)); root.addView(r);
        r=row(); add(r,key("📡 VOL −",VOL_DOWN,50,KEY,16)); add(r,key("TV",SOURCE,50,KEY,16));
        add(r,key("CH −  −",CH_DOWN,50,KEY,16)); root.addView(r);

        section(root,"SMART TV");
        r=row(); add(r,key("RED",RED,50,Color.rgb(145,18,18),14));
        add(r,key("GREEN",GREEN,50,Color.rgb(18,118,48),14));
        add(r,key("YELLOW",YELLOW,50,Color.rgb(166,132,8),14));
        add(r,key("BLUE",BLUE,50,Color.rgb(24,78,155),14)); root.addView(r);

        section(root,"TECLADO");
        String[][] nums={{"1","2 ABC","3 DEF"},{"4 GHI","5 JKL","6 MNO"},{"7 PQRS","8 TUV","9 WXYZ"},{"CC","0","SUBTITLE"}};
        int[][] cmds={{1,2,3},{4,5,6},{7,8,9},{0x3C,0,SUBTITLE}};
        for(int i=0;i<nums.length;i++){ r=row(); for(int j=0;j<3;j++){
            int fs=(i==0&&j==0)?20:14; add(r,key(nums[i][j],cmds[i][j],50,KEY_DARK,fs));
        } root.addView(r); }

        section(root,"CONTROLE DE MÍDIA");
        r=row(); add(r,key("◀◀",REWIND,50,KEY,19)); add(r,key("▶",PLAY,50,KEY,19));
        add(r,key("Ⅱ",PAUSE,50,KEY,19)); add(r,key("■",STOP,50,KEY,19));
        add(r,key("▶▶",FAST_FORWARD,50,KEY,19)); root.addView(r);

        boolean available=ir!=null&&ir.hasIrEmitter();
        String perfilStatus=controleAtivo!=null?controleAtivo.perfil:(lgMode?"LG / NEC":"Philips / RC6");
        TextView status=label(available?"●  Emissor IR detectado  •  "+perfilStatus+" • "+(controleAtivo!=null?controleAtivo.frequencia:(lgMode?38000:36000))+" Hz":"○  Emissor IR não detectado",12);
        status.setTextColor(available?Color.rgb(75,145,95):GRAY);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));

        if(controleAtivo!=null){
            LinearLayout deviceBar=row();
            deviceBar.setPadding(dp(2),dp(2),dp(2),dp(2));
            GradientDrawable barBg=new GradientDrawable();
            barBg.setColor(Color.rgb(24,30,26)); barBg.setCornerRadius(dp(14));
            deviceBar.setBackground(barBg);

            TextView deviceInfo=label("✓  "+controleAtivo.nome+"\n"+controleStorage.quantidadeComandos(controleAtivo)+" botões configurados",12);
            deviceInfo.setTextColor(Color.rgb(105,175,115));
            deviceInfo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            deviceBar.addView(deviceInfo,new LinearLayout.LayoutParams(0,dp(52),1));

            Button configurar=new Button(this);
            configurar.setText("EDITAR");
            configurar.setTextColor(WHITE);
            configurar.setTextSize(11);
            configurar.setAllCaps(false);
            GradientDrawable configBg=new GradientDrawable();
            configBg.setColor(Color.rgb(55,75,60));
            configBg.setCornerRadius(dp(12));
            configurar.setBackground(configBg);
            actionFeedback(configurar);
            configurar.setOnClickListener(v->showAprenderComandos(controleAtivo));
            deviceBar.addView(configurar,new LinearLayout.LayoutParams(dp(82),dp(44)));
            LinearLayout.LayoutParams barParams=new LinearLayout.LayoutParams(-1,dp(56));
            barParams.setMargins(dp(2),dp(5),dp(2),dp(2));
            root.addView(deviceBar,barParams);
        }

        Button meusControles=new Button(this);
        meusControles.setText("★  MEUS CONTROLES  •  "+controleStorage.listar().size());
        meusControles.setTextColor(WHITE);
        meusControles.setTextSize(13);
        meusControles.setAllCaps(false);
        GradientDrawable meusBg=new GradientDrawable();
        meusBg.setColor(KEY_DARK);
        meusBg.setCornerRadius(dp(16));
        meusControles.setBackground(meusBg);
        actionFeedback(meusControles);
        meusControles.setOnClickListener(v->showMeusControles());
        LinearLayout.LayoutParams meusParams=new LinearLayout.LayoutParams(-1,dp(50));
        meusParams.setMargins(dp(2),dp(6),dp(2),0);
        root.addView(meusControles,meusParams);
        sv.addView(root); mostrar(sv);
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