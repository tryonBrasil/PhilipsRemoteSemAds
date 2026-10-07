package com.example.philipsremote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Banco IR sob demanda baseado no formato .ir do Flipper-IRDB.
 * Não baixa a base inteira: consulta apenas a categoria/marca solicitada e baixa
 * o arquivo do modelo escolhido. Isso mantém o APK pequeno e permite atualizações
 * independentes do aplicativo.
 */
public final class IrRemoteDatabase {
    private static final String API = "https://api.github.com/repos/Lucaslhm/Flipper-IRDB/contents/";
    private static final int TIMEOUT_MS = 12000;

    private IrRemoteDatabase() {}

    public static final class RemoteFile {
        public final String category, brand, model, path, downloadUrl;
        RemoteFile(String c, String b, String m, String p, String u) {
            category=c; brand=b; model=m; path=p; downloadUrl=u;
        }
        @Override public String toString() { return model; }
    }

    public static final class Signal {
        public final String name;
        public final int frequency;
        public final int[] pattern;
        public final String protocol;
        public final int address, command;
        public final boolean raw;

        Signal(String n, int f, int[] p, String proto, int a, int cmd, boolean r) {
            name=n; frequency=f; pattern=p; protocol=proto; address=a; command=cmd; raw=r;
        }
    }

