package com.example.panel.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;

import java.util.List;
import java.util.Locale;

/** Multi-sensor temperature tiles. Fault tiles flash; caution tiles are solid amber. */
public final class TempPanelView extends PanelView {

    private PanelData data;
    private float warnC = 60f;
    private float faultC = 75f;

    private final Paint fill = new Paint();
    private final RectF rect = new RectF();

    public TempPanelView(Context context) {
        this(context, null);
    }

    public TempPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setAntiAlias(false);
    }

    public void setData(PanelData data) {
        this.data = data;
        invalidate();
    }

    public void setThresholds(float warn, float fault) {
        warnC = warn;
        faultC = fault;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        frame(canvas);
        title(canvas, "温度");

        int contentTop = titleBaseline() + ui / 2 + 4;
        int left = pad;
        int right = getWidth() - pad;
        int bottom = getHeight() - pad;
        int gap = 2;

        List<PanelData.Temp> temps = data == null ? null : data.temps;
        if (temps == null || temps.isEmpty()) {
            drawCjk(canvas, "无效", left, contentTop + ui, ui, DashUi.WHITE_60);
            return;
        }

        int count = Math.min(4, temps.size());
        if (count == 0) {
            return;
        }
        int cols = count == 1 ? 1 : 2;
        int rows = (count + cols - 1) / cols;
        int tileW = (right - left - gap * (cols - 1)) / cols;
        int tileH = (bottom - contentTop - gap * (rows - 1)) / rows;
        boolean hasFault = false;
        boolean phase = (SystemClock.uptimeMillis() / 500) % 2 == 0;
        for (int i = 0; i < count; i++) {
            int col = i % cols;
            int row = i / cols;
            int x = left + col * (tileW + gap);
            int y = contentTop + row * (tileH + gap);
            rect.set(x, y, x + tileW, y + tileH);
            PanelData.Temp temp = temps.get(i);
            boolean fault = temp.celsius >= faultC;
            boolean warn = !fault && temp.celsius >= warnC;
            if (fault) {
                hasFault = true;
                drawFaultTile(canvas, temp, phase);
            } else if (warn) {
                fill.setColor(DashUi.AMBER);
                canvas.drawRect(rect, fill);
                drawTemp(canvas, temp, DashUi.BLACK);
            } else {
                framePaint.setColor(DashUi.WHITE_20);
                canvas.drawRect(rect, framePaint);
                drawTemp(canvas, temp, DashUi.WHITE);
            }
        }
        if (hasFault) {
            postInvalidateDelayed(250);
        }
    }

    private void drawFaultTile(Canvas canvas, PanelData.Temp temp, boolean solid) {
        fill.setColor(solid ? DashUi.RED : 0x33FF1744);
        canvas.drawRect(rect, fill);
        drawTemp(canvas, temp, solid ? DashUi.BLACK : DashUi.RED);
    }

    private void drawTemp(Canvas canvas, PanelData.Temp temp, int color) {
        int x = (int) rect.left + ui / 2;
        int valueSize = ui + ui / 2;
        int baseline = (int) rect.centerY() + valueSize / 2;
        drawCjk(canvas, temp.name, x, baseline, ui, color);
        String value = String.valueOf((int) Math.round(temp.celsius));
        int unitWidth = (int) mono.measureText("C") + 2;
        int valueRight = (int) rect.right - ui / 2 - unitWidth;
        drawDsegRight(canvas, value, valueRight, baseline, valueSize, color);
        drawMono(canvas, "C", valueRight + 2, baseline, ui, color);
    }

}
