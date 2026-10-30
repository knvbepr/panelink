package com.knvbepr.panelink.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/** Seven-segment readout. Every pure numeral in this specimen goes through this class. */
public class DsegTextView extends AppCompatTextView {

    public DsegTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public DsegTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DsegTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setTypeface(PanelTypefaces.dseg(getContext()));
        setIncludeFontPadding(false);
    }
}
