package com.Remo.ilayoutsettings;

import android.content.res.Resources;
import android.view.View;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Restores the theme's card side margins on HyperOS 4.
 *
 * <p>On HyperOS 3 the miuix {@code CardItemDecoration} read
 * {@code miuix_recyclerview_card_group_margin_start/end} straight from the app
 * resources, so a theme could change the home-card side margins through
 * {@code theme_values.xml}. HyperOS 4 added
 * {@code SettingsFragment.updateCardGroupMarginForSmallScreen()}, which reads
 * its own {@code card_group_margin_horizontal} (12dp) and calls
 * {@code setCardMarginStart/End} — overwriting the themed value every time the
 * list is built. This hook re-applies the miuix dimensions right after that
 * method ran, so the theme value wins again while the untouched ROM keeps its
 * 12dp.</p>
 */
final class CardMargins {

    private CardMargins() {
    }

    static void install(ClassLoader cl) {
        Class<?> fragment = XposedHelpers.findClassIfExists(
                "com.android.settings.SettingsFragment", cl);
        if (fragment == null) {
            XLog.w("SettingsFragment not found, card margins stay native");
            return;
        }
        try {
            XposedBridge.hookAllMethods(fragment, "updateCardGroupMarginForSmallScreen",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                apply(param.thisObject);
                            } catch (Throwable t) {
                                XLog.e("card margin hook failed", t);
                            }
                        }
                    });
            XLog.i("hooked updateCardGroupMarginForSmallScreen");
        } catch (Throwable t) {
            XLog.w("card margin hook not installed: " + t);
        }
    }

    /** Re-applies the themed start/end margin to the home card decoration. */
    static void apply(Object fragment) {
        // HyperOS 3's miuix decoration already reads the themed dimensions, so
        // there is nothing to override there.
        if (android.os.Build.VERSION.SDK_INT < 37) {
            return;
        }
        View list = null;
        Object listObject = HeaderData.field(fragment, "mListView");
        if (listObject instanceof View) {
            list = (View) listObject;
        }
        if (list == null) {
            return;
        }
        Resources res = list.getContext().getResources();
        int start = dimen(res, "miuix_recyclerview_card_group_margin_start");
        int end = dimen(res, "miuix_recyclerview_card_group_margin_end");
        int nativeValue = dimen(res, "card_group_margin_horizontal");
        if (start <= 0) {
            start = nativeValue;
        }
        if (end <= 0) {
            end = nativeValue;
        }
        if (start <= 0 && end <= 0) {
            return;
        }
        for (int i = 0, count = decorationCount(list); i < count; i++) {
            Object decoration = decorationAt(list, i);
            if (decoration == null
                    || !decoration.getClass().getName().endsWith("CardItemDecoration")) {
                continue;
            }
            boolean startOk = callInt(decoration, "setCardMarginStart", start);
            boolean endOk = callInt(decoration, "setCardMarginEnd", end);
            if (startOk || endOk) {
                XLog.i("card margins started/ended at " + start + "/" + end);
            }
        }
        list.invalidate();
    }

    private static int dimen(Resources res, String name) {
        try {
            int id = res.getIdentifier(name, "dimen", "com.android.settings");
            return id == 0 ? 0 : res.getDimensionPixelSize(id);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int decorationCount(View list) {
        try {
            Method m = list.getClass().getMethod("getItemDecorationCount");
            Object value = m.invoke(list);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Object decorationAt(View list, int index) {
        try {
            Method m = list.getClass().getMethod("getItemDecorationAt", int.class);
            return m.invoke(list, index);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Calls {@code setCardMarginXxx(int)}. Returns whether the call landed. */
    private static boolean callInt(Object target, String name, int value) {
        try {
            Class<?> c = target.getClass();
            while (c != null) {
                try {
                    Method m = c.getDeclaredMethod(name, int.class);
                    m.setAccessible(true);
                    m.invoke(target, value);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
