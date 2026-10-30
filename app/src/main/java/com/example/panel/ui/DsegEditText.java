package com.example.panel.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

/** Numeric input that renders its value in the seven-segment face. */
public class DsegEditText extends AppCompatEditText {

    public DsegEditText(@NonNull Context context) {
        super(context);
        init();
    }

    public DsegEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DsegEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setTypeface(PanelTypefaces.dseg(getContext()));
        setIncludeFontPadding(false);
    }
}
