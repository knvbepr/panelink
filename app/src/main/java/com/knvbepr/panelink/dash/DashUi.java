package com.knvbepr.panelink.dash;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;

import com.knvbepr.panelink.ui.PanelTypefaces;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shared colours, pixel sizes and formatting for the canvas dashboard. */
public final class DashUi {

    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int WHITE_80 = 0xCCFFFFFF;
    public static final int WHITE_50 = 0x80FFFFFF;
    public static final int WHITE_60 = 0x99FFFFFF;
    public static final int WHITE_40 = 0x66FFFFFF;
    public static final int WHITE_30 = 0x4DFFFFFF;
    public static final int WHITE_20 = 0x33FFFFFF;
    public static final int WHITE_15 = 0x26FFFFFF;
    public static final int WHITE_08 = 0x14FFFFFF;

    public static final int GREEN = 0xFF00C853;
    public static final int AMBER = 0xFFFFB000;
    public static final int RED = 0xFFFF1744;

    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private DashUi() {
    }

    /**
     * Chinese pixel face is bundled at a 16px design size. Render it at integer
     * multiples so every glyph lands on the same physical pixel grid.
     */
    public static int unit(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int factor = Math.max(1, (int) Math.ceil(density / 1.75f));
        return 16 * factor;
    }

    public static int stroke(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(density / 1.5f));
    }

    public static Paint paint(Context context, Typeface typeface, float px, int color,
                              Paint.Align align, boolean antiAlias) {
        Paint paint = new Paint();
        paint.setAntiAlias(antiAlias);
        paint.setSubpixelText(false);
        paint.setTypeface(typeface);
        paint.setTextSize(px);
        paint.setColor(color);
        paint.setTextAlign(align);
        return paint;
    }

    public static Paint cjkPaint(Context context) {
        return paint(context, PanelTypefaces.pixel(context), unit(context),
                WHITE, Paint.Align.LEFT, false);
    }

    public static Paint dsegPaint(Context context) {
        return paint(context, PanelTypefaces.dseg(context), unit(context) * 2f,
                WHITE, Paint.Align.LEFT, true);
    }

    public static Paint monoPaint(Context context) {
        return paint(context, PanelTypefaces.mono(context), unit(context),
                WHITE, Paint.Align.LEFT, true);
    }

    public static String time(long millis) {
        return TIME.format(new Date(millis));
    }

    public static String percent(float value) {
        return String.format(Locale.US, "%03d", Math.round(Math.max(0, Math.min(100, value))));
    }

    /** Compact byte count with one decimal, used for RAM/disk totals. */
    public static String bytes(long kb) {
        if (kb >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1fG", kb / (1024.0 * 1024.0));
        }
        if (kb >= 1024L) {
            return String.format(Locale.US, "%.0fM", kb / 1024.0);
        }
        return kb + "K";
    }

    /** Compact transfer rate: B/s, K/s, M/s. */
    public static String rate(long bps) {
        if (bps >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1fM", bps / (1024.0 * 1024.0));
        }
        if (bps >= 1024L) {
            return String.format(Locale.US, "%.0fK", bps / 1024.0);
        }
        return bps + "B";
    }

    public static int dp(Context context, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics()));
    }
}
