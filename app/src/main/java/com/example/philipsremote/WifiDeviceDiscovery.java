package com.example.philipsremote;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers common smart-TV services on the local Wi-Fi network.
 * Discovery does not imply that a device supports remote commands; each
 * manufacturer/platform still needs its own pairing and control protocol.
 */
public final class WifiDeviceDiscovery {
    private static final String TAG = "WifiDeviceDiscovery";
    private static final String[] SERVICE_TYPES = {
            "_androidtvremote2._tcp.",
            "_googlecast._tcp.",
            "_samsungmsf._tcp.",
            "_webos-second-screen._tcp."
    };

    public interface Listener {
        void onDeviceFound(Device device);
        void onStatus(String message);
    }

    public static final class Device {
        public final String name;
        public final String host;
        public final int port;
        public final String serviceType;
        public final String platform;

        Device(String name, String host, int port, String serviceType) {
            this.name = name == null || name.trim().isEmpty() ? "Dispositivo Smart" : name;
            this.host = host == null ? "" : host;
            this.port = port;
            this.serviceType = serviceType == null ? "" : serviceType;
            this.platform = platformFor(serviceType);
        }

        private static String platformFor(String type) {
            if (type == null) return "Desconhecido";
            if (type.contains("androidtvremote")) return "Android TV / Google TV";
            if (type.contains("googlecast")) return "Google Cast";
            if (type.contains("samsungmsf")) return "Samsung";
            if (type.contains("webos")) return "LG webOS";
            return "Outro";
        }

        @Override public String toString() {
            return name + " — " + platform + (host.isEmpty() ? "" : "\n" + host + ":" + port);
        }

        String uniqueKey() {
            return serviceType + "|" + name + "|" + host + "|" + port;
        }
    }

    private final NsdManager nsd;
    private final Listener listener;
    private final Map<String, NsdManager.DiscoveryListener> active =
            new LinkedHashMap<>();
    private final Map<String, Device> found = new LinkedHashMap<>();
    private boolean scanning;

    public WifiDeviceDiscovery(Context context, Listener listener) {
        this.nsd = (NsdManager) context.getApplicationContext()
                .getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    public synchronized void start() {
        stop();
        found.clear();
        if (nsd == null) {
            listener.onStatus("A descoberta de dispositivos não está disponível neste celular.");
            return;
        }
        scanning = true;
        listener.onStatus("Procurando TVs e dispositivos compatíveis na rede Wi-Fi…");
        for (String type : SERVICE_TYPES) startType(type);
    }

    private void startType(final String type) {
        NsdManager.DiscoveryListener discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String serviceType) {
                listener.onStatus("Busca ativa: " + serviceType);
            }

            @Override public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (serviceInfo == null || serviceInfo.getServiceType() == null) return;
                resolve(serviceInfo);
            }

            @Override public void onServiceLost(NsdServiceInfo serviceInfo) {
                // Keep already discovered devices visible for this scan session.
            }

            @Override public void onDiscoveryStopped(String serviceType) { }

            @Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                stopListener(type);
                Log.w(TAG, "Discovery failed for " + serviceType + ": " + errorCode);
            }

            @Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                stopListener(type);
                Log.w(TAG, "Stopping discovery failed for " + serviceType + ": " + errorCode);
            }
        };
        synchronized (this) {
            if (!scanning) return;
            active.put(type, discoveryListener);
        }
        try {
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (RuntimeException error) {
            stopListener(type);
            Log.w(TAG, "Could not start discovery for " + type, error);
        }
    }

    @SuppressWarnings("deprecation")
    private void resolve(final NsdServiceInfo serviceInfo) {
        try {
            nsd.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    Log.d(TAG, "Could not resolve service: " + errorCode);
                }

                @Override public void onServiceResolved(NsdServiceInfo info) {
                    String host = info.getHost() == null ? "" : info.getHost().getHostAddress();
                    Device device = new Device(info.getServiceName(), host,
                            info.getPort(), info.getServiceType());
                    synchronized (WifiDeviceDiscovery.this) {
                        if (!scanning || found.containsKey(device.uniqueKey())) return;
                        found.put(device.uniqueKey(), device);
                    }
                    listener.onDeviceFound(device);
                }
            });
        } catch (RuntimeException error) {
            Log.d(TAG, "Service resolution unavailable", error);
        }
    }

    public synchronized List<Device> getFoundDevices() {
        return Collections.unmodifiableList(new ArrayList<>(found.values()));
    }

    public synchronized void stop() {
        scanning = false;
        for (Map.Entry<String, NsdManager.DiscoveryListener> entry :
                new ArrayList<>(active.entrySet())) {
            try {
                nsd.stopServiceDiscovery(entry.getValue());
            } catch (RuntimeException error) {
                Log.d(TAG, "Discovery already stopped: " + entry.getKey());
            }
        }
        active.clear();
    }

    private synchronized void stopListener(String type) {
        active.remove(type);
    }
}
