package com.example.panel.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** Two-column settings sheet with wide rows and large +/- targets. */
public final class SettingsOverlayView extends View {

    public interface Listener {
        void onSettingsChanged();

        void onClosed();

        void onDiscoverRequested();
    }

    private static final int ROW_REMOTE = 0;
    private static final int ROW_HOST = 1;
    private static final int ROW_PORT = 2;
    private static final int ROW_FULLSCREEN = 3;
    private static final int ROW_INTERVAL = 4;
    private static final int ROW_ROWS = 5;
    private static final int ROW_BATT_SCALE = 6;
    private static final int ROW_BATT_OFFSET = 7;
    private static final int ROW_TEMP_OFFSET = 8;
    private static final int ROW_TEMP_FAULT = 9;
    private static final int ROW_RESET = 10;
    private static final int ROW_CLOSE = 11;
    private static final int ROWS = 12;
    private static final int COLS = 2;
    private static final int PER_COL = 6;

    private final int ui;
    private final Paint p = new Paint();
    private final Paint cjk;
    private final Paint dseg;
    private final Paint mono;
    private final Rect[] rowRects = new Rect[ROWS];
    private final Rect[] minusRects = new Rect[ROWS];
    private final Rect[] plusRects = new Rect[ROWS];

    private int rowH;
    private int labelSize;
    private int valueSize;
    private DashSettings settings;
    private Listener listener;
    private int batteryPct = -1;
    private boolean batteryCharging;
    private int batteryCurrent;
    private String remoteStatus = "未连接";

    public SettingsOverlayView(Context context) {
        this(context, null);
    }

