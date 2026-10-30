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

    private static final String VERSION = "1.5.0";

    public interface Listener {
        void onSettingsChanged();

        void onClosed();

        void onDiscoverRequested();

        void onEditHostRequested();

        void onExitRequested();
    }

    private static final int ROW_REMOTE = 0;
    private static final int ROW_HOST = 1;
    private static final int ROW_PORT = 2;
    private static final int ROW_FULLSCREEN = 3;
    private static final int ROW_INTERVAL = 4;
    private static final int ROW_ROWS = 5;
    private static final int ROW_TEMP_WARN = 6;
    private static final int ROW_BATT_SCALE = 7;
    private static final int ROW_BATT_OFFSET = 8;
    private static final int ROW_TEMP_OFFSET = 9;
    private static final int ROW_TEMP_FAULT = 10;
    private static final int ROW_RESET = 11;
    private static final int ROW_CLOSE = 12;
    private static final int ROWS = 13;
    private static final int COLS = 2;
    private static final int PER_COL = 7;

    private final int ui;
    private final Paint p = new Paint();
    private final Paint cjk;
    private final Paint dseg;
    private final Paint mono;
    private final Rect[] rowRects = new Rect[ROWS];
    private final Rect[] minusRects = new Rect[ROWS];
    private final Rect[] plusRects = new Rect[ROWS];
    private final Rect backRect = new Rect();
    private final Rect aboutRect = new Rect();
    private final Rect aboutDialogRect = new Rect();
    private final Rect aboutCloseRect = new Rect();
    private final Rect hostEditRect = new Rect();
    private final Rect exitDialogRect = new Rect();
    private final Rect exitCancelRect = new Rect();
    private final Rect exitOkRect = new Rect();
    private final Rect editDialogRect = new Rect();
    private final Rect[] keyRects = new Rect[12];
    private final Rect editBackRect = new Rect();
    private final Rect editCancelRect = new Rect();
    private final Rect editOkRect = new Rect();
    private boolean confirmExit;
    private boolean aboutVisible;
    private int editingRow = -1;
    private final StringBuilder editText = new StringBuilder();
    private boolean editFresh;

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
        for (int i = 0; i < keyRects.length; i++) {
            keyRects[i] = new Rect();
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

        int backW = Math.min(w / 4, ui * 9);
        backRect.set(ui, h - ui * 3, ui + backW, h - ui / 2);
        aboutRect.set(backRect.right + ui, backRect.top, backRect.right + ui + backW,
                backRect.bottom);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(DashUi.WHITE_50);
        canvas.drawRect(backRect, p);
        canvas.drawRect(aboutRect, p);
        drawCjk(canvas, "返回", backRect.left + ui / 2,
                backRect.centerY() + labelSize / 3, labelSize, DashUi.WHITE);
        drawCjk(canvas, "关于", aboutRect.left + ui / 2,
                aboutRect.centerY() + labelSize / 3, labelSize, DashUi.WHITE);
        if (aboutVisible) {
            drawAbout(canvas, w, h);
        } else if (editingRow >= 0) {
            drawNumericEditor(canvas, w, h);
        } else if (confirmExit) {
            drawExitConfirm(canvas, w, h);
        }
    }

    private void drawAbout(Canvas canvas, int w, int h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB0000000);
        canvas.drawRect(0, 0, w, h, p);

        int dw = Math.min(w * 4 / 5, ui * 52);
        int dh = Math.min(h * 4 / 5, ui * 24);
        int left = (w - dw) / 2;
        int top = (h - dh) / 2;
        aboutDialogRect.set(left, top, left + dw, top + dh);
        p.setColor(DashUi.BLACK);
        canvas.drawRect(aboutDialogRect, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE_60);
        canvas.drawRect(aboutDialogRect, p);

        drawCjk(canvas, "关于 PANELINK", left + ui, top + ui * 2, labelSize, DashUi.WHITE);
        drawMono(canvas, "v" + VERSION, left + ui, top + ui * 4,
                ui, DashUi.WHITE_60);
        drawCjk(canvas, "作者  knvbepr", left + ui, top + ui * 6, ui, DashUi.WHITE_60);
        drawCjk(canvas, "开源组件", left + ui, top + ui * 8, ui, DashUi.WHITE);

        String[] licenses = {
                "AndroidX / AppCompat      Apache-2.0",
                "DSEG7 Classic             SIL OFL 1.1",
                "JetBrains Mono            SIL OFL 1.1",
                "WenQuanYi Bitmap Song     GPL-2.0 + font exception",
                "LibreHardwareMonitor      MPL-2.0",
        };
        int y = top + ui * 10;
        for (String line : licenses) {
            drawMono(canvas, line, left + ui, y, ui / 2 + ui / 3, DashUi.WHITE_60);
            y += ui + ui / 2;
        }
        drawMono(canvas, "Source: github.com/knvbepr/panelink", left + ui, y,
                ui / 2 + ui / 3, DashUi.WHITE_30);

        int btnW = Math.min(dw / 3, ui * 8);
        int btnH = ui * 2 + ui / 2;
        aboutCloseRect.set(left + dw - ui - btnW, top + dh - ui / 2 - btnH,
                left + dw - ui, top + dh - ui / 2);
        drawDialogButton(canvas, aboutCloseRect, "关闭", DashUi.WHITE);
    }

    private void drawExitConfirm(Canvas canvas, int w, int h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB0000000);
        canvas.drawRect(0, 0, w, h, p);

        int dw = Math.min(w * 3 / 5, ui * 40);
        int dh = Math.min(h / 3, ui * 12);
        int left = (w - dw) / 2;
        int top = (h - dh) / 2;
        exitDialogRect.set(left, top, left + dw, top + dh);

        p.setColor(DashUi.BLACK);
        canvas.drawRect(exitDialogRect, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE_60);
        canvas.drawRect(exitDialogRect, p);

        drawCjk(canvas, "退出 PANELINK", left + ui, top + ui * 2, labelSize, DashUi.WHITE);
        drawCjk(canvas, "确定退出系统面板？", left + ui, top + ui * 4, ui, DashUi.WHITE_60);

        int btnH = ui * 2 + ui / 2;
        int btnW = (dw - ui * 3) / 2;
        int by = top + dh - btnH - ui / 2;
        exitCancelRect.set(left + ui, by, left + ui + btnW, by + btnH);
        exitOkRect.set(left + dw - ui - btnW, by, left + dw - ui, by + btnH);
        drawDialogButton(canvas, exitCancelRect, "取消", DashUi.WHITE_30);
        drawDialogButton(canvas, exitOkRect, "退出", DashUi.WHITE);
    }

    private void drawDialogButton(Canvas canvas, Rect rect, String text, int border) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(border);
        canvas.drawRect(rect, p);
        cjk.setTextSize(labelSize);
        cjk.setColor(DashUi.WHITE);
        cjk.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, rect.centerX(), rect.centerY() + labelSize / 3, cjk);
        cjk.setTextAlign(Paint.Align.LEFT);
    }

    private void drawNumericEditor(Canvas canvas, int w, int h) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xB0000000);
        canvas.drawRect(0, 0, w, h, p);

        int dw = Math.min(w * 7 / 10, ui * 40);
        int dh = Math.min(h * 9 / 10, ui * 26);
        int left = (w - dw) / 2;
        int top = (h - dh) / 2;
        editDialogRect.set(left, top, left + dw, top + dh);

        p.setColor(DashUi.BLACK);
        canvas.drawRect(editDialogRect, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE_60);
        canvas.drawRect(editDialogRect, p);

        drawCjk(canvas, labelFor(editingRow), left + ui, top + ui * 2, labelSize, DashUi.WHITE);
        String shown = editText.length() == 0 ? "0" : editText.toString();
        drawDsegRight(canvas, shown, left + dw - ui, top + ui * 2, ui * 2, DashUi.WHITE);
        p.setColor(DashUi.WHITE_20);
        canvas.drawLine(left + ui, top + ui * 3, left + dw - ui, top + ui * 3, p);

        int gridLeft = left + ui;
        int gridTop = top + ui * 4;
        int gridBottom = top + dh - ui * 4;
        int keyW = (dw - ui * 2) / 3;
        int keyH = Math.max(ui * 2, (gridBottom - gridTop) / 4);
        String[] keys = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "-", "0", "."};
        for (int i = 0; i < 12; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = gridLeft + col * keyW;
            int y = gridTop + row * keyH;
            keyRects[i].set(x, y, x + keyW, y + keyH);
            p.setColor(DashUi.WHITE_30);
            canvas.drawRect(keyRects[i], p);
            dseg.setTextSize(ui * 2);
            dseg.setColor(DashUi.WHITE);
            dseg.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(keys[i], keyRects[i].centerX(), keyRects[i].centerY() + ui / 2, dseg);
            dseg.setTextAlign(Paint.Align.LEFT);
        }

        int actionY = top + dh - ui * 3;
        int actionH = ui * 2;
        int actionW = (dw - ui * 4) / 3;
        editBackRect.set(left + ui, actionY, left + ui + actionW, actionY + actionH);
        editCancelRect.set(left + ui * 2 + actionW, actionY,
                left + ui * 2 + actionW * 2, actionY + actionH);
        editOkRect.set(left + ui * 3 + actionW * 2, actionY,
                left + ui * 3 + actionW * 3, actionY + actionH);
        drawDialogButton(canvas, editBackRect, "退格", DashUi.WHITE_30);
        drawDialogButton(canvas, editCancelRect, "取消", DashUi.WHITE_30);
        drawDialogButton(canvas, editOkRect, "确定", DashUi.WHITE);
    }

    private String labelFor(int row) {
        switch (row) {
            case ROW_PORT: return "端口";
            case ROW_INTERVAL: return "刷新间隔 ms";
            case ROW_ROWS: return "进程行数";
            case ROW_BATT_SCALE: return "电流倍率";
            case ROW_BATT_OFFSET: return "电流偏移 mA";
            case ROW_TEMP_OFFSET: return "温度偏移 C";
            case ROW_TEMP_WARN: return "温度预警 C";
            case ROW_TEMP_FAULT: return "温度报警 C";
            default: return "";
        }
    }

    private String rawValue(int row) {
        switch (row) {
            case ROW_PORT: return String.valueOf(settings.remotePort);
            case ROW_INTERVAL: return String.valueOf(settings.intervalMs);
            case ROW_ROWS: return String.valueOf(settings.procRows);
            case ROW_BATT_SCALE: return String.format(Locale.US, "%.2f", settings.battScale);
            case ROW_BATT_OFFSET: return String.valueOf(settings.battOffsetMa);
            case ROW_TEMP_OFFSET: return String.format(Locale.US, "%.1f", settings.tempOffsetC);
            case ROW_TEMP_WARN: return String.format(Locale.US, "%.0f", settings.tempWarnC);
            case ROW_TEMP_FAULT: return String.format(Locale.US, "%.0f", settings.tempFaultC);
            default: return "";
        }
    }

    private void beginEdit(int row) {
        editingRow = row;
        editText.setLength(0);
        editText.append(rawValue(row));
        editFresh = true;
        confirmExit = false;
        invalidate();
    }

    public void cancelNumericEdit() {
        editingRow = -1;
        invalidate();
    }

    public boolean isNumericEditVisible() {
        return editingRow >= 0;
    }

    private void handleNumericTouch(int x, int y) {
        for (int i = 0; i < keyRects.length; i++) {
            if (!keyRects[i].contains(x, y)) {
                continue;
            }
            String key = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "-",
                    "0", "."}[i];
            if (editFresh) {
                editText.setLength(0);
                editFresh = false;
            }
            if (".".equals(key)) {
                if (editText.indexOf(".") < 0) {
                    editText.append('.');
                }
            } else if ("-".equals(key)) {
                if (editText.length() == 0 || editText.charAt(0) != '-') {
                    editText.insert(0, '-');
                } else {
                    editText.deleteCharAt(0);
                }
            } else if (editText.length() < 9) {
                editText.append(key);
            }
            invalidate();
            return;
        }
        if (editBackRect.contains(x, y)) {
            if (editFresh) {
                editText.setLength(0);
                editFresh = false;
            } else if (editText.length() > 0) {
                editText.deleteCharAt(editText.length() - 1);
            }
            invalidate();
            return;
        }
        if (editCancelRect.contains(x, y)) {
            editingRow = -1;
            invalidate();
            return;
        }
        if (editOkRect.contains(x, y)) {
            applyEdited();
            return;
        }
        if (!editDialogRect.contains(x, y)) {
            editingRow = -1;
            invalidate();
        }
    }

    private void applyEdited() {
        String text = editText.toString();
        if (text.isEmpty() || "-".equals(text) || ".".equals(text)) {
            editingRow = -1;
            return;
        }
        try {
            int row = editingRow;
            switch (row) {
                case ROW_PORT:
                    settings.remotePort = clamp(Integer.parseInt(text), 1024, 65535);
                    break;
                case ROW_INTERVAL:
                    settings.intervalMs = clamp(Integer.parseInt(text), 100, 5000);
                    break;
                case ROW_ROWS:
                    settings.procRows = clamp(Integer.parseInt(text), 3, 20);
                    break;
                case ROW_BATT_SCALE:
                    settings.battScale = clamp(Float.parseFloat(text), 0.5f, 2f);
                    break;
                case ROW_BATT_OFFSET:
                    settings.battOffsetMa = clamp(Integer.parseInt(text), -1000, 1000);
                    break;
                case ROW_TEMP_OFFSET:
                    settings.tempOffsetC = clamp(Float.parseFloat(text), -20f, 20f);
                    break;
                case ROW_TEMP_WARN:
                    settings.tempWarnC = clamp(Float.parseFloat(text), 30f,
                            Math.max(30f, settings.tempFaultC - 1f));
                    break;
                case ROW_TEMP_FAULT:
                    settings.tempFaultC = clamp(Float.parseFloat(text),
                            settings.tempWarnC + 1f, 120f);
                    break;
                default:
                    break;
            }
            editingRow = -1;
            changed();
        } catch (Exception ignored) {
            // invalid text: leave the editor open
        }
        invalidate();
    }

    public void cancelExitConfirm() {
        confirmExit = false;
        invalidate();
    }

    public void cancelEditors() {
        confirmExit = false;
        editingRow = -1;
        aboutVisible = false;
        invalidate();
    }

    public boolean isAboutVisible() {
        return aboutVisible;
    }

    public void cancelAbout() {
        aboutVisible = false;
        invalidate();
    }

    public boolean isExitConfirmVisible() {
        return confirmExit;
    }

    private void layoutRow(int index, int left, int right, int y) {
        rowRects[index].set(left, y, right, y + rowH);
        int btn = Math.min(rowH - 4, ui * 3);
        plusRects[index].set(right - btn, y + (rowH - btn) / 2, right, y + (rowH + btn) / 2);
        minusRects[index].set(right - btn * 2 - ui / 2, y + (rowH - btn) / 2,
                right - btn - ui / 2, y + (rowH + btn) / 2);
        if (index == ROW_HOST) {
            int hb = Math.min(ui * 5, right - left);
            hostEditRect.set(right - hb, y + (rowH - btn) / 2, right,
                    y + (rowH + btn) / 2);
        }
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
            case ROW_TEMP_WARN:
                label = "温度预警";
                value = String.format(Locale.US, "%.0fC", settings.tempWarnC);
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
                label = "温度报警";
                value = String.format(Locale.US, "%.0fC", settings.tempFaultC);
                hasStep = true;
                break;
            case ROW_RESET:
                label = "恢复默认";
                value = "";
                break;
            case ROW_CLOSE:
            default:
                label = "退出";
                value = "";
                break;
        }

        drawCjk(canvas, label, left, baseline, labelSize, DashUi.WHITE);
        if (hasStep) {
            dseg.setTextSize(valueSize);
            dseg.setColor(DashUi.WHITE);
            dseg.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(value, minusRects[index].left - ui, baseline, dseg);
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
                drawMonoRight(canvas, value, hostEditRect.left - ui, baseline, valueSize,
                        DashUi.WHITE_60);
                drawTextButton(canvas, hostEditRect, "编辑", valueSize);
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
            drawCjk(canvas, index == ROW_RESET ? "重置" : "退出", bx + ui / 2,
                    baseline, labelSize, DashUi.WHITE);
        }
    }

    private void drawTextButton(Canvas canvas, Rect rect, String text, int textSize) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE_50);
        canvas.drawRect(rect, p);
        drawCjk(canvas, text, rect.left + ui / 2, rect.centerY() + textSize / 3,
                textSize, DashUi.WHITE);
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
        if (aboutVisible) {
            if (aboutCloseRect.contains(x, y) || !aboutDialogRect.contains(x, y)) {
                aboutVisible = false;
            }
            invalidate();
            return true;
        }
        if (editingRow >= 0) {
            handleNumericTouch(x, y);
            return true;
        }
        if (confirmExit) {
            if (exitOkRect.contains(x, y)) {
                confirmExit = false;
                listener.onExitRequested();
            } else if (exitCancelRect.contains(x, y) || !exitDialogRect.contains(x, y)) {
                confirmExit = false;
            }
            invalidate();
            return true;
        }
        if (aboutRect.contains(x, y)) {
            aboutVisible = true;
            invalidate();
            return true;
        }
        if (hostEditRect.contains(x, y)) {
            if (listener != null) {
                listener.onEditHostRequested();
            }
            return true;
        }
        if (backRect.contains(x, y)) {
            if (listener != null) {
                listener.onClosed();
            }
            return true;
        }
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
                    confirmExit = true;
                    break;
                default:
                    if (minusRects[i].contains(x, y)) {
                        step(i, -1);
                    } else if (plusRects[i].contains(x, y)) {
                        step(i, 1);
                    } else {
                        beginEdit(i);
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
            case ROW_TEMP_WARN:
                settings.tempWarnC = clamp(settings.tempWarnC + direction, 30f,
                        Math.max(30f, settings.tempFaultC - 1f));
                break;
            case ROW_TEMP_FAULT:
                settings.tempFaultC = clamp(settings.tempFaultC + direction,
                        settings.tempWarnC + 1f, 120f);
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

    private void drawMono(Canvas canvas, String text, int x, int baseline, int size, int color) {
        mono.setTextSize(size);
        mono.setColor(color);
        mono.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, x, baseline, mono);
    }

    private void drawDsegRight(Canvas canvas, String text, int right, int baseline, int size,
                               int color) {
        dseg.setTextSize(size);
        dseg.setColor(color);
        dseg.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, right, baseline, dseg);
        dseg.setTextAlign(Paint.Align.LEFT);
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
