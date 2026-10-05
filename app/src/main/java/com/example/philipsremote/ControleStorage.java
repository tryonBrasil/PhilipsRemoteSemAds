package com.example.philipsremote;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ControleStorage {
    private static final String PREFS = "remote_controls";
    private static final String KEY = "items";
    private final SharedPreferences prefs;

    public static class Controle {
        public long id;
        public String nome, categoria, marca, modelo, perfil, descricao;
        public int codigo, frequencia;
        public JSONObject comandos;

        Controle(long id,String n,String c,String m,String mo,String p,String d,int code,int freq){
            this.id=id; nome=n;categoria=c;marca=m;modelo=mo;perfil=p;descricao=d;codigo=code;frequencia=freq;
            comandos=new JSONObject();
        }
    }

    public ControleStorage(Context c){ prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); }

    public void salvar(String nome,String categoria,String marca,String modelo,String perfil,String descricao,int codigo,int frequencia){
        try{
            JSONArray a=ler();
            JSONObject o=new JSONObject();
            long id=System.currentTimeMillis();
            o.put("id",id);
            o.put("nome",nome); o.put("categoria",categoria); o.put("marca",marca);
            o.put("modelo",modelo); o.put("perfil",perfil); o.put("descricao",descricao);
            o.put("codigo",codigo); o.put("frequencia",frequencia); o.put("created",id);
            o.put("comandos",new JSONObject().toString());
            a.put(o); prefs.edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public List<Controle> listar(){
        List<Controle> out=new ArrayList<>();
        try{
            JSONArray a=ler();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                long id=o.optLong("id",o.optLong("created",0));
                Controle c=new Controle(id,o.optString("nome","Meu controle"),o.optString("categoria","IR"),
                    o.optString("marca",""),o.optString("modelo",""),o.optString("perfil",""),
                    o.optString("descricao",""),o.optInt("codigo",-1),o.optInt("frequencia",38000));
                try{ c.comandos=new JSONObject(o.optString("comandos","{}")); }catch(Exception ignored){}
                out.add(c);
            }
        }catch(Exception ignored){}
        return out;
    }

    public void salvarComando(Controle controle,String funcao,int codigo,String perfil,int frequencia){
        if(controle==null || funcao==null || funcao.isEmpty() || codigo<0) return;
        try{
            JSONArray a=ler();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                long oid=o.optLong("id",o.optLong("created",0));
                if(oid==controle.id && oid>0){
                    JSONObject mapa;
                    try{ mapa=new JSONObject(o.optString("comandos","{}")); }catch(Exception e){ mapa=new JSONObject(); }
                    JSONObject item=new JSONObject();
                    item.put("codigo",codigo); item.put("perfil",perfil); item.put("frequencia",frequencia);
                    mapa.put(funcao,item);
                    o.put("comandos",mapa.toString());
                    a.put(i,o); prefs.edit().putString(KEY,a.toString()).apply();
                    controle.comandos=mapa;
                    return;
                }
            }
        }catch(Exception ignored){}
    }

    public int codigoComando(Controle controle,String funcao){
        if(controle==null || funcao==null) return -1;
        try{
            JSONObject item=controle.comandos.optJSONObject(funcao);
            return item==null?-1:item.optInt("codigo",-1);
        }catch(Exception e){return -1;}
    }

    public String perfilComando(Controle controle,String funcao){
        if(controle==null || funcao==null) return "";
        try{
            JSONObject item=controle.comandos.optJSONObject(funcao);
            return item==null?"":item.optString("perfil","");
        }catch(Exception e){return "";}
    }

    public int frequenciaComando(Controle controle,String funcao){
        if(controle==null || funcao==null) return 0;
        try{
            JSONObject item=controle.comandos.optJSONObject(funcao);
            return item==null?0:item.optInt("frequencia",0);
        }catch(Exception e){return 0;}
    }

    public void excluir(Controle controle){
        if(controle==null) return;
        try{
            JSONArray a=ler(), novo=new JSONArray();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                long oid=o.optLong("id",o.optLong("created",0));
                if(oid!=controle.id) novo.put(o);
            }
            prefs.edit().putString(KEY,novo.toString()).apply();
        }catch(Exception ignored){}
    }

    public void limpar(){ prefs.edit().remove(KEY).apply(); }
    private JSONArray ler(){
        try{return new JSONArray(prefs.getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}
    }
}