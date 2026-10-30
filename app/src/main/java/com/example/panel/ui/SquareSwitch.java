package com.example.panel.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Square industrial toggle. Fixed geometry, instant jump on state change, no animation,
 * no rounded corners. 48dp touch target; the track is 40 x 20dp.
 */
public class SquareSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(SquareSwitch view, boolean isChecked);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final RectF knob = new RectF();
    private final float density;

    private boolean checked;
    private String label = "Switch";
    private OnCheckedChangeListener listener;

    public SquareSwitch(Context context) {
        this(context, null);
    }

    public SquareSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SquareSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
        refreshAccessibility();
        setOnClickListener(v -> {
            checked = !checked;
            refreshAccessibility();
            invalidate();
            if (listener != null) {
                listener.onCheckedChanged(this, checked);
            }
        });
    }

    public void setChecked(boolean value) {
        if (checked == value) {
            return;
        }
        checked = value;
        refreshAccessibility();
        invalidate();
    }

    public boolean isChecked() {
        return checked;
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener newListener) {
        listener = newListener;
    }

    /** Spoken label, e.g. "EXTERNAL POWER"; the on/off suffix is appended automatically. */
    public void setLabel(String newLabel) {
        label = newLabel;
        refreshAccessibility();
    }

    private void refreshAccessibility() {
        setContentDescription(label + ", " + PanelI18n.t(getContext(), checked ? "ON" : "OFF"));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(48 * density);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float cx = getWidth() / 2f;
        final float cy = getHeight() / 2f;
        final float trackW = dp(40);
        final float trackH = dp(20);
        final float stroke = dp(1);

        // Fixed pressed colour: white 8%. No ripple, no animation.
        if (isPressed()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x14FFFFFF);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }

        track.set(cx - trackW / 2f, cy - trackH / 2f, cx + trackW / 2f, cy + trackH / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(checked ? 0xFFFFFFFF : 0x4DFFFFFF);
        canvas.drawRect(track, paint);

        final float knobSize = dp(14);
        final float inset = dp(3);
        final float left = checked ? track.right - inset - knobSize : track.left + inset;
        knob.set(left, cy - knobSize / 2f, left + knobSize, cy + knobSize / 2f);
        if (checked) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            canvas.drawRect(knob, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(0x80FFFFFF);
            canvas.drawRect(knob, paint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
        info.setText(getContentDescription());
    }

    private float dp(float value) {
        return value * density;
    }
}
