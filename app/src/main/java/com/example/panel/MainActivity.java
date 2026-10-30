package com.example.panel;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.panel.ui.PanelI18n;
import com.example.panel.ui.PanelTypefaces;
import com.example.panel.ui.SquareSwitch;

/**
 * Design specimen for the PANEL HMI. Single surface, single theme, no navigation.
 * The fault-class indicators (FAULT badge + WARNING banner) blink continuously with
 * an instant step between full and 15% alpha; no easing, no other motion.
 *
 * The ZH edition is a design mock-up: the copy is translated at attach time and the
 * Chinese labels switch to the bundled WenQuanYi Bitmap Song pixel face.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "panel_specimen";
    private static final String KEY_LANG = "lang";
    private static final long BLINK_STEP_MS = 250L;

    private final Handler blinkHandler = new Handler(Looper.getMainLooper());
    private boolean blinkDim;

    private SharedPreferences prefs;
    private View faultBadge;
    private View warningBanner;

    private final Runnable blinkRunnable = new Runnable() {
        @Override
        public void run() {
            blinkDim = !blinkDim;
            float alpha = blinkDim ? 0.15f : 1.0f;
            if (faultBadge != null) {
                faultBadge.setAlpha(alpha);
            }
            if (warningBanner != null) {
                warningBanner.setAlpha(alpha);
            }
            blinkHandler.postDelayed(this, BLINK_STEP_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        PanelI18n.setChinese("zh".equals(prefs.getString(KEY_LANG, "en")));

        setContentView(R.layout.activity_main);

        bindLanguageToggle();
        bindSwitch(R.id.switch_ext_power, R.id.switch_ext_power_state, "EXTERNAL POWER", true);
        bindSwitch(R.id.switch_arm, R.id.switch_arm_state, "ARM SYSTEMS", false);

        faultBadge = findViewById(R.id.badge_fault);
        warningBanner = findViewById(R.id.banner_warning);
    }

    @Override
    protected void onResume() {
        super.onResume();
        blinkHandler.removeCallbacks(blinkRunnable);
        blinkDim = false;
        blinkHandler.postDelayed(blinkRunnable, BLINK_STEP_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        blinkHandler.removeCallbacks(blinkRunnable);
        if (faultBadge != null) {
            faultBadge.setAlpha(1.0f);
        }
        if (warningBanner != null) {
            warningBanner.setAlpha(1.0f);
        }
    }

    private void bindLanguageToggle() {
        final TextView toggle = findViewById(R.id.lang_toggle);
        toggle.setTypeface(PanelTypefaces.pixel(this));
        toggle.setText(PanelI18n.isChinese() ? "EN" : "中文");
        toggle.setOnClickListener(v -> {
            boolean toChinese = !PanelI18n.isChinese();
            prefs.edit().putString(KEY_LANG, toChinese ? "zh" : "en").apply();
            recreate();
        });
    }

    private void bindSwitch(int switchId, int stateId, String label, boolean initial) {
        final SquareSwitch toggle = findViewById(switchId);
        final TextView state = findViewById(stateId);
        toggle.setLabel(PanelI18n.t(this, label));
        toggle.setChecked(initial);
        state.setText(PanelI18n.t(this, initial ? "ON" : "OFF"));
        toggle.setOnCheckedChangeListener((view, checked) ->
                state.setText(PanelI18n.t(this, checked ? "ON" : "OFF")));
    }
}
