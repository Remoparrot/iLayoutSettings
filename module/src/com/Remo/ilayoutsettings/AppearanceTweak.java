package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 外观 page: keep the original gap between the light/dark previews and put a
 * card background (same colour / radius as the other cards) behind them.
 */
final class AppearanceTweak {

    private AppearanceTweak() {
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> cls = XposedHelpers.findClassIfExists(
                    "com.android.settings.MiuiDisplaySettings", cl);
            if (cls == null) {
                XLog.w("MiuiDisplaySettings not found");
                return;
            }
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        inject(param.thisObject);
                    } catch (Throwable t) {
                        XLog.e("appearance tweak failed", t);
                    }
                }
            };
            XposedBridge.hookAllMethods(cls, "onViewCreated", hook);
            XposedBridge.hookAllMethods(cls, "onResume", hook);
            XLog.i("hooked MiuiDisplaySettings (preview card)");
        } catch (Throwable t) {
            XLog.e("appearance hook failed", t);
        }
    }

    private static void inject(Object fragment) {
        attempt(fragment, 0);
    }

    private static void attempt(final Object fragment, final int tries) {
        View root = (View) call(fragment, "getView");
        Activity activity = (Activity) call(fragment, "getActivity");
        if (root == null || activity == null) {
            return;
        }
        android.content.res.Resources res = activity.getResources();
        View outer = root.findViewById(res.getIdentifier(
                "light_mode_outer_view", "id", "com.android.settings"));
        if (outer == null) {
            // The preference (and its widget) may be inflated a bit later.
            if (tries < 25) {
                root.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        attempt(fragment, tries + 1);
                    }
                }, 60);
            } else {
                XLog.w("preview outer view not found after retries");
            }
            return;
        }
        boolean applied = false;
        View lightGroup = root.findViewById(res.getIdentifier(
                "light_mode_enable", "id", "com.android.settings"));
        View darkGroup = root.findViewById(res.getIdentifier(
                "dark_mode_enable", "id", "com.android.settings"));
        View pair = lightGroup != null && lightGroup.getParent() instanceof View
                ? (View) lightGroup.getParent() : null;
        if (pair != null) {
            // One single card behind both previews, with the same side margins
            // and corner radius as every other card.
            pair.setBackground(cardBackground(activity));
            pair.setClipToOutline(true);
            int startEnd = dimen(activity, "miuix_recyclerview_card_group_margin_start", 12);
            // The guide view already carries part of the card margin, so use a
            // much smaller inset to line up with the other cards.
            int side = Math.max(0, startEnd - Math.round(10 * activity.getResources()
                    .getDisplayMetrics().density));
            ViewGroup.LayoutParams lp = pair.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) lp;
                margins.leftMargin = side;
                margins.rightMargin = side;
                pair.setLayoutParams(margins);
            }
            // No gap between light and dark preview.
            if (lightGroup != null && lightGroup.getLayoutParams()
                    instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins =
                        (ViewGroup.MarginLayoutParams) lightGroup.getLayoutParams();
                margins.rightMargin = 0;
                lightGroup.setLayoutParams(margins);
            }
            // Push the two previews a bit lower inside the card.
            int topShift = Math.round(20 * activity.getResources()
                    .getDisplayMetrics().density);
            for (View group : new View[]{lightGroup, darkGroup}) {
                if (group != null && group.getLayoutParams()
                        instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margins =
                            (ViewGroup.MarginLayoutParams) group.getLayoutParams();
                    margins.topMargin = topShift;
                    group.setLayoutParams(margins);
                }
            }
            // Individual halves must be transparent so only the card shows.
            for (String idName : new String[]{"light_mode_outer_view", "dark_mode_outer_view"}) {
                View outerView = root.findViewById(res.getIdentifier(idName, "id", "com.android.settings"));
                if (outerView != null) {
                    outerView.setBackground(null);
                    // Drop the picture padding so the two previews get bigger.
                    outerView.setPadding(0, 0, 0, 0);
                }
            }
            applied = true;
        }
        XLog.i("preview card background applied: " + applied
                + (darkGroup == null ? "" : " (dark found)"));
        // reverted: the extra 自动 switch is not added anymore
    }

    /** Adds the "自动" dark-mode switch right below the preview card. */
    private static void addAutoSwitch(Object fragment, Activity activity) {
        try {
            Object screen = call(fragment, "getPreferenceScreen");
            if (screen == null || call(screen, "findPreference", "ios_auto_dark") != null) {
                return;
            }
            ClassLoader cl = activity.getClassLoader();
            Class<?> prefClass = XposedHelpers.findClassIfExists(
                    "com.android.settingslib.miuisettings.preference.SwitchPreference", cl);
            if (prefClass == null) {
                prefClass = XposedHelpers.findClassIfExists(
                        "androidx.preference.SwitchPreferenceCompat", cl);
            }
            if (prefClass == null) {
                return;
            }
            Object pref = prefClass.getConstructor(android.content.Context.class)
                    .newInstance(activity);
            call(pref, "setTitle", "自动");
            call(pref, "setSummary", "基于日出或日落，自动在浅色和深色之间切换。");
            call(pref, "setKey", "ios_auto_dark");
            call(pref, "setChecked", isAutoDark(activity));
            wireToggle(pref, activity);
            // Insert right after the first preference (the preview card).
            Object countObject = call(screen, "getPreferenceCount");
            int count = countObject instanceof Integer ? (Integer) countObject : 0;
            if (count <= 0) {
                call(screen, "addPreference", pref);
                return;
            }
            Object first = call(screen, "getPreference", 0);
            java.util.List<Object> rest = new java.util.ArrayList<>();
            for (int i = 1; i < count; i++) {
                Object other = call(screen, "getPreference", i);
                if (other != null) {
                    rest.add(other);
                }
            }
            for (int i = count - 1; i >= 0; i--) {
                Object other = call(screen, "getPreference", i);
                if (other != null) {
                    call(screen, "removePreference", other);
                }
            }
            call(screen, "addPreference", first);
            call(screen, "addPreference", pref);
            for (Object other : rest) {
                call(screen, "addPreference", other);
            }
            XLog.i("auto dark switch added to appearance page");
        } catch (Throwable t) {
            XLog.e("add auto dark switch failed", t);
        }
    }

    private static boolean isAutoDark(Activity activity) {
        try {
            Class<?> cls = Class.forName("miui.provider.MiuiSettings$System",
                    false, activity.getClassLoader());
            java.lang.reflect.Method m = cls.getMethod("getBoolean",
                    android.content.ContentResolver.class, String.class, boolean.class);
            Object value = m.invoke(null, activity.getContentResolver(),
                    "dark_mode_auto_time_enable", false);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void wireToggle(final Object pref, final Activity activity) {
        try {
            for (java.lang.reflect.Method m : pref.getClass().getMethods()) {
                if (!m.getName().equals("setOnPreferenceChangeListener")
                        || m.getParameterCount() != 1) {
                    continue;
                }
                final Class<?> listenerType = m.getParameterTypes()[0];
                if (!listenerType.isInterface()) {
                    continue;
                }
                Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                        activity.getClassLoader(), new Class<?>[]{listenerType},
                        new java.lang.reflect.InvocationHandler() {
                            @Override
                            public Object invoke(Object p, java.lang.reflect.Method method,
                                                 Object[] args) {
                                if (method.getName().equals("onPreferenceChange")
                                        && args != null && args.length == 2) {
                                    try {
                                        Class<?> cls = Class.forName(
                                                "miui.provider.MiuiSettings$System", false,
                                                activity.getClassLoader());
                                        java.lang.reflect.Method put = cls.getMethod("putBoolean",
                                                android.content.ContentResolver.class,
                                                String.class, boolean.class);
                                        put.invoke(null, activity.getContentResolver(),
                                                "dark_mode_auto_time_enable",
                                                (Boolean) args[1]);
                                    } catch (Throwable ignored) {
                                    }
                                    return Boolean.TRUE;
                                }
                                return method.getReturnType() == boolean.class
                                        ? Boolean.FALSE : null;
                            }
                        });
                m.invoke(pref, proxy);
                return;
            }
        } catch (Throwable t) {
            XLog.e("wire auto dark switch failed", t);
        }
    }

    private static GradientDrawable cardBackground(Activity activity) {
        boolean dark = UiTweaks.isDarkMode(activity);
        int color = dark ? 0xFF1C1C1E : 0xFFFFFFFF;
        try {
            android.content.res.Resources res = activity.getResources();
            int id = res.getIdentifier(dark
                            ? "miuix_recyclerview_card_group_background_dark"
                            : "miuix_recyclerview_card_group_background_light",
                    "color", "com.android.settings");
            if (id != 0) {
                color = res.getColor(id, activity.getTheme());
            }
        } catch (Throwable ignored) {
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dimen(activity, "card_background_radius", 16));
        return drawable;
    }

    private static int dimen(Activity activity, String name, int fallbackDp) {
        try {
            android.content.res.Resources res = activity.getResources();
            int id = res.getIdentifier(name, "dimen", "com.android.settings");
            if (id != 0) {
                return res.getDimensionPixelSize(id);
            }
        } catch (Throwable ignored) {
        }
        return Math.round(fallbackDp * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
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
