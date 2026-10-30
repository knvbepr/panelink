package com.example.panel.dash;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * LAN client for the C PC service. Discovery is UDP broadcast; state is line-delimited
 * JSON over TCP. The protocol carries data only and the phone never sends a command
 * that the PC would execute.
 */
public final class RemoteClient {

    public interface Listener {
        void onStatus(String status, boolean connected);

        void onOffer(String host, int port, String name);

        void onState(PanelData data);
    }

    private volatile boolean running;
    private volatile String host = "";
    private volatile int port = 47823;
    private volatile boolean discovery = true;
    private volatile Socket socket;
    private Thread worker;
    private Listener listener;
    private final Context context;
    private WifiManager.MulticastLock multicastLock;

    public RemoteClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized void start(String host, int port, boolean discovery, Listener listener) {
        stop();
        this.host = host == null ? "" : host;
        this.port = port <= 0 ? 47823 : port;
        this.discovery = discovery;
        this.listener = listener;
        running = true;
        if (multicastLock == null) {
            try {
                WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("panelink-discovery");
                    multicastLock.setReferenceCounted(false);
                    multicastLock.acquire();
                }
            } catch (Exception ignored) {
                // discovery can still use unicast broadcast without the lock
            }
        }
        worker = new Thread(this::loop, "remote-client");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void stop() {
        running = false;
        closeSocket();
        if (worker != null) {
            worker.interrupt();
            worker = null;
        }
        if (multicastLock != null) {
            try {
                if (multicastLock.isHeld()) multicastLock.release();
            } catch (Exception ignored) {
                // ignore
            }
            multicastLock = null;
        }
    }

    /** Drop the current host and run discovery again. */
    public void rediscover() {
        host = "";
        closeSocket();
    }

    private void loop() {
        while (running) {
            try {
                if (host == null || host.isEmpty()) {
                    if (!discovery) {
                        status("未设置主机", false);
                        sleep(1500);
                        continue;
                    }
                    status("搜索电脑...", false);
                    if (!discoverOnce()) {
                        sleep(1200);
                        continue;
                    }
                }
                status("连接 " + host + ":" + port, false);
                connectAndRead();
            } catch (Exception e) {
                status("未连接", false);
            } finally {
                closeSocket();
            }
            if (running) {
                sleep(1500);
                if (discovery) {
                    host = "";
                }
            }
        }
    }