    /** Lista modelos da categoria/marca, consultando apenas os diretórios necessários. */
    public static List<RemoteFile> listar(String categoria, String marca) throws Exception {
        String pasta = pastaCategoria(categoria);
        List<JSONObject> raiz = getJsonArray(API + pasta);
        String alvo = normalizar(marca);
        String brandPath = null;
        String brandName = marca == null ? "" : marca.trim();

        for (JSONObject o : raiz) {
            if (!"dir".equals(o.optString("type"))) continue;
            String nome = o.optString("name", "");
            if (normalizar(nome).equals(alvo)) {
                brandPath = o.optString("path", "");
                brandName = nome;
                break;
            }
        }

        List<RemoteFile> out = new ArrayList<>();
        if (brandPath != null && !brandPath.isEmpty()) {
            for (JSONObject o : getJsonArray(API + brandPath)) {
                if (!"file".equals(o.optString("type"))) continue;
                String n=o.optString("name","");
                if (!n.toLowerCase(Locale.ROOT).endsWith(".ir")) continue;
                out.add(new RemoteFile(categoria,brandName,semExt(n),o.optString("path",""),o.optString("download_url","")));
            }
        } else {
            // Algumas marcas aparecem diretamente na pasta da categoria.
            for (JSONObject o : raiz) {
                if (!"file".equals(o.optString("type"))) continue;
                String n=o.optString("name","");
                if (!n.toLowerCase(Locale.ROOT).endsWith(".ir")) continue;
                if (normalizar(n).contains(alvo)) {
                    out.add(new RemoteFile(categoria,marca,semExt(n),o.optString("path",""),o.optString("download_url","")));
                }
            }
        }
        Collections.sort(out, Comparator.comparing(x -> x.model.toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Baixa e converte um arquivo .ir em sinais transmitíveis pelo ConsumerIrManager. */
    public static List<Signal> baixarSinais(RemoteFile remote) throws Exception {
        if (remote == null || remote.downloadUrl == null || remote.downloadUrl.isEmpty()) return Collections.emptyList();
        String body = getText(remote.downloadUrl);
        return parse(body);
    }

    public static List<Signal> parse(String text) throws Exception {
        List<Signal> out=new ArrayList<>();
        if(text==null) return out;
        String[] blocos=text.split("(?m)^\\s*#\\s*$");
        String currentComment="";
        for(String bloco:blocos){
            String name=valor(bloco,"name");
            String type=valor(bloco,"type");
            if(name.isEmpty() || type.isEmpty()) continue;
            if("raw".equalsIgnoreCase(type)){
                int freq=parseIntSeguro(valor(bloco,"frequency"),38000);
                String data=valor(bloco,"data");
                int[] p=parsePattern(data);
                if(p.length>0) out.add(new Signal(name,freq,p,"RAW",0,0,true));
            } else if("parsed".equalsIgnoreCase(type)){
                String protocol=valor(bloco,"protocol");
                int address=parseFlipperHex(valor(bloco,"address"));
                int command=parseFlipperHex(valor(bloco,"command"));
                int[] p=encodeParsed(protocol,address,command);
                if(p!=null && p.length>0) out.add(new Signal(name,freqForProtocol(protocol),p,protocol,address,command,false));
            }
        }
        return out;
    }

    private static int[] encodeParsed(String protocol,int address,int command){
        if(protocol==null) return null;
        String p=protocol.trim().toUpperCase(Locale.ROOT);
        if("NEC".equals(p) || "NECEXT".equals(p) || "NEC42".equals(p) || "NEC42EXT".equals(p))
            return IrEncoder.nec(address & 255, command & 255);
        if("SAMSUNG32".equals(p)) return IrEncoder.samsung(address & 255,command & 255);
        if("RC5".equals(p) || "RC5X".equals(p)) return IrEncoder.rc5(address & 31,command & 127,false);
        if("RC6".equals(p)) return IrEncoder.rc6(address & 255,command & 255,false);
        if("SIRC".equals(p)) return IrEncoder.sony(address & 31,command & 127);
        return null;
    }

    private static int freqForProtocol(String p){
        if(p==null) return 38000;
        String x=p.toUpperCase(Locale.ROOT);
        if(x.startsWith("SIRC")) return 40000;
        if(x.startsWith("RC5") || x.startsWith("RC6")) return 36000;
        return 38000;
    }

    private static int parseIntSeguro(String s,int padrao){ try { return Integer.parseInt(s.trim()); } catch(Exception e){ return padrao; } }

    private static int parseFlipperHex(String s){
        if(s==null || s.trim().isEmpty()) return 0;
        String[] b=s.trim().split("\\s+");
        long v=0;
        for(int i=0;i<b.length && i<4;i++){
            try { v |= (Long.parseLong(b[i],16)&255L) << (8*i); } catch(Exception ignored){}
        }
        return (int)v;
    }

    private static int[] parsePattern(String s){
        if(s==null || s.trim().isEmpty()) return new int[0];
        String[] a=s.trim().split("\\s+");
        int[] out=new int[a.length]; int n=0;
        for(String x:a){
            try{
                long v=Long.parseLong(x);
                if(v>0 && v<=1000000L) out[n++]=(int)v;
            }catch(Exception ignored){}
        }
        int[] r=new int[n];
        System.arraycopy(out,0,r,0,n);
        return r;
    }

    private static String valor(String bloco,String chave){
        String prefix=chave+":";
        for(String linha:bloco.split("\\R")){
            String x=linha.trim();
            if(x.startsWith(prefix)) return x.substring(prefix.length()).trim();
        }
        return "";
    }

    private static String pastaCategoria(String categoria){
        if(categoria==null) return "Fans";
        String c=categoria.toUpperCase(Locale.ROOT);
        if(c.contains("AC") || c.contains("AR")) return "ACs";
        if(c.contains("TV")) return "TVs";
        if(c.contains("FAN") || c.contains("VENT")) return "Fans";
        return "Fans";
    }

    private static String semExt(String n){
        return n.toLowerCase(Locale.ROOT).endsWith(".ir") ? n.substring(0,n.length()-3) : n;
    }

    private static String normalizar(String s){
        if(s==null) return "";
        return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
    }

    private static List<JSONObject> getJsonArray(String url) throws Exception {
        String body=getText(url);
        JSONArray a=new JSONArray(body);
        List<JSONObject> out=new ArrayList<>();
        for(int i=0;i<a.length();i++) out.add(a.getJSONObject(i));
        return out;
    }

    private static String getText(String urlString) throws Exception {
        HttpURLConnection c=null;
        try{
            URL u=new URL(urlString);
            c=(HttpURLConnection)u.openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept","application/vnd.github+json");
            c.setRequestProperty("User-Agent","IR-Remote-BR");
            int code=c.getResponseCode();
            InputStream in=code>=200 && code<300?c.getInputStream():c.getErrorStream();
            String body=read(in);
            if(code<200 || code>=300) throw new IllegalStateException("Banco online indisponível ("+code+")");
            return body;
        }finally{ if(c!=null)c.disconnect(); }
    }

    private static String read(InputStream in) throws Exception {
        if(in==null) return "";
        BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder(); String line;
        while((line=r.readLine())!=null) b.append(line).append('\
');
        r.close(); return b.toString();
    }
}
