package com.Remo.ilayoutsettings;

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
import android.widget.Switch;
import android.widget.TextView;

/**
 * Module settings screen.
 *
 * <p>Sliders that write {@code /data/data/com.Remo.ilayoutsettings/
 * shared_prefs/ios_settings.xml}, the file the hooked Settings process reads
 * through {@link Prefs}: the search glass blur / refraction, and the top-bar
 * (NestedHeaderLayout) blur + opacity. Values are applied on the next draw /
 * scroll in Settings, no reboot needed.</p>
 */
public final class MainActivity extends Activity {

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

        TextView title = text(appLabel(), 30, Typeface.DEFAULT_BOLD, textPrimary);
        root.addView(title);
        TextView sub = text("LSPosed 模块 · v" + versionName(), 13, Typeface.DEFAULT, textSecondary);
        LinearLayout.LayoutParams subLp = wrap();
        subLp.topMargin = dp(4);
        root.addView(sub, subLp);

        LinearLayout glass = card(root, "搜索栏玻璃");
        addSlider(glass, "模糊", Prefs.GLASS_BLUR, 0, 20, 0.1f, Prefs.DEFAULT_GLASS_BLUR,
                "dp，支持小数（0.1 步进）；0 = 不模糊");
        addSlider(glass, "折射", Prefs.REFRACTION_AMOUNT, 0, 30, 1f,
                Prefs.DEFAULT_REFRACTION_AMOUNT, "dp，边缘弯曲的力度；越小越平");
        addSlider(glass, "折射厚度", Prefs.REFRACTION_HEIGHT, 2, 40, 1f,
                Prefs.DEFAULT_REFRACTION_HEIGHT, "dp，弯曲影响到的边缘宽度");

        // 16.6: the "advanced" master switch is gone. The top-bar controls are
        // part of the normal screen, so a fresh install can tune the bar without
        // first discovering a hidden gate.
        LinearLayout top = card(root, "顶栏");
        // Percent instead of dp: MIUI's native blur saturates at 400 px, and a
        // 0..300 dp slider spent half its travel in that dead zone.
        addSlider(top, "模糊", Prefs.TOPBAR_BLUR, 0, 100, 1,
                Prefs.DEFAULT_TOPBAR_BLUR_PCT,
                "%，顶栏的背景模糊；0 = 关闭，100 = 最糊");
        addSlider(top, "透明度", Prefs.TOPBAR_ALPHA, 0, 100, 1, Prefs.DEFAULT_TOPBAR_ALPHA,
                "%，顶栏底色的不透明度；0 = 全透明，100 = 不透明");
        addSwitch(top, "显示顶栏分割线", Prefs.TOPBAR_DIVIDER,
                Prefs.DEFAULT_TOPBAR_DIVIDER,
                "折叠顶栏出现时在底部画 0.5dp 发丝线；浅色黑色 12%、深色中性灰 30%");

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
                        .putInt(Prefs.TOPBAR_BLUR, Prefs.DEFAULT_TOPBAR_BLUR_PCT)
                        .putInt(Prefs.TOPBAR_ALPHA, Prefs.DEFAULT_TOPBAR_ALPHA)
                        .putBoolean(Prefs.TOPBAR_DIVIDER, Prefs.DEFAULT_TOPBAR_DIVIDER)
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
     * as-is (Prefs.getFloat), ints for blur / opacity percentages.
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

        final boolean isInt = Prefs.TOPBAR_BLUR.equals(key)
                || Prefs.TOPBAR_ALPHA.equals(key);
        final boolean percent = Prefs.TOPBAR_BLUR.equals(key)
                || Prefs.TOPBAR_ALPHA.equals(key);
        // 16.1 stored dp here (default 110). Show the migrated percentage, not
        // 110 %, while leaving the old value untouched until the user drags.
        float current = Prefs.TOPBAR_BLUR.equals(key)
                ? Prefs.topBarBlurPercent(this)
                : readNumber(key, fallback);

        final SeekBar bar = new SeekBar(this);
        // Progress is measured in steps, not dp: the glass blur uses 0.1 dp so
        // the old Math.round(current / step) - min indexing collapsed to 0..20
        // whole numbers and the slider could never select 1.5 dp.
        final int stepCount = Math.max(1, Math.round((max - min) / step));
        bar.setMax(stepCount);
        int initial = Math.max(0, Math.min(stepCount,
                Math.round((current - min) / step)));
        bar.setProgress(initial);
        value.setText(format(min + initial * step, isInt, percent));
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
                float raw = min + progress * step;
                // Keep 0.1 dp precision in shared_prefs too, not 1.5000001.
                raw = Math.round(raw * 10f) / 10f;
                value.setText(format(raw, isInt, percent));
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
     * One labelled on/off row. The switch writes the boolean straight into the
     * shared prefs; {@code after} (optional) runs after a change, e.g. so the
     * advanced section can appear without leaving the screen.
     */
    private Switch addSwitch(LinearLayout card, String label, final String key,
                             final boolean fallback, String hint, final Runnable after) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = text(label, 15, Typeface.DEFAULT, textPrimary);
        row.addView(name, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final Switch toggle = new Switch(this);
        toggle.setShowText(false);
        toggle.setChecked(readBoolean(key, fallback));
        row.addView(toggle);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(16);
        card.addView(row, rowLp);

        TextView caption = text(hint, 12, Typeface.DEFAULT, textSecondary);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        capLp.bottomMargin = dp(14);
        card.addView(caption, capLp);

        toggle.setOnCheckedChangeListener(
                new android.widget.CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(android.widget.CompoundButton button,
                                                 boolean checked) {
                        prefs.edit().putBoolean(key, checked).apply();
                        if (after != null) {
                            after.run();
                        }
                    }
                });
        return toggle;
    }

    private Switch addSwitch(LinearLayout card, String label, String key, boolean fallback,
                             String hint) {
        return addSwitch(card, label, key, fallback, hint, null);
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

    /** Boolean preference, tolerant of a number written by an older build. */
    private boolean readBoolean(String key, boolean fallback) {
        try {
            Object value = prefs.getAll().get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private String format(float value, boolean isInt, boolean percent) {
        if (isInt) {
            int rounded = Math.round(value);
            return percent ? rounded + "%" : String.valueOf(rounded);
        }
        return (Math.round(value * 10f) / 10f) + " dp";
    }

    /** The manifest label, so the title cannot drift from the installed app name. */
    private String appLabel() {
        try {
            CharSequence label = getApplicationInfo().loadLabel(getPackageManager());
            if (label != null && label.length() > 0) {
                return label.toString();
            }
        } catch (Throwable ignored) {
        }
        return "iLayoutSettings";
    }

    /** Version comes from the manifest, so the subtitle cannot drift again. */
    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "?";
        }
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
