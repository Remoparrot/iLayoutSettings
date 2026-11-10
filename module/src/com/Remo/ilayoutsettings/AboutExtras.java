package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Extra content for the "关于本机" page ({@code MiuiMyDeviceSettings}):
 * moves the 通用 / 高级设置 entry to the bottom of that page and forces the
 * advanced material (blurred hero background) on.
 */
final class AboutExtras {

    private static final String KEY = "ios_general";

    private AboutExtras() {
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.android.settings.device.MiuiMyDeviceSettings", cl);
            if (cls == null) {
                XLog.w("MiuiMyDeviceSettings not found");
                return;
            }
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        inject(param.thisObject);
                    } catch (Throwable t) {
                        XLog.e("about extras failed", t);
                    }
                }
            };
            XposedBridge.hookAllMethods(cls, "onViewCreated", hook);
            XposedBridge.hookAllMethods(cls, "onResume", hook);
            XLog.i("hooked MiuiMyDeviceSettings (general entry / material)");
        } catch (Throwable t) {
            XLog.e("about extras hook failed", t);
        }
    }

    private static void inject(Object fragment) {
        Context context = contextOf(fragment);
        if (context == null) {
            return;
        }
        Object screen = call(fragment, "getPreferenceScreen");
        if (screen != null && call(screen, "findPreference", KEY) == null) {
            Object pref = newPreference(context, screen, string(context, "other_advanced_settings", "通用"));
            if (pref != null) {
                call(pref, "setFragment", "com.android.settings.personal.OtherPersonalSettings");
                call(screen, "addPreference", pref);
                XLog.i("general entry added to about page");
            }
        }
    }

    private static void forceMaterial(Object fragment, Context context) {
        try {
            Object root = call(fragment, "getView");
            if (!(root instanceof android.view.View)) {
                return;
            }
            int id = context.getResources().getIdentifier(
                    "miui_version_card_view", "id", "com.android.settings");
            android.view.View card = id == 0 ? null
                    : ((android.view.View) root).findViewById(id);
            if (card == null) {
                return;
            }
            Class<?> blurUtils = XposedHelpers.findClassIfExists(
                    "miuix.core.util.MiuiBlurUtils", context.getClassLoader());
            if (blurUtils != null) {
                for (Method m : blurUtils.getDeclaredMethods()) {
                    if (!m.getName().equals("setBackgroundBlur") || m.getParameterCount() != 2) {
                        continue;
                    }
                    try {
                        m.setAccessible(true);
                        m.invoke(null, card, 120);
                        XLog.i("about hero blur forced");
                    } catch (Throwable ignored) {
                    }
                    break;
                }
            }
            if (card instanceof android.view.ViewGroup) {
                android.view.ViewGroup group = (android.view.ViewGroup) card;
                for (int i = 0; i < group.getChildCount(); i++) {
                    android.view.View child = group.getChildAt(i);
                    try {
                        child.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            XLog.w("about material force failed: " + t);
        }
    }

    private static Object newPreference(Context context, Object screen, String title) {
        try {
            Object template = firstPreference(screen);
            Class<?> cls;
            if (template != null) {
                cls = template.getClass();
            } else {
                cls = XposedHelpers.findClassIfExists(
                        "com.android.settingslib.miuisettings.preference.Preference",
                        context.getClassLoader());
                if (cls == null) {
                    cls = XposedHelpers.findClass("androidx.preference.Preference",
                            context.getClassLoader());
                }
            }
            Object pref = cls.getConstructor(Context.class).newInstance(context);
            call(pref, "setTitle", title);
            call(pref, "setKey", KEY);
            if (template != null) {
                copyStyle(template, pref);
            } else {
                setDefaultLayout(context, pref);
            }
            return pref;
        } catch (Throwable t) {
            XLog.e("create preference failed", t);
            return null;
        }
    }

    private static Object firstPreference(Object screen) {
        Object countObject = call(screen, "getPreferenceCount");
        int count = countObject instanceof Integer ? (Integer) countObject : 0;
        for (int i = 0; i < count; i++) {
            Object pref = call(screen, "getPreference", i);
            if (pref == null || KEY.equals(String.valueOf(call(pref, "getKey")))) {
                continue;
            }
            String cls = pref.getClass().getName();
            if (cls.contains("Category")) {
                continue;
            }
            if (intOf(call(pref, "getWidgetLayoutResource")) != 0) {
                continue;
            }
            if (call(pref, "getTitle") == null) {
                continue;
            }
            return pref;
        }
        return null;
    }

    static void copyStyle(Object template, Object pref) {
        int layout = intOf(call(template, "getLayoutResource"));
        if (layout != 0) {
            call(pref, "setLayoutResource", layout);
        }
        int widget = intOf(call(template, "getWidgetLayoutResource"));
        if (widget != 0) {
            call(pref, "setWidgetLayoutResource", widget);
        }
    }

    private static void setDefaultLayout(Context context, Object pref) {
        int layout = context.getResources().getIdentifier(
                "miuix_preference_layout", "layout", "com.android.settings");
        if (layout != 0) {
            call(pref, "setLayoutResource", layout);
        }
    }

    private static int intOf(Object value) {
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static String string(Context context, String name, String fallback) {
        try {
            Resources res = context.getResources();
            int id = res.getIdentifier(name, "string", "com.android.settings");
            if (id != 0) {
                return res.getString(id);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static Context contextOf(Object fragment) {
        Object activity = call(fragment, "getActivity");
        if (activity instanceof Activity) {
            return (Activity) activity;
        }
        Object ctx = HeaderData.field(fragment, "mContext");
        return ctx instanceof Context ? (Context) ctx : null;
    }

    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                    continue;
                }
                try {
                    m.setAccessible(true);
                    return m.invoke(target, args);
                } catch (Throwable t) {
                    return null;
                }
            }
        }
        return null;
    }
}
