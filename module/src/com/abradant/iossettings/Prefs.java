package com.abradant.iossettings;

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
    static final String TOPBAR_BLUR = "topbar_blur";

    /** Thickness of the refracted band at the capsule edge, in dp. */
    static final float DEFAULT_REFRACTION_HEIGHT = 18f;
    /** How far the lens pulls the backdrop inwards at the rim, in dp. */
    static final float DEFAULT_REFRACTION_AMOUNT = 10f;
    /** Reserved for Kyant's dispersion shader; 0 keeps the single-sample lens. */
    static final float DEFAULT_CHROMATIC = 0f;
    /** Opacity of the dark band hugging the bottom edge. */
    static final float DEFAULT_INNER_SHADOW = 0.18f;
    /** Width of that inner-shadow band, in dp. */
    static final float DEFAULT_SHADOW_W = 8f;
    /** Backdrop blur radius before the lens, in dp. */
    static final float DEFAULT_GLASS_BLUR = 10f;
    /** Peak opacity of the 45° rim highlight. */
    static final float DEFAULT_RIM_ALPHA = 0.5f;
    /** Width of the rim highlight band, in dp. */
    static final float DEFAULT_RIM_WIDTH = 1f;
    static final int DEFAULT_TOPBAR_BLUR = 110;

    private Prefs() {
    }

    static XSharedPreferences read() {
        try {
            XSharedPreferences prefs = new XSharedPreferences(
                    "com.abradant.iossettings", FILE);
            prefs.reload();
            return prefs;
        } catch (Throwable t) {
            return null;
        }
    }

    static float getFloat(XSharedPreferences prefs, String key, float fallback) {
        if (prefs == null) {
            return fallback;
        }
        try {
            return prefs.getFloat(key, fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }

    static int getInt(XSharedPreferences prefs, String key, int fallback) {
        if (prefs == null) {
            return fallback;
        }
        try {
            return prefs.getInt(key, fallback);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
