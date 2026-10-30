package com.example.panel.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.Locale;

/** CPU history. Tap toggles total / per-core; frequencies are always shown. */
public final class CpuPanelView extends PanelView {

    private static final int HISTORY = 60;
    private static final float THRESHOLD = 80f;

    private final float[] totalHistory = new float[HISTORY];
    private final float[][] coreHistory = new float[8][HISTORY];
    private int count;

    private PanelData data;
    private boolean perCore;

    private final Paint grid = new Paint();
    private final Paint line = new Paint();
    private final Paint dashed = new Paint();

    public CpuPanelView(Context context) {
        this(context, null);
    }

    public CpuPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        grid.setAntiAlias(false);
        grid.setStrokeWidth(1);
        grid.setColor(DashUi.WHITE_15);
        line.setAntiAlias(true);
        line.setStrokeWidth(stroke * 2);
        line.setColor(DashUi.WHITE);
        dashed.setAntiAlias(false);
        dashed.setStrokeWidth(1);
        dashed.setColor(DashUi.RED);
        dashed.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0));
    }

    public void setData(PanelData data) {
        this.data = data;
        if (data != null) {
            shift();
            totalHistory[HISTORY - 1] = data.cpuTotal;
            int cores = Math.min(coreHistory.length, data.cpuCores.length);
            for (int i = 0; i < cores; i++) {
                coreHistory[i][HISTORY - 1] = data.cpuCores[i];
            }
            if (count < HISTORY) {
                count++;
            }
        }
        invalidate();
    }

    private void shift() {
        System.arraycopy(totalHistory, 1, totalHistory, 0, HISTORY - 1);
        for (float[] history : coreHistory) {
            System.arraycopy(history, 1, history, 0, HISTORY - 1);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        frame(canvas);
        title(canvas, "CPU");

        int right = getWidth() - pad;
        if (data != null) {
            drawMonoRight(canvas, "%", right, titleBaseline() + ui / 2 + dp(2), ui,
                    DashUi.WHITE_60);
            drawDsegRight(canvas, DashUi.percent(data.cpuTotal), right - ui,
                    titleBaseline() + ui / 2 + dp(2), ui * 2,
                    data.cpuTotal >= THRESHOLD ? DashUi.RED : DashUi.WHITE);
            String mode = perCore ? "分核" : "总占用";
            int hintX = pad + (int) cjk.measureText("CPU") + ui;
            drawCjk(canvas, mode, hintX, titleBaseline(), ui, DashUi.WHITE_60);
            if (data.cpuVoltage > 0 || data.cpuPowerW > 0) {
                String extra = "";
                if (data.cpuVoltage > 0) {
                    extra = String.format(Locale.US, "%.2fV", data.cpuVoltage);
                }
                if (data.cpuPowerW > 0) {
                    extra += (extra.isEmpty() ? "" : " ")
                            + String.format(Locale.US, "%.1fW", data.cpuPowerW);
                }
                drawMono(canvas, extra, hintX + (int) cjk.measureText(mode) + ui,
                        titleBaseline(), ui / 2 + ui / 3, DashUi.WHITE_60);
            }
        }

        int left = pad;
        int chartRight = getWidth() - pad;
        int cores = data == null ? 0 : data.cpuFreqMhz.length;
        int cols = 4;
        int freqRows = Math.min(2, Math.max(1, (Math.max(1, cores) + cols - 1) / cols));
        int freqRowH = ui + ui / 2;
        int chartTop = titleBaseline() + ui / 2 + dp(6) + ui / 2;
        int chartBottom = getHeight() - pad - freqRows * freqRowH;
        if (chartBottom - chartTop < ui * 2) {
            return;
        }

        boolean compactLabels = chartBottom - chartTop < ui * 7;
        for (int i = 0; i <= 4; i++) {
            int y = chartBottom - (chartBottom - chartTop) * i / 4;
            canvas.drawLine(left, y, chartRight, y, grid);
            if (compactLabels && i % 2 != 0) {
                continue;
            }
            int labelBaseline = i == 0 ? y - 2 : y + ui + dp(2);
            drawDseg(canvas, String.format(Locale.US, "%03d", i * 25), left,
                    labelBaseline, ui, DashUi.WHITE_30);
        }

        float thresholdY = chartBottom - (chartBottom - chartTop) * (THRESHOLD / 100f);
        canvas.drawLine(left, thresholdY, chartRight, thresholdY, dashed);
        int thresholdLabelY = Math.max(chartTop + ui, (int) thresholdY - dp(3));
        drawDsegRight(canvas, "080", chartRight - ui / 2, thresholdLabelY, ui, DashUi.RED);
        drawMonoRight(canvas, "%", chartRight, thresholdLabelY, ui / 2, DashUi.RED);

        if (count >= 2) {
            int start = HISTORY - count;
            float stepX = (chartRight - left) / (float) (HISTORY - 1);
            if (!perCore) {
                drawSeries(canvas, totalHistory, start, stepX, left, chartTop, chartBottom,
                        DashUi.WHITE, stroke * 2);
            } else {
                int shown = data == null ? 0 : Math.min(coreHistory.length, data.cpuCores.length);
                int[] alphas = {0xFF, 0xCC, 0xA0, 0x80, 0x66, 0x55, 0x44, 0x33};
                for (int core = 0; core < shown; core++) {
                    int color = (alphas[core] << 24) | 0xFFFFFF;
                    drawSeries(canvas, coreHistory[core], start, stepX, left, chartTop, chartBottom,
                            color, stroke);
                }
            }
        }

        // One cell per core, four per row, so values cannot collide.
        if (data != null && data.cpuFreqMhz.length > 0) {
            int cellW = (chartRight - left) / cols;
            int shownFreq = Math.min(data.cpuFreqMhz.length, cols * freqRows);
            for (int i = 0; i < shownFreq; i++) {
                int row = i / cols;
                int col = i % cols;
                int baseline = getHeight() - pad - (freqRows - 1 - row) * freqRowH;
                int fx = left + col * cellW;
                String index = "C" + i;
                drawMono(canvas, index, fx, baseline, ui / 2 + ui / 3, DashUi.WHITE_30);
                fx += (int) mono.measureText(index) + ui / 2;
                String value = String.valueOf(data.cpuFreqMhz[i]);
                drawDseg(canvas, value, fx, baseline, ui, DashUi.WHITE_60);
                fx += (int) dseg.measureText(value) + 1;
                drawMono(canvas, "M", fx, baseline, ui / 2 + ui / 3, DashUi.WHITE_30);
            }
            if (data.cpuFreqMhz.length > shownFreq) {
                drawMonoRight(canvas, "+" + (data.cpuFreqMhz.length - shownFreq),
                        chartRight, getHeight() - pad, ui / 2 + ui / 3, DashUi.WHITE_30);
            }
        }
    }

    private void drawSeries(Canvas canvas, float[] values, int start, float stepX, int left,
                            int top, int bottom, int color, int width) {
        line.setColor(color);
        line.setStrokeWidth(width);
        float prevX = 0;
        float prevY = 0;
        for (int i = start; i < HISTORY; i++) {
            float x = left + stepX * i;
            float y = bottom - (bottom - top) * (Math.max(0, Math.min(100, values[i])) / 100f);
            if (i > start) {
                canvas.drawLine(prevX, prevY, x, y, line);
            }
            prevX = x;
            prevY = y;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            perCore = !perCore;
            invalidate();
        }
        return true;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
