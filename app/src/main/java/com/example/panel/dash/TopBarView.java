package com.example.panel.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.example.panel.ui.PanelTypefaces;

import java.util.Locale;

/**
 * Top status strip. Every cluster gets a fixed slot so changing digit counts
 * (rates, battery current, percentages) never shove the neighbours sideways.
 */
public final class TopBarView extends View {

    private final int ui;
    private final int stroke;
    private final Paint p;
    private final Paint cjk;
    private final Paint dseg;
    private final Paint mono;

    private final Rect settingsRect = new Rect();
    private Runnable onSettingsClick;
    private PanelData data;

    public TopBarView(Context context) {
        this(context, null);
    }

    public TopBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        ui = DashUi.unit(context);
        stroke = DashUi.stroke(context);
        p = new Paint();
        p.setAntiAlias(false);
        cjk = DashUi.paint(context, PanelTypefaces.pixel(context), ui, DashUi.WHITE,
                Paint.Align.LEFT, false);
        dseg = DashUi.paint(context, PanelTypefaces.dseg(context), ui * 2f, DashUi.WHITE,
                Paint.Align.LEFT, true);
        mono = DashUi.paint(context, PanelTypefaces.mono(context), ui, DashUi.WHITE,
                Paint.Align.LEFT, true);
    }

    public void setOnSettingsClickListener(Runnable listener) {
        onSettingsClick = listener;
    }

    public void setData(PanelData data) {
        this.data = data;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(Integer.MAX_VALUE, widthMeasureSpec),
                resolveSize(ui * 7 / 2, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int pad = ui / 2;
        int bigBase = h / 2 + ui;
        int smallBase = h / 2 + ui / 3;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setColor(DashUi.WHITE_20);
        canvas.drawLine(0, h - 1, w - 1, h - 1, p);
        if (data == null) {
            return;
        }

        // Fixed slots: time 19%, rates 24%, connectivity 24%, right 33%.
        int timeL = pad;
        int timeR = w * 19 / 100;
        int rateL = timeR;
        int rateR = w * 43 / 100;
        int connL = rateR;
        int connR = w * 67 / 100;

        String time = DashUi.time(data.timeMs);
        drawDseg(canvas, time, timeL + ui / 2, bigBase, ui * 2, DashUi.WHITE);
        p.setColor(DashUi.WHITE_20);
        canvas.drawLine(timeR, pad, timeR, h - pad, p);

        int slot = (rateR - rateL) / 2;
        drawRateSlot(canvas, rateL, rateL + slot, smallBase, true, data.rxBps);
        drawRateSlot(canvas, rateL + slot, rateR, smallBase, false, data.txBps);
        p.setColor(DashUi.WHITE_15);
        canvas.drawLine(rateL + slot, h / 4, rateL + slot, h * 3 / 4, p);
        canvas.drawLine(rateR, h / 4, rateR, h * 3 / 4, p);

        drawConnectivity(canvas, connL, connR, h, bigBase, smallBase);

        drawRight(canvas, connR, w, h, bigBase, smallBase);
    }

    private void drawRateSlot(Canvas canvas, int left, int right, int baseline, boolean rx,
                              long bps) {
        int arrow = ui / 2 + ui / 3;
        int tagSize = ui / 2 + ui / 4;
        int unitSize = ui / 2 + ui / 3;
        int inner = ui / 2;
        String tag = rx ? "RX" : "TX";
        String rate = DashUi.rate(bps);
        int split = rate.length();
        while (split > 0) {
            char c = rate.charAt(split - 1);
            if (Character.isDigit(c) || c == '.') split--; else break;
        }
        String number = rate.substring(0, split);
        String unit = rate.substring(split);

        mono.setTextSize(tagSize);
        int tagW = (int) mono.measureText(tag);
        unitSize = ui / 2 + ui / 3;
        mono.setTextSize(ui);
        int slashW = (int) mono.measureText("/s");
        int unitW = (int) mono.measureText(unit);
        dseg.setTextSize(ui);
        int numberW = (int) dseg.measureText(number);
        int groupW = tagW + 4 + arrow + 4 + numberW + 2 + unitW + slashW;
        int x = Math.max(left + inner, right - inner - groupW);
        drawMono(canvas, tag, x, baseline, tagSize, DashUi.WHITE_30);
        x += tagW + 4;
        PixelIcons.drawArrow(canvas, p, x, baseline - arrow, arrow, !rx);
        x += arrow + 4;
        drawDseg(canvas, number, x, baseline, ui, DashUi.WHITE);
        x += numberW + 2;
        drawMono(canvas, unit, x, baseline, unitSize, DashUi.WHITE_60);
        x += unitW;
        drawMono(canvas, "/s", x, baseline, unitSize, DashUi.WHITE_60);
    }

    private void drawConnectivity(Canvas canvas, int left, int right, int h, int bigBase,
                                  int smallBase) {
        int quarter = (right - left) / 4;
        int icon = ui - 2;
        int cy = h / 2;
        int labelSize = ui / 2 + ui / 3;

        int wifiCx = left + quarter / 2;
        PixelIcons.drawWifi(canvas, p, wifiCx - icon / 2, cy - icon / 2, icon,
                data.wifiConnected, data.internetState, data.txBps > 1024, data.rxBps > 1024);

        int cellCx = left + quarter + quarter / 2;
        String gen = data.cellConnected ? data.cellType : "无";
        mono.setTextSize(labelSize);
        int genW = (int) mono.measureText(gen);
        int cellGroup = icon + 4 + genW;
        int cellStart = cellCx - cellGroup / 2;
        PixelIcons.drawSignal(canvas, p, cellStart, cy - icon / 2, icon, data.cellConnected);
        if (data.cellConnected) {
            drawMono(canvas, gen, cellStart + icon + 4, smallBase, labelSize, DashUi.WHITE_60);
        } else {
            drawCjk(canvas, gen, cellStart + icon + 4, smallBase, labelSize, DashUi.WHITE_30);
        }

        if (data.ethernet) {
            int ethCx = left + quarter * 2 + quarter / 2;
            PixelIcons.drawEthernet(canvas, p, ethCx - icon / 2, cy - icon / 2, icon);
        }
        if (data.usbPlugged) {
            int usbCx = left + quarter * 3 + quarter / 2;
            PixelIcons.drawUsb(canvas, p, usbCx - icon / 2, cy - icon / 2, icon);
        }
    }

    private void drawRight(Canvas canvas, int left, int w, int h, int bigBase, int smallBase) {
        int pad = ui / 2;
        int gear = ui * 2;
        int gearX = w - pad - gear;
        settingsRect.set(gearX - ui, 0, w, h);
        PixelIcons.drawGear(canvas, p, gearX, h / 2 - gear / 2, gear);

        int rootRight = gearX - ui / 2;
        String root = data.remoteConnected ? "REMOTE" : data.root ? "ROOT" : "NO ROOT";
        drawMonoRight(canvas, root, rootRight, smallBase, ui / 2 + ui / 3,
                data.remoteConnected ? DashUi.GREEN
                        : data.root ? DashUi.WHITE_60 : DashUi.AMBER);

        int battRight = rootRight - ui * 5;
        boolean compact = w < 1200;
        int pctSize = compact ? ui + ui / 4 : ui + ui / 2;
        int pctBase = h / 2 + pctSize / 2;
        String pctText = String.format(Locale.US, "%03d", Math.max(0, data.batteryPct));
        dseg.setTextSize(pctSize);
        int pctWidth = (int) dseg.measureText(pctText);
        mono.setTextSize(ui);
        int unitWidth = (int) mono.measureText("%");
        int batW = compact ? ui * 2 : ui * 5 / 2;
        int batH = pctSize;
        int pctRight = battRight - unitWidth - 2;
        int batX = pctRight - pctWidth - ui / 2 - batW;
        int pctColor = data.batteryPct <= 15 ? DashUi.RED
                : data.batteryPct <= 30 ? DashUi.AMBER : DashUi.WHITE;
        PixelIcons.drawBattery(canvas, p, batX, h / 2 - batH / 2, batW, batH,
                Math.max(0, data.batteryPct), data.charging);
        drawDseg(canvas, pctText, pctRight - pctWidth, pctBase, pctSize, pctColor);
        drawMono(canvas, "%", battRight, pctBase, ui, DashUi.WHITE_60);

        int currentRight = batX - ui / 2;
        if (data.batteryCurrentValid) {
            String state = compact
                    ? (data.charging ? "充" : "放")
                    : (data.batteryStatus == android.os.BatteryManager.BATTERY_STATUS_FULL
                    ? "已满" : data.charging ? "充电" : "放电");
            String current = String.valueOf(data.batteryCurrentMa);
            int stateSize = compact ? ui : ui;
            cjk.setTextSize(stateSize);
            int stateW = (int) cjk.measureText(state);
            dseg.setTextSize(ui);
            int currentW = (int) dseg.measureText(current);
            mono.setTextSize(ui);
            int unitW = (int) mono.measureText("mA");
            int gap = compact ? ui / 4 : ui / 2;
            int total = stateW + gap + currentW + unitW;
            int start = currentRight - total;
            drawCjk(canvas, state, start, smallBase, stateSize,
                    data.charging ? DashUi.GREEN : DashUi.WHITE_60);
            drawDseg(canvas, current, start + stateW + gap, smallBase, ui, DashUi.WHITE);
            drawMono(canvas, "mA", start + stateW + gap + currentW, smallBase, ui,
                    DashUi.WHITE_60);
        }
    }

    private void drawMonoCentered(Canvas canvas, String text, int cx, int baseline, int size,
                                  int color) {
        mono.setTextSize(size);
        mono.setColor(color);
        mono.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx, baseline, mono);
        mono.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCjkCentered(Canvas canvas, String text, int cx, int baseline, int size,
                                 int color) {
        cjk.setTextSize(size);
        cjk.setColor(color);
        cjk.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx, baseline, cjk);
        cjk.setTextAlign(Paint.Align.LEFT);
    }

    private void drawCjk(Canvas canvas, String text, int x, int baseline, int size, int color) {
        cjk.setTextSize(size);
        cjk.setColor(color);
        canvas.drawText(text, x, baseline, cjk);
    }

    private void drawMono(Canvas canvas, String text, int x, int baseline, int size, int color) {
        mono.setTextSize(size);
        mono.setColor(color);
        canvas.drawText(text, x, baseline, mono);
    }

    private void drawMonoRight(Canvas canvas, String text, int right, int baseline, int size,
                               int color) {
        mono.setTextSize(size);
        mono.setColor(color);
        mono.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, right, baseline, mono);
        mono.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDseg(Canvas canvas, String text, int x, int baseline, int size, int color) {
        dseg.setTextSize(size);
        dseg.setColor(color);
        canvas.drawText(text, x, baseline, dseg);
    }

    private void drawDsegRight(Canvas canvas, String text, int right, int baseline, int size,
                               int color) {
        dseg.setTextSize(size);
        dseg.setColor(color);
        dseg.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, right, baseline, dseg);
        dseg.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP
                && settingsRect.contains((int) event.getX(), (int) event.getY())) {
            if (onSettingsClick != null) {
                onSettingsClick.run();
            }
        }
        return true;
    }
}
