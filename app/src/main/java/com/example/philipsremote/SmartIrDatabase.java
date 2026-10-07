package com.example.philipsremote;

import android.util.Base64;
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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Adaptador do formato SmartIR Climate.
 * O índice identifica fabricante/modelo; o JSON do modelo contém os estados completos
 * do ar-condicionado. Cada estado gera um sinal IR completo, em vez de tratar temperatura
 * ou modo como comandos independentes.
 */
public final class SmartIrDatabase {
    private static final String INDEX_URL =
            "https://raw.githubusercontent.com/tonyperkins/smartir-code-aggregator/main/smartir_device_index.json";
    private static final String CODE_URL =
            "https://raw.githubusercontent.com/smartHomeHub/SmartIR/master/codes/climate/";
    private static final int TIMEOUT_MS = 12000;
    private static final double BRDLINK_UNIT_US = 269.0 / 8192.0;

    private SmartIrDatabase() {}

    public static final class Model {
        public final String manufacturer;
        public final String code;
        public final String model;
        public Model(String manufacturer, String code, String model) {
            this.manufacturer=manufacturer; this.code=code; this.model=model;
        }
        @Override public String toString(){ return model; }
    }

    public static final class Climate {
        public final String manufacturer, code;
        public final List<String> models, modes, fans, swings;
        public final int minTemp, maxTemp, precision;
        private final JSONObject commands;

        Climate(String manufacturer,String code,List<String> models,List<String> modes,List<String> fans,List<String> swings,
                int minTemp,int maxTemp,int precision,JSONObject commands){
            this.manufacturer=manufacturer; this.code=code; this.models=models; this.modes=modes;
            this.fans=fans; this.swings=swings; this.minTemp=minTemp; this.maxTemp=maxTemp; this.precision=precision;
            this.commands=commands;
        }

        public String offCommand(){ return commands.optString("off",""); }

        /** Procura o comando de estado mode -> fan -> temperature, tolerando pequenas variações de estrutura. */
        public String command(String mode,String fan,int temperature){ return command(mode,fan,swings.isEmpty()?null:swings.get(0),temperature); }

        public String command(String mode,String fan,String swing,int temperature){
            if(mode==null || mode.trim().isEmpty()) return offCommand();
            JSONObject modeObj=commands.optJSONObject(mode);
            if(modeObj==null) modeObj=findObjectIgnoreCase(commands,mode);
            if(modeObj==null) return "";
            JSONObject fanObj=modeObj;
            if(fan!=null && !fan.isEmpty()){
                JSONObject candidate=modeObj.optJSONObject(fan);
                if(candidate==null) candidate=findObjectIgnoreCase(modeObj,fan);
                if(candidate!=null) fanObj=candidate;
            }
            JSONObject tempObj=fanObj;
            if(swing!=null && !swing.isEmpty()){
                JSONObject candidate=fanObj.optJSONObject(swing);
                if(candidate==null) candidate=findObjectIgnoreCase(fanObj,swing);
                if(candidate!=null) tempObj=candidate;
            }
            String exact=tempObj.optString(String.valueOf(temperature),"");
            if(!exact.isEmpty()) return exact;
            String closest="";
            int best=Integer.MAX_VALUE;
            Iterator<String> it=tempObj.keys();
            while(it.hasNext()){
                String k=it.next();
                try{
                    int t=Integer.parseInt(k);
                    int d=Math.abs(t-temperature);
                    if(d<best && tempObj.optString(k,"").length()>0){best=d;closest=tempObj.optString(k,"");}
                }catch(Exception ignored){}
            }
            return closest;
        }

        private static JSONObject findObjectByTemperature(JSONObject o,int t){
            Iterator<String> it=o.keys();
            while(it.hasNext()){
                String k=it.next();
                Object v=o.opt(k);
                if(v instanceof JSONObject){
                    JSONObject n=(JSONObject)v;
                    if(n.has(String.valueOf(t))) return n;
                }
            }
            return null;
        }

        private static JSONObject findObjectIgnoreCase(JSONObject o,String wanted){
            Iterator<String> it=o.keys();
            while(it.hasNext()){
                String k=it.next();
                if(k.equalsIgnoreCase(wanted)){
                    Object v=o.opt(k);
                    if(v instanceof JSONObject) return (JSONObject)v;
                }
            }
            return null;
        }
    }

