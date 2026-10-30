package com.example.panel.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import com.example.panel.ui.PanelTypefaces;

/** Shared canvas frame, title and text helpers. Everything is integer-aligned. */
public abstract class PanelView extends View {

    protected final int ui;
    protected final int pad;
    protected final int stroke;

    protected final Paint framePaint;
    protected final Paint cjk;
    protected final Paint dseg;
    protected final Paint mono;

    protected PanelView(Context context) {
        this(context, null);
    }

    protected PanelView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        ui = DashUi.unit(context);
        pad = Math.max(4, ui / 2);
        stroke = DashUi.stroke(context);

        framePaint = new Paint();
        framePaint.setAntiAlias(false);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(stroke);
        framePaint.setColor(DashUi.WHITE_30);

        cjk = DashUi.paint(context, PanelTypefaces.pixel(context), ui, DashUi.WHITE,
                Paint.Align.LEFT, false);
        dseg = DashUi.paint(context, PanelTypefaces.dseg(context), ui * 2f, DashUi.WHITE,
                Paint.Align.LEFT, true);
        mono = DashUi.paint(context, PanelTypefaces.mono(context), ui, DashUi.WHITE,
                Paint.Align.LEFT, true);

        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    protected void frame(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawLine(0, 0, w - 1, 0, framePaint);
        canvas.drawLine(0, h - 1, w - 1, h - 1, framePaint);
        canvas.drawLine(0, 0, 0, h - 1, framePaint);
        canvas.drawLine(w - 1, 0, w - 1, h - 1, framePaint);
    }

    protected int titleBaseline() {
        return pad + ui;
    }

    protected void title(Canvas canvas, String text) {
        drawCjk(canvas, text, pad, titleBaseline(), ui, DashUi.WHITE);
        framePaint.setColor(DashUi.WHITE_20);
        canvas.drawLine(pad, titleBaseline() + ui / 2, pad + (int) cjk.measureText(text) + pad,
                titleBaseline() + ui / 2, framePaint);
        framePaint.setColor(DashUi.WHITE_30);
    }

    protected void drawCjk(Canvas canvas, String text, int x, int baseline, int size, int color) {
        cjk.setTextSize(size);
        cjk.setColor(color);
        canvas.drawText(text, x, baseline, cjk);
    }

    protected void drawMono(Canvas canvas, String text, int x, int baseline, int size, int color) {
        mono.setTextSize(size);
        mono.setColor(color);
        canvas.drawText(text, x, baseline, mono);
    }

    protected void drawDseg(Canvas canvas, String text, int x, int baseline, int size, int color) {
        dseg.setTextSize(size);
        dseg.setColor(color);
        canvas.drawText(text, x, baseline, dseg);
    }

    protected void drawDsegRight(Canvas canvas, String text, int right, int baseline, int size,
                                 int color) {
        dseg.setTextSize(size);
        dseg.setColor(color);
        dseg.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, right, baseline, dseg);
        dseg.setTextAlign(Paint.Align.LEFT);
    }

    protected void drawMonoRight(Canvas canvas, String text, int right, int baseline, int size,
                                 int color) {
        mono.setTextSize(size);
        mono.setColor(color);
        mono.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, right, baseline, mono);
        mono.setTextAlign(Paint.Align.LEFT);
    }

    protected int textWidth(Paint paint, String text) {
        return (int) paint.measureText(text);
    }

    protected int statusColor(float value, float warn, float fault) {
        if (value >= fault) {
            return DashUi.RED;
        }
        if (value >= warn) {
            return DashUi.AMBER;
        }
        return DashUi.WHITE;
    }
}
