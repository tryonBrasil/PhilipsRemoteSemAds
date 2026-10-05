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
        public String nome, categoria, marca, modelo, perfil, descricao;
        public int codigo, frequencia;
        Controle(String n,String c,String m,String mo,String p,String d,int code,int freq){
            nome=n;categoria=c;marca=m;modelo=mo;perfil=p;descricao=d;codigo=code;frequencia=freq;
        }
    }

    public ControleStorage(Context c){ prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE); }

    public void salvar(String nome,String categoria,String marca,String modelo,String perfil,String descricao,int codigo,int frequencia){
        try{
            JSONArray a=ler();
            JSONObject o=new JSONObject();
            o.put("nome",nome); o.put("categoria",categoria); o.put("marca",marca);
            o.put("modelo",modelo); o.put("perfil",perfil); o.put("descricao",descricao);
            o.put("codigo",codigo); o.put("frequencia",frequencia); o.put("created",System.currentTimeMillis());
            a.put(o); prefs.edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public List<Controle> listar(){
        List<Controle> out=new ArrayList<>();
        try{
            JSONArray a=ler();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                out.add(new Controle(o.optString("nome","Meu controle"),o.optString("categoria","IR"),
                    o.optString("marca",""),o.optString("modelo",""),o.optString("perfil",""),
                    o.optString("descricao",""),o.optInt("codigo",-1),o.optInt("frequencia",38000)));
            }
        }catch(Exception ignored){}
        return out;
    }

    public void limpar(){ prefs.edit().remove(KEY).apply(); }
    private JSONArray ler(){
        try{return new JSONArray(prefs.getString(KEY,"[]"));}catch(Exception e){return new JSONArray();}
    }
}