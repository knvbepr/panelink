package com.example.panel.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Scrolling process list. Tap a column header to change the sort key/direction. */
public final class ProcessPanelView extends PanelView {

    public static final int SORT_CPU = 0;
    public static final int SORT_MEM = 1;
    public static final int SORT_PID = 2;
    public static final int SORT_NAME = 3;

    private final List<PanelData.Proc> procs = new ArrayList<>();
    private final Rect headerRect = new Rect();
    private final Rect[] columnRects = new Rect[4];

    private int sortKey = SORT_CPU;
    private boolean ascending = false;
    private boolean root;
    private PanelData data;
    private float offset;
    private boolean dragging;
    private float lastY;

    private final Paint fill = new Paint();

    public ProcessPanelView(Context context) {
        this(context, null);
    }

    public ProcessPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setAntiAlias(false);
        for (int i = 0; i < columnRects.length; i++) {
            columnRects[i] = new Rect();
        }
    }

    public void setData(PanelData data) {
        this.data = data;
        procs.clear();
        root = data != null && data.root;
        if (data != null && data.procs != null) {
            procs.addAll(data.procs);
        }
        sort();
        clampScroll();
        invalidate();
    }

    public int desiredHeight(int rows) {
        int rowH = ui + ui / 2;
        int titleH = ui + ui / 2 + 4;
        int headerH = ui + ui / 2;
        return pad + titleH + headerH + rows * rowH + pad;
    }

    private void sort() {
        Collections.sort(procs, new Comparator<PanelData.Proc>() {
            @Override
            public int compare(PanelData.Proc a, PanelData.Proc b) {
                int result;
                switch (sortKey) {
                    case SORT_MEM:
                        result = Long.compare(a.rssKb, b.rssKb);
                        break;
                    case SORT_PID:
                        result = Integer.compare(a.pid, b.pid);
                        break;
                    case SORT_NAME:
                        result = a.name.compareToIgnoreCase(b.name);
                        break;
                    case SORT_CPU:
                    default:
                        result = Float.compare(a.cpu, b.cpu);
                        break;
                }
                return ascending ? result : -result;
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        frame(canvas);
        if (data != null && data.remote) {
            title(canvas, "风扇");
            drawRemoteFans(canvas, data);
            return;
        }
        title(canvas, "进程");

        int left = pad;
        int right = getWidth() - pad - ui;
        int titleLine = titleBaseline() + ui / 2;
        int headerBaseline = titleLine + ui + 2;
        int rowsTop = headerBaseline + ui / 2;
        int rowH = ui + ui / 2;
        int pidRight = left + (right - left) * 14 / 100;
        int nameRight = left + (right - left) * 60 / 100;
        int cpuRight = left + (right - left) * 82 / 100;
        columnRects[0].set(left, titleLine, pidRight, rowsTop);
        columnRects[1].set(pidRight, titleLine, nameRight, rowsTop);
        columnRects[2].set(nameRight, titleLine, cpuRight, rowsTop);
        columnRects[3].set(cpuRight, titleLine, right, rowsTop);

        drawMono(canvas, "PID", left, headerBaseline, ui / 2 + ui / 3, DashUi.WHITE_30);
        drawCjk(canvas, "进程", pidRight, headerBaseline, ui, DashUi.WHITE_30);
        drawMono(canvas, "CPU", nameRight, headerBaseline, ui / 2 + ui / 3, DashUi.WHITE_30);
        drawMonoRight(canvas, "MEM", right, headerBaseline, ui / 2 + ui / 3, DashUi.WHITE_30);

        int arrowSize = Math.max(4, ui / 3);
        int arrowX = sortKey == SORT_PID ? left + ui * 2 : sortKey == SORT_NAME ? pidRight + ui * 3
                : sortKey == SORT_CPU ? nameRight + ui * 3 : cpuRight + ui * 3;
        PixelIcons.drawSortArrow(canvas, fill, arrowX, headerBaseline - arrowSize, arrowSize, ascending);

        framePaint.setColor(DashUi.WHITE_20);
        canvas.drawLine(left, rowsTop, right, rowsTop, framePaint);

        int contentTop = rowsTop + 2;
        int visible = Math.max(0, (getHeight() - pad - contentTop) / rowH);
        int max = Math.max(0, procs.size() - visible);
        if (procs.isEmpty()) {
            drawCjk(canvas, root ? "读取中" : "需要 ROOT", left, contentTop + ui, ui,
                    root ? DashUi.WHITE_30 : DashUi.AMBER);
            return;
        }

        canvas.save();
        canvas.clipRect(0, contentTop, getWidth(), getHeight() - 1);
        int first = Math.max(0, (int) (offset / rowH));
        for (int i = first; i < procs.size(); i++) {
            int y = contentTop + (int) (i * rowH - offset);
            if (y > getHeight() - 1) {
                break;
            }
            if (y + rowH < contentTop) {
                continue;
            }
            PanelData.Proc proc = procs.get(i);
            int baseline = y + rowH - 2;
            drawDseg(canvas, String.valueOf(proc.pid), left, baseline, ui, DashUi.WHITE_60);
            drawMono(canvas, fit(proc.name, nameRight - pidRight - ui / 2), pidRight, baseline, ui,
                    DashUi.WHITE);
            String cpu = String.format(Locale.US, "%.1f", proc.cpu);
            drawDseg(canvas, cpu, nameRight, baseline, ui,
                    proc.cpu >= 50 ? DashUi.AMBER : DashUi.WHITE_60);
            drawDsegRight(canvas, DashUi.bytes(proc.rssKb), right, baseline, ui, DashUi.WHITE_60);
            if (i < procs.size() - 1) {
                framePaint.setColor(DashUi.WHITE_15);
                canvas.drawLine(left, y + rowH, right, y + rowH, framePaint);
            }
        }
        canvas.restore();

        if (max > 0) {
            int trackTop = contentTop;
            int trackBottom = getHeight() - pad;
            int trackX = getWidth() - 3;
            framePaint.setColor(DashUi.WHITE_15);
            canvas.drawLine(trackX, trackTop, trackX, trackBottom, framePaint);
            float frac = visible / (float) procs.size();
            int thumb = Math.max(8, (int) ((trackBottom - trackTop) * frac));
            int travel = trackBottom - trackTop - thumb;
            int pos = trackTop + (int) (travel * (offset / (rowH * max)));
            fill.setColor(DashUi.WHITE_60);
            canvas.drawRect(trackX - 1, pos, trackX + 1, pos + thumb, fill);
        }
    }

    private void drawRemoteFans(Canvas canvas, PanelData data) {
        int left = pad;
        int right = getWidth() - pad;
        int baseline = titleBaseline() + ui * 2;
        if (!data.remoteConnected) {
            drawCjk(canvas, "未连接", left, baseline, ui, DashUi.AMBER);
            return;
        }
        int x = left;
        int count = 0;
        if (data.fans.isEmpty()) {
            drawCjk(canvas, "无风扇读数", left, baseline, ui, DashUi.WHITE_30);
        }
        for (PanelData.RemoteFan fan : data.fans) {
            if (count >= 3) {
                break;
            }
            drawMono(canvas, fan.name, left, baseline + count * (ui + ui / 2), ui, DashUi.WHITE_60);
            String value = fan.value >= 0 ? String.valueOf(fan.value) : "N/A";
            drawDseg(canvas, value, left + ui * 8, baseline + count * (ui + ui / 2), ui, DashUi.WHITE);
            drawMono(canvas, fan.percent ? "%" : "RPM", left + ui * 8 + (int) dseg.measureText(value) + 2,
                    baseline + count * (ui + ui / 2), ui / 2, DashUi.WHITE_30);
            count++;
        }
        int y = baseline + Math.max(1, count) * (ui + ui / 2) + ui / 2;
        if (data.pcBatteryPresent) {
            drawCjk(canvas, "电池", left, y, ui, DashUi.WHITE_60);
            drawDseg(canvas, String.format(Locale.US, "%03d", data.pcBatteryPct), left + ui * 3,
                    y, ui, DashUi.WHITE);
            drawMono(canvas, "%", left + ui * 3 + (int) dseg.measureText(String.valueOf(data.pcBatteryPct)) + 2,
                    y, ui / 2, DashUi.WHITE_30);
            drawCjk(canvas, data.pcCharging ? "充电" : "放电", left + ui * 7, y, ui,
                    data.pcCharging ? DashUi.GREEN : DashUi.WHITE_60);
            y += ui + ui / 2;
        }
        drawMono(canvas, "REMOTE " + data.remoteName, left, y, ui / 2 + ui / 3, DashUi.WHITE_30);
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

    private void clampScroll() {
        int rowH = ui + ui / 2;
        int contentTop = titleBaseline() + ui / 2 + ui + 2 + ui / 2 + 2;
        int visible = Math.max(0, (getHeight() - pad - contentTop) / rowH);
        int max = Math.max(0, procs.size() - visible) * rowH;
        if (offset > max) {
            offset = max;
        }
        if (offset < 0) {
            offset = 0;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                lastY = event.getY();
                headerRect.set(0, 0, getWidth(), titleBaseline() + ui / 2 + ui);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dy = event.getY() - lastY;
                if (Math.abs(dy) > 2) {
                    dragging = true;
                    offset -= dy;
                    lastY = event.getY();
                    clampScroll();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && headerRect.contains((int) event.getX(), (int) event.getY())) {
                    for (int i = 0; i < columnRects.length; i++) {
                        if (columnRects[i].contains((int) event.getX(), (int) event.getY())) {
                            int key = i == 0 ? SORT_PID
                                    : i == 1 ? SORT_NAME
                                    : i == 2 ? SORT_CPU : SORT_MEM;
                            if (sortKey == key) {
                                ascending = !ascending;
                            } else {
                                sortKey = key;
                                ascending = false;
                            }
                            sort();
                            offset = 0;
                            invalidate();
                            break;
                        }
                    }
                }
                dragging = false;
                return true;
            default:
                return true;
        }
    }
}
