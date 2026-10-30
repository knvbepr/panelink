package com.example.panel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.example.panel.R;

/**
 * RAM usage breakdown. Fixed-height rows, 1dp white track at 20%, solid white fill,
 * amber above 70%, red above 90%. Values jump instantly; there is no progress animation.
 * The status word next to each value keeps the meaning readable without colour.
 */
public class RamBarChartView extends View {

    private static final String[] LABELS = {"KERNEL", "APPS", "CACHE", "MEDIA"};
    private static final float[] VALUES = {24f, 58f, 74f, 91f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int white;
    private final int white60;
    private final int white40;
    private final int trackColor;
    private final int green;
    private final int amber;
    private final int red;

    public RamBarChartView(Context context) {
        this(context, null);
    }

    public RamBarChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RamBarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        white = 0xFFFFFFFF;
        white60 = 0x99FFFFFF;
        white40 = 0x99FFFFFF;
        trackColor = 0x33FFFFFF;
        green = ContextCompat.getColor(context, R.color.status_green);
        amber = ContextCompat.getColor(context, R.color.status_amber);
        red = ContextCompat.getColor(context, R.color.status_red);
        setContentDescription(PanelI18n.isChinese()
                ? "内存占用：内核 24%，应用 58%，缓存 74% 注意，媒体 91% 严重。"
                : "RAM usage breakdown: kernel 24 percent, apps 58 percent, "
                + "cache 74 percent warning, media 91 percent critical.");
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float labelWidth = dp(60);
        final float valueWidth = dp(74);
        final float left = labelWidth;
        final float right = getWidth() - valueWidth;
        if (right <= left + dp(20)) {
            return;
        }
        final float rowHeight = getHeight() / (float) LABELS.length;
        final float barHeight = dp(10);

        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < LABELS.length; i++) {
            final float centerY = rowHeight * i + rowHeight / 2f;
            final float value = VALUES[i];

            paint.setTypeface(PanelI18n.uiTypeface(getContext()));
            paint.setTextSize(sp(9));
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(white60);
            canvas.drawText(PanelI18n.t(getContext(), LABELS[i]), dp(2), centerY - sp(2), paint);

            // Status word sits under the label: the bar itself never carries meaning by colour alone.
            paint.setTextSize(sp(7.5f));
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setColor(value >= 90 ? red : value >= 70 ? amber : white40);
            canvas.drawText(PanelI18n.t(getContext(), statusWord(value)), dp(2), centerY + sp(9), paint);

            // Track.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(trackColor);
            canvas.drawRect(left, centerY - barHeight / 2f, right, centerY + barHeight / 2f, paint);

            // Fill: fixed height, direct value mapping, no interpolation.
            final float fillLeft = left + dp(2.5f);
            final float fillRight = fillLeft + (right - left - dp(5)) * (value / 100f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(statusColor(value));
            canvas.drawRect(fillLeft, centerY - barHeight / 2f + dp(2.5f),
                    fillRight, centerY + barHeight / 2f - dp(2.5f), paint);

            // Percentage in DSEG (fixed 3 digits) + mono unit.
            String number = String.format(java.util.Locale.US, "%03.0f", value);
            paint.setTypeface(PanelTypefaces.dseg(getContext()));
            paint.setTextSize(sp(13));
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(white);
            canvas.drawText(number, getWidth() - dp(13), centerY + sp(5), paint);
            paint.setTypeface(PanelTypefaces.mono(getContext()));
            paint.setTextSize(sp(9));
            paint.setColor(white60);
            canvas.drawText("%", getWidth() - dp(1), centerY + sp(5), paint);
        }
    }

    private int statusColor(float value) {
        if (value >= 90f) {
            return red;
        }
        if (value >= 70f) {
            return amber;
        }
        return white;
    }

    private String statusWord(float value) {
        if (value >= 90f) {
            return "CRIT";
        }
        if (value >= 70f) {
            return "WARN";
        }
        return "OK";
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
