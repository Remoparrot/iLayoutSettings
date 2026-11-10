package com.Remo.ilayoutsettings;

import de.robv.android.xposed.XSharedPreferences;

/** Keys and access for the module settings (shared with the target process). */
final class Prefs {

    static final String FILE = "ios_settings";

    static final String REFRACTION_HEIGHT = "refraction_height";
    static final String REFRACTION_AMOUNT = "refraction_amount";
    static final String CHROMATIC = "chromatic_aberration";
    static final String INNER_SHADOW = "inner_shadow";
    static final String SHADOW_W = "shadow_w";
    static final String GLASS_BLUR = "glass_blur";
    static final String RIM_ALPHA = "rim_alpha";
    static final String RIM_WIDTH = "rim_width";
    /** 16.2: percent (0..100). 16.1 stored a raw dp radius here; see below. */
    static final String TOPBAR_BLUR = "topbar_blur";
    /** Opacity (0..100 %) of the scrolled top bar surface, 16.2. */
    static final String TOPBAR_ALPHA = "topbar_alpha";
    /** 16.4: show the 0.5 dp hairline under the condensed top bar. */
    static final String TOPBAR_DIVIDER = "topbar_divider";
    /** 16.4: unlocks the advanced section (top bar controls) in the GUI. */
    static final String ADVANCED = "advanced_mode";

    /** Thickness of the refracted band at the capsule edge, in dp. */
    static final float DEFAULT_REFRACTION_HEIGHT = 12f;
    /** How far the lens pulls the backdrop inwards at the rim, in dp (16.2: 19). */
    static final float DEFAULT_REFRACTION_AMOUNT = 19f;
    /** Reserved for Kyant's dispersion shader; 0 keeps the single-sample lens. */
    static final float DEFAULT_CHROMATIC = 0f;
    /** Opacity of the dark band hugging the bottom edge. */
    static final float DEFAULT_INNER_SHADOW = 0.18f;
    /** Width of that inner-shadow band, in dp. */
    static final float DEFAULT_SHADOW_W = 8f;
    /** Backdrop blur radius before the lens, in dp. */
    static final float DEFAULT_GLASS_BLUR = 1f;
    /** Retired in 15.1: the rim highlight is gone, kept so old prefs still parse. */
    static final float DEFAULT_RIM_ALPHA = 0f;
    /** Retired in 15.1 with {@link #DEFAULT_RIM_ALPHA}. */
    static final float DEFAULT_RIM_WIDTH = 0f;
    /** Native blur is capped at 400 px, ~145 dp on a 2.75x screen. */
    static final int MAX_TOPBAR_BLUR_DP = 150;
    /**
     * 16.3 default: an almost clear bar — 5 % blur + 2 % opacity. iOS' scrolled
     * navigation bar is basically the page content with a hairline on top, so
     * the 16.2 low-blur slab (30 % / 45 %) was still too milky.
     */
    static final int DEFAULT_TOPBAR_BLUR_PCT = 5;
    static final int DEFAULT_TOPBAR_ALPHA = 2;
    /** The hairline is on by default; the GUI switch only exists in advanced mode. */
    static final boolean DEFAULT_TOPBAR_DIVIDER = true;
    /** Advanced mode is opt-in, so the first screen only has the glass sliders. */
    static final boolean DEFAULT_ADVANCED = false;

    private Prefs() {
    }