    public static List<Model> listarModelos(String marca) throws Exception {
        JSONObject root=getJson(INDEX_URL);
        JSONObject climate=root.optJSONObject("platforms");
        if(climate==null) return Collections.emptyList();
        JSONObject manufacturers=climate.optJSONObject("climate");
        if(manufacturers==null) manufacturers=climate.optJSONObject("climate");
        if(manufacturers==null) return Collections.emptyList();

        String wanted=normalizar(marca);
        List<Model> out=new ArrayList<>();
        Iterator<String> names=manufacturers.keys();
        while(names.hasNext()){
            String manufacturer=names.next();
            if(!normalizar(manufacturer).contains(wanted)) continue;
            JSONObject data=manufacturers.optJSONObject(manufacturer);
            if(data==null) continue;
            JSONArray entries=data.optJSONArray("models");
            if(entries==null) continue;
            for(int i=0;i<entries.length();i++){
                JSONObject e=entries.optJSONObject(i);
                if(e==null) continue;
                String code=e.optString("code","");
                JSONArray ms=e.optJSONArray("models");
                if(ms==null) continue;
                for(int j=0;j<ms.length();j++){
                    String model=ms.optString(j,"").trim();
                    if(!model.isEmpty()) out.add(new Model(manufacturer,code,model));
                }
            }
        }
        Collections.sort(out,Comparator.comparing(x->x.model.toLowerCase(Locale.ROOT)));
        return out;
    }

    public static Climate carregar(Model model) throws Exception {
        if(model==null || model.code.isEmpty()) throw new IllegalArgumentException("Modelo inválido");
        String url=CODE_URL+model.code+".json";
        return parse(getText(url));
    }

    public static Climate carregarPorUrl(String url) throws Exception {
        return parse(getText(url));
    }

    public static Climate parse(String json) throws Exception {
        JSONObject o=new JSONObject(json);
        String manufacturer=o.optString("manufacturer","Desconhecido");
        String code=o.optString("device_code","");
        List<String> models=toList(o.optJSONArray("supportedModels"));
        List<String> modes=toList(o.optJSONArray("operationModes"));
        List<String> fans=toList(o.optJSONArray("fanModes"));
        List<String> swings=toList(o.optJSONArray("swingModes"));
        int min=(int)Math.round(o.optDouble("minTemperature",16));
        int max=(int)Math.round(o.optDouble("maxTemperature",30));
        int precision=(int)Math.round(o.optDouble("precision",1));
        JSONObject commands=o.optJSONObject("commands");
        if(commands==null) commands=new JSONObject();
        return new Climate(manufacturer,code,models,modes,fans,swings,min,max,Math.max(1,precision),commands);
    }

    /**
     * Converte o Base64 Broadlink usado pelo SmartIR em pulsos de microssegundos.
     * O formato usa unidade 269/8192 s e valores longos codificados com prefixo 0x00.
     */
    public static int[] decodeBase64(String value) throws Exception {
        if(value==null || value.trim().isEmpty()) return new int[0];
        byte[] b=Base64.decode(value,Base64.DEFAULT);
        if(b.length<8) return new int[0];
        int payloadLength=(b[2]&255)|((b[3]&255)<<8);
        int end=Math.min(b.length,4+payloadLength);
        int i=4;
        List<Integer> out=new ArrayList<>();
        while(i<end){
            int raw;
            if((b[i]&255)==0){
                if(i+2>=end) break;
                raw=((b[i+1]&255)<<8)|(b[i+2]&255);
                i+=3;
            }else{
                raw=b[i]&255;
                i++;
            }
            if(raw<=0) continue;
            int us=(int)Math.round(raw/BRDLINK_UNIT_US);
            if(us>0 && us<=1000000) out.add(us);
        }
        int[] result=new int[out.size()];
        for(i=0;i<result.length;i++) result[i]=out.get(i);
        return result;
    }

    private static List<String> toList(JSONArray a){
        List<String> out=new ArrayList<>();
        if(a==null) return out;
        for(int i=0;i<a.length();i++){
            String s=a.optString(i,"").trim();
            if(!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static String normalizar(String s){
        if(s==null) return "";
        return java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]","");
    }

    private static JSONObject getJson(String url) throws Exception {
        return new JSONObject(getText(url));
    }

    private static String getText(String urlString) throws Exception {
        HttpURLConnection c=null;
        try{
            URL u=new URL(urlString);
            c=(HttpURLConnection)u.openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setRequestMethod("GET");
            c.setRequestProperty("Accept","application/json");
            c.setRequestProperty("User-Agent","IR-Remote-BR");
            int code=c.getResponseCode();
            InputStream in=code>=200 && code<300?c.getInputStream():c.getErrorStream();
            String body=read(in);
            if(code<200 || code>=300) throw new IllegalStateException("Banco de ar-condicionado indisponível ("+code+")");
            return body;
        }finally{if(c!=null)c.disconnect();}
    }

    private static String read(InputStream in) throws Exception {
        if(in==null) return "";
        BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();
        String line;
        while((line=r.readLine())!=null) b.append(line).append('\n');
        r.close();
        return b.toString();
    }
}
