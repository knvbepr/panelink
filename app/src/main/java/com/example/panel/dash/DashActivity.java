package com.example.panel.dash;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.example.panel.R;

/** Landscape system panel. Local metrics by default; remote PC mode from settings. */
public final class DashActivity extends AppCompatActivity implements SettingsOverlayView.Listener {

    private DashSettings settings;
    private DashPoller poller;
    private RemoteClient remoteClient;

    private TopBarView topBar;
    private CpuPanelView cpuPanel;
    private RamPanelView ramPanel;
    private DiskPanelView diskPanel;
    private TempPanelView tempPanel;
    private GpuPanelView gpuPanel;
    private ProcessPanelView processPanel;
    private SettingsOverlayView settingsOverlay;
    private View leftColumn;

    private PanelData localData;
    private PanelData remoteData;
    private long remoteAt;
    private String remoteStatus = "未连接";

    private boolean lastRemoteMode;
    private String lastRemoteHost = "";
    private int lastRemotePort = 51737;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new DashSettings();
        settings.load(this);
        String debugHost = getIntent().getStringExtra("remote_host");
        if (debugHost != null && !debugHost.isEmpty()) {
            settings.remoteHost = debugHost;
            settings.remoteMode = true;
            int debugPort = getIntent().getIntExtra("remote_port", 0);
            if (debugPort > 0) {
                settings.remotePort = debugPort;
            }
            float debugWarn = getIntent().getFloatExtra("temp_warn", -1f);
            if (debugWarn > 0) {
                settings.tempWarnC = debugWarn;
            }
            float debugFault = getIntent().getFloatExtra("temp_fault", -1f);
            if (debugFault > 0) {
                settings.tempFaultC = debugFault;
            }
            int debugCpuWarn = getIntent().getIntExtra("cpu_warn", -1);
            if (debugCpuWarn > 0) {
                settings.cpuWarnPct = debugCpuWarn;
            }
            settings.save(this);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        setContentView(R.layout.activity_dash);
        bindViews();
        applyFullscreen();

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 7);
        }

        tempPanel.setThresholds(settings.tempWarnC, settings.tempFaultC);
        cpuPanel.setThreshold(settings.cpuWarnPct);
        settingsOverlay.setSettings(settings, this);
        settingsOverlay.setVisibility(View.GONE);
        settingsOverlay.setRemoteStatus(remoteStatus);
        topBar.setOnSettingsClickListener(this::toggleSettings);

        leftColumn = findViewById(R.id.left_column);
        leftColumn.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateProcessHeight());
        leftColumn.post(this::updateProcessHeight);

        lastRemoteMode = settings.remoteMode;
        lastRemoteHost = settings.remoteHost;
        lastRemotePort = settings.remotePort;

        startPoller();
        if (settings.remoteMode) {
            startRemote();
        }
    }

    private void bindViews() {
        topBar = findViewById(R.id.top_bar);
        cpuPanel = findViewById(R.id.cpu_panel);
        ramPanel = findViewById(R.id.ram_panel);
        diskPanel = findViewById(R.id.disk_panel);
        tempPanel = findViewById(R.id.temp_panel);
        gpuPanel = findViewById(R.id.gpu_panel);
        processPanel = findViewById(R.id.process_panel);
        settingsOverlay = findViewById(R.id.settings_overlay);
    }

    private void startPoller() {
        if (poller != null) {
            poller.stop();
        }
        poller = new DashPoller(this, settings, data -> {
            localData = data;
            publish();
        });
        poller.start();
    }

    private void startRemote() {
        stopRemote();
        if (!settings.remoteMode) {
            remoteData = null;
            publish();
            return;
        }
        remoteStatus = settings.remoteHost.isEmpty() ? "搜索电脑..." : "连接电脑...";
        settingsOverlay.setRemoteStatus(remoteStatus);
        remoteClient = new RemoteClient(this);
        remoteClient.start(settings.remoteHost, settings.remotePort, true,
                new RemoteClient.Listener() {
                    @Override
                    public void onStatus(String status, boolean connected) {
                        runOnUiThread(() -> {
                            remoteStatus = status;
                            settingsOverlay.setRemoteStatus(remoteStatus);
                            publish();
                        });
                    }

                    @Override
                    public void onOffer(String host, int port, String name) {
                        runOnUiThread(() -> {
                            settings.remoteHost = host;
                            settings.remotePort = port;
                            lastRemoteHost = host;
                            lastRemotePort = port;
                            settings.save(DashActivity.this);
                            remoteStatus = "发现 " + host + ":" + port;
                            settingsOverlay.setRemoteStatus(remoteStatus);
                            publish();
                        });
                    }

                    @Override
                    public void onState(PanelData data) {
                        runOnUiThread(() -> {
                            remoteData = data;
                            remoteAt = System.currentTimeMillis();
                            remoteStatus = "已连接 " + data.remoteName;
                            settingsOverlay.setRemoteStatus(remoteStatus);
                            publish();
                        });
                    }
                });
    }

    private void stopRemote() {
        if (remoteClient != null) {
            remoteClient.stop();
            remoteClient = null;
        }
    }

    private void publish() {
        if (localData == null) {
            return;
        }
        PanelData data = localData;
        if (settings.remoteMode) {
            data.remoteMode = true;
            data.remote = true;
            boolean live = remoteData != null && System.currentTimeMillis() - remoteAt < 6000;
            data.remoteConnected = live;
            if (live) {
                data.remoteName = remoteData.remoteName;
                data.cpuTotal = remoteData.cpuTotal;
                data.cpuCores = remoteData.cpuCores;
                data.cpuFreqMhz = remoteData.cpuFreqMhz;
                data.cpuVoltage = remoteData.cpuVoltage;
                data.cpuPowerW = remoteData.cpuPowerW;
                data.coreCount = remoteData.coreCount;
                data.memTotalKb = remoteData.memTotalKb;
                data.memUsedKb = remoteData.memUsedKb;
                data.gpus.clear();
                data.gpus.addAll(remoteData.gpus);
                data.disks.clear();
                data.disks.addAll(remoteData.disks);
                data.temps.clear();
                data.temps.addAll(remoteData.temps);
                data.fans.clear();
                data.fans.addAll(remoteData.fans);
                data.pcBatteryPresent = remoteData.pcBatteryPresent;
                data.pcBatteryPct = remoteData.pcBatteryPct;
                data.pcCharging = remoteData.pcCharging;
                data.pcBatteryCurrentMa = remoteData.pcBatteryCurrentMa;
            } else {
                data.remoteName = "";
                data.cpuTotal = 0;
                data.cpuCores = new float[0];
                data.cpuFreqMhz = new int[0];
                data.cpuVoltage = -1f;
                data.cpuPowerW = -1f;
                data.memTotalKb = 0;
                data.memUsedKb = 0;
                data.gpus.clear();
                data.disks.clear();
                data.temps.clear();
                data.fans.clear();
                data.pcBatteryPresent = false;
            }
        } else {
            data.remoteMode = false;
            data.remote = false;
            data.remoteConnected = false;
        }

        topBar.setData(data);
        cpuPanel.setData(data);
        ramPanel.setData(data);
        diskPanel.setData(data);
        tempPanel.setData(data);
        gpuPanel.setData(data);
        processPanel.setData(data);
        updateProcessHeight(data);
        settingsOverlay.setBattery(localData.batteryPct, localData.charging,
                localData.batteryCurrentMa);
        settingsOverlay.setRemoteStatus(remoteStatus);
    }

    private void updateProcessHeight() {
        updateProcessHeight(localData);
    }

    private void updateProcessHeight(PanelData data) {
        if (leftColumn == null || processPanel == null) {
            return;
        }
        int available = leftColumn.getHeight();
        if (available <= 0) {
            return;
        }
        int rows = settings.procRows;
        if (data != null && data.remoteMode) {
            rows = 3 + data.fans.size() + (data.pcBatteryPresent ? 1 : 0);
            rows = Math.max(3, Math.min(6, rows));
        }
        int desired = processPanel.desiredHeight(rows);
        int max = available * 55 / 100;
        int min = available * 25 / 100;
        int safety = available - available * 40 / 100;
        int height = Math.max(min, Math.min(max, Math.min(desired, safety)));
        ViewGroup.LayoutParams lp = processPanel.getLayoutParams();
        if (lp.height == height) {
            return;
        }
        lp.height = height;
        processPanel.setLayoutParams(lp);
    }

    private void toggleSettings() {
        boolean showing = settingsOverlay.getVisibility() == View.VISIBLE;
        settingsOverlay.setVisibility(showing ? View.GONE : View.VISIBLE);
        if (!showing) {
            settingsOverlay.bringToFront();
        }
    }

    private void applyFullscreen() {
        Window window = getWindow();
        View decor = window.getDecorView();
        if (settings.fullscreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (poller == null) {
            startPoller();
        }
        if (settings.remoteMode && remoteClient == null) {
            startRemote();
        }
        applyFullscreen();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (poller != null) {
            poller.stop();
            poller = null;
        }
        stopRemote();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRemote();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyFullscreen();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            toggleSettings();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK
                && settingsOverlay.getVisibility() == View.VISIBLE) {
            if (settingsOverlay.isAboutVisible()) {
                settingsOverlay.cancelAbout();
                return true;
            }
            if (settingsOverlay.isNumericEditVisible()) {
                settingsOverlay.cancelNumericEdit();
                return true;
            }
            if (settingsOverlay.isExitConfirmVisible()) {
                settingsOverlay.cancelExitConfirm();
                return true;
            }
            settingsOverlay.setVisibility(View.GONE);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onSettingsChanged() {
        boolean remoteChanged = settings.remoteMode != lastRemoteMode
                || !settings.remoteHost.equals(lastRemoteHost)
                || settings.remotePort != lastRemotePort;
        settings.save(this);
        lastRemoteMode = settings.remoteMode;
        lastRemoteHost = settings.remoteHost;
        lastRemotePort = settings.remotePort;

        settingsOverlay.setRemoteStatus(remoteStatus);
        tempPanel.setThresholds(settings.tempWarnC, settings.tempFaultC);
        cpuPanel.setThreshold(settings.cpuWarnPct);
        updateProcessHeight();
        applyFullscreen();
        if (poller != null) {
            startPoller();
        }
        if (remoteChanged) {
            if (settings.remoteMode) {
                startRemote();
            } else {
                stopRemote();
                remoteData = null;
                remoteStatus = "未连接";
                publish();
            }
        } else {
            publish();
        }
    }

    @Override
    public void onClosed() {
        settingsOverlay.cancelEditors();
        settingsOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onExitRequested() {
        finishAffinity();
    }

    @Override
    public void onEditHostRequested() {
        EditText input = new EditText(this);
        input.setText(settings.remoteHost);
        input.setHint("192.168.1.3");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(DashUi.WHITE_30);
        input.setBackgroundColor(Color.BLACK);
        input.setPadding(24, 16, 24, 16);
        new AlertDialog.Builder(this)
                .setTitle("远程主机")
                .setView(input)
                .setPositiveButton("连接", (dialog, which) -> {
                    settings.remoteHost = input.getText().toString().trim();
                    settings.remoteMode = true;
                    settings.save(this);
                    lastRemoteMode = true;
                    lastRemoteHost = settings.remoteHost;
                    lastRemotePort = settings.remotePort;
                    startRemote();
                    publish();
                })
                .setNeutralButton("自动发现", (dialog, which) -> {
                    settings.remoteHost = "";
                    settings.remoteMode = true;
                    settings.save(this);
                    lastRemoteMode = true;
                    lastRemoteHost = "";
                    startRemote();
                    publish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onDiscoverRequested() {
        settings.remoteMode = true;
        settings.remoteHost = "";
        settings.save(this);
        lastRemoteMode = true;
        lastRemoteHost = "";
        remoteStatus = "搜索电脑...";
        settingsOverlay.setRemoteStatus(remoteStatus);
        startRemote();
        publish();
    }
}
