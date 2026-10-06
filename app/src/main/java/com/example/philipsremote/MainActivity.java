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

    private static final int FREQ = 36000;
    private static final int LG_FREQ = 38000;
    private static final int UNIT = 444;
    private static final int LG_UNIT = 560;

    private static final int BG = Color.rgb(12,12,12);
    private static final int KEY = Color.rgb(48,48,48);
    private static final int KEY_DARK = Color.rgb(34,34,34);
    private static final int WHITE = Color.WHITE;
    private static final int GRAY = Color.rgb(175,175,175);


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
        g.setColor(color); g.setCornerRadius(dp(18));
        g.setStroke(dp(1),Color.rgb(55,55,58));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.rgb(85,85,90)),g,null));
        b.setHapticFeedbackEnabled(true);
        b.setOnClickListener(v->{ v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP); send(cmd); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(h),1);
        p.setMargins(dp(4),dp(4),dp(4),dp(4)); b.setLayoutParams(p);
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
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setLetterSpacing(.03f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(34));
        p.setMargins(0,dp(7),0,0); root.addView(t,p);
    }

    private void showSelector(){
        showingSelector=true;
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL); root.setPadding(dp(18),dp(28),dp(18),dp(30));
        TextView title=label("ESCOLHA O CONTROLE",28); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView sub=label("Controle remoto por infravermelho • rápido e sem anúncios",14); sub.setTextColor(GRAY);
        root.addView(sub,new LinearLayout.LayoutParams(-1,dp(30)));
        List<ControleStorage.Controle> salvosHome=controleStorage.listar();
        TextView resumo=label(salvosHome.isEmpty()?"Nenhum dispositivo salvo":"● "+salvosHome.size()+" dispositivo"+(salvosHome.size()>1?"s":"")+" salvo"+(salvosHome.size()>1?"s":""),13);
        resumo.setTextColor(salvosHome.isEmpty()?GRAY:Color.rgb(75,145,95));
        root.addView(resumo,new LinearLayout.LayoutParams(-1,dp(30)));
        if(controleAtivo!=null){
            TextView ativo=label("CONTROLE ATIVO  •  "+controleAtivo.nome,13);
            ativo.setTextColor(WHITE);
            GradientDrawable ativoBg=new GradientDrawable(); ativoBg.setColor(Color.rgb(38,70,48)); ativoBg.setCornerRadius(dp(14)); ativo.setBackground(ativoBg);
            ativo.setPadding(dp(14),0,dp(14),0);
            root.addView(ativo,new LinearLayout.LayoutParams(-1,dp(42)));
        }
        LinearLayout philips=tvCard("PHILIPS","50PUG6513/7",!lgMode,v->{lgMode=false;});
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(125)); cp.setMargins(0,dp(28),0,dp(10)); root.addView(philips,cp);
        LinearLayout lg=tvCard("LG","32LB620B",lgMode,v->{lgMode=true;});
        LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,dp(125)); cl.setMargins(0,dp(10),0,dp(24)); root.addView(lg,cl);
        TextView chosen=label(lgMode?"✓ LG 32LB620B":"✓ Philips 50PUG6513/7",15); chosen.setTextColor(Color.rgb(75,145,95));
        root.addView(chosen,new LinearLayout.LayoutParams(-1,dp(38)));
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
        if(updateManager!=null) updateManager.verificarAoAbrir();
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
        for(int i=0;i<perfis.length;i++) if(perfis[i].equals(alvo)) return i;
        return 0;
    }


    private int frequenciaPerfil(String perfil){
        if(perfil==null) return 38000;
        if(perfil.contains("Sony")) return 40000;
        if(perfil.contains("Philips")) return 36000;
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
        box.setPadding(dp(18),dp(8),dp(18),dp(4));
        TextView status=label("Pronto para testar • "+perfilInicial,15);
        status.setTextColor(GRAY);
        status.setGravity(Gravity.CENTER);
        box.addView(status,new LinearLayout.LayoutParams(-1,dp(70)));

        TextView detalhe=label("Pressione TESTAR PRÓXIMO até a TV reagir.\nDepois toque em FUNCIONOU / SALVAR.",13);
        detalhe.setTextColor(GRAY);
        detalhe.setGravity(Gravity.CENTER);
        box.addView(detalhe,new LinearLayout.LayoutParams(-1,dp(64)));

        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("TESTE UNIVERSAL • "+marca+" "+modelo)
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
                status.setText(resultado);
                detalhe.setText("Se a TV respondeu, toque em FUNCIONOU / SALVAR.");
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
                Toast.makeText(this,"✓ Controle salvo em Meus Controles.",Toast.LENGTH_SHORT).show();
                showMeusControles();
            });
        });
        dialog.show();
    }

    private void showAprenderComandos(ControleStorage.Controle controle){
        if(controle==null){ showMeusControles(); return; }
        final String[] funcoes={"Ligar/desligar","Mute","Volume +","Volume -","Canal +","Canal -","Cima","Baixo","Esquerda","Direita","OK","Voltar","Menu","Home","Source","Info","Guide","Netflix","Configurações","Play","Pause","Stop","Retroceder","Avançar","Subtitle","Exit"};
        final String[] chaves={"POWER","MUTE","VOL_UP","VOL_DOWN","CH_UP","CH_DOWN","UP","DOWN","LEFT","RIGHT","OK","BACK","MENU","HOME","SOURCE","INFO","GUIDE","NETFLIX","SETTINGS","PLAY","PAUSE","STOP","REWIND","FAST_FORWARD","SUBTITLE","EXIT"};
        final int[] pos={0};
        final String[] perfis=irPerfilTeste.perfis();

        new android.app.AlertDialog.Builder(this)
            .setTitle("APRENDER BOTÕES • "+controle.nome)
            .setMessage("Selecione uma função. O teste avança um código por vez; quando a TV reagir, salve o código.")
            .setSingleChoiceItems(funcoes,0,(d,which)->pos[0]=which)
            .setNegativeButton("FECHAR",(d,w)->showMeusControles())
            .setNeutralButton("TESTAR CÓDIGO",(d,w)->{
                String perfil=controle.perfil;
                if(perfil==null || perfil.isEmpty()){
                    perfil=perfis[perfilInicialPara(controle.marca,controle.modelo,perfis)];
                }
                if(!perfil.equals(irPerfilTeste.getPerfil())){
                    irPerfilTeste.selecionar(perfil);
                }
                String resultado=irPerfilTeste.next();
                Toast.makeText(this,resultado+" • "+funcoes[pos[0]],Toast.LENGTH_SHORT).show();
                showAprenderComandos(controle);
            })
            .setPositiveButton("FUNCIONOU / SALVAR",(d,w)->{
                int codigo=irPerfilTeste.currentCode();
                String perfil=irPerfilTeste.getPerfil();
                if(codigo<0 || perfil==null || perfil.isEmpty()){
                    Toast.makeText(this,"Primeiro use TESTAR CÓDIGO.",Toast.LENGTH_SHORT).show();
                    showAprenderComandos(controle);
                    return;
                }
                String funcao=chaves[pos[0]];
                controleStorage.salvarComando(controle,funcao,codigo,perfil,frequenciaPerfil(perfil));
                Toast.makeText(this,"✓ "+funcoes[pos[0]]+" configurado",Toast.LENGTH_SHORT).show();
                showAprenderComandos(controle);
            }).show();
    }

    private void showMeusControles(){
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true); sv.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(16),dp(14),dp(28)); root.setBackgroundColor(BG);
        LinearLayout top=row();
        TextView title=label("MEUS CONTROLES",24); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        Button voltar=new Button(this); voltar.setText("VOLTAR"); voltar.setTextColor(WHITE); voltar.setTextSize(12); voltar.setAllCaps(false);
        GradientDrawable vb=new GradientDrawable(); vb.setColor(KEY_DARK); vb.setCornerRadius(dp(14)); voltar.setBackground(vb); voltar.setOnClickListener(v->showSelector());
        top.addView(voltar,new LinearLayout.LayoutParams(dp(90),dp(44))); root.addView(top);

        List<ControleStorage.Controle> lista=controleStorage.listar();
        TextView resumo=label(lista.isEmpty()?"Gerencie seus dispositivos":"Você tem "+lista.size()+" controle"+(lista.size()>1?"s":"")+" salvo"+(lista.size()>1?"s":"")+" neste aparelho",13);
        resumo.setTextColor(GRAY);
        root.addView(resumo,new LinearLayout.LayoutParams(-1,dp(34)));
        if(lista.isEmpty()){
            TextView vazio=label("Você ainda não salvou nenhum controle.\n\nUse o teste universal, confirme um código e salve em Meus controles.",15);
            vazio.setTextColor(GRAY); vazio.setGravity(Gravity.CENTER);
            root.addView(vazio,new LinearLayout.LayoutParams(-1,dp(180)));
        }else{
            for(ControleStorage.Controle c:lista){
                LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(10),dp(16),dp(10));
                GradientDrawable cb=new GradientDrawable(); cb.setColor(KEY_DARK); cb.setCornerRadius(dp(18)); cb.setStroke(dp(1),Color.rgb(55,55,58)); card.setBackground(cb);
                TextView n=label(c.nome,18); n.setTypeface(Typeface.DEFAULT,Typeface.BOLD); n.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
                card.addView(n,new LinearLayout.LayoutParams(-1,dp(34)));
                TextView detail=label(c.categoria+" • "+c.marca+" • "+c.modelo+"\n"+c.perfil+" • "+c.descricao,12);
                detail.setTextColor(GRAY); detail.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
                card.addView(detail,new LinearLayout.LayoutParams(-1,dp(48)));
                Button abrir=new Button(this); abrir.setText("ABRIR CONTROLE"); abrir.setTextColor(WHITE); abrir.setTextSize(12); abrir.setAllCaps(false);
                GradientDrawable ab=new GradientDrawable(); ab.setColor(Color.rgb(55,80,55)); ab.setCornerRadius(dp(12)); abrir.setBackground(ab);
                abrir.setOnClickListener(v->{
                    controleAtivo=c;
                    prefs.edit().putLong("active_control_id",c.id).apply();
                    lgMode="LG".equals(c.marca); build();
                    Toast.makeText(this,"Controle "+c.nome+" carregado",Toast.LENGTH_SHORT).show();
                });
                card.addView(abrir,new LinearLayout.LayoutParams(-1,dp(46)));
                Button editar=new Button(this); editar.setText("✎  RENOMEAR"); editar.setTextColor(WHITE); editar.setTextSize(12); editar.setAllCaps(false);
                GradientDrawable edb=new GradientDrawable(); edb.setColor(Color.rgb(55,65,80)); edb.setCornerRadius(dp(12)); editar.setBackground(edb);
                editar.setOnClickListener(v->{
                    final EditText campo=new EditText(this); campo.setSingleLine(true); campo.setText(c.nome); campo.setSelectAllOnFocus(true); campo.setHint("Nome do controle");
                    int pad=dp(8); campo.setPadding(pad,pad,pad,pad);
                    new android.app.AlertDialog.Builder(this).setTitle("Renomear controle").setView(campo)
                        .setNegativeButton("CANCELAR",null)
                        .setPositiveButton("SALVAR",(d,w)->{
                            String novo=campo.getText().toString().trim();
                            if(novo.isEmpty()){ Toast.makeText(this,"Digite um nome.",Toast.LENGTH_SHORT).show(); return; }
                            controleStorage.renomear(c,novo);
                            if(controleAtivo==c) controleAtivo=c;
                            showMeusControles();
                        }).show();
                });
                LinearLayout.LayoutParams edp=new LinearLayout.LayoutParams(-1,dp(42)); edp.setMargins(0,dp(5),0,0); card.addView(editar,edp);

                Button aprender=new Button(this); aprender.setText("⚙  CONFIGURAR BOTÕES"); aprender.setTextColor(WHITE); aprender.setTextSize(12); aprender.setAllCaps(false);
                GradientDrawable apb=new GradientDrawable(); apb.setColor(Color.rgb(65,65,72)); apb.setCornerRadius(dp(12)); aprender.setBackground(apb);
                aprender.setOnClickListener(v->{ controleAtivo=c; showAprenderComandos(c); });
                LinearLayout.LayoutParams app=new LinearLayout.LayoutParams(-1,dp(42)); app.setMargins(0,dp(5),0,dp(0)); card.addView(aprender,app);
                Button excluir=new Button(this); excluir.setText("EXCLUIR DISPOSITIVO"); excluir.setTextColor(WHITE); excluir.setTextSize(12); excluir.setAllCaps(false);
                GradientDrawable exb=new GradientDrawable(); exb.setColor(Color.rgb(95,48,48)); exb.setCornerRadius(dp(12)); excluir.setBackground(exb);
                excluir.setOnClickListener(v->{
                    new android.app.AlertDialog.Builder(this).setTitle("Excluir dispositivo?")
                        .setMessage("O controle " + c.nome + " será removido deste aparelho.")
                        .setNegativeButton("CANCELAR",null)
                        .setPositiveButton("EXCLUIR",(d,w)->{ controleStorage.excluir(c);
                            if(controleAtivo==c){
                                controleAtivo=null;
                                prefs.edit().remove("active_control_id").apply();
                            } showMeusControles(); })
                        .show();
                });
                LinearLayout.LayoutParams exp=new LinearLayout.LayoutParams(-1,dp(42)); exp.setMargins(0,dp(5),0,dp(0)); card.addView(excluir,exp);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(240)); cp.setMargins(0,dp(8),0,dp(8)); root.addView(card,cp);
            }
        }
        Button sobre=new Button(this); sobre.setText("ⓘ  SOBRE O APLICATIVO"); sobre.setTextColor(WHITE); sobre.setTextSize(13); sobre.setAllCaps(false);
        GradientDrawable sobreBg=new GradientDrawable(); sobreBg.setColor(Color.rgb(45,45,50)); sobreBg.setCornerRadius(dp(16)); sobre.setBackground(sobreBg);
        sobre.setOnClickListener(v->showSobre()); root.addView(sobre,new LinearLayout.LayoutParams(-1,dp(50)));

        Button add=new Button(this); add.setText("+  ADICIONAR OUTRO CONTROLE"); add.setTextColor(WHITE); add.setTextSize(13); add.setAllCaps(false);
        GradientDrawable addBg=new GradientDrawable(); addBg.setColor(Color.rgb(55,55,60)); addBg.setCornerRadius(dp(16)); add.setBackground(addBg);
        add.setOnClickListener(v->showAddControlWizard()); root.addView(add,new LinearLayout.LayoutParams(-1,dp(52)));
        sv.addView(root); setContentView(sv);
    }

    private void showSobre(){
        String versao="1.2.2";
        try{
            android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);
            if(info.versionName!=null) versao=info.versionName;
        }catch(Exception ignored){}
        new android.app.AlertDialog.Builder(this)
            .setTitle("IR Remote BR")
            .setMessage("Controle remoto por infravermelho\n\nVersão "+versao+"\n\nControle TVs compatíveis usando o emissor infravermelho do celular.\n\nSeus controles e configurações são armazenados localmente no aparelho.\n\nPara transmitir IR, o celular precisa possuir emissor infravermelho compatível.")
            .setPositiveButton("OK",null).show();
    }

    private void showAddControlWizard(){
        new android.app.AlertDialog.Builder(this)
            .setTitle("Adicionar controle")
            .setMessage("Vamos configurar sua TV passo a passo.")
            .setPositiveButton("CONTINUAR",(d,w)->showBrandWizard())
            .setNegativeButton("CANCELAR",null).show();
    }

    private void showBrandWizard(){
        final String[] marcas={"Philips","LG","Samsung","Sony"};
        new android.app.AlertDialog.Builder(this).setTitle("1 de 3 • Marca da TV")
            .setItems(marcas,(d,w)->showModelWizard(marcas[w])).setNegativeButton("VOLTAR",(x,y)->showAddControlWizard()).show();
    }

    private void showModelWizard(String marca){
        final String[] modelos;
        if("Philips".equals(marca)) modelos=new String[]{"50PUG6513/7","Outro modelo"};
        else if("LG".equals(marca)) modelos=new String[]{"32LB620B","Outro modelo"};
        else modelos=new String[]{"Modelo não informado","Outro modelo"};
        new android.app.AlertDialog.Builder(this).setTitle("2 de 3 • Modelo")
            .setItems(modelos,(d,w)->{
                lgMode="LG".equals(marca);
                controleAtivo=null;
                prefs.edit().remove("active_control_id").putBoolean("lg_mode",lgMode).apply();
                setupBrand=marca; setupModel=modelos[w];
                showTvSetup(marca,modelos[w]);
            }).setNegativeButton("VOLTAR",(x,y)->showBrandWizard()).show();
    }

    private void showTvSetup(String marca,String modelo){
        new android.app.AlertDialog.Builder(this).setTitle("3 de 3 • Testar controle")
            .setMessage("Vamos procurar automaticamente um código compatível com sua TV. Teste os candidatos e salve somente quando a TV responder.")
            .setNegativeButton("CANCELAR",null)
            .setPositiveButton("ABRIR TESTE UNIVERSAL",(d,w)->showUniversalScanner(marca,modelo)).show();
    }

    private void build(){
        ScrollView sv=new ScrollView(this); sv.setFillViewport(true);
        sv.setBackgroundColor(BG); sv.setClipToPadding(false);

        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(8),dp(10),dp(28)); root.setBackgroundColor(BG);

        LinearLayout modelRow=row();
        TextView title=label("CONTROLE REMOTO",22);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        modelRow.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));

        Button change= new Button(this);
        change.setText("TROCAR TV"); change.setTextColor(WHITE); change.setTextSize(12); change.setAllCaps(false);
        GradientDrawable changeBg=new GradientDrawable(); changeBg.setColor(KEY_DARK); changeBg.setCornerRadius(dp(14));
        change.setBackground(changeBg); actionFeedback(change); change.setOnClickListener(v->showSelector());
        modelRow.addView(change,new LinearLayout.LayoutParams(dp(112),dp(44)));
        root.addView(modelRow);

        TextView selected=label(controleAtivo!=null ? "●  "+controleAtivo.nome+"  •  "+controleAtivo.marca+" "+controleAtivo.modelo : (lgMode?"●  LG • 32LB620B":"●  PHILIPS • 50PUG6513/7"),13);
        selected.setTextColor(controleAtivo!=null?Color.rgb(90,170,105):Color.rgb(150,150,155));
        LinearLayout.LayoutParams selectedParams=new LinearLayout.LayoutParams(-1,dp(32)); selectedParams.setMargins(0,0,0,dp(2)); root.addView(selected,selectedParams);

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
        LinearLayout.LayoutParams powerParams=new LinearLayout.LayoutParams(dp(82),dp(82));
        powerParams.setMargins(dp(4),dp(2),dp(4),dp(2)); powerButton.setLayoutParams(powerParams);
        r.addView(powerButton); root.addView(r);

        r=row();
        add(r,key("SOURCE",SOURCE,50,KEY,16)); add(r,key("INFO",INFO,50,KEY,16));
        add(r,key("⚙",SETTINGS,50,KEY,25)); root.addView(r);

        r=row();
        add(r,key("GUIDE",GUIDE,50,KEY,16)); add(r,key("HOME",HOME,50,KEY,16));
        add(r,key(lgMode?"SMART":"NETFLIX",NETFLIX,50,KEY,16)); root.addView(r);

        section(root,"NAVEGAÇÃO");
        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.VERTICAL);
        nav.setGravity(Gravity.CENTER); nav.setPadding(dp(42),dp(8),dp(42),dp(8));
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
        int[][] cmds={{1,2,3},{4,5,6},{7,8,9},{0,0x3C,SUBTITLE}};
        for(int i=0;i<nums.length;i++){ r=row(); for(int j=0;j<3;j++){
            int fs=(i==0&&j==0)?20:14; add(r,key(nums[i][j],cmds[i][j],50,KEY_DARK,fs));
        } root.addView(r); }

        section(root,"CONTROLE DE MÍDIA");
        r=row(); add(r,key("◀◀",REWIND,50,KEY,19)); add(r,key("▶",PLAY,50,KEY,19));
        add(r,key("Ⅱ",PAUSE,50,KEY,19)); add(r,key("■",STOP,50,KEY,19));
        add(r,key("▶▶",FAST_FORWARD,50,KEY,19)); root.addView(r);

        boolean available=ir!=null&&ir.hasIrEmitter();
        TextView status=label(available?"●  Emissor IR detectado  •  "+(lgMode?"LG 38":"Philips 36")+" kHz":"○  Emissor IR não detectado",12);
        status.setTextColor(available?Color.rgb(75,145,95):GRAY);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));

        if(controleAtivo!=null){
            LinearLayout deviceBar=row();
            TextView deviceInfo=label("✓ "+controleAtivo.nome,13);
            deviceInfo.setTextColor(Color.rgb(75,145,95));
            deviceBar.addView(deviceInfo,new LinearLayout.LayoutParams(0,dp(44),1));

            Button configurar=new Button(this);
            configurar.setText("CONFIGURAR");
            configurar.setTextColor(WHITE);
            configurar.setTextSize(11);
            configurar.setAllCaps(false);
            GradientDrawable configBg=new GradientDrawable();
            configBg.setColor(KEY_DARK);
            configBg.setCornerRadius(dp(12));
            configurar.setBackground(configBg);
            actionFeedback(configurar);
            configurar.setOnClickListener(v->showAprenderComandos(controleAtivo));
            deviceBar.addView(configurar,new LinearLayout.LayoutParams(dp(108),dp(42)));
            root.addView(deviceBar);
        }

        Button meusControles=new Button(this);
        meusControles.setText("★  MEUS CONTROLES");
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
            case NETFLIX:return "NETFLIX"; case SETTINGS:return "SETTINGS"; case PLAY:return "PLAY"; case PAUSE:return "PAUSE"; case STOP:return "STOP";
            case REWIND:return "REWIND"; case FAST_FORWARD:return "FAST_FORWARD"; case SUBTITLE:return "SUBTITLE"; case EXIT:return "EXIT";
            default:return "";
        }
    }

    private boolean enviarComandoSalvo(String funcao){
        if(controleAtivo==null || funcao.isEmpty()) return false;
        int codigo=controleStorage.codigoComando(controleAtivo,funcao);
        String perfil=controleStorage.perfilComando(controleAtivo,funcao);
        if(codigo<0 || perfil.isEmpty()) return false;
        return irPerfilTeste.transmitirSalvo(perfil,codigo);
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
                boolean ok=irPerfilTeste.transmitirSalvo(controleAtivo.perfil,controleAtivo.codigo);
                if(!ok) Toast.makeText(this,"Não foi possível enviar o código salvo",Toast.LENGTH_SHORT).show();
                return;
            }
            if(lgMode) ir.transmit(LG_FREQ,lgNec(command));
            else { toggle=!toggle; ir.transmit(FREQ,rc6(0x00,command,toggle)); }
        }catch(Exception e){
            Toast.makeText(this,"Falha ao enviar IR: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }

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
