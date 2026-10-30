package com.knvbepr.panelink.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import java.util.Locale;

/** GPU load bar and current clock. */
public final class GpuPanelView extends PanelView {

    private PanelData data;
    private final Paint fill = new Paint();

    public GpuPanelView(Context context) {
        this(context, null);
    }

    public GpuPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setAntiAlias(false);
    }

    public void setData(PanelData data) {
        this.data = data;
        invalidate();
    }

    private void drawRemote(Canvas canvas, PanelData data) {
        if (data.gpus.isEmpty()) {
            drawCjk(canvas, "无效", pad, titleBaseline() + ui + pad, ui, DashUi.WHITE_60);
            return;
        }
        int left = pad;
        int right = getWidth() - pad;
        int top = titleBaseline() + ui / 2 + ui / 4;
        int count = Math.min(2, data.gpus.size());
        int bottom = getHeight() - pad;
        int rowH = Math.max(ui * 2 + ui / 2, (bottom - top) / count);
        boolean anyOverload = false;
        for (int i = 0; i < count; i++) {
            PanelData.RemoteGpu gpu = data.gpus.get(i);
            int y = top + i * rowH;
            String name = fit(gpu.name, right - left - ui * 5);
            drawMono(canvas, name, left, y + ui, ui, DashUi.WHITE);
            if (gpu.loadPct >= 0) {
                if (gpu.loadPct >= 90) {
                    anyOverload = true;
                    drawPercentBadge(canvas, DashUi.percent(gpu.loadPct), right, y + ui,
                            ui, ui / 2, overloadSolid());
                } else {
                    drawMonoRight(canvas, "%", right, y + ui, ui / 2, DashUi.WHITE_60);
                    drawDsegRight(canvas, DashUi.percent(gpu.loadPct), right - ui, y + ui, ui,
                            gpu.loadPct >= 75 ? DashUi.AMBER : DashUi.WHITE);
                }
            } else {
                drawMonoRight(canvas, "N/A", right, y + ui, ui, DashUi.WHITE_30);
            }
            int barTop = y + ui + ui / 4;
            int barBottom = barTop + Math.max(3, ui / 4);
            framePaint.setColor(DashUi.WHITE_20);
            canvas.drawRect(left, barTop, right, barBottom, framePaint);
            if (gpu.loadPct >= 0) {
                fill.setColor(gpu.loadPct >= 90 ? DashUi.RED
                        : gpu.loadPct >= 75 ? DashUi.AMBER : DashUi.WHITE);
                canvas.drawRect(left + 1, barTop + 1,
                        left + 1 + (right - left - 2) * gpu.loadPct / 100f, barBottom - 1, fill);
            }
            int note = barBottom + ui / 2 + 2;
            if (note < y + rowH) {
                String clock = gpu.clockMhz >= 0 ? gpu.clockMhz + "MHz" : "N/A";
                String temp = gpu.tempC >= 0 ? gpu.tempC + "C" : "N/A";
                String fan = gpu.fanPct >= 0 ? gpu.fanPct + "%" : "N/A";
                drawMono(canvas, clock + "  " + temp + "  FAN " + fan, left, note,
                        ui / 2 + ui / 3, DashUi.WHITE_60);
                if (gpu.memTotalMb > 0) {
                    drawMonoRight(canvas, (gpu.memUsedMb / 1024) + "/" + (gpu.memTotalMb / 1024) + "G",
                            right, note, ui / 2 + ui / 3, DashUi.WHITE_30);
                }
            }
        }
        if (anyOverload) {
            scheduleOverloadBlink();
        }
        if (data.gpus.size() > 2) {
            drawMonoRight(canvas, "+" + (data.gpus.size() - 2), right, getHeight() - pad, ui, DashUi.WHITE_30);
        }
    }

    private String fit(String text, int maxWidth) {
        mono.setTextSize(ui);
        if (mono.measureText(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 2 && mono.measureText(result + "..") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "..";
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        frame(canvas);
        title(canvas, "GPU");

        if (data != null && data.remote) {
            drawRemote(canvas, data);
            return;
        }
        int left = pad;
        int right = getWidth() - pad;
        if (data == null) {
            return;
        }

        String freq = data.gpuFreqMhz >= 0 ? String.valueOf(data.gpuFreqMhz) : "--";
        int freqBaseline = titleBaseline() + ui * 3;
        drawDseg(canvas, freq, left, freqBaseline, ui * 2, DashUi.WHITE);
        drawMono(canvas, "MHz", left + (int) dseg.measureText(freq) + ui / 3, freqBaseline,
                ui / 2, DashUi.WHITE_60);

        if (data.gpuLoadPct >= 0) {
            if (data.gpuLoadPct >= 90) {
                drawPercentBadge(canvas, DashUi.percent(data.gpuLoadPct), right,
                        freqBaseline + 2, ui * 2, ui, overloadSolid());
                scheduleOverloadBlink();
            } else {
                drawMonoRight(canvas, "%", right, freqBaseline + 2, ui, DashUi.WHITE_60);
                drawDsegRight(canvas, DashUi.percent(data.gpuLoadPct), right - ui,
                        freqBaseline + 2, ui * 2,
                        data.gpuLoadPct >= 75 ? DashUi.AMBER : DashUi.WHITE);
            }
        } else {
            drawCjk(canvas, "无效", right - ui * 3, freqBaseline, ui, DashUi.WHITE_30);
        }

        int barTop = freqBaseline + ui / 2;
        int barBottom = barTop + Math.max(5, ui / 2);
        framePaint.setColor(DashUi.WHITE_20);
        canvas.drawRect(left, barTop, right, barBottom, framePaint);
        if (data.gpuLoadPct >= 0) {
            fill.setColor(data.gpuLoadPct >= 90 ? DashUi.RED
                    : data.gpuLoadPct >= 75 ? DashUi.AMBER : DashUi.WHITE);
            canvas.drawRect(left + 1, barTop + 1,
                    left + 1 + (right - left - 2) * data.gpuLoadPct / 100f, barBottom - 1, fill);
        }

    }
}
