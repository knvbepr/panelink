package com.knvbepr.panelink.dash;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.StatFs;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.telephony.TelephonyManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads live system metrics. Everything that is directly readable is read directly;
 * root is only a fallback and every failure degrades to an "invalid" value.
 */
public final class SystemCollector {

    private final Context context;
    private final DashSettings settings;

    private long prevCpuTotal;
    private long prevCpuIdle;
    private long[] prevCoreTotal = new long[0];
    private long[] prevCoreIdle = new long[0];

    private long prevDiskRead;
    private long prevDiskWrite;
    private long prevDiskTime;

    private long prevRx;
    private long prevTx;
    private long prevNetTime;

    private final Map<Integer, Long> prevProcTicks = new HashMap<>();
    private final java.util.List<PanelData.Proc> lastProcs = new ArrayList<>();
    private long lastProcessTime;
    private long lastProcessCpuTotal;
    private final Map<Integer, String> nameCache = new HashMap<>();
    private final Set<Integer> seenPids = new HashSet<>();

    private int internetState;
    private int internetFails;
    private long lastInternetCheck;

    public SystemCollector(Context context, DashSettings settings) {
        this.context = context.getApplicationContext();
        this.settings = settings;
    }

    public PanelData collect() {
        PanelData data = new PanelData();
        data.timeMs = System.currentTimeMillis();
        data.root = RootShell.available();
        data.rootState = data.root ? "ROOT" : "NO ROOT";

        long now = System.currentTimeMillis();
        readCpu(data);
        readMemory(data);
        readDisk(data, now);
        readGpu(data);
        readTemperatures(data);
        readBatteryAndUsb(data);
        readNetwork(data, now);
        if (now - lastProcessTime >= 2000) {
            long processDelta = prevCpuTotal - lastProcessCpuTotal;
            readProcesses(data, processDelta);
            lastProcessTime = now;
            lastProcessCpuTotal = prevCpuTotal;
            lastProcs.clear();
            lastProcs.addAll(data.procs);
        } else {
            data.procs.addAll(lastProcs);
        }
        return data;
    }

    // ------------------------------------------------------------------ CPU

