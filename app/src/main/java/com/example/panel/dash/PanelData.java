package com.example.panel.dash;

import java.util.ArrayList;
import java.util.List;

/** One immutable-by-convention snapshot of everything the panel draws. */
public final class PanelData {

    public long timeMs;

    public float cpuTotal;
    public float[] cpuCores = new float[0];
    public int[] cpuFreqMhz = new int[0];
    public float cpuVoltage = -1f;
    public float cpuPowerW = -1f;
    public int coreCount;

    public long memTotalKb;
    public long memUsedKb;

    public long diskTotalKb;
    public long diskUsedKb;
    public long diskReadBps;
    public long diskWriteBps;

    public final List<Temp> temps = new ArrayList<>();

    public int gpuFreqMhz = -1;
    public int gpuLoadPct = -1;

    public final List<Proc> procs = new ArrayList<>();

    public int batteryPct = -1;
    public int batteryStatus = -1;
    public boolean charging;
    public boolean usbPlugged;
    public int batteryCurrentMa;
    public boolean batteryCurrentValid;
    public int batteryTempC = -1;

    public boolean wifiConnected;
    public int wifiRssi = -127;
    public int internetState; // 0 unknown, 1 reachable, 2 unreachable
    public boolean internet;
    public boolean ethernet;
    public boolean cellConnected;
    public int cellGen;
    public String cellType = "";
    public long rxBps;
    public long txBps;

    public boolean root;
    public String rootState = "检测中";

    // Remote PC mode
    public boolean remote;
    public boolean remoteMode;
    public boolean remoteConnected;
    public String remoteName = "";
    public final List<RemoteGpu> gpus = new ArrayList<>();
    public final List<RemoteDisk> disks = new ArrayList<>();
    public final List<RemoteFan> fans = new ArrayList<>();
    public boolean pcBatteryPresent;
    public int pcBatteryPct;
    public boolean pcCharging;
    public int pcBatteryCurrentMa = -1;

    public static final class Proc {
        public int pid;
        public String name = "";
        public float cpu;
        public long rssKb;
    }

    public static final class RemoteGpu {
        public String name = "";
        public int loadPct = -1;
        public int clockMhz = -1;
        public long memUsedMb;
        public long memTotalMb;
        public int tempC = -1;
        public int fanPct = -1;
    }

    public static final class RemoteDisk {
        public String name = "";
        public long usedBytes;
        public long totalBytes;
        public long readBps;
        public long writeBps;
    }

    public static final class RemoteFan {
        public String name = "";
        public int value = -1;
        public boolean percent;
    }

    public static final class Temp {
        public String name = "";
        public float celsius;
        public boolean valid = true;
    }
}
