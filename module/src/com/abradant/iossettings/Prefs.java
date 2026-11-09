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
    static final String TOPBAR_BLUR = "topbar_blur";

    static final float DEFAULT_REFRACTION_HEIGHT = 40f;
    static final float DEFAULT_REFRACTION_AMOUNT = 20f;
    static final float DEFAULT_CHROMATIC = 0f;
    static final float DEFAULT_INNER_SHADOW = 0.35f;
    static final float DEFAULT_SHADOW_W = 20f;
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
