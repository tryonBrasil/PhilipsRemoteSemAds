package com.example.philipsremote;

import android.content.Context;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Catálogo local de compatibilidade IR.
 * Os itens são perfis candidatos; o teste IR confirma a compatibilidade real.
 */
public final class IrCatalog {
    public static final class Device {
        public final String type;
        public final String brand;
        public final String model;
        public final String profile;

        Device(String type, String brand, String model, String profile) {
            this.type = type;
            this.brand = brand;
            this.model = model;
            this.profile = profile;
        }

        public String label() {
            return brand + " • " + model;
        }

        public String searchText() {
            return (type + " " + brand + " " + model + " " + profile)
                    .toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final List<Device> devices = new ArrayList<>();

    public IrCatalog(Context context) {
        carregar(context);
    }

    private void carregar(Context context) {
        try (InputStream in = context.getAssets().open("ir_catalog.json")) {
            byte[] data = new byte[in.available()];
            int n = in.read(data);
            JSONObject root = new JSONObject(new String(data, 0, n, StandardCharsets.UTF_8));
            JSONArray arr = root.optJSONArray("devices");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray d = arr.optJSONArray(i);
                    if (d != null && d.length() >= 4) {
                        String type = d.optString(0, "");
                        String brand = d.optString(1, "");
                        String model = d.optString(2, "");
                        String profile = d.optString(3, "");
                        if (!type.isEmpty() && !brand.isEmpty() && !profile.isEmpty()) {
                            devices.add(new Device(type, brand, model, profile));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // O aplicativo continua usando os perfis embutidos se o catálogo não puder ser lido.
        }
    }

    public List<Device> all() {
        return new ArrayList<>(devices);
    }

    public List<Device> filter(String query, String type) {
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<Device> out = new ArrayList<>();
        for (Device d : devices) {
            boolean typeOk = type == null || type.isEmpty() || type.equals(d.type);
            boolean textOk = q.isEmpty() || d.searchText().contains(q);
            if (typeOk && textOk) out.add(d);
        }
        return out;
    }

    public int size() {
        return devices.size();
    }
}