    private void readCpu(PanelData data) {
        long total = 0;
        long idle = 0;
        List<Long> coreTotals = new ArrayList<>();
        List<Long> coreIdles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("cpu")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 6) {
                    continue;
                }
                long sum = 0;
                for (int i = 1; i < parts.length; i++) {
                    sum += parseLong(parts[i]);
                }
                long idleTicks = parseLong(parts[4]);
                if (iowaitIndex(parts)) {
                    idleTicks += parseLong(parts[5]);
                }
                if ("cpu".equals(parts[0])) {
                    total = sum;
                    idle = idleTicks;
                } else {
                    coreTotals.add(sum);
                    coreIdles.add(idleTicks);
                }
            }
        } catch (Exception ignored) {
            // leave previous values
        }

        long dTotal = total - prevCpuTotal;
        long dIdle = idle - prevCpuIdle;
        data.cpuTotal = (prevCpuTotal > 0 && dTotal > 0)
                ? clamp(100f * (dTotal - dIdle) / dTotal, 0f, 100f) : 0f;

        int cores = coreTotals.size();
        data.coreCount = cores;
        data.cpuCores = new float[cores];
        if (prevCoreTotal.length != cores) {
            prevCoreTotal = new long[cores];
            prevCoreIdle = new long[cores];
        }
        for (int i = 0; i < cores; i++) {
            long ct = coreTotals.get(i);
            long ci = coreIdles.get(i);
            long dt = ct - prevCoreTotal[i];
            long di = ci - prevCoreIdle[i];
            data.cpuCores[i] = (prevCoreTotal[i] > 0 && dt > 0)
                    ? clamp(100f * (dt - di) / dt, 0f, 100f) : 0f;
            prevCoreTotal[i] = ct;
            prevCoreIdle[i] = ci;
        }
        prevCpuTotal = total;
        prevCpuIdle = idle;

        int[] freqs = new int[cores];
        for (int i = 0; i < cores; i++) {
            freqs[i] = (int) (readKhz("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq") / 1000);
        }
        data.cpuFreqMhz = freqs;
    }

    private static boolean iowaitIndex(String[] parts) {
        return parts.length > 5;
    }

    // ---------------------------------------------------------------- memory

    private void readMemory(PanelData data) {
        long total = 0;
        long free = 0;
        long buffers = 0;
        long cached = 0;
        long available = -1;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    total = parseMeminfo(line);
                } else if (line.startsWith("MemFree:")) {
                    free = parseMeminfo(line);
                } else if (line.startsWith("Buffers:")) {
                    buffers = parseMeminfo(line);
                } else if (line.startsWith("Cached:")) {
                    cached = parseMeminfo(line);
                } else if (line.startsWith("MemAvailable:")) {
                    available = parseMeminfo(line);
                }
            }
        } catch (Exception ignored) {
            // leave zeros
        }
        if (available < 0) {
            available = free + buffers + cached;
        }
        data.memTotalKb = total;
        data.memUsedKb = Math.max(0, total - available);
    }

    private static long parseMeminfo(String line) {
        String[] parts = line.trim().split("\\s+");
        return parts.length > 1 ? parseLong(parts[1]) : 0;
    }

    // ------------------------------------------------------------------ disk

    private void readDisk(PanelData data, long now) {
        try {
            StatFs stat = new StatFs("/data");
            long blockSize = stat.getBlockSizeLong();
            data.diskTotalKb = stat.getBlockCountLong() * blockSize / 1024;
            data.diskUsedKb = (stat.getBlockCountLong() - stat.getAvailableBlocksLong()) * blockSize / 1024;
        } catch (Exception ignored) {
            // ignore
        }

        long sectorsRead = 0;
        long sectorsWritten = 0;
        boolean found = false;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/diskstats"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 10) {
                    continue;
                }
                String name = parts[2];
                if ("mmcblk0".equals(name) || "sda".equals(name)) {
                    sectorsRead = parseLong(parts[5]);
                    sectorsWritten = parseLong(parts[9]);
                    found = true;
                    break;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        long dt = prevDiskTime == 0 ? 0 : now - prevDiskTime;
        if (found && prevDiskTime > 0) {
            data.diskReadBps = dt > 0 ? Math.max(0, (sectorsRead - prevDiskRead) * 512L * 1000 / dt) : 0;
            data.diskWriteBps = dt > 0 ? Math.max(0, (sectorsWritten - prevDiskWrite) * 512L * 1000 / dt) : 0;
        }
        prevDiskRead = sectorsRead;
        prevDiskWrite = sectorsWritten;
        prevDiskTime = now;
    }

    // ------------------------------------------------------------------- GPU

    private void readGpu(PanelData data) {
        String busy = readText("/sys/class/kgsl/kgsl-3d0/gpubusy");
        if (busy != null) {
            String[] p = busy.trim().split("\\s+");
            if (p.length >= 2) {
                long b = parseLong(p[0]);
                long t = parseLong(p[1]);
                if (t > 0) {
                    data.gpuLoadPct = (int) Math.round(100.0 * b / t);
                } else if (b == 0) {
                    data.gpuLoadPct = 0; // GPU idle, counters reset to 0 0
                }
            }
        }
        String clk = readText("/sys/class/kgsl/kgsl-3d0/gpuclk");
        if (clk == null) {
            clk = readText("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq");
        }
        if (clk != null) {
            long hz = parseLong(clk.trim());
            if (hz > 0) {
                data.gpuFreqMhz = (int) (hz / 1_000_000L);
            }
        }

    }

    // ------------------------------------------------------------------ temp

    private void readTemperatures(PanelData data) {
        addTemp(data, "电池", readTempC("/sys/class/power_supply/battery/temp", 10f));
        addTemp(data, "充电器", readTempC("/sys/class/power_supply/usb/temp", 10f));
        addTemp(data, "电源", readTempC("/sys/class/power_supply/bms/temp", 10f));

        File thermal = new File("/sys/class/thermal");
        File[] zones = thermal.listFiles();
        if (zones != null) {
            int added = 0;
            for (File zone : zones) {
                if (!zone.getName().startsWith("thermal_zone") || added >= 2) {
                    continue;
                }
                String type = readText(zone.getAbsolutePath() + "/type");
                String raw = readText(zone.getAbsolutePath() + "/temp");
                if (type == null || raw == null) {
                    continue;
                }
                float c = parseTemp(raw.trim());
                if (c > -20f && c < 150f) {
                    addTemp(data, type.trim(), c);
                    added++;
                }
            }
        }
        // Apply calibration once, after all sources are in.
        for (PanelData.Temp temp : data.temps) {
            temp.celsius += settings.tempOffsetC;
        }
    }

    private void addTemp(PanelData data, String name, float celsius) {
        if (Float.isNaN(celsius) || celsius < -20f || celsius > 150f) {
            return;
        }
        for (PanelData.Temp existing : data.temps) {
            if (existing.name.equalsIgnoreCase(name)) {
                return;
            }
        }
        PanelData.Temp t = new PanelData.Temp();
        t.name = name;
        t.celsius = celsius;
        data.temps.add(t);
    }

    /** Reads a temperature node with a divisor; returns NaN when unreadable. */
    private float readTempC(String path, float divisor) {
        String raw = readText(path);
        if (raw == null) {
            return Float.NaN;
        }
        try {
            return Float.parseFloat(raw.trim()) / divisor;
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    private static float parseTemp(String raw) {
        try {
            float v = Float.parseFloat(raw);
            if (Math.abs(v) > 1000) {
                return v / 1000f;
            }
            if (Math.abs(v) > 150) {
                return v / 10f;
            }
            return v;
        } catch (Exception e) {
            return Float.NaN;
        }
    }

    // --------------------------------------------------------------- battery

    private void readBatteryAndUsb(PanelData data) {
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                data.batteryPct = scale > 0 ? level * 100 / scale : level;
                data.batteryStatus = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                data.charging = data.batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING
                        || data.batteryStatus == BatteryManager.BATTERY_STATUS_FULL;
                int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (temp != Integer.MIN_VALUE) {
                    data.batteryTempC = Math.round(temp / 10f);
                }
                int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                data.usbPlugged = plugged == BatteryManager.BATTERY_PLUGGED_USB
                        || plugged == BatteryManager.BATTERY_PLUGGED_AC;
            }
        } catch (Exception ignored) {
            // ignore
        }

        String currentRaw = readText("/sys/class/power_supply/battery/current_now");
        if (currentRaw != null) {
            try {
                float raw = Float.parseFloat(currentRaw.trim());
                if (Math.abs(raw) > 20000f) {
                    raw = raw / 1000f; // some kernels report uA
                }
                float calibrated = raw * settings.battScale + settings.battOffsetMa;
                data.batteryCurrentMa = Math.round(Math.abs(calibrated));
                data.batteryCurrentValid = true;
            } catch (Exception ignored) {
                // invalid
            }
        }
        if (!data.batteryCurrentValid) {
            String status = readText("/sys/class/power_supply/battery/status");
            if (status != null) {
                data.batteryCurrentValid = false;
            }
        }

        try {
            UsbManager usb = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usb != null && !usb.getDeviceList().isEmpty()) {
                data.usbPlugged = true;
            }
        } catch (Exception ignored) {
            // ignore
        }

        if (!data.charging && data.batteryStatus == BatteryManager.BATTERY_STATUS_DISCHARGING) {
            data.charging = false;
        }
    }

    // --------------------------------------------------------------- network

    private void readNetwork(PanelData data, long now) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                WifiInfo info = wifi.getConnectionInfo();
                if (info != null && info.getNetworkId() != -1
                        && info.getSupplicantState() == SupplicantState.COMPLETED) {
                    data.wifiConnected = true;
                    data.wifiRssi = info.getRssi();
                }
            }
        } catch (Exception ignored) {
            // permission
        }

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo[] networks = cm == null ? null : cm.getAllNetworkInfo();
            if (networks != null) {
                for (NetworkInfo info : networks) {
                    if (info != null && info.getType() == ConnectivityManager.TYPE_ETHERNET
                            && info.isConnected()) {
                        data.ethernet = true;
                    }
                }
            }
            NetworkInfo active = cm == null ? null : cm.getActiveNetworkInfo();
            if (active == null || !active.isConnected()) {
                internetState = 0;
                internetFails = 0;
            }
        } catch (Exception ignored) {
            // ignore
        }

        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                int type = tm.getNetworkType();
                data.cellType = networkTypeName(type);
                data.cellGen = networkGeneration(type);
                data.cellConnected = type != TelephonyManager.NETWORK_TYPE_UNKNOWN;
            }
        } catch (Exception ignored) {
            // ignore
        }

        long rx = 0;
        long tx = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/dev"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String name = line.substring(0, colon).trim();
                if ("lo".equals(name) || name.startsWith("dummy") || name.startsWith("sit")
                        || name.startsWith("p2p")) {
                    continue;
                }
                String[] values = line.substring(colon + 1).trim().split("\\s+");
                if (values.length < 9) {
                    continue;
                }
                rx += parseLong(values[0]);
                tx += parseLong(values[8]);
            }
        } catch (Exception ignored) {
            // ignore
        }
        long dt = prevNetTime == 0 ? 0 : now - prevNetTime;
        if (prevNetTime > 0 && dt > 0) {
            data.rxBps = Math.max(0, (rx - prevRx) * 1000L / dt);
            data.txBps = Math.max(0, (tx - prevTx) * 1000L / dt);
        }
        prevRx = rx;
        prevTx = tx;
        prevNetTime = now;

        if (now - lastInternetCheck > 10000) {
            lastInternetCheck = now;
            if (probeInternet()) {
                internetState = 1;
                internetFails = 0;
            } else {
                internetFails++;
                if (internetFails >= 2) {
                    internetState = 2;
                }
            }
        }
        data.internetState = internetState;
        data.internet = internetState == 1;
    }

    /** Two Chinese DNS endpoints; a single failure never flips the indicator. */
    private static boolean probeInternet() {
        String[] hosts = {"114.114.114.114", "223.5.5.5"};
        for (String host : hosts) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(host, 53), 500);
                return true;
            } catch (Exception ignored) {
                // try next
            } finally {
                try {
                    socket.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        return false;
    }

    private static int networkGeneration(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_IDEN:
            case TelephonyManager.NETWORK_TYPE_GSM:
                return 2;
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return 3;
            case TelephonyManager.NETWORK_TYPE_LTE:
                return 4;
            case 20: // NETWORK_TYPE_NR (API 29 constant, absent on API 22)
                return 5;
            default:
                return 0;
        }
    }

    private static String networkTypeName(int type) {
        int gen = networkGeneration(type);
        if (gen > 0) {
            return gen + "G";
        }
        return type == TelephonyManager.NETWORK_TYPE_UNKNOWN ? "无" : "";
    }

    // ------------------------------------------------------------- processes

    private void readProcesses(PanelData data, long totalDelta) {
        File proc = new File("/proc");
        File[] dirs = proc.listFiles();
        if (dirs == null) {
            return;
        }
        int cores = Math.max(1, data.coreCount);

        seenPids.clear();
        for (File dir : dirs) {
            String name = dir.getName();
            if (!isNumber(name)) {
                continue;
            }
            int pid = Integer.parseInt(name);
            if (pid <= 0) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader("/proc/" + pid + "/stat"))) {
                String line = reader.readLine();
                if (line == null) {
                    continue;
                }
                int open = line.indexOf('(');
                int close = line.lastIndexOf(')');
                if (open < 0 || close < open) {
                    continue;
                }
                String comm = line.substring(open + 1, close);
                String[] tail = line.substring(close + 2).trim().split("\\s+");
                if (tail.length < 22) {
                    continue;
                }
                long utime = parseLong(tail[11]);
                long stime = parseLong(tail[12]);
                long rssPages = parseLong(tail[21]);
                long ticks = utime + stime;
                Long previous = prevProcTicks.get(pid);
                long delta = previous == null ? 0 : Math.max(0, ticks - previous);
                prevProcTicks.put(pid, ticks);

                PanelData.Proc p = new PanelData.Proc();
                p.pid = pid;
                String full = nameCache.get(pid);
                if (full == null) {
                    full = readCmdline(pid);
                    if (full == null || full.isEmpty()) {
                        full = comm;
                    }
                    nameCache.put(pid, full);
                }
                p.name = full;
                p.cpu = totalDelta > 0 ? (100f * delta * cores / totalDelta) : 0f;
                p.rssKb = rssPages * 4;
                data.procs.add(p);
                seenPids.add(pid);
            } catch (Exception ignored) {
                // process disappeared or not readable
            }
        }
        prevProcTicks.keySet().retainAll(seenPids);
    }

    private String readCmdline(int pid) {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/" + pid + "/cmdline"))) {
            int value;
            while ((value = reader.read()) != -1) {
                if (value == 0) {
                    if (result.length() > 0) {
                        break;
                    }
                    continue;
                }
                result.append((char) value);
            }
            return result.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // ----------------------------------------------------------------- utils

    private long readKhz(String path) {
        String raw = readText(path);
        if (raw == null) {
            return 0;
        }
        return parseLong(raw.trim());
    }

    private static String readText(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            return line == null ? null : line;
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
