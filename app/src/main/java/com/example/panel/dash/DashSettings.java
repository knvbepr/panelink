package com.example.panel.dash;

import android.content.Context;
import android.content.SharedPreferences;

/** Persisted user settings. All values are clamped on read. */
public final class DashSettings {

    private static final String PREFS = "dash_settings";
    private static final String K_FULLSCREEN = "fullscreen";
    private static final String K_INTERVAL = "interval_ms";
    private static final String K_ROWS = "proc_rows";
    private static final String K_BATT_SCALE = "batt_scale";
    private static final String K_BATT_OFFSET = "batt_offset";
    private static final String K_TEMP_OFFSET = "temp_offset";
    private static final String K_TEMP_WARN = "temp_warn";
    private static final String K_TEMP_FAULT = "temp_fault";
    private static final String K_REMOTE_MODE = "remote_mode";
    private static final String K_REMOTE_HOST = "remote_host";
    private static final String K_REMOTE_PORT = "remote_port";

    public boolean fullscreen = true;
    public int intervalMs = 1000;
    public int procRows = 8;
    public float battScale = 1.0f;
    public int battOffsetMa = 0;
    public float tempOffsetC = 0f;
    public float tempWarnC = 60f;
    public float tempFaultC = 75f;
    public boolean remoteMode = false;
    public String remoteHost = "";
    public int remotePort = 47823;

    public void load(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        fullscreen = p.getBoolean(K_FULLSCREEN, true);
        intervalMs = clamp(p.getInt(K_INTERVAL, 1000), 100, 5000);
        procRows = clamp(p.getInt(K_ROWS, 8), 3, 20);
        battScale = clamp(p.getFloat(K_BATT_SCALE, 1f), 0.5f, 2f);
        battOffsetMa = clamp(p.getInt(K_BATT_OFFSET, 0), -1000, 1000);
        tempOffsetC = clamp(p.getFloat(K_TEMP_OFFSET, 0f), -20f, 20f);
        tempWarnC = clamp(p.getFloat(K_TEMP_WARN, 60f), 30f, 100f);
        tempFaultC = clamp(p.getFloat(K_TEMP_FAULT, 75f), 40f, 120f);
        remoteMode = p.getBoolean(K_REMOTE_MODE, false);
        remoteHost = p.getString(K_REMOTE_HOST, "");
        remotePort = clamp(p.getInt(K_REMOTE_PORT, 47823), 1024, 65535);
    }

    public void save(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(K_FULLSCREEN, fullscreen)
                .putInt(K_INTERVAL, intervalMs)
                .putInt(K_ROWS, procRows)
                .putFloat(K_BATT_SCALE, battScale)
                .putInt(K_BATT_OFFSET, battOffsetMa)
                .putFloat(K_TEMP_OFFSET, tempOffsetC)
                .putFloat(K_TEMP_WARN, tempWarnC)
                .putFloat(K_TEMP_FAULT, tempFaultC)
                .putBoolean(K_REMOTE_MODE, remoteMode)
                .putString(K_REMOTE_HOST, remoteHost)
                .putInt(K_REMOTE_PORT, remotePort)
                .apply();
    }

    public void reset() {
        fullscreen = true;
        intervalMs = 1000;
        procRows = 8;
        battScale = 1f;
        battOffsetMa = 0;
        tempOffsetC = 0f;
        tempWarnC = 60f;
        tempFaultC = 75f;
        remoteMode = false;
        remoteHost = "";
        remotePort = 47823;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
