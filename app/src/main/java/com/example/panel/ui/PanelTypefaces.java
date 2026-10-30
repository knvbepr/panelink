package com.example.panel.ui;

import android.content.Context;
import android.graphics.Typeface;

/** Loads the bundled fonts once and caches them. Fonts live in assets/fonts (offline). */
public final class PanelTypefaces {

    private static Typeface dseg;
    private static Typeface mono;
    private static Typeface pixel;

    private PanelTypefaces() {
    }

    /** Seven-segment numerals, DSEG7-Classic. */
    public static synchronized Typeface dseg(Context context) {
        if (dseg == null) {
            dseg = load(context, "fonts/DSEG7Classic-Regular.ttf");
        }
        return dseg;
    }

    /** Latin labels, JetBrains Mono. */
    public static synchronized Typeface mono(Context context) {
        if (mono == null) {
            mono = load(context, "fonts/JetBrainsMono-Regular.ttf");
        }
        return mono;
    }

    /** Chinese labels, WenQuanYi Bitmap Song (pixel Song face). */
    public static synchronized Typeface pixel(Context context) {
        if (pixel == null) {
            pixel = load(context, "fonts/WenQuanYiBitmapSong16px.ttf");
        }
        return pixel;
    }

    private static Typeface load(Context context, String assetPath) {
        try {
            return Typeface.createFromAsset(context.getApplicationContext().getAssets(), assetPath);
        } catch (RuntimeException e) {
            // Degrade to the platform monospace font rather than crashing the HMI.
            return Typeface.MONOSPACE;
        }
    }
}
