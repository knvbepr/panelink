package com.example.panel.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/** Labels, prose and status words. JetBrains Mono in EN, pixel Song in the ZH edition. */
public class MonoTextView extends AppCompatTextView {

    public MonoTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public MonoTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MonoTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setTypeface(PanelI18n.uiTypeface(getContext()));
        setIncludeFontPadding(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (PanelI18n.isChinese()) {
            CharSequence current = getText();
            if (current != null) {
                String translated = PanelI18n.t(getContext(), current.toString());
                if (!translated.contentEquals(current)) {
                    setText(translated);
                }
            }
        }
    }
}
