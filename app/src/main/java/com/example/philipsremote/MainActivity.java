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
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private ConsumerIrManager ir;
    private IrPerfilTeste irPerfilTeste;
    private boolean toggle = false;
    private boolean lgMode = false;
    private boolean showingSelector = true;
    private SharedPreferences prefs;
    private ControleStorage controleStorage;
    private ControleStorage.Controle controleAtivo;
    private String setupBrand="Philips";
    private String setupModel="50PUG6513/7";
    private UpdateManager updateManager;
    private int aprenderFuncaoPos = 0;

    private static final int FREQ = 36000;
    private static final int LG_FREQ = 38000;
    private static final int UNIT = 444;
    private static final int LG_UNIT = 560;

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


    private static final int POWER=0x0C, MUTE=0x0D, VOL_DOWN=0x11, VOL_UP=0x10;
    private static final int CH_DOWN=0x21, CH_UP=0x20;
    private static final int UP=0x58, DOWN=0x59, LEFT=0x5A, RIGHT=0x5B, OK=0x5C;
    private static final int BACK=0x0A, MENU=0x57, HOME=0x54, SOURCE=0x38;
    private static final int INFO=0x0F, GUIDE=0xCC, NETFLIX=0x76, SETTINGS=0xBF;
    private static final int RED=0x6D, GREEN=0x6E, YELLOW=0x6F, BLUE=0x70;
    private static final int PLAY=0x2C, STOP=0x31, PAUSE=0x30, REWIND=0x2B;
    private static final int FAST_FORWARD=0x28, RECORD=0x37, SUBTITLE=0x4B, EXIT=0x9F;

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
        updateManager.verificarSilenciosamente();
        showSelector();
    }

    private int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }

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

    private void showSelector(){
        showingSelector=true;
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(dp(18),dp(28),dp(18),dp(30));
        TextView brand=label("IR REMOTE BR",13); brand.setTextColor(ACCENT);
        brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD); brand.setLetterSpacing(.14f);
        root.addView(brand,new LinearLayout.LayoutParams(-1,dp(24)));
        TextView title=label("Escolha seu controle",28); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView sub=label("Infravermelho • rápido • sem anúncios",14); sub.setTextColor(GRAY);
        sub.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        root.addView(sub,new LinearLayout.LayoutParams(-1,dp(30)));
        List<ControleStorage.Controle> salvosHome=controleStorage.listar();
        boolean temControlesSalvos=!salvosHome.isEmpty();
        LinearLayout bancoCard=new LinearLayout(this); bancoCard.setOrientation(LinearLayout.HORIZONTAL); bancoCard.setGravity(Gravity.CENTER_VERTICAL);
        bancoCard.setPadding(dp(14),0,dp(14),0);
        GradientDrawable bancoBg=new GradientDrawable(); bancoBg.setColor(CARD); bancoBg.setCornerRadius(dp(15)); bancoBg.setStroke(dp(1),Color.rgb(55,55,60)); bancoCard.setBackground(bancoBg);
        TextView bancoTitulo=label("●  BANCO LOCAL",12); bancoTitulo.setTextColor(Color.rgb(105,190,125)); bancoTitulo.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        bancoCard.addView(bancoTitulo,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView bancoQtd=label(salvosHome.size()+" "+(salvosHome.size()==1?"controle":"controles"),12); bancoQtd.setTextColor(GRAY);
        bancoCard.addView(bancoQtd,new LinearLayout.LayoutParams(-2,dp(42)));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(44)); bp.setMargins(0,dp(10),0,dp(4)); root.addView(bancoCard,bp);
        if(controleAtivo!=null){
            TextView ativo=label("CONTROLE ATIVO  •  "+controleAtivo.nome,13);
            ativo.setTextColor(WHITE);
            GradientDrawable ativoBg=new GradientDrawable(); ativoBg.setColor(Color.rgb(38,70,48)); ativoBg.setCornerRadius(dp(14)); ativo.setBackground(ativoBg);
            ativo.setPadding(dp(14),0,dp(14),0);
            root.addView(ativo,new LinearLayout.LayoutParams(-1,dp(42)));
        }
        if(!temControlesSalvos){
            LinearLayout philips=tvCard("PHILIPS","50PUG6513/7",!lgMode,v->{lgMode=false;});
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(125)); cp.setMargins(0,dp(28),0,dp(10)); root.addView(philips,cp);
            LinearLayout lg=tvCard("LG","32LB620B",lgMode,v->{lgMode=true;});
            LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,dp(125)); cl.setMargins(0,dp(10),0,dp(24)); root.addView(lg,cl);
            TextView chosen=label(lgMode?"✓ LG 32LB620B":"✓ Philips 50PUG6513/7",15); chosen.setTextColor(Color.rgb(75,145,95));
            root.addView(chosen,new LinearLayout.LayoutParams(-1,dp(38)));
        } else {
            TextView acesso=label("Seu controle salvo está disponível em MEUS CONTROLES.",14);
            acesso.setTextColor(GRAY); acesso.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(70)); ap.setMargins(0,dp(18),0,dp(4)); root.addView(acesso,ap);
        }
        Button continueBtn=new Button(this); continueBtn.setText("CONTINUAR"); continueBtn.setTextColor(WHITE); continueBtn.setTextSize(17); continueBtn.setAllCaps(false);
        GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.rgb(190,24,32)); bg.setCornerRadius(dp(18)); continueBtn.setBackground(bg); continueBtn.setOnClickListener(v->{prefs.edit().putBoolean("lg_mode",lgMode).apply();showingSelector=false;build();});
        root.addView(continueBtn,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView info=label("A escolha ficará salva para a próxima vez.",12); info.setTextColor(GRAY); root.addView(info,new LinearLayout.LayoutParams(-1,dp(42)));
        Button meus=new Button(this); meus.setText("★  MEUS CONTROLES"); meus.setTextColor(WHITE); meus.setTextSize(14); meus.setAllCaps(false);
        GradientDrawable meusBg=new GradientDrawable(); meusBg.setColor(KEY_DARK); meusBg.setCornerRadius(dp(16)); meus.setBackground(meusBg); actionFeedback(meus); meus.setOnClickListener(v->showMeusControles());
        root.addView(meus,new LinearLayout.LayoutParams(-1,dp(52)));
        Button atualizar=new Button(this); atualizar.setText("↻  VERIFICAR ATUALIZAÇÃO"); atualizar.setTextColor(WHITE); atualizar.setTextSize(13); atualizar.setAllCaps(false);
        GradientDrawable atualizarBg=new GradientDrawable(); atualizarBg.setColor(KEY_DARK); atualizarBg.setCornerRadius(dp(16)); atualizar.setBackground(atualizarBg); actionFeedback(atualizar); atualizar.setOnClickListener(v->updateManager.verificarManualmente());
        LinearLayout.LayoutParams atualizarParams=new LinearLayout.LayoutParams(-1,dp(50)); atualizarParams.setMargins(0,dp(8),0,0); root.addView(atualizar,atualizarParams);
        sv.addView(root); setContentView(sv);
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

    private int perfilInicialPara(String marca,String modelo,String[] perfis){
        if(marca==null) marca="";
        String alvo="";
        if("LG".equalsIgnoreCase(marca)) alvo="LG / NEC";
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
        return 38000;
    }

    private void showUniversalScanner(String marca,String modelo){
        final String[] perfis=irPerfilTeste.perfis();
        int idx=perfilInicialPara(marca,modelo,perfis);
        final String perfilInicial=perfis[idx];
        irPerfilTeste.selecionar(perfilInicial);
        irPerfilTeste.limparResultado();

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(8),dp(18),dp(8));

        TextView etapa=label("PASSO 3 DE 3  •  TESTE IR",12);
        etapa.setTextColor(ACCENT);
        etapa.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(etapa,new LinearLayout.LayoutParams(-1,dp(28)));

        TextView aparelho=label(marca+" "+modelo,18);
        aparelho.setTextColor(WHITE);
        aparelho.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(aparelho,new LinearLayout.LayoutParams(-1,dp(34)));

        TextView status=label("●  Pronto para testar",15);
        status.setTextColor(GRAY);
        status.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable statusBg=new GradientDrawable();
        statusBg.setColor(CARD);
        statusBg.setCornerRadius(dp(14));
        statusBg.setStroke(dp(1),BORDER);
        status.setPadding(dp(14),0,dp(14),0);
        status.setBackground(statusBg);
        box.addView(status,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView detalhe=label("Perfil automático: "+perfilInicial+"\nAponte o celular para a TV e toque em TESTAR PRÓXIMO.",13);
        detalhe.setTextColor(GRAY);
        detalhe.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(detalhe,new LinearLayout.LayoutParams(-1,dp(70)));

        TextView progresso=label("Candidato 0",12);
        progresso.setTextColor(GRAY);
        progresso.setGravity(Gravity.CENTER);
        box.addView(progresso,new LinearLayout.LayoutParams(-1,dp(28)));

        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("TESTE UNIVERSAL")
            .setView(box)
            .setNegativeButton("CANCELAR",(d,w)->showMeusControles())
            .setNeutralButton("TESTAR PRÓXIMO",null)
            .setPositiveButton("FUNCIONOU / SALVAR",null)
            .create();

        dialog.setOnShowListener(x->{
            Button testar=dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
            Button salvar=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            testar.setOnClickListener(v->{
                String resultado=irPerfilTeste.next();
                status.setText("●  "+resultado);
                status.setTextColor(SUCCESS);
                int atual=irPerfilTeste.position();
                int total=irPerfilTeste.total();
                progresso.setText("Candidato "+atual+" de "+total);
                if(atual>=total){
                    detalhe.setText("Fim dos candidatos deste perfil. Se a TV não respondeu, cancele e tente novamente.");
                }else{
                    detalhe.setText("Código enviado. Observe a TV.\nSe ela respondeu, toque em FUNCIONOU / SALVAR.");
                }
            });
            salvar.setOnClickListener(v->{
                int codigo=irPerfilTeste.currentCode();
                String perfil=irPerfilTeste.getPerfil();
                if(codigo<0 || perfil==null || perfil.isEmpty()){
                    Toast.makeText(this,"Teste pelo menos um código antes de salvar.",Toast.LENGTH_SHORT).show();
                    return;
                }
                int freq=frequenciaPerfil(perfil);
                String nome=marca+" "+modelo;
                String descricao=irPerfilTeste.lastDescription();
                controleStorage.salvar(nome,"TV",marca,modelo,perfil,descricao,codigo,freq);
                dialog.dismiss();
                List<ControleStorage.Controle> salvos=controleStorage.listar();
                if(!salvos.isEmpty()){
                    controleAtivo=salvos.get(salvos.size()-1);
                    prefs.edit().putLong("active_control_id",controleAtivo.id).apply();
                }
                lgMode="LG".equalsIgnoreCase(marca);
                prefs.edit().putBoolean("lg_mode",lgMode).apply();
                Toast.makeText(this,"✓ Controle salvo e definido como ativo.",Toast.LENGTH_SHORT).show();
                showMeusControles();
            });
        });
        dialog.show();
    }

    private void showAprenderComandos(ControleStorage.Controle controle){
        if(controle==null){ showMeusControles(); return; }

        final String[] funcoes={"Ligar/desligar","Mute","Volume +","Volume -","Canal +","Canal -","Cima","Baixo","Esquerda","Direita","OK","Voltar","Menu","Home","Source","Info","Guide","Netflix","Configurações","Vermelho","Verde","Amarelo","Azul","Play","Pause","Stop","Retroceder","Avançar","Subtitle","Exit"};
        final String[] chaves={"POWER","MUTE","VOL_UP","VOL_DOWN","CH_UP","CH_DOWN","UP","DOWN","LEFT","RIGHT","OK","BACK","MENU","HOME","SOURCE","INFO","GUIDE","NETFLIX","SETTINGS","RED","GREEN","YELLOW","BLUE","PLAY","PAUSE","STOP","REWIND","FAST_FORWARD","SUBTITLE","EXIT"};
        final int[] pos={Math.max(0,Math.min(aprenderFuncaoPos,funcoes.length-1))};
        final String[] perfis=irPerfilTeste.perfis();
        final int configuradosInicial=controleStorage.quantidadeComandos(controle);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(4),dp(18),dp(4));

        TextView intro=label("Configure os botões um por vez. Teste os códigos até a TV responder e depois salve.",13);
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

        TextView progress=label("",12);
        progress.setTextColor(Color.rgb(105,175,115));
        progress.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        box.addView(progress,new LinearLayout.LayoutParams(-1,dp(30)));

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
        listaScroll.addView(lista);
        LinearLayout.LayoutParams lsp=new LinearLayout.LayoutParams(-1,dp(260));
        lsp.setMargins(0,dp(2),0,dp(2));
        box.addView(listaScroll,lsp);

        final Button[] botoes=new Button[funcoes.length];

        Runnable atualizarLista=()->{
            int qtd=controleStorage.quantidadeComandos(controle);
            progress.setText(qtd+" de "+funcoes.length+" botões configurados");
            selectedTitle.setText("Configurando: "+funcoes[pos[0]]);
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
            .setView(box)
            .setNegativeButton("FECHAR",(d,w)->showMeusControles())
            .setNeutralButton("TESTAR CÓDIGO",null)
            .setPositiveButton("FUNCIONOU / SALVAR",null)
            .create();

        dialog.setOnShowListener(x->{
            Button testar=dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
            Button salvar=dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);

            testar.setOnClickListener(v->{
                String perfil=controle.perfil;
                if(perfil==null || perfil.isEmpty()){
                    perfil=perfis[perfilInicialPara(controle.marca,controle.modelo,perfis)];
                }
                if(!perfil.equals(irPerfilTeste.getPerfil())){
                    irPerfilTeste.selecionar(perfil);
                }
                String resultado=irPerfilTeste.next();
                status.setText("●  "+resultado+"  •  "+funcoes[pos[0]]);
                status.setTextColor(Color.rgb(205,180,90));
                salvar.setEnabled(true);
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
            });
        });

        dialog.show();
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
            resumo.setText(filtro.isEmpty() ? (todos.size()+" "+(todos.size()==1?"controle salvo":"controles salvos"))
                    : (lista.size()+" resultado"+(lista.size()==1?"":"s")+" para \""+filtro+"\""));
            if(lista.isEmpty()){
                TextView vazio=label(filtro.isEmpty()?"Nenhum controle salvo ainda.":"Nenhum controle encontrado.",15);
                vazio.setTextColor(GRAY); vazio.setGravity(Gravity.CENTER); listaBox.addView(vazio,new LinearLayout.LayoutParams(-1,dp(130)));
                Button addVazio=new Button(this); addVazio.setText("+  ADICIONAR CONTROLE"); addVazio.setTextColor(WHITE); addVazio.setTextSize(13); addVazio.setAllCaps(false);
                GradientDrawable av=new GradientDrawable(); av.setColor(ACCENT); av.setCornerRadius(dp(15)); addVazio.setBackground(av); actionFeedback(addVazio);
                addVazio.setOnClickListener(v->showAddControlWizard()); listaBox.addView(addVazio,new LinearLayout.LayoutParams(-1,dp(50)));
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
                TextView detail=label(c.marca+"  •  "+c.modelo+"\n"+c.perfil+"  •  "+qtd+" botão"+(qtd==1?"":"ões")+" configurado"+(qtd==1?"":"s"),12);
                detail.setTextColor(GRAY); detail.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); card.addView(detail,new LinearLayout.LayoutParams(-1,dp(54)));

                LinearLayout actions=row();
                Button abrir=smallAction("ABRIR",Color.rgb(55,110,65));
                Button config=smallAction("CONFIGURAR",Color.rgb(65,65,72));
                Button copiar=smallAction("DUPLICAR",Color.rgb(55,65,80));
                actions.addView(abrir,new LinearLayout.LayoutParams(0,dp(42),1));
                actions.addView(config,new LinearLayout.LayoutParams(0,dp(42),1));
                actions.addView(copiar,new LinearLayout.LayoutParams(0,dp(42),1));
                card.addView(actions);
                abrir.setOnClickListener(v->{controleAtivo=c;lgMode="LG".equalsIgnoreCase(c.marca);prefs.edit().putLong("active_control_id",c.id).putBoolean("lg_mode",lgMode).apply();showingSelector=false;build();Toast.makeText(this,"✓ "+c.nome+" está ativo",Toast.LENGTH_SHORT).show();});
                config.setOnClickListener(v->{controleAtivo=c;lgMode="LG".equalsIgnoreCase(c.marca);prefs.edit().putLong("active_control_id",c.id).putBoolean("lg_mode",lgMode).apply();showAprenderComandos(c);});
                copiar.setOnClickListener(v->{
                    ControleStorage.Controle novo=controleStorage.duplicar(c,c.nome+" (cópia)");
                    if(novo!=null) Toast.makeText(this,"✓ Controle duplicado",Toast.LENGTH_SHORT).show();
                    render[0].run();
                });

                LinearLayout editRow=row();
                Button renomear=smallAction("RENOMEAR",Color.rgb(55,65,80));
                Button excluir=smallAction("EXCLUIR",Color.rgb(95,48,48));
                editRow.addView(renomear,new LinearLayout.LayoutParams(0,dp(38),1));
                editRow.addView(excluir,new LinearLayout.LayoutParams(0,dp(38),1));
                card.addView(editRow);
                renomear.setOnClickListener(v->{
                    final EditText campo=new EditText(this); campo.setSingleLine(true); campo.setText(c.nome); campo.setSelectAllOnFocus(true); campo.setHint("Nome do controle");
                    new android.app.AlertDialog.Builder(this).setTitle("Renomear controle").setView(campo)
                        .setNegativeButton("CANCELAR",null).setPositiveButton("SALVAR",(d,w)->{
                            String novoNome=campo.getText().toString().trim();
                            if(!novoNome.isEmpty()){controleStorage.renomear(c,novoNome);render[0].run();}
                        }).show();
                });
                excluir.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("Excluir controle?")
                    .setMessage("Remover \""+c.nome+"\" deste aparelho?").setNegativeButton("CANCELAR",null)
                    .setPositiveButton("EXCLUIR",(d,w)->{controleStorage.excluir(c);if(controleAtivo!=null&&controleAtivo.id==c.id){controleAtivo=null;prefs.edit().remove("active_control_id").apply();}render[0].run();}).show());

                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(190)); cp.setMargins(0,dp(6),0,dp(6)); listaBox.addView(card,cp);
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
        sv.addView(root); setContentView(sv);
    }

    private Button smallAction(String text,int color){
        Button b=new Button(this); b.setText(text); b.setTextColor(WHITE); b.setTextSize(11); b.setAllCaps(false); b.setMinHeight(0); b.setMinWidth(0); b.setPadding(0,0,0,0);
        GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(11)); g.setStroke(dp(1),Color.rgb(70,70,75)); b.setBackground(g); actionFeedback(b); return b;
    }

    private void showSobre(){
        String versao="1.3.0";
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
        wizardDialog("PASSO 1 DE 3 • MARCA","Escolha a marca da TV. O aplicativo usará automaticamente o perfil IR mais provável.")
            .setItems(marcas,(d,w)->showModelWizard(marcas[w]))
            .setNegativeButton("CANCELAR",(x,y)->showMeusControles()).show();
    }

    private void showModelWizard(String marca){
        final String[] modelos;
        if("Philips".equals(marca)) modelos=new String[]{"50PUG6513/7","Outro modelo"};
        else if("LG".equals(marca)) modelos=new String[]{"32LB620B","Outro modelo"};
        else if("Samsung".equals(marca)) modelos=new String[]{"Smart TV","Outro modelo"};
        else if("Sony".equals(marca)) modelos=new String[]{"Smart TV","Outro modelo"};
        else if("Panasonic".equals(marca)) modelos=new String[]{"Smart TV","Outro modelo"};
        else if("AOC".equals(marca)) modelos=new String[]{"Smart TV","Outro modelo"};
        else if("TCL".equals(marca)) modelos=new String[]{"Smart TV","Outro modelo"};
        else if("Philco".equals(marca)) modelos=new String[]{"Smart TV","Outro modelo"};
        else modelos=new String[]{"Smart TV","Outro modelo"};

        new android.app.AlertDialog.Builder(this)
            .setTitle("PASSO 2 DE 3 • MODELO")
            .setMessage("Marca selecionada: "+marca+"\\n\\nEscolha um modelo conhecido ou informe o modelo manualmente.")
            .setItems(modelos,(d,w)->{
                if(w==modelos.length-1){
                    final EditText input=new EditText(this);
                    input.setSingleLine(true);
                    input.setHint("Ex.: 50PUG6513/7");
                    input.setTextColor(WHITE);
                    input.setHintTextColor(GRAY);
                    input.setTextSize(16);
                    input.setPadding(dp(12),0,dp(12),0);
                    GradientDrawable inputBg=new GradientDrawable();
                    inputBg.setColor(CARD);
                    inputBg.setCornerRadius(dp(12));
                    inputBg.setStroke(dp(1),BORDER);
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
            })
            .setNegativeButton("VOLTAR",(x,y)->showBrandWizard()).show();
    }

    private void prepararNovoControle(String marca,String modelo){
        lgMode="LG".equals(marca);
        controleAtivo=null;
        prefs.edit().remove("active_control_id").putBoolean("lg_mode",lgMode).apply();
        setupBrand=marca; setupModel=modelo;
        showTvSetup(marca,modelo);
    }

    private void showTvSetup(String marca,String modelo){
        wizardDialog("3 de 3 • Teste IR","TV: "+marca+" "+modelo+"\\n\\n1. Aponte o celular para a TV.\\n2. Toque em TESTAR PRÓXIMO.\\n3. Quando a TV responder, toque em FUNCIONOU / SALVAR.\\n\\nO primeiro código confirmado será usado como base do controle.")
            .setNegativeButton("CANCELAR",(d,w)->showMeusControles())
            .setPositiveButton("INICIAR TESTE",(d,w)->showUniversalScanner(marca,modelo)).show();
    }

    private void build(){
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
        sv.addView(root); setContentView(sv);
    }

    private String funcaoDoComando(int command){
        switch(command){
            case POWER:return "POWER"; case MUTE:return "MUTE";
            case VOL_UP:return "VOL_UP"; case VOL_DOWN:return "VOL_DOWN";
            case CH_UP:return "CH_UP"; case CH_DOWN:return "CH_DOWN";
            case UP:return "UP"; case DOWN:return "DOWN"; case LEFT:return "LEFT"; case RIGHT:return "RIGHT"; case OK:return "OK";
            case BACK:return "BACK"; case MENU:return "MENU"; case HOME:return "HOME"; case SOURCE:return "SOURCE"; case INFO:return "INFO"; case GUIDE:return "GUIDE";
            case NETFLIX:return "NETFLIX"; case SETTINGS:return "SETTINGS"; case RED:return "RED"; case GREEN:return "GREEN"; case YELLOW:return "YELLOW"; case BLUE:return "BLUE"; case PLAY:return "PLAY"; case PAUSE:return "PAUSE"; case STOP:return "STOP";
            case REWIND:return "REWIND"; case FAST_FORWARD:return "FAST_FORWARD"; case SUBTITLE:return "SUBTITLE"; case EXIT:return "EXIT";
            default:return "";
        }
    }

    private boolean enviarComandoSalvo(String funcao){
        if(controleAtivo==null || funcao.isEmpty()) return false;
        int codigo=controleStorage.codigoComando(controleAtivo,funcao);
        String perfil=controleStorage.perfilComando(controleAtivo,funcao);
        if(codigo<0 || perfil.isEmpty()) return false;
        int frequencia=controleStorage.frequenciaComando(controleAtivo,funcao);
         return irPerfilTeste.transmitirSalvo(perfil,codigo,frequencia);
    }

    private void send(int command){
        if(ir==null||!ir.hasIrEmitter()){
            Toast.makeText(this,"Este telemóvel não possui emissor IR.",Toast.LENGTH_SHORT).show(); return;
        }
        try{
            String funcao=funcaoDoComando(command);
            if(enviarComandoSalvo(funcao)){
                Toast.makeText(this,"Código salvo enviado",Toast.LENGTH_SHORT).show();
                return;
            }
            if(command==POWER && controleAtivo!=null && controleAtivo.codigo>=0){
                boolean ok=irPerfilTeste.transmitirSalvo(controleAtivo.perfil,controleAtivo.codigo,controleAtivo.frequencia);
                if(!ok) Toast.makeText(this,"Não foi possível enviar o código salvo",Toast.LENGTH_SHORT).show();
                return;
            }
            // Para controles universais, não envie um código genérico de outro protocolo.
            // Se o botão não foi aprendido, peça ao usuário para configurá-lo.
            if(controleAtivo!=null){
                String perfil=controleAtivo.perfil==null?"":controleAtivo.perfil;
                if("LG / NEC".equals(perfil)) {
                    transmitirSeguro(LG_FREQ,lgNec(command));
                    return;
                }
                if("Philips / RC6".equals(perfil)) {
                    toggle=!toggle;
                    transmitirSeguro(FREQ,rc6(0x00,command,toggle));
                    return;
                }
                Toast.makeText(this,"Este botão ainda não foi configurado. Toque em EDITAR para aprendê-lo.",Toast.LENGTH_SHORT).show();
                return;
            }
            if(lgMode) transmitirSeguro(LG_FREQ,lgNec(command));
            else { toggle=!toggle; transmitirSeguro(FREQ,rc6(0x00,command,toggle)); }
        }catch(Exception e){
            Toast.makeText(this,"Falha ao enviar IR: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

    private int frequenciaIR(int desejada){
        if(ir==null) return desejada;
        try{
            ConsumerIrManager.CarrierFrequencyRange[] ranges=ir.getCarrierFrequencies();
            if(ranges==null || ranges.length==0) return desejada;
            int melhor=desejada; long distancia=Long.MAX_VALUE;
            for(ConsumerIrManager.CarrierFrequencyRange r:ranges){
                int candidato=desejada;
                if(desejada<r.getMinFrequency()) candidato=r.getMinFrequency();
                else if(desejada>r.getMaxFrequency()) candidato=r.getMaxFrequency();
                long d=Math.abs((long)candidato-desejada);
                if(d<distancia){distancia=d;melhor=candidato;}
                if(d==0) break;
            }
            return melhor;
        }catch(Exception e){return desejada;}
    }

    private void transmitirSeguro(int frequencia,int[] padrao){ ir.transmit(frequenciaIR(frequencia),padrao); }

    private int lgCode(int c){
        switch(c){
            case POWER:return 0x08; case MUTE:return 0x09; case VOL_UP:return 0x02; case VOL_DOWN:return 0x03;
            case CH_UP:return 0x00; case CH_DOWN:return 0x01; case UP:return 0x40; case DOWN:return 0x41;
            case LEFT:return 0x07; case RIGHT:return 0x06; case OK:return 0x44; case BACK:return 0x28;
            case MENU:return 0x43; case HOME:return 0x7C; case SOURCE:return 0x0B; case INFO:return 0xAA;
            case GUIDE:return 0xAB; case SETTINGS:return 0x43; case RED:return 0x72; case GREEN:return 0x71;
            case YELLOW:return 0x63; case BLUE:return 0x61; case PLAY:return 0xB0; case STOP:return 0xB1;
            case PAUSE:return 0xBA; case REWIND:return 0x8F; case FAST_FORWARD:return 0x8E;
            case SUBTITLE:return 0x39; case EXIT:return 0x5B; case NETFLIX:return 0xB5;
            case 0x3C:return 0x10; case 0:return 0x10;
            case 1:return 0x11; case 2:return 0x12; case 3:return 0x13; case 4:return 0x14;
            case 5:return 0x15; case 6:return 0x16; case 7:return 0x17; case 8:return 0x18; case 9:return 0x19;
            default:return c & 0xFF;
        }
    }

    private int[] lgNec(int command){
        int data=lgCode(command)&0xFF;
        int[] bytes={0x04,0xFB,data,(~data)&0xFF};
        ArrayList<Integer> p=new ArrayList<>();
        append(p,true,9000); append(p,false,4500);
        for(int b:bytes) for(int m=1;m<=0x80;m<<=1){
            append(p,true,LG_UNIT);
            append(p,false,(b&m)!=0?1690:560);
        }
        append(p,true,LG_UNIT); append(p,false,20000);
        int[] out=new int[p.size()]; for(int i=0;i<p.size();i++) out[i]=p.get(i);
        return out;
    }

    private int[] rc6(int address,int command,boolean tog){
        ArrayList<Integer> p=new ArrayList<>();
        append(p,true,2666); append(p,false,889);
        appendBit(p,1,UNIT); appendBit(p,0,UNIT); appendBit(p,0,UNIT); appendBit(p,0,UNIT);
        appendBit(p,tog?1:0,UNIT*2);
        for(int m=0x80;m!=0;m>>=1) appendBit(p,(address&m)!=0?1:0,UNIT);
        for(int m=0x80;m!=0;m>>=1) appendBit(p,(command&m)!=0?1:0,UNIT);
        append(p,false,2666);
        int[] out=new int[p.size()]; for(int i=0;i<p.size();i++)out[i]=p.get(i); return out;
    }

    private void appendBit(ArrayList<Integer> p,int bit,int half){
        if(bit==1){append(p,true,half);append(p,false,half);}
        else{append(p,false,half);append(p,true,half);}
    }

    private void append(ArrayList<Integer> p,boolean mark,int duration){
        if(duration<=0)return;
        if(p.isEmpty()){if(!mark)p.add(0);p.add(duration);return;}
        boolean expectedMark=(p.size()%2==1);
        if(expectedMark==mark){int i=p.size()-1;p.set(i,p.get(i)+duration);}
        else p.add(duration);
    }
}