    static XSharedPreferences read() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(
                    "com.Remo.ilayoutsettings", FILE);
            prefs.reload();
            return prefs;
        } catch (Throwable t) {
            return null;
        }
    }

    private static android.os.Bundle cached;
    private static long cachedAt;
    /** The hooked process polls at ~2 Hz; one IPC per poll is plenty. */
    private static final long CACHE_MS = 400;

    /**
     * Reads every setting from the module app through {@link PrefsProvider}.
     * Returns null when the provider is unreachable (then callers fall back to
     * XSharedPreferences, which only works on setups where the file happens to
     * be readable).
     */
    static android.os.Bundle snapshot(android.content.Context context) {
        if (context == null) {
            return null;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (cached != null && now - cachedAt < CACHE_MS) {
            return cached;
        }
        try {
            cached = context.getContentResolver().call(
                    PrefsProvider.URI, PrefsProvider.METHOD_GET, null, null);
        } catch (Throwable ignored) {
            cached = null;
        }
        cachedAt = now;
        return cached;
    }

    static float getFloat(android.content.Context context, String key, float fallback) {
        android.os.Bundle values = snapshot(context);
        if (values != null && values.containsKey(key)) {
            Object value = values.get(key);
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
        }
        return getFloat(read(), key, fallback);
    }

    static int getInt(android.content.Context context, String key, int fallback) {
        android.os.Bundle values = snapshot(context);
        if (values != null && values.containsKey(key)) {
            Object value = values.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            if (value instanceof Boolean) {
                return ((Boolean) value) ? 1 : 0;
            }
        }
        return getInt(read(), key, fallback);
    }

    static boolean getBoolean(android.content.Context context, String key, boolean fallback) {
        android.os.Bundle values = snapshot(context);
        if (values != null && values.containsKey(key)) {
            Object value = values.get(key);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
        }
        XSharedPreferences prefs = read();
        if (prefs == null) {
            return fallback;
        }
        try {
            return prefs.getBoolean(key, fallback);
        } catch (Throwable t) {
            // A slider stored this key as a number in an older build.
            try {
                return prefs.getInt(key, fallback ? 1 : 0) != 0;
            } catch (Throwable ignored) {
                return fallback;
            }
        }
    }

    /** Hairline visibility: on unless the user (in advanced mode) turned it off. */
    static boolean topBarDivider(android.content.Context context) {
        return getBoolean(context, TOPBAR_DIVIDER, DEFAULT_TOPBAR_DIVIDER);
    }

    /** Whether the GUI should expose the top-bar / hairline section. */
    static boolean advanced(android.content.Context context) {
        return getBoolean(context, ADVANCED, DEFAULT_ADVANCED);
    }

    /**
     * Top bar blur strength, now expressed as 0..100 %.
     *
     * <p>16.1 stored the radius in dp (default 110, slider went to 300). Values
     * above 100 are therefore old radius values; convert them instead of clamping so
     * an upgrading user keeps roughly the same look.</p>
     */
    static int topBarBlurPercent(android.content.Context context) {
        int raw = getInt(context, TOPBAR_BLUR, -1);
        if (raw < 0) {
            return DEFAULT_TOPBAR_BLUR_PCT;
        }
        if (raw <= 100) {
            return raw;
        }
        return Math.max(0, Math.min(100,
                Math.round(raw * 100f / MAX_TOPBAR_BLUR_DP)));
    }

    /** 0..100 % → 0..150 dp, the range MIUI's native blur can actually show. */
    static int topBarBlurRadius(android.content.Context context) {
        return Math.round(topBarBlurPercent(context) * MAX_TOPBAR_BLUR_DP / 100f);
    }

    static float getFloat(XSharedPreferences prefs, String key, float fallback) {
        if (prefs == null) {
            return fallback;
        }
        try {
            return prefs.getFloat(key, fallback);
        } catch (Throwable t) {
            // A slider stored this key as an Integer in an older build.
            try {
                return prefs.getInt(key, Math.round(fallback));
            } catch (Throwable ignored) {
                return fallback;
            }
        }
    }

    static int getInt(XSharedPreferences prefs, String key, int fallback) {
        if (prefs == null) {
            return fallback;
        }
        try {
            return prefs.getInt(key, fallback);
        } catch (Throwable t) {
            try {
                return Math.round(prefs.getFloat(key, fallback));
            } catch (Throwable ignored) {
                return fallback;
            }
        }
    }
}
