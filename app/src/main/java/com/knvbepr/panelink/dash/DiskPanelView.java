package com.knvbepr.panelink.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

/** Disk usage and live read/write rates. */
public final class DiskPanelView extends PanelView {

    private PanelData data;
    private final Paint fill = new Paint();

    public DiskPanelView(Context context) {
        this(context, null);
    }

    public DiskPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setAntiAlias(false);
    }

    public void setData(PanelData data) {
        this.data = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        frame(canvas);
        title(canvas, "磁盘");

        if (data != null && data.remote) {
            drawRemote(canvas, data);
            return;
        }
        if (data == null || data.diskTotalKb <= 0) {
            drawCjk(canvas, "读取中", pad, titleBaseline() + ui + pad, ui, DashUi.WHITE_30);
            return;
        }
        float pct = 100f * data.diskUsedKb / data.diskTotalKb;
        int color = pct >= 95 ? DashUi.RED : pct >= 85 ? DashUi.AMBER : DashUi.WHITE;
        if (pct >= 95) {
            drawPercentBadge(canvas, DashUi.percent(pct), getWidth() - pad,
                    titleBaseline() + ui / 2 + 2, ui * 2, ui, overloadSolid());
            scheduleOverloadBlink();
        } else {
            drawMonoRight(canvas, "%", getWidth() - pad, titleBaseline() + ui / 2 + 2, ui,
                    DashUi.WHITE_60);
            drawDsegRight(canvas, DashUi.percent(pct), getWidth() - pad - ui,
                    titleBaseline() + ui / 2 + 2, ui * 2, color);
        }

        int left = pad;
        int right = getWidth() - pad;
        int barTop = titleBaseline() + ui + ui / 3;
        int barBottom = barTop + Math.max(4, ui / 3);
        framePaint.setColor(DashUi.WHITE_20);
        canvas.drawRect(left, barTop, right, barBottom, framePaint);
        fill.setColor(color);
        canvas.drawRect(left + 1, barTop + 1, left + 1 + (right - left - 2) * pct / 100f,
                barBottom - 1, fill);

        int y = barBottom + ui + ui / 2;
        drawRate(canvas, "读", data.diskReadBps, y, DashUi.WHITE);
        y += ui + ui / 4;
        if (y < getHeight() - pad) {
            drawRate(canvas, "写", data.diskWriteBps, y, DashUi.WHITE_60);
        }
    }

    private void drawRemote(Canvas canvas, PanelData data) {
        if (data.disks.isEmpty()) {
            drawCjk(canvas, "无效", pad, titleBaseline() + ui + pad, ui, DashUi.WHITE_60);
            return;
        }
        int left = pad;
        int right = getWidth() - pad;
        int top = titleBaseline() + ui / 2 + ui / 4;
        int bottom = getHeight() - pad;
        int count = Math.min(3, data.disks.size());
        int rowH = Math.max(ui + ui / 2, (bottom - top) / count);
        int infoSize = ui * 3 / 4;
        boolean anyOverload = false;
        for (int i = 0; i < count; i++) {
            PanelData.RemoteDisk disk = data.disks.get(i);
            int y = top + i * rowH;
            int baseline = y + ui;
            long pct = disk.totalBytes > 0 ? disk.usedBytes * 100 / disk.totalBytes : 0;
            drawMono(canvas, disk.name, left, baseline, ui, DashUi.WHITE);
            if (pct >= 95) {
                anyOverload = true;
                drawPercentBadge(canvas, DashUi.percent(pct), right, baseline, ui, infoSize,
                        overloadSolid());
            } else {
                mono.setTextSize(infoSize);
                int unitW = (int) mono.measureText("%");
                drawDsegRight(canvas, DashUi.percent(pct), right - unitW - 2, baseline, ui,
                        pct >= 85 ? DashUi.AMBER : DashUi.WHITE_60);
                drawMonoRight(canvas, "%", right, baseline, infoSize, DashUi.WHITE_60);
            }

            int x = left + (int) mono.measureText(disk.name) + ui;
            drawMono(canvas, "R", x, baseline, infoSize, DashUi.WHITE_30);
            x += (int) mono.measureText("R") + 2;
            String read = DashUi.rate(disk.readBps);
            drawDseg(canvas, read, x, baseline, infoSize, DashUi.WHITE_60);
            x += (int) dseg.measureText(read) + 2;
            drawMono(canvas, "/s", x, baseline, infoSize, DashUi.WHITE_30);
            x += (int) mono.measureText("/s") + ui / 2;
            drawMono(canvas, "W", x, baseline, infoSize, DashUi.WHITE_30);
            x += (int) mono.measureText("W") + 2;
            String write = DashUi.rate(disk.writeBps);
            drawDseg(canvas, write, x, baseline, infoSize, DashUi.WHITE_60);
            x += (int) dseg.measureText(write) + 2;
            drawMono(canvas, "/s", x, baseline, infoSize, DashUi.WHITE_30);

            int barTop = y + ui + ui / 4;
            int barBottom = barTop + Math.max(2, ui / 8);
            framePaint.setColor(DashUi.WHITE_20);
            canvas.drawRect(left, barTop, right, barBottom, framePaint);
            fill.setColor(pct >= 95 ? DashUi.RED : pct >= 85 ? DashUi.AMBER : DashUi.WHITE);
            canvas.drawRect(left + 1, barTop + 1, left + 1 + (right - left - 2) * pct / 100f,
                    barBottom - 1, fill);
        }
        if (anyOverload) {
            scheduleOverloadBlink();
        }
    }

    private void drawRate(Canvas canvas, String label, long bps, int baseline, int color) {
        int left = pad;
        drawCjk(canvas, label, left, baseline, ui, color);
        int x = left + (int) cjk.measureText(label) + ui / 2;
        String rate = DashUi.rate(bps);
        drawDseg(canvas, rate, x, baseline, ui, color);
        x += (int) dseg.measureText(rate) + 2;
        drawMono(canvas, "/s", x, baseline, ui / 2, DashUi.WHITE_60);
        x += (int) mono.measureText("/s") + ui / 2;

        int barRight = getWidth() - pad;
        int barLeft = x;
        if (barRight - barLeft < ui) {
            return;
        }
        int top = baseline - ui + 2;
        int bottom = baseline;
        framePaint.setColor(DashUi.WHITE_20);
        canvas.drawRect(barLeft, top, barRight, bottom, framePaint);
        long scale = Math.max(1024L * 1024L, Math.max(data.diskReadBps, data.diskWriteBps));
        int w = (int) ((barRight - barLeft - 2) * Math.min(1.0, bps / (double) scale));
        fill.setColor(color);
        canvas.drawRect(barLeft + 1, top + 1, barLeft + 1 + w, bottom - 1, fill);
    }
}