    private void connectAndRead() throws Exception {
        Socket s = new Socket();
        socket = s;
        s.connect(new InetSocketAddress(host, port), 3000);
        s.setSoTimeout(8000);
        status("已连接 " + host, true);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (!line.startsWith("{")) {
                    continue;
                }
                try {
                    PanelData data = parseState(new JSONObject(line));
                    if (data != null) {
                        listener.onState(data);
                    }
                } catch (Exception ignored) {
                    // one bad frame must not kill the stream
                }
            }
        } catch (SocketTimeoutException timeout) {
            status("连接超时", false);
        }
        status("连接断开", false);
    }

    private boolean discoverOnce() {
        String foundHost = null;
        int foundPort = port;
        String foundName = "";
        DatagramSocket ds = null;
        try {
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            ds.setSoTimeout(350);
            byte[] payload = "PANELINK_DISCOVER".getBytes(StandardCharsets.UTF_8);
            List<InetAddress> targets = broadcastAddresses();
            long deadline = System.currentTimeMillis() + 1800;
            while (running && System.currentTimeMillis() < deadline && foundHost == null) {
                for (InetAddress target : targets) {
                    try {
                        ds.send(new DatagramPacket(payload, payload.length, target, port));
                    } catch (Exception ignored) {
                        // try the next interface
                    }
                }
                long receiveUntil = System.currentTimeMillis() + 400;
                while (running && System.currentTimeMillis() < receiveUntil && foundHost == null) {
                    byte[] buffer = new byte[512];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try {
                        ds.receive(packet);
                    } catch (SocketTimeoutException timeout) {
                        break;
                    }
                    String text = new String(packet.getData(), 0, packet.getLength(),
                            StandardCharsets.UTF_8);
                    try {
                        JSONObject offer = new JSONObject(text);
                        if (!"offer".equals(offer.optString("type"))) {
                            continue;
                        }
                        String candidate = packet.getAddress().getHostAddress();
                        int candidatePort = offer.optInt("port", port);
                        String name = offer.optString("name", "PC");
                        if (candidate != null && !candidate.isEmpty()) {
                            foundHost = candidate;
                            foundPort = candidatePort;
                            foundName = name;
                        }
                    } catch (Exception ignored) {
                        // not ours
                    }
                }
            }
            if (foundHost != null) {
                host = foundHost;
                port = foundPort;
                listener.onOffer(foundHost, foundPort, foundName);
                status("发现 " + foundHost + ":" + foundPort, false);
                return true;
            }
        } catch (Exception e) {
            status("搜索失败", false);
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
        return false;
    }

    private List<InetAddress> broadcastAddresses() {
        List<InetAddress> result = new ArrayList<>();
        try {
            result.add(InetAddress.getByName("255.255.255.255"));
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress address : network.getInterfaceAddresses()) {
                    InetAddress broadcast = address.getBroadcast();
                    if (broadcast != null) {
                        result.add(broadcast);
                    }
                }
            }
        } catch (Exception ignored) {
            // fall back to the limited broadcast address only
        }
        return result;
    }

    private PanelData parseState(JSONObject root) {
        if (!"state".equals(root.optString("type"))) {
            return null;
        }
        PanelData data = new PanelData();
        data.remote = true;
        data.remoteMode = true;
        data.remoteConnected = true;
        data.timeMs = System.currentTimeMillis();
        data.remoteName = root.optString("name", "PC");

        JSONObject cpu = root.optJSONObject("cpu");
        if (cpu != null) {
            data.cpuTotal = (float) cpu.optDouble("usage", 0);
            data.cpuVoltage = (float) cpu.optDouble("voltage", -1);
            data.cpuPowerW = (float) cpu.optDouble("power", -1);
            JSONArray cores = cpu.optJSONArray("cores");
            if (cores != null) {
                data.cpuCores = new float[cores.length()];
                for (int i = 0; i < cores.length(); i++) {
                    data.cpuCores[i] = (float) cores.optDouble(i, 0);
                }
                data.coreCount = data.cpuCores.length;
            }
            JSONArray freqs = cpu.optJSONArray("freqs");
            if (freqs != null) {
                data.cpuFreqMhz = new int[freqs.length()];
                for (int i = 0; i < freqs.length(); i++) {
                    data.cpuFreqMhz[i] = freqs.optInt(i, 0);
                }
            }
        }

        JSONObject mem = root.optJSONObject("mem");
        if (mem != null) {
            data.memTotalKb = mem.optLong("total") / 1024;
            data.memUsedKb = mem.optLong("used") / 1024;
        }

        JSONArray gpus = root.optJSONArray("gpus");
        if (gpus != null) {
            for (int i = 0; i < gpus.length(); i++) {
                JSONObject g = gpus.optJSONObject(i);
                if (g == null) {
                    continue;
                }
                PanelData.RemoteGpu gpu = new PanelData.RemoteGpu();
                gpu.name = g.optString("name", "GPU " + i);
                gpu.loadPct = g.optInt("load", -1);
                gpu.clockMhz = g.optInt("clock", -1);
                gpu.memUsedMb = g.optLong("mem_used_mb");
                gpu.memTotalMb = g.optLong("mem_total_mb");
                gpu.tempC = g.optInt("temp", -1);
                gpu.fanPct = g.optInt("fan", -1);
                data.gpus.add(gpu);
            }
        }

        JSONArray disks = root.optJSONArray("disks");
        if (disks != null) {
            for (int i = 0; i < disks.length(); i++) {
                JSONObject d = disks.optJSONObject(i);
                if (d == null) {
                    continue;
                }
                PanelData.RemoteDisk disk = new PanelData.RemoteDisk();
                disk.name = d.optString("name", "?");
                disk.usedBytes = d.optLong("used");
                disk.totalBytes = d.optLong("total");
                disk.readBps = d.optLong("read");
                disk.writeBps = d.optLong("write");
                data.disks.add(disk);
            }
        }

        JSONObject battery = root.optJSONObject("battery");
        if (battery != null) {
            data.pcBatteryPresent = battery.optBoolean("present");
            data.pcBatteryPct = battery.optInt("pct", -1);
            data.pcCharging = battery.optBoolean("charging");
            data.pcBatteryCurrentMa = battery.optInt("current", -1);
        }

        JSONArray fans = root.optJSONArray("fans");
        if (fans != null) {
            for (int i = 0; i < fans.length(); i++) {
                JSONObject f = fans.optJSONObject(i);
                if (f == null) {
                    continue;
                }
                PanelData.RemoteFan fan = new PanelData.RemoteFan();
                fan.name = f.optString("name", "FAN " + i);
                fan.value = f.optInt("value", -1);
                fan.percent = f.optBoolean("percent");
                data.fans.add(fan);
            }
        }

        JSONArray temps = root.optJSONArray("temps");
        if (temps != null) {
            for (int i = 0; i < temps.length(); i++) {
                JSONObject t = temps.optJSONObject(i);
                if (t == null) {
                    continue;
                }
                PanelData.Temp temp = new PanelData.Temp();
                temp.name = t.optString("name", "T" + i);
                temp.celsius = (float) t.optDouble("c", 0);
                data.temps.add(temp);
            }
        }
        return data;
    }

    private void status(String text, boolean connected) {
        if (listener != null) {
            listener.onStatus(text, connected);
        }
    }

    private void closeSocket() {
        Socket s = socket;
        socket = null;
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
                // ignore
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
