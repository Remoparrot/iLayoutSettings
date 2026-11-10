package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Edits the "控制中心 / 更多连接" second-level page
 * ({@code com.android.settings.MiuiWirelessSettings}):
 *
 * <ul>
 *   <li>removes the 飞行模式 row (airplane mode now lives on the home page),</li>
 *   <li>adds a 隔空播放与连续互通 row that opens the MiLink mirror settings.</li>
 * </ul>
 */
final class WirelessPage {

    private static final String KEY = "ios_interconnection";

    private WirelessPage() {
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.android.settings.MiuiWirelessSettings", cl);
            if (cls == null) {
                XLog.w("MiuiWirelessSettings not found");
                return;
            }
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        inject(param.thisObject);
                    } catch (Throwable t) {
                        XLog.e("wireless page edit failed", t);
                    }
                }
            };
            XposedBridge.hookAllMethods(cls, "onViewCreated", hook);
            XposedBridge.hookAllMethods(cls, "onResume", hook);
            XLog.i("hooked MiuiWirelessSettings (airplay row / airplane removal)");
        } catch (Throwable t) {
            XLog.e("wireless page hook failed", t);
        }
    }

    private static void inject(Object fragment) {
        Object screen = call(fragment, "getPreferenceScreen");
        if (screen == null) {
            return;
        }
        Object countObject = call(screen, "getPreferenceCount");
        int count = countObject instanceof Integer ? (Integer) countObject : 0;
        for (int i = count - 1; i >= 0; i--) {
            Object pref = call(screen, "getPreference", i);
            if (pref == null) {
                continue;
            }
            String title = String.valueOf(call(pref, "getTitle"));
            String key = String.valueOf(call(pref, "getKey"));
            if (title.contains("飞行模式") || key.contains("airplane")
                    || key.contains("flight_mode")) {
                call(screen, "removePreference", pref);
                XLog.i("removed airplane row from wireless page: " + title);
            }
        }
        if (true) {
            // 隔空播放 moved to the home page (below 无障碍); only the airplane
            // row removal is kept for this page.
            return;
        }
        if (call(screen, "findPreference", KEY) != null) {
            return;
        }
        Context context = contextOf(fragment);
        if (context == null) {
            return;
        }
        Intent intent = new Intent("miui.intent.action.MIRROR_SETTING");
        intent.setPackage("com.milink.service");
        if (context.getPackageManager().resolveActivity(intent, 0) == null) {
            XLog.w("mirror settings activity not resolvable, skip airplay row");
            return;
        }
        try {
            Object searchCategory = null;
            Object countObj = call(screen, "getPreferenceCount");
            int n = countObj instanceof Integer ? (Integer) countObj : 0;
            for (int i = 0; i < n; i++) {
                Object candidate = call(screen, "getPreference", i);
                String title = String.valueOf(call(candidate, "getTitle"));
                if (title.contains("查找其他设置")) {
                    searchCategory = candidate;
                    break;
                }
            }
            if (searchCategory != null) {
                call(screen, "removePreference", searchCategory);
            }
            ClassLoader cl = context.getClassLoader();
            Object template = firstPreference(screen);
            Class<?> prefClass;
            if (template != null) {
                prefClass = template.getClass();
            } else {
                prefClass = XposedHelpers.findClassIfExists(
                        "com.android.settingslib.miuisettings.preference.Preference", cl);
                if (prefClass == null) {
                    prefClass = XposedHelpers.findClass("androidx.preference.Preference", cl);
                }
            }
            Object pref = prefClass.getConstructor(Context.class).newInstance(context);
            call(pref, "setTitle", "隔空播放与连续互通");
            call(pref, "setKey", KEY);
            call(pref, "setIntent", intent);
            if (template != null) {
                AboutExtras.copyStyle(template, pref);
            }
            call(screen, "addPreference", pref);
            if (searchCategory != null) {
                call(screen, "addPreference", searchCategory);
            }
            XLog.i("airplay row added to wireless page");
        } catch (Throwable t) {
            XLog.e("add airplay row failed", t);
        }
    }

    private static Object firstPreference(Object screen) {
        Object countObject = call(screen, "getPreferenceCount");
        int count = countObject instanceof Integer ? (Integer) countObject : 0;
        for (int i = 0; i < count; i++) {
            Object pref = call(screen, "getPreference", i);
            if (pref == null) {
                continue;
            }
            String cls = pref.getClass().getName();
            if (cls.contains("Category")) {
                continue;
            }
            Object widget = call(pref, "getWidgetLayoutResource");
            if (widget instanceof Integer && (Integer) widget != 0) {
                continue;
            }
            if (call(pref, "getTitle") == null) {
                continue;
            }
            return pref;
        }
        return null;
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
