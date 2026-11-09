package com.abradant.iossettings;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Module settings screen.
 *
 * <p>Three sliders that write {@code /data/data/com.abradant.iossettings/
 * shared_prefs/ios_settings.xml}, the file the hooked Settings process reads
 * through {@link Prefs}: the search glass blur, the search glass refraction
 * and the top-bar (NestedHeaderLayout) blur. Values are applied on the next
 * draw / scroll in Settings, no reboot needed.</p>
 */
public final class MainActivity extends Activity {

    private static final String VERSION = "15.2";

    private SharedPreferences prefs;
    private boolean dark;

    private int textPrimary;
    private int textSecondary;
    private int cardColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // No system action bar: the screen has its own big title.
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        prefs = getSharedPreferences(Prefs.FILE, MODE_PRIVATE);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        textPrimary = dark ? 0xFFFFFFFF : 0xFF000000;
        textSecondary = 0xFF8E8E93;
        cardColor = dark ? 0xFF1C1C1E : 0xFFFFFFFF;

        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(dark ? 0xFF000000 : 0xFFF2F2F7);
        // The app is edge-to-edge on targetSdk 35+, so keep the content clear of
        // the status bar and the navigation bar ourselves.
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public android.view.WindowInsets onApplyWindowInsets(View v,
                                                                 android.view.WindowInsets insets) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            }
        });
        scroll.requestApplyInsets();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(40));
        scroll.addView(root);

        TextView title = text("iLyayot", 30, Typeface.DEFAULT_BOLD, textPrimary);
        root.addView(title);
        TextView sub = text("LSPosed 模块 · v" + VERSION, 13, Typeface.DEFAULT, textSecondary);
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);

        LinearLayout glass = card(root, "搜索栏玻璃");
        addSlider(glass, "模糊", Prefs.GLASS_BLUR, 0, 20, 1f, Prefs.DEFAULT_GLASS_BLUR,
                "dp，越大越糊；0 = 不模糊");
        addSlider(glass, "折射", Prefs.REFRACTION_AMOUNT, 0, 30, 1f,
                Prefs.DEFAULT_REFRACTION_AMOUNT, "dp，边缘弯曲的力度；越小越平");
        addSlider(glass, "折射厚度", Prefs.REFRACTION_HEIGHT, 2, 40, 1f,
                Prefs.DEFAULT_REFRACTION_HEIGHT, "dp，弯曲影响到的边缘宽度");

        LinearLayout top = card(root, "顶栏");
        addSlider(top, "模糊", Prefs.TOPBAR_BLUR, 0, 300, 1, Prefs.DEFAULT_TOPBAR_BLUR,
                "列表滚动后，设置标题后面的背景模糊；0 = 关闭");

        TextView reset = text("恢复默认值", 15, Typeface.DEFAULT_BOLD, 0xFF0A84FF);
        reset.setPadding(0, dp(22), 0, dp(10));
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit()
                        .putFloat(Prefs.GLASS_BLUR, Prefs.DEFAULT_GLASS_BLUR)
                        .putFloat(Prefs.REFRACTION_AMOUNT, Prefs.DEFAULT_REFRACTION_AMOUNT)
                        .putFloat(Prefs.CHROMATIC, Prefs.DEFAULT_CHROMATIC)
                        .putFloat(Prefs.INNER_SHADOW, Prefs.DEFAULT_INNER_SHADOW)
                        .putFloat(Prefs.SHADOW_W, Prefs.DEFAULT_SHADOW_W)
                        .putFloat(Prefs.REFRACTION_HEIGHT, Prefs.DEFAULT_REFRACTION_HEIGHT)
                        .putInt(Prefs.TOPBAR_BLUR, Prefs.DEFAULT_TOPBAR_BLUR)
                        .apply();
                recreate();
            }
        });
        root.addView(reset);

        TextView note = text("改动即时生效：切回设置页，重新滚动一下或重开设置。", 12,
                Typeface.DEFAULT, textSecondary);
        root.addView(note);

        setContentView(scroll);
    }

    private LinearLayout card(LinearLayout root, String heading) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setPadding(dp(18), dp(16), dp(18), dp(6));

        TextView headingView = text(heading, 15, Typeface.DEFAULT_BOLD, textPrimary);
        card.addView(headingView);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        root.addView(card, lp);
        return card;
    }

    /**
     * One labelled slider. {@code step} scales stored values: floats are stored
     * as-is (Prefs.getFloat), ints for the MIUI blur radius (Prefs.getInt).
     */
    private void addSlider(LinearLayout card, String label, final String key,
                           final int min, final int max, final float step,
                           float fallback, String hint) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = text(label, 15, Typeface.DEFAULT, textPrimary);
        header.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView value = text("", 14, Typeface.DEFAULT, textSecondary);
        header.addView(value);

        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerLp.topMargin = dp(16);
        card.addView(header, headerLp);

        final boolean isInt = step == 1 && Prefs.TOPBAR_BLUR.equals(key);
        float current = readNumber(key, fallback);

        final SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, Math.round(current / step) - min)));
        value.setText(format(isInt ? bar.getProgress() + min : (bar.getProgress() + min) * step,
                isInt));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = dp(2);
        card.addView(bar, barLp);

        TextView caption = text(hint, 12, Typeface.DEFAULT, textSecondary);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        capLp.bottomMargin = dp(14);
        card.addView(caption, capLp);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float raw = (progress + min) * step;
                value.setText(format(raw, isInt));
                if (!fromUser) {
                    return;
                }
                SharedPreferences.Editor editor = prefs.edit();
                if (isInt) {
                    editor.putInt(key, Math.round(raw));
                } else {
                    editor.putFloat(key, raw);
                }
                editor.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    /**
     * Reads a numeric preference whatever type an older build stored it as.
     * 15.x experiments wrote some of these keys as floats, and a plain
     * {@code getInt} throws ClassCastException on those files.
     */
    private float readNumber(String key, float fallback) {
        try {
            Object value = prefs.getAll().get(key);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private String format(float value, boolean isInt) {        if (isInt) {
            return String.valueOf(Math.round(value));
        }
        return (Math.round(value * 10f) / 10f) + " dp";
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String value, int sizeSp, Typeface typeface, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(typeface);
        view.setTextColor(color);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
