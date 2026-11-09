package com.abradant.iossettings;

/** Public bridge so the ported dev.settingslab classes can read the module prefs. */
public final class PrefsBridge {

    private PrefsBridge() {
    }

    public static de.robv.android.xposed.XSharedPreferences read() {
        return Prefs.read();
    }

    public static float getFloat(de.robv.android.xposed.XSharedPreferences prefs,
                                 String key, float fallback) {
        return Prefs.getFloat(prefs, key, fallback);
    }

    public static int getInt(de.robv.android.xposed.XSharedPreferences prefs,
                             String key, int fallback) {
        return Prefs.getInt(prefs, key, fallback);
    }
}
