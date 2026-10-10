package com.example.philipsremote;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.DhcpInfo;
import android.util.Log;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

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
            if (type.contains("philips-jointspace")) return "Philips";
            if (type.contains("lg-netcast")) return "LG";
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
    private volatile boolean scanning;
    private final Context applicationContext;
    private WifiManager.MulticastLock multicastLock;
    private ExecutorService subnetExecutor;
    private volatile int scanGeneration = 0;
    private static final int[][] CANDIDATE_PORTS = {{1925,1},{8080,2},{3000,3},{8008,4},{8009,4},{8001,5},{8002,5}};

    public WifiDeviceDiscovery(Context context, Listener listener) {
        this.applicationContext = context.getApplicationContext();
        this.nsd = (NsdManager) applicationContext.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    public synchronized void start() {
        stop();
        found.clear();
        scanning = true;
        final int generation = ++scanGeneration;
        acquireMulticastLock();
        listener.onStatus("Procurando dispositivos anunciados e verificando a rede local…");
        if (nsd != null) {
            for (String type : SERVICE_TYPES) startType(type);
        }
        scanLocalSubnet(generation);
    }


    private void acquireMulticastLock() {
        try {
            WifiManager wifi = (WifiManager) applicationContext.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("PhilipsRemoteWifiDiscovery");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not acquire Wi-Fi multicast lock", error);
        }
    }

    private void scanLocalSubnet(final int generation) {
        WifiManager wifi = (WifiManager) applicationContext.getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi == null ? null : wifi.getDhcpInfo();
        if (dhcp == null || dhcp.ipAddress == 0 || dhcp.netmask == 0) {
            listener.onStatus("Não consegui identificar a rede Wi-Fi. Confira o Wi-Fi ou informe o IP da TV.");
            return;
        }
        int localIp = Integer.reverseBytes(dhcp.ipAddress);
        int mask = Integer.reverseBytes(dhcp.netmask);
        final long network = (((long)(localIp & mask)) & 0xffffffffL);
        final long broadcast = network | (((long)(~mask)) & 0xffffffffL);
        final long first = network + 1;
        final long last = broadcast - 1;
        if (last < first || last - first > 1022) {
            listener.onStatus("A rede é grande demais para a busca automática. Informe o IP da TV.");
            return;
        }
        final int total = (int)(last - first + 1);
        final AtomicInteger completed = new AtomicInteger();
        subnetExecutor = Executors.newFixedThreadPool(24);
        listener.onStatus("Verificando " + total + " endereços da rede Wi-Fi…");
        for (long address = first; address <= last; address++) {
            if (!isScanning(generation)) break;
            final String host = toIpv4(address);
            subnetExecutor.execute(() -> {
                try {
                    for (int[] item : CANDIDATE_PORTS) {
                        if (!isScanning(generation)) return;
                        if (!portOpen(host, item[0], 180)) continue;
                        String platform;
                        String serviceType;
                        switch (item[1]) {
                            case 1: platform = "Philips JointSpace"; serviceType = "_philips-jointspace._tcp."; break;
                            case 2: platform = "LG NetCast (possível)"; serviceType = "_lg-netcast._tcp."; break;
                            case 3: platform = "LG webOS (possível)"; serviceType = "_webos._tcp."; break;
                            case 4: platform = "Google Cast (possível)"; serviceType = "_googlecast._tcp."; break;
                            default: platform = "Samsung (possível)"; serviceType = "_samsungmsf._tcp."; break;
                        }
                        Device device = new Device(platform + " • " + host, host, item[0], serviceType);
                        synchronized (WifiDeviceDiscovery.this) {
                            if (!isScanning(generation) || found.containsKey(device.uniqueKey())) return;
                            found.put(device.uniqueKey(), device);
                        }
                        listener.onDeviceFound(device);
                        break;
                    }
                } finally {
                    int done = completed.incrementAndGet();
                    if (done == total && isScanning(generation)) {
                        List<Device> results = getFoundDevices();
                        listener.onStatus(results.isEmpty()
                                ? "Busca concluída: nenhum serviço compatível encontrado. Use CONECTAR POR IP."
                                : "Busca concluída. Candidatos encontrados: " + results.size());
                    } else if (done % 32 == 0 && isScanning(generation)) {
                        listener.onStatus("Verificando rede Wi-Fi… " + done + "/" + total);
                    }
                }
            });
        }
    }

    private boolean isScanning(int generation) {
        return scanning && scanGeneration == generation;
    }

    private static boolean portOpen(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String toIpv4(long address) {
        return ((address >> 24) & 255) + "." + ((address >> 16) & 255) + "."
                + ((address >> 8) & 255) + "." + (address & 255);
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
        scanGeneration++;
        if (subnetExecutor != null) { subnetExecutor.shutdownNow(); subnetExecutor = null; }
        for (Map.Entry<String, NsdManager.DiscoveryListener> entry :
                new ArrayList<>(active.entrySet())) {
            try {
                nsd.stopServiceDiscovery(entry.getValue());
            } catch (RuntimeException error) {
                Log.d(TAG, "Discovery already stopped: " + entry.getKey());
            }
        }
        active.clear();
        if (multicastLock != null) {
            try { if (multicastLock.isHeld()) multicastLock.release(); }
            catch (RuntimeException error) { Log.d(TAG, "Multicast lock already released", error); }
            multicastLock = null;
        }
    }

    private synchronized void stopListener(String type) {
        active.remove(type);
    }
}
