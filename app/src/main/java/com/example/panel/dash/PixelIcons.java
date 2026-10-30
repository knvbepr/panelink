package com.example.panel.dash;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Pixel icons: integer coordinates, no anti-aliasing, no rounded corners. */
public final class PixelIcons {

    private PixelIcons() {
    }

    public static void drawWifi(Canvas c, Paint p, int x, int y, int size, boolean on,
                                int reachState, boolean up, boolean down) {
        p.setStyle(Paint.Style.FILL);
        int bars = 4;
        int bw = Math.max(2, size / 6);
        int gap = Math.max(1, bw / 2);
        for (int i = 0; i < bars; i++) {
            int h = Math.max(2, (size * (i + 1)) / (bars * 2));
            p.setColor(on ? DashUi.WHITE : DashUi.WHITE_30);
            c.drawRect(x + i * (bw + gap), y + size - h, x + i * (bw + gap) + bw, y + size, p);
        }
        int checkX = x + size + gap;
        if (reachState == 1) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            p.setColor(DashUi.GREEN);
            c.drawLine(checkX, y + size / 2, checkX + size / 5, y + size * 3 / 4, p);
            c.drawLine(checkX + size / 5, y + size * 3 / 4, checkX + size / 2, y + size / 4, p);
        } else if (reachState == 2) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1);
            p.setColor(DashUi.RED);
            c.drawLine(checkX, y + size / 3, checkX + size / 3, y + size * 2 / 3, p);
            c.drawLine(checkX + size / 3, y + size / 3, checkX, y + size * 2 / 3, p);
        }
    }

    public static void drawSignal(Canvas c, Paint p, int x, int y, int size, boolean on) {
        p.setStyle(Paint.Style.FILL);
        int bars = 4;
        int bw = Math.max(2, size / 6);
        int gap = Math.max(1, bw / 2);
        for (int i = 0; i < bars; i++) {
            int h = Math.max(2, (size * (i + 1)) / (bars * 2));
            p.setColor(on ? DashUi.WHITE : DashUi.WHITE_30);
            c.drawRect(x + i * (bw + gap), y + size - h, x + i * (bw + gap) + bw, y + size, p);
        }
    }

    public static void drawBattery(Canvas c, Paint p, int x, int y, int w, int h, int pct,
                                   boolean charging) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE);
        c.drawRect(x, y, x + w, y + h, p);
        int tip = Math.max(2, w / 12);
        c.drawRect(x + w, y + h / 3, x + w + tip, y + h * 2 / 3, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(pct <= 15 ? DashUi.RED : pct <= 30 ? DashUi.AMBER : DashUi.WHITE);
        int inner = Math.max(0, w - 4);
        c.drawRect(x + 2, y + 2, x + 2 + inner * Math.max(0, Math.min(100, pct)) / 100, y + h - 2, p);

        if (charging) {
            p.setColor(DashUi.BLACK);
            int cx = x + w / 2;
            Path bolt = new Path();
            bolt.moveTo(cx + 1, y + 2);
            bolt.lineTo(cx - h / 4, y + h / 2);
            bolt.lineTo(cx + h / 8, y + h / 2);
            bolt.lineTo(cx - 1, y + h - 2);
            bolt.lineTo(cx + h / 4, y + h / 3);
            bolt.lineTo(cx - h / 8, y + h / 3);
            bolt.close();
            c.drawPath(bolt, p);
        }
    }

    public static void drawUsb(Canvas c, Paint p, int x, int y, int size) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE);
        int cx = x + size / 2;
        int top = y + size / 5;
        int bottom = y + size * 4 / 5;
        c.drawLine(cx, top, cx, bottom, p);
        c.drawLine(cx, y + size / 2, x + size / 5, y + size / 2, p);
        c.drawLine(cx, y + size * 2 / 3, x + size * 4 / 5, y + size * 2 / 3, p);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(x + size / 8, y + size / 2 - 2, x + size / 8 + 3,
                y + size / 2 + 1, p);
        c.drawRect(x + size * 4 / 5 - 2, y + size * 2 / 3 - 2,
                x + size * 4 / 5 + 1, y + size * 2 / 3 + 1, p);
        c.drawRect(cx - 2, bottom - 2, cx + 2, bottom + 2, p);
    }

    public static void drawEthernet(Canvas c, Paint p, int x, int y, int size) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE);
        int w = size * 2 / 3;
        int h = size * 2 / 3;
        c.drawRect(x, y + size / 6, x + w, y + size / 6 + h, p);
        for (int i = 0; i < 4; i++) {
            int px = x + 2 + i * Math.max(2, w / 5);
            c.drawLine(px, y + size / 6, px, y + size / 6 + h / 4, p);
        }
    }

    public static void drawGear(Canvas c, Paint p, int x, int y, int size) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        p.setColor(DashUi.WHITE);
        int third = size / 3;
        for (int i = 0; i < 3; i++) {
            int ly = y + third / 2 + i * third;
            c.drawLine(x, ly, x + size, ly, p);
            int knobX = x + (i == 1 ? size / 3 : size * 2 / 3);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(knobX - 2, ly - 2, knobX + 2, ly + 2, p);
            p.setStyle(Paint.Style.STROKE);
        }
    }

    public static void drawArrow(Canvas c, Paint p, int x, int y, int size, boolean up) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(DashUi.WHITE_60);
        Path path = new Path();
        if (up) {
            path.moveTo(x, y + size);
            path.lineTo(x + size / 2, y);
            path.lineTo(x + size, y + size);
        } else {
            path.moveTo(x, y);
            path.lineTo(x + size / 2, y + size);
            path.lineTo(x + size, y);
        }
        path.close();
        c.drawPath(path, p);
    }

    public static void drawSortArrow(Canvas c, Paint p, int x, int y, int size, boolean asc) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(DashUi.WHITE);
        Path path = new Path();
        if (asc) {
            path.moveTo(x, y + size);
            path.lineTo(x + size / 2, y);
            path.lineTo(x + size, y + size);
        } else {
            path.moveTo(x, y);
            path.lineTo(x + size / 2, y + size);
            path.lineTo(x + size, y);
        }
        path.close();
        c.drawPath(path, p);
    }
}