    public SettingsOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ui = DashUi.unit(context);
        p.setAntiAlias(false);
        cjk = DashUi.paint(context, com.example.panel.ui.PanelTypefaces.pixel(context), ui,
                DashUi.WHITE, Paint.Align.LEFT, false);
        dseg = DashUi.paint(context, com.example.panel.ui.PanelTypefaces.dseg(context), ui,
                DashUi.WHITE, Paint.Align.RIGHT, true);
        mono = DashUi.paint(context, com.example.panel.ui.PanelTypefaces.mono(context), ui,
                DashUi.WHITE, Paint.Align.RIGHT, true);
        for (int i = 0; i < ROWS; i++) {
            rowRects[i] = new Rect();
            minusRects[i] = new Rect();
            plusRects[i] = new Rect();
        }
    }

    public void setSettings(DashSettings settings, Listener listener) {
        this.settings = settings;
        this.listener = listener;
        invalidate();
    }

    public void setBattery(int pct, boolean charging, int currentMa) {
        batteryPct = pct;
        batteryCharging = charging;
        batteryCurrent = currentMa;
    }

    public void setRemoteStatus(String status) {
        remoteStatus = status == null ? "" : status;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(DashUi.BLACK);
        if (settings == null) {
            return;
        }
        int w = getWidth();
        int h = getHeight();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE_30);
        canvas.drawRect(1, 1, w - 2, h - 2, p);

        drawCjk(canvas, "设置", ui, ui * 2, ui * 2, DashUi.WHITE);
        int y = ui * 3;
        p.setColor(DashUi.WHITE_20);
        canvas.drawLine(ui, y, w - ui, y, p);
        y += ui / 2 + ui / 2;

        if (batteryPct >= 0) {
            String state = batteryCharging ? "充电" : "放电";
            drawCjk(canvas, "电池 " + String.format(Locale.US, "%03d", batteryPct) + "% "
                    + state + " " + batteryCurrent + "mA", ui, y + ui, ui, DashUi.WHITE_60);
            y += ui + ui / 2;
        }
        if (settings.remoteMode) {
            drawCjk(canvas, "远程 " + remoteStatus, ui, y + ui, ui,
                    remoteStatus.contains("已连接") ? DashUi.GREEN : DashUi.WHITE_60);
            y += ui + ui / 2;
        }

        int colGap = ui;
        int colW = (w - ui * 2 - colGap) / COLS;
        int contentTop = y + ui;
        rowH = Math.min(ui * 3, Math.max(ui * 2, (h - contentTop - ui) / PER_COL));
        labelSize = Math.min(ui * 2, rowH - 8);
        valueSize = Math.min(ui + ui / 2, rowH - 16);

        for (int i = 0; i < ROWS; i++) {
            int col = i / PER_COL;
            int row = i % PER_COL;
            int left = ui + col * (colW + colGap);
            int right = left + colW;
            int top = contentTop + row * rowH;
            layoutRow(i, left, right, top);
            drawRow(canvas, i, left, top);
            p.setColor(DashUi.WHITE_15);
            canvas.drawLine(left, top + rowH, right, top + rowH, p);
        }
    }

    private void layoutRow(int index, int left, int right, int y) {
        rowRects[index].set(left, y, right, y + rowH);
        int btn = Math.min(rowH - 4, ui * 2 + ui / 2);
        plusRects[index].set(right - btn, y + (rowH - btn) / 2, right, y + (rowH + btn) / 2);
        minusRects[index].set(right - btn * 2 - ui / 2, y + (rowH - btn) / 2,
                right - btn - ui / 2, y + (rowH + btn) / 2);
    }

    private void drawRow(Canvas canvas, int index, int left, int y) {
        int baseline = y + rowH / 2 + labelSize / 3;
        String label;
        String value;
        boolean hasStep = false;
        boolean wholeRowAction = false;
        switch (index) {
            case ROW_REMOTE:
                label = "远程模式";
                value = settings.remoteMode ? "开" : "关";
                wholeRowAction = true;
                break;
            case ROW_HOST:
                label = "远程主机";
                value = settings.remoteHost.isEmpty() ? "自动发现" : settings.remoteHost;
                wholeRowAction = true;
                break;
            case ROW_PORT:
                label = "端口";
                value = String.valueOf(settings.remotePort);
                hasStep = true;
                break;
            case ROW_FULLSCREEN:
                label = "全屏显示";
                value = settings.fullscreen ? "开" : "关";
                wholeRowAction = true;
                break;
            case ROW_INTERVAL:
                label = "刷新间隔";
                value = String.format(Locale.US, "%.1fs", settings.intervalMs / 1000f);
                hasStep = true;
                break;
            case ROW_ROWS:
                label = "进程行数";
                value = String.valueOf(settings.procRows);
                hasStep = true;
                break;
            case ROW_BATT_SCALE:
                label = "电流倍率";
                value = String.format(Locale.US, "%.2f", settings.battScale);
                hasStep = true;
                break;
            case ROW_BATT_OFFSET:
                label = "电流偏移";
                value = String.format(Locale.US, "%+dmA", settings.battOffsetMa);
                hasStep = true;
                break;
            case ROW_TEMP_OFFSET:
                label = "温度偏移";
                value = String.format(Locale.US, "%+.1fC", settings.tempOffsetC);
                hasStep = true;
                break;
            case ROW_TEMP_FAULT:
                label = "温度告警";
                value = String.format(Locale.US, "%.0fC", settings.tempFaultC);
                hasStep = true;
                break;
            case ROW_RESET:
                label = "恢复默认";
                value = "";
                break;
            case ROW_CLOSE:
            default:
                label = "关闭";
                value = "";
                break;
        }

        drawCjk(canvas, label, left, baseline, labelSize, DashUi.WHITE);
        if (hasStep) {
            dseg.setTextSize(valueSize);
            dseg.setColor(DashUi.WHITE);
            dseg.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(value, rowRects[index].right - ui * 5 - ui / 2, baseline, dseg);
            drawButton(canvas, minusRects[index], "-");
            drawButton(canvas, plusRects[index], "+");
        } else if (wholeRowAction) {
            int color = DashUi.WHITE_60;
            if (index == ROW_REMOTE) {
                color = settings.remoteMode ? DashUi.GREEN : DashUi.WHITE_60;
            } else if (index == ROW_FULLSCREEN) {
                color = settings.fullscreen ? DashUi.GREEN : DashUi.WHITE_60;
            }
            if (index == ROW_HOST) {
                drawMonoRight(canvas, value, rowRects[index].right, baseline, valueSize,
                        DashUi.WHITE_60);
            } else {
                drawCjkRight(canvas, value, rowRects[index].right, baseline, labelSize, color);
            }
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setColor(DashUi.WHITE_50);
            int bw = Math.min(rowRects[index].width() / 2, ui * 8);
            int bx = rowRects[index].right - bw;
            int by = rowRects[index].top + (rowH - (rowH - ui)) / 2;
            canvas.drawRect(bx, by, bx + bw, by + rowH - ui, p);
            drawCjk(canvas, index == ROW_RESET ? "重置" : "关闭", bx + ui / 2,
                    baseline, labelSize, DashUi.WHITE);
        }
    }

    private void drawButton(Canvas canvas, Rect rect, String text) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE_50);
        canvas.drawRect(rect, p);
        mono.setTextSize(Math.max(ui, rect.height() / 2));
        mono.setColor(DashUi.WHITE);
        mono.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, rect.centerX(), rect.centerY() + ui / 3, mono);
        mono.setTextAlign(Paint.Align.RIGHT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (settings == null || event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        int x = (int) event.getX();
        int y = (int) event.getY();
        for (int i = 0; i < ROWS; i++) {
            if (!rowRects[i].contains(x, y)) {
                continue;
            }
            switch (i) {
                case ROW_REMOTE:
                    settings.remoteMode = !settings.remoteMode;
                    if (settings.remoteMode && settings.remoteHost.isEmpty()) {
                        listener.onDiscoverRequested();
                    }
                    changed();
                    break;
                case ROW_HOST:
                    settings.remoteMode = true;
                    listener.onDiscoverRequested();
                    changed();
                    break;
                case ROW_FULLSCREEN:
                    settings.fullscreen = !settings.fullscreen;
                    changed();
                    break;
                case ROW_RESET:
                    settings.reset();
                    changed();
                    break;
                case ROW_CLOSE:
                    if (listener != null) {
                        listener.onClosed();
                    }
                    break;
                default:
                    if (minusRects[i].contains(x, y)) {
                        step(i, -1);
                    } else if (plusRects[i].contains(x, y)) {
                        step(i, 1);
                    }
                    break;
            }
            invalidate();
            return true;
        }
        return true;
    }

    private void step(int row, int direction) {
        switch (row) {
            case ROW_PORT:
                settings.remotePort = clamp(settings.remotePort + direction, 1024, 65535);
                break;
            case ROW_INTERVAL:
                settings.intervalMs = clamp(settings.intervalMs + direction * 100, 100, 5000);
                break;
            case ROW_ROWS:
                settings.procRows = clamp(settings.procRows + direction, 3, 20);
                break;
            case ROW_BATT_SCALE:
                settings.battScale = clamp(settings.battScale + direction * 0.05f, 0.5f, 2f);
                break;
            case ROW_BATT_OFFSET:
                settings.battOffsetMa = clamp(settings.battOffsetMa + direction * 10, -1000, 1000);
                break;
            case ROW_TEMP_OFFSET:
                settings.tempOffsetC = clamp(settings.tempOffsetC + direction * 0.5f, -20f, 20f);
                break;
            case ROW_TEMP_FAULT:
                settings.tempFaultC = clamp(settings.tempFaultC + direction, 30f, 120f);
                break;
            default:
                return;
        }
        changed();
    }

    private void changed() {
        if (listener != null) {
            listener.onSettingsChanged();
        }
    }

    private void drawCjk(Canvas canvas, String text, int x, int baseline, int size, int color) {
        cjk.setTextSize(size);
        cjk.setColor(color);
        canvas.drawText(text, x, baseline, cjk);
    }

    private void drawCjkRight(Canvas canvas, String text, int right, int baseline, int size,
                              int color) {
        cjk.setTextSize(size);
        cjk.setColor(color);
        cjk.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, right, baseline, cjk);
        cjk.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMonoRight(Canvas canvas, String text, int right, int baseline, int size,
                               int color) {
        mono.setTextSize(size);
        mono.setColor(color);
        mono.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, right, baseline, mono);
        mono.setTextAlign(Paint.Align.LEFT);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
