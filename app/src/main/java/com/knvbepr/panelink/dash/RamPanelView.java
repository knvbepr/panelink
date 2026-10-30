package com.knvbepr.panelink.dash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

/** RAM usage bar with used/total readout. */
public final class RamPanelView extends PanelView {

    private PanelData data;
    private final Paint fill = new Paint();

    public RamPanelView(Context context) {
        this(context, null);
    }

    public RamPanelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        fill.setAntiAlias(false);
    }

    public void setData(PanelData data) {
        this.data = data;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        frame(canvas);
        title(canvas, "内存");

        if (data == null || data.memTotalKb <= 0) {
            drawCjk(canvas, "读取中", pad, titleBaseline() + ui + pad, ui, DashUi.WHITE_30);
            return;
        }

        float pct = 100f * data.memUsedKb / data.memTotalKb;
        int color = pct >= 95 ? DashUi.RED : pct >= 85 ? DashUi.AMBER : DashUi.WHITE;
        if (pct >= 95) {
            drawPercentBadge(canvas, DashUi.percent(pct), getWidth() - pad,
                    titleBaseline() + ui / 2 + 2, ui * 2, ui, overloadSolid());
            scheduleOverloadBlink();
        } else {
            drawMonoRight(canvas, "%", getWidth() - pad, titleBaseline() + ui / 2 + 2, ui,
                    DashUi.WHITE_60);
            drawDsegRight(canvas, DashUi.percent(pct), getWidth() - pad - ui,
                    titleBaseline() + ui / 2 + 2, ui * 2, color);
        }

        int barTop = titleBaseline() + ui + ui / 2;
        int barBottom = barTop + Math.max(6, ui / 2);
        int left = pad;
        int right = getWidth() - pad;
        framePaint.setColor(DashUi.WHITE_20);
        canvas.drawRect(left, barTop, right, barBottom, framePaint);
        fill.setColor(color);
        canvas.drawRect(left + 1, barTop + 1, left + 1 + (right - left - 2) * pct / 100f,
                barBottom - 1, fill);

        int baseline = Math.min(getHeight() - pad, barBottom + ui + ui / 2);
        drawCjk(canvas, "已用", left, baseline, ui, DashUi.WHITE_60);
        drawDseg(canvas, DashUi.bytes(data.memUsedKb), left + (int) cjk.measureText("已用") + ui / 2,
                baseline, ui, DashUi.WHITE);
        String total = " / " + DashUi.bytes(data.memTotalKb);
        drawMono(canvas, total, left + (int) cjk.measureText("已用") + ui / 2
                + (int) dseg.measureText(DashUi.bytes(data.memUsedKb)), baseline, ui, DashUi.WHITE_60);
    }
}
