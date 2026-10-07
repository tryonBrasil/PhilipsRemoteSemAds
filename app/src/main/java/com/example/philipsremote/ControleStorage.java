package com.example.philipsremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ControleStorage {
    private static final String OLD_PREFS = "remote_controls";
    private static final String OLD_KEY = "items";
    private static final String MIGRATED = "sqlite_migrated_v1";
    private final ControleDatabase helper;
    private final SharedPreferences oldPrefs;

    public static class Controle {
        public long id;
        public String nome, categoria, marca, modelo, perfil, descricao;
        public int codigo, frequencia;
        public JSONObject comandos;

        Controle(long id,String n,String c,String m,String mo,String p,String d,int code,int freq){
            this.id=id; nome=n; categoria=c; marca=m; modelo=mo; perfil=p; descricao=d;
            codigo=code; frequencia=freq; comandos=new JSONObject();
        }
    }

    public ControleStorage(Context c){
        helper=new ControleDatabase(c);
        oldPrefs=c.getSharedPreferences(OLD_PREFS,Context.MODE_PRIVATE);
        migrarDadosAntigos();
    }

    private void migrarDadosAntigos(){
        if(oldPrefs.getBoolean(MIGRATED,false)) return;
        SQLiteDatabase db=helper.getWritableDatabase();
        Cursor check=db.rawQuery("SELECT COUNT(*) FROM controls",null);
        int count=0;
        try{ if(check.moveToFirst()) count=check.getInt(0); }finally{ check.close(); }
        if(count>0){ oldPrefs.edit().putBoolean(MIGRATED,true).apply(); return; }

        try{
            JSONArray a=new JSONArray(oldPrefs.getString(OLD_KEY,"[]"));
            db.beginTransaction();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);
                long oldId=o.optLong("id",o.optLong("created",System.currentTimeMillis()+i));
                android.content.ContentValues v=new android.content.ContentValues();
                v.put("id",oldId);
                v.put("nome",o.optString("nome","Meu controle"));
                v.put("categoria",o.optString("categoria","IR"));
                v.put("marca",o.optString("marca",""));
                v.put("modelo",o.optString("modelo",""));
                v.put("perfil",o.optString("perfil",""));
                v.put("descricao",o.optString("descricao",""));
                v.put("codigo",o.optInt("codigo",-1));
                v.put("frequencia",o.optInt("frequencia",38000));
                v.put("created",o.optLong("created",oldId));
                long id=db.insertWithOnConflict("controls",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                if(id==-1) continue;
                JSONObject mapa;
                try{ mapa=new JSONObject(o.optString("comandos","{}")); }catch(Exception e){ mapa=new JSONObject(); }
                JSONArray names=mapa.names();
                if(names!=null) for(int j=0;j<names.length();j++){
                    String funcao=names.optString(j,"");
                    JSONObject item=mapa.optJSONObject(funcao);
                    if(item==null) continue;
                    android.content.ContentValues cv=new android.content.ContentValues();
                    cv.put("control_id",oldId);
                    cv.put("funcao",funcao);
                    cv.put("codigo",item.optInt("codigo",-1));
                    cv.put("perfil",item.optString("perfil",""));
                    cv.put("frequencia",item.optInt("frequencia",0));
                    db.insertWithOnConflict("commands",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
            oldPrefs.edit().putBoolean(MIGRATED,true).apply();
        }catch(Exception e){ android.util.Log.w("ControleStorage","Falha ao migrar controles antigos",e); } finally{
            if(db.inTransaction()) db.endTransaction();
        }
    }

    public void salvar(String nome,String categoria,String marca,String modelo,String perfil,String descricao,int codigo,int frequencia){
        SQLiteDatabase db=helper.getWritableDatabase();
        android.content.ContentValues v=new android.content.ContentValues();
        long now=System.currentTimeMillis();
        v.put("nome",nome); v.put("categoria",categoria); v.put("marca",marca);
        v.put("modelo",modelo); v.put("perfil",perfil); v.put("descricao",descricao);
        v.put("codigo",codigo); v.put("frequencia",frequencia); v.put("created",now);
        db.insert("controls",null,v);
    }

    public List<Controle> listar(){
        List<Controle> out=new ArrayList<>();
        SQLiteDatabase db=helper.getReadableDatabase();
        Cursor c=db.query("controls",null,null,null,null,null,"created ASC");
        try{
            while(c.moveToNext()){
                Controle item=fromCursor(c);
                carregarComandos(db,item);
                out.add(item);
            }
        }finally{ c.close(); }
        return out;
    }

    private Controle fromCursor(Cursor c){
        return new Controle(
            c.getLong(c.getColumnIndexOrThrow("id")),
            c.getString(c.getColumnIndexOrThrow("nome")),
            c.getString(c.getColumnIndexOrThrow("categoria")),
            c.getString(c.getColumnIndexOrThrow("marca")),
            c.getString(c.getColumnIndexOrThrow("modelo")),
            c.getString(c.getColumnIndexOrThrow("perfil")),
            c.getString(c.getColumnIndexOrThrow("descricao")),
            c.getInt(c.getColumnIndexOrThrow("codigo")),
            c.getInt(c.getColumnIndexOrThrow("frequencia"))
        );
    }

    private void carregarComandos(SQLiteDatabase db,Controle controle){
        Cursor c=db.query("commands",new String[]{"funcao","codigo","perfil","frequencia"},
                "control_id=?",new String[]{String.valueOf(controle.id)},null,null,"funcao ASC");
        try{
            while(c.moveToNext()){
                JSONObject item=new JSONObject();
                item.put("codigo",c.getInt(1));
                item.put("perfil",c.getString(2)==null?"":c.getString(2));
                item.put("frequencia",c.getInt(3));
                controle.comandos.put(c.getString(0),item);
            }
        }catch(Exception e){ android.util.Log.w("ControleStorage","Falha ao carregar comandos",e); } finally{ c.close(); }
    }

    public void salvarComando(Controle controle,String funcao,int codigo,String perfil,int frequencia){
        if(controle==null || funcao==null || funcao.isEmpty() || codigo<0) return;
        SQLiteDatabase db=helper.getWritableDatabase();
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("control_id",controle.id); v.put("funcao",funcao); v.put("codigo",codigo);
        v.put("perfil",perfil); v.put("frequencia",frequencia);
        db.insertWithOnConflict("commands",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        try{
            JSONObject item=new JSONObject();
            item.put("codigo",codigo); item.put("perfil",perfil); item.put("frequencia",frequencia);
            controle.comandos.put(funcao,item);
        }catch(Exception ignored){}
    }

    public int codigoComando(Controle controle,String funcao){
        JSONObject item=controle==null||controle.comandos==null?null:controle.comandos.optJSONObject(funcao);
        return item==null?-1:item.optInt("codigo",-1);
    }

    public String perfilComando(Controle controle,String funcao){
        JSONObject item=controle==null||controle.comandos==null?null:controle.comandos.optJSONObject(funcao);
        return item==null?"":item.optString("perfil","");
    }

    public int quantidadeComandos(Controle controle){
        return controle==null||controle.comandos==null?0:controle.comandos.length();
    }

    public boolean possuiComando(Controle controle,String funcao){
        return controle!=null && funcao!=null && controle.comandos!=null && controle.comandos.has(funcao);
    }

    public int frequenciaComando(Controle controle,String funcao){
        JSONObject item=controle==null||controle.comandos==null?null:controle.comandos.optJSONObject(funcao);
        return item==null?0:item.optInt("frequencia",0);
    }

    public void renomear(Controle controle,String novoNome){
        if(controle==null||novoNome==null||novoNome.trim().isEmpty()) return;
        String nome=novoNome.trim();
        SQLiteDatabase db=helper.getWritableDatabase();
        android.content.ContentValues v=new android.content.ContentValues();
        v.put("nome",nome);
        db.update("controls",v,"id=?",new String[]{String.valueOf(controle.id)});
        controle.nome=nome;
    }

    public void excluir(Controle controle){
        if(controle==null) return;
        SQLiteDatabase db=helper.getWritableDatabase();
        db.delete("commands","control_id=?",new String[]{String.valueOf(controle.id)});
        db.delete("controls","id=?",new String[]{String.valueOf(controle.id)});
    }

    public Controle duplicar(Controle original,String novoNome){
        if(original==null) return null;
        salvar(novoNome==null||novoNome.trim().isEmpty()?original.nome+" (cópia)":novoNome.trim(),
                original.categoria,original.marca,original.modelo,original.perfil,original.descricao,
                original.codigo,original.frequencia);
        List<Controle> lista=listar();
        if(lista.isEmpty()) return null;
        Controle novo=lista.get(lista.size()-1);
        if(original.comandos!=null){
            JSONArray names=original.comandos.names();
            if(names!=null) for(int i=0;i<names.length();i++){
                String funcao=names.optString(i,"");
                JSONObject item=original.comandos.optJSONObject(funcao);
                if(item!=null) salvarComando(novo,funcao,item.optInt("codigo",-1),
                        item.optString("perfil",""),item.optInt("frequencia",0));
            }
        }
        return novo;
    }

    public String exportarJson(){
        JSONArray controls=new JSONArray();
        for(Controle c:listar()){
            try{
                JSONObject o=new JSONObject();
                o.put("id",c.id);
                o.put("nome",c.nome); o.put("categoria",c.categoria); o.put("marca",c.marca);
                o.put("modelo",c.modelo); o.put("perfil",c.perfil); o.put("descricao",c.descricao);
                o.put("codigo",c.codigo); o.put("frequencia",c.frequencia);
                JSONArray comandos=new JSONArray();
                if(c.comandos!=null){
                    JSONArray nomes=c.comandos.names();
                    if(nomes!=null) for(int i=0;i<nomes.length();i++){
                        String funcao=nomes.optString(i,"");
                        JSONObject item=c.comandos.optJSONObject(funcao);
                        if(item==null) continue;
                        JSONObject cmd=new JSONObject();
                        cmd.put("funcao",funcao);
                        cmd.put("codigo",item.optInt("codigo",-1));
                        cmd.put("perfil",item.optString("perfil",""));
                        cmd.put("frequencia",item.optInt("frequencia",0));
                        comandos.put(cmd);
                    }
                }
                o.put("comandos",comandos);
                controls.put(o);
            }catch(Exception e){ android.util.Log.w("ControleStorage","Falha ao exportar controle",e); }
        }
        JSONObject root=new JSONObject();
        try{
            root.put("format","IRRemoteBR");
            root.put("version",1);
            root.put("controls",controls);
        }catch(Exception e){ android.util.Log.w("ControleStorage","Falha ao montar backup",e); }
        return root.toString(2);
    }

    public int importarJson(String json){
        if(json==null || json.trim().isEmpty()) throw new IllegalArgumentException("Arquivo vazio");
        try{
            JSONObject root=new JSONObject(json);
            if(!"IRRemoteBR".equals(root.optString("format",""))) throw new IllegalArgumentException("Arquivo não é um backup do IR Remote BR");
            JSONArray controls=root.optJSONArray("controls");
            if(controls==null) throw new IllegalArgumentException("Backup sem controles");
            int imported=0;
            for(int i=0;i<controls.length();i++){
                JSONObject o=controls.getJSONObject(i);
                String nome=o.optString("nome","Controle importado");
                salvar(nome,o.optString("categoria","IR"),o.optString("marca",""),o.optString("modelo",""),
                        o.optString("perfil",""),o.optString("descricao",""),o.optInt("codigo",-1),o.optInt("frequencia",38000));
                List<Controle> lista=listar();
                if(lista.isEmpty()) continue;
                Controle novo=lista.get(lista.size()-1);
                JSONArray comandos=o.optJSONArray("comandos");
                if(comandos!=null) for(int j=0;j<comandos.length();j++){
                    JSONObject cmd=comandos.getJSONObject(j);
                    salvarComando(novo,cmd.optString("funcao",""),cmd.optInt("codigo",-1),
                            cmd.optString("perfil",""),cmd.optInt("frequencia",0));
                }
                imported++;
            }
            return imported;
        }catch(JSONException e){
            throw new IllegalArgumentException("JSON inválido",e);
        }
    }

    public void limpar(){
        SQLiteDatabase db=helper.getWritableDatabase();
        db.delete("commands",null,null);
        db.delete("controls",null,null);
    }
}
