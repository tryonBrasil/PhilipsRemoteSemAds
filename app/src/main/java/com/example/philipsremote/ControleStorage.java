package com.example.philipsremote;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistência dos controles e botões aprendidos (SQLite), com migração do formato antigo em SharedPreferences. */
public class ControleStorage {
    private static final String TAG = "ControleStorage";
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

        Controle(long id, String n, String c, String m, String mo, String p, String d, int code, int freq) {
            this.id = id; nome = n; categoria = c; marca = m; modelo = mo; perfil = p; descricao = d;
            codigo = code; frequencia = freq; comandos = new JSONObject();
        }
    }

    public ControleStorage(Context c) {
        helper = new ControleDatabase(c);
        oldPrefs = c.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE);
        migrarDadosAntigos();
    }

    private static String s(String v) { return v == null ? "" : v; }

    private static String nomeSeguro(String v, String padrao) {
        String n = s(v).trim().replaceAll("\\s+", " ");
        if (n.length() > 80) n = n.substring(0, 80).trim();
        return n.isEmpty() ? padrao : n;
    }

    /** Quantidade atual de controles salvos. */
    public int quantidadeControles() {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM controls", null);
            try { return c.moveToFirst() ? c.getInt(0) : 0; }
            finally { c.close(); }
        } catch (Exception e) {
            Log.e(TAG, "Falha ao contar controles", e);
            return 0;
        }
    }

    private void migrarDadosAntigos() {
        if (oldPrefs.getBoolean(MIGRATED, false)) return;
        SQLiteDatabase db = helper.getWritableDatabase();
        int count = 0;
        Cursor check = db.rawQuery("SELECT COUNT(*) FROM controls", null);
        try { if (check.moveToFirst()) count = check.getInt(0); } finally { check.close(); }
        if (count > 0) { oldPrefs.edit().putBoolean(MIGRATED, true).apply(); return; }

        try {
            JSONArray a = new JSONArray(oldPrefs.getString(OLD_KEY, "[]"));
            db.beginTransaction();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                long oldId = o.optLong("id", o.optLong("created", System.currentTimeMillis() + i));
                ContentValues v = new ContentValues();
                v.put("id", oldId);
                v.put("nome", o.optString("nome", "Meu controle"));
                v.put("categoria", o.optString("categoria", "IR"));
                v.put("marca", o.optString("marca", ""));
                v.put("modelo", o.optString("modelo", ""));
                v.put("perfil", o.optString("perfil", ""));
                v.put("descricao", o.optString("descricao", ""));
                v.put("codigo", o.optInt("codigo", -1));
                v.put("frequencia", o.optInt("frequencia", 38000));
                long created = o.optLong("created", oldId);
                v.put("created", created);
                v.put("updated", created);
                if (db.insertWithOnConflict("controls", null, v, SQLiteDatabase.CONFLICT_IGNORE) == -1) continue;
                JSONObject mapa;
                try { mapa = new JSONObject(o.optString("comandos", "{}")); } catch (Exception e) { mapa = new JSONObject(); }
                JSONArray names = mapa.names();
                if (names != null) for (int j = 0; j < names.length(); j++) {
                    String funcao = names.optString(j, "");
                    JSONObject item = mapa.optJSONObject(funcao);
                    if (item == null) continue;
                    ContentValues cv = new ContentValues();
                    cv.put("control_id", oldId);
                    cv.put("funcao", funcao);
                    cv.put("codigo", item.optInt("codigo", -1));
                    cv.put("perfil", item.optString("perfil", ""));
                    cv.put("frequencia", item.optInt("frequencia", 0));
                    db.insertWithOnConflict("commands", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
            oldPrefs.edit().putBoolean(MIGRATED, true).apply();
        } catch (Exception e) {
            Log.e(TAG, "Falha ao migrar controles antigos (será tentado de novo na próxima abertura)", e);
        } finally {
            if (db.inTransaction()) db.endTransaction();
        }
    }

    /** Salva um novo controle e devolve o id (ou -1 em caso de erro). */
    public long salvar(String nome, String categoria, String marca, String modelo, String perfil, String descricao, int codigo, int frequencia) {
        if (nome == null || nome.trim().isEmpty()) return -1;
        if (codigo < -1 || frequencia < 0) return -1;
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues v = new ContentValues();
            long now = System.currentTimeMillis();
            v.put("nome", nomeSeguro(nome, "Meu controle")); v.put("categoria", s(categoria).trim()); v.put("marca", s(marca).trim());
            v.put("modelo", s(modelo).trim()); v.put("perfil", s(perfil).trim()); v.put("descricao", s(descricao).trim());
            v.put("codigo", codigo); v.put("frequencia", frequencia);
            v.put("created", now); v.put("updated", now);
            return db.insert("controls", null, v);
        } catch (Exception e) {
            Log.e(TAG, "Falha ao salvar controle", e);
            return -1;
        }
    }

    public List<Controle> listar() {
        List<Controle> out = new ArrayList<>();
        Map<Long, Controle> porId = new HashMap<>();
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor c = db.query("controls", null, null, null, null, null, "created ASC, id ASC");
            try {
                while (c.moveToNext()) { Controle item = fromCursor(c); out.add(item); porId.put(item.id, item); }
            } finally { c.close(); }
            Cursor k = db.query("commands", new String[]{"control_id", "funcao", "codigo", "perfil", "frequencia"},
                    null, null, null, null, null);
            try {
                while (k.moveToNext()) {
                    Controle dono = porId.get(k.getLong(0));
                    if (dono != null) colocarComando(dono, k.getString(1), k.getInt(2), k.getString(3), k.getInt(4));
                }
            } finally { k.close(); }
        } catch (Exception e) {
            Log.e(TAG, "Falha ao listar controles", e);
        }
        return out;
    }

    /** Controle por id, ou null se não existir. */
    public Controle buscar(long id) {
        try {
            SQLiteDatabase db = helper.getReadableDatabase();
            Cursor c = db.query("controls", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
            try {
                if (!c.moveToFirst()) return null;
                Controle item = fromCursor(c);
                Cursor k = db.query("commands", new String[]{"funcao", "codigo", "perfil", "frequencia"},
                        "control_id=?", new String[]{String.valueOf(id)}, null, null, null);
                try {
                    while (k.moveToNext()) colocarComando(item, k.getString(0), k.getInt(1), k.getString(2), k.getInt(3));
                } finally { k.close(); }
                return item;
            } finally { c.close(); }
        } catch (Exception e) {
            Log.e(TAG, "Falha ao buscar controle " + id, e);
            return null;
        }
    }

    private Controle fromCursor(Cursor c) {
        return new Controle(
            c.getLong(c.getColumnIndexOrThrow("id")),
            s(c.getString(c.getColumnIndexOrThrow("nome"))),
            s(c.getString(c.getColumnIndexOrThrow("categoria"))),
            s(c.getString(c.getColumnIndexOrThrow("marca"))),
            s(c.getString(c.getColumnIndexOrThrow("modelo"))),
            s(c.getString(c.getColumnIndexOrThrow("perfil"))),
            s(c.getString(c.getColumnIndexOrThrow("descricao"))),
            c.getInt(c.getColumnIndexOrThrow("codigo")),
            c.getInt(c.getColumnIndexOrThrow("frequencia")));
    }

    private static void colocarComando(Controle controle, String funcao, int codigo, String perfil, int frequencia) {
        try {
            JSONObject item = new JSONObject();
            item.put("codigo", codigo); item.put("perfil", s(perfil)); item.put("frequencia", frequencia);
            controle.comandos.put(funcao, item);
        } catch (Exception e) { Log.w(TAG, "Comando ignorado: " + funcao, e); }
    }

    public void salvarComando(Controle controle, String funcao, int codigo, String perfil, int frequencia) {
        if (controle == null || funcao == null || funcao.trim().isEmpty() || codigo < 0) return;
        funcao = funcao.trim();
        if (funcao.length() > 80) funcao = funcao.substring(0, 80);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("control_id", controle.id); v.put("funcao", funcao); v.put("codigo", codigo);
            v.put("perfil", perfil); v.put("frequencia", frequencia);
            db.insertWithOnConflict("commands", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            tocar(db, controle.id);
            colocarComando(controle, funcao, codigo, perfil, frequencia);
        } catch (Exception e) { Log.e(TAG, "Falha ao salvar comando " + funcao, e); }
    }

    private void tocar(SQLiteDatabase db, long id) {
        ContentValues v = new ContentValues();
        v.put("updated", System.currentTimeMillis());
        db.update("controls", v, "id=?", new String[]{String.valueOf(id)});
    }

    public int codigoComando(Controle controle, String funcao) {
        JSONObject item = controle == null || controle.comandos == null ? null : controle.comandos.optJSONObject(funcao);
        return item == null ? -1 : item.optInt("codigo", -1);
    }

    public String perfilComando(Controle controle, String funcao) {
        JSONObject item = controle == null || controle.comandos == null ? null : controle.comandos.optJSONObject(funcao);
        return item == null ? "" : item.optString("perfil", "");
    }

    public int quantidadeComandos(Controle controle) {
        return controle == null || controle.comandos == null ? 0 : controle.comandos.length();
    }

    public boolean possuiComando(Controle controle, String funcao) {
        return controle != null && funcao != null && controle.comandos != null && controle.comandos.has(funcao);
    }

    public int frequenciaComando(Controle controle, String funcao) {
        JSONObject item = controle == null || controle.comandos == null ? null : controle.comandos.optJSONObject(funcao);
        return item == null ? 0 : item.optInt("frequencia", 0);
    }

    public void renomear(Controle controle, String novoNome) {
        if (controle == null || novoNome == null || novoNome.trim().isEmpty()) return;
        String nome = nomeSeguro(novoNome, controle.nome);
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("nome", nome); v.put("updated", System.currentTimeMillis());
            db.update("controls", v, "id=?", new String[]{String.valueOf(controle.id)});
            controle.nome = nome;
        } catch (Exception e) { Log.e(TAG, "Falha ao renomear", e); }
    }

    public void excluir(Controle controle) {
        if (controle == null) return;
        SQLiteDatabase db = null;
        try {
            db = helper.getWritableDatabase();
            db.beginTransaction();
            db.delete("commands", "control_id=?", new String[]{String.valueOf(controle.id)});
            db.delete("controls", "id=?", new String[]{String.valueOf(controle.id)});
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Falha ao excluir", e);
        } finally {
            if (db != null && db.inTransaction()) db.endTransaction();
        }
    }

    public Controle duplicar(Controle original, String novoNome) {
        if (original == null) return null;
        SQLiteDatabase db = null;
        long id = -1;
        try {
            db = helper.getWritableDatabase();
            db.beginTransaction();
            ContentValues v = new ContentValues();
            long now = System.currentTimeMillis();
            v.put("nome", (novoNome == null || novoNome.trim().isEmpty()) ? original.nome + " (cópia)" : novoNome.trim());
            v.put("categoria", s(original.categoria)); v.put("marca", s(original.marca));
            v.put("modelo", s(original.modelo)); v.put("perfil", s(original.perfil));
            v.put("descricao", s(original.descricao)); v.put("codigo", original.codigo);
            v.put("frequencia", original.frequencia); v.put("created", now); v.put("updated", now);
            id = db.insert("controls", null, v);
            if (id < 0) return null;
            JSONArray names = original.comandos == null ? null : original.comandos.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String funcao = names.optString(i, "");
                JSONObject item = original.comandos.optJSONObject(funcao);
                if (item == null || funcao.isEmpty()) continue;
                ContentValues cv = new ContentValues();
                cv.put("control_id", id); cv.put("funcao", funcao);
                cv.put("codigo", item.optInt("codigo", -1));
                cv.put("perfil", item.optString("perfil", ""));
                cv.put("frequencia", Math.max(0, item.optInt("frequencia", 0)));
                if (db.insertWithOnConflict("commands", null, cv, SQLiteDatabase.CONFLICT_REPLACE) < 0) return null;
            }
            db.setTransactionSuccessful();
            return buscar(id);
        } catch (Exception e) {
            Log.e(TAG, "Falha ao duplicar controle", e);
            return null;
        } finally {
            if (db != null && db.inTransaction()) db.endTransaction();
        }
    }

    public String exportarJson() {
        JSONObject raiz = new JSONObject();
        JSONArray controles = new JSONArray();
        try {
            for (Controle c : listar()) {
                JSONObject o = new JSONObject();
                o.put("nome", s(c.nome)); o.put("categoria", s(c.categoria));
                o.put("marca", s(c.marca)); o.put("modelo", s(c.modelo));
                o.put("perfil", s(c.perfil)); o.put("descricao", s(c.descricao));
                o.put("codigo", c.codigo); o.put("frequencia", c.frequencia);
                JSONArray comandos = new JSONArray();
                if (c.comandos != null) {
                    JSONArray nomes = c.comandos.names();
                    if (nomes != null) for (int i=0;i<nomes.length();i++) {
                        String funcao=nomes.optString(i,"");
                        JSONObject item=c.comandos.optJSONObject(funcao);
                        if(item==null||funcao.isEmpty()) continue;
                        JSONObject cmd=new JSONObject();
                        cmd.put("funcao",funcao); cmd.put("codigo",item.optInt("codigo",-1));
                        cmd.put("perfil",item.optString("perfil",""));
                        cmd.put("frequencia",Math.max(0,item.optInt("frequencia",0)));
                        comandos.put(cmd);
                    }
                }
                o.put("comandos",comandos); controles.put(o);
            }
            raiz.put("app","IR Remote BR"); raiz.put("backup_version",1);
            raiz.put("created_at",System.currentTimeMillis()); raiz.put("controles",controles);
            return raiz.toString(2);
        } catch(Exception e){ Log.e(TAG,"Falha ao exportar backup",e); return null; }
    }

    public int importarJson(String json, boolean substituir) {
        if(json==null||json.trim().isEmpty()) return -1;
        SQLiteDatabase db=null;
        try {
            JSONObject raiz=new JSONObject(json);
            if(raiz.optInt("backup_version",0)!=1) return -2;
            JSONArray controles=raiz.optJSONArray("controles");
            if(controles==null) return -3;
            db=helper.getWritableDatabase(); db.beginTransaction();
            if(substituir){ db.delete("commands",null,null); db.delete("controls",null,null); }
            int adicionados=0;
            for(int i=0;i<controles.length();i++){
                JSONObject o=controles.optJSONObject(i); if(o==null) continue;
                ContentValues v=new ContentValues();
                v.put("nome",nomeSeguro(o.optString("nome",""),"Meu controle"));
                v.put("categoria",s(o.optString("categoria","IR"))); v.put("marca",s(o.optString("marca","")));
                v.put("modelo",s(o.optString("modelo",""))); v.put("perfil",s(o.optString("perfil","")));
                v.put("descricao",s(o.optString("descricao",""))); v.put("codigo",o.optInt("codigo",-1));
                v.put("frequencia",Math.max(0,o.optInt("frequencia",38000)));
                long now=System.currentTimeMillis()+i; v.put("created",now); v.put("updated",now);
                long id=db.insert("controls",null,v); if(id<0) continue;
                JSONArray comandos=o.optJSONArray("comandos");
                if(comandos!=null) for(int j=0;j<comandos.length();j++){
                    JSONObject cmd=comandos.optJSONObject(j); if(cmd==null) continue;
                    String funcao=s(cmd.optString("funcao","")).trim(); int codigo=cmd.optInt("codigo",-1);
                    if(funcao.isEmpty()||codigo<0) continue;
                    if(funcao.length()>80) funcao=funcao.substring(0,80);
                    ContentValues cv=new ContentValues(); cv.put("control_id",id); cv.put("funcao",funcao);
                    cv.put("codigo",codigo); cv.put("perfil",s(cmd.optString("perfil","")));
                    cv.put("frequencia",Math.max(0,cmd.optInt("frequencia",0)));
                    db.insertWithOnConflict("commands",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                }
                adicionados++;
            }
            db.setTransactionSuccessful(); return adicionados;
        } catch(Exception e){ Log.e(TAG,"Falha ao importar backup",e); return -1; }
        finally { if(db!=null&&db.inTransaction()) db.endTransaction(); }
    }

    public void limpar() {
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.delete("commands", null, null);
            db.delete("controls", null, null);
        } catch (Exception e) { Log.e(TAG, "Falha ao limpar", e); }
    }
}
