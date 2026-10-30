package com.example.panel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.panel.R;

/**
 * CPU load trend. White 1.5dp polyline, 15% white grid, 35% baseline and a dashed
 * status-red threshold. Static data, no animation, no points, no fill.
 */
public class CpuLineChartView extends View {

    private static final float THRESHOLD = 80f;
    private static final float[] SAMPLES = {
            42, 48, 44, 61, 58, 67, 72, 64, 70, 76, 72, 68, 74, 72
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final DashPathEffect dash;
    private final float density;

    public CpuLineChartView(Context context) {
        this(context, null);
    }

    public CpuLineChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CpuLineChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        dash = new DashPathEffect(new float[]{dp(5), dp(4)}, 0f);
        setContentDescription(PanelI18n.isChinese()
                ? "CPU 占用趋势，当前 072%，阈值 080%。"
                : "CPU load history. Current 72 percent, threshold 80 percent.");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float left = dp(34);
        final float top = dp(10);
        final float right = getWidth() - dp(6);
        final float bottom = getHeight() - dp(16);
        if (right <= left || bottom <= top) {
            return;
        }

        final int gridColor = 0x26FFFFFF;
        final int baselineColor = 0x59FFFFFF;

        // Grid + baseline.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setPathEffect(null);
        paint.setColor(gridColor);
        for (int i = 0; i <= 4; i++) {
            float y = bottom - (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, paint);
        }
        for (int i = 0; i < SAMPLES.length; i += 4) {
            float x = left + (right - left) * i / (SAMPLES.length - 1f);
            canvas.drawLine(x, top, x, bottom, paint);
        }
        paint.setColor(baselineColor);
        canvas.drawLine(left, bottom, right, bottom, paint);

        // Y labels, fixed 3-digit DSEG values.
        paint.setTypeface(PanelTypefaces.dseg(getContext()));
        paint.setTextSize(sp(8.5f));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(0x99FFFFFF);
        String[] labels = {"000", "025", "050", "075", "100"};
        for (int i = 0; i <= 4; i++) {
            float y = bottom - (bottom - top) * i / 4f + sp(3);
            canvas.drawText(labels[i], left - dp(5), y, paint);
        }

        // Threshold: status red, dashed, labelled.
        float thresholdY = bottom - (bottom - top) * (THRESHOLD / 100f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(ContextCompat.getColor(getContext(), R.color.status_red));
        paint.setPathEffect(dash);
        canvas.drawLine(left, thresholdY, right, thresholdY, paint);
        paint.setPathEffect(null);
        paint.setTypeface(PanelI18n.uiTypeface(getContext()));
        paint.setTextSize(sp(8));
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("THR 080", right, thresholdY - dp(4), paint);

        // Series.
        line.reset();
        final float stepX = (right - left) / (SAMPLES.length - 1f);
        for (int i = 0; i < SAMPLES.length; i++) {
            float x = left + stepX * i;
            float y = bottom - (bottom - top) * (SAMPLES[i] / 100f);
            if (i == 0) {
                line.moveTo(x, y);
            } else {
                line.lineTo(x, y);
            }
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setStrokeJoin(Paint.Join.MITER);
        paint.setColor(0xFFFFFFFF);
        paint.setPathEffect(null);
        canvas.drawPath(line, paint);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
