package com.abradant.iossettings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Adds leading icons to second-level preference rows (通用 / 更多设置 / ... pages).
 *
 * <p>MIUI's preference item layout already contains an {@code @android:id/icon}
 * ImageView, it is just empty for most rows. The icons themselves are bundled in
 * this module and looked up by preference key, with a title keyword fallback.</p>
 */
final class SubPageIcons {

    private static final String MODULE_PKG = "com.abradant.iossettings";
    private static boolean hooked;
    private static Resources moduleResources;

    private static final String[][] KEY_ICONS = {
            {"date_time_settings", "ic_sub_clock"},
            {"language_settings", "ic_sub_keyboard"},
            {"locale_settings", "ic_sub_globe"},
            {"circle_to_search_switch", "ic_sub_search"},
            {"back_strap", "ic_sub_light"},
            {"game_booster", "ic_sub_game"},
            {"beauty_fc_assistant", "ic_sub_camera"},
            {"conversation_assistant", "ic_sub_chat"},
            {"securitycenter_sidebar_box", "ic_sub_sidebar"},
            {"taplus_settings", "ic_sub_portal"},
            {"smart_clipboard_settings", "ic_sub_clipboard"},
            {"freeform_guide_settings", "ic_sub_window"},
            {"mi_wallet_settings", "ic_sub_wallet"},
            {"screen_recorder", "ic_sub_record"},
            {"miui_desktop_mode_settings", "ic_sub_desktop"},
            {"external_ram", "ic_sub_memory"},
            {"parental_control", "ic_sub_parent"},
            {"kid_space_settings", "ic_sub_child"},
            {"edge_settings", "ic_sub_edge"},
            {"speaker_settings", "ic_sub_speaker"},
            {"user_settings", "ic_sub_user"},
            {"account_sync", "ic_sub_sync"}
    };

    private static final String[][] TITLE_ICONS = {
            {"日期", "ic_sub_clock"}, {"时间", "ic_sub_clock"},
            {"语言", "ic_sub_keyboard"}, {"输入法", "ic_sub_keyboard"}, {"键盘", "ic_sub_keyboard"},
            {"地区", "ic_sub_globe"},
            {"搜索", "ic_sub_search"},
            {"灯效", "ic_sub_light"},
            {"游戏", "ic_sub_game"},
            {"美颜", "ic_sub_camera"}, {"相机", "ic_sub_camera"},
            {"对话", "ic_sub_chat"}, {"会话", "ic_sub_chat"},
            {"侧边栏", "ic_sub_sidebar"},
            {"传送门", "ic_sub_portal"},
            {"剪贴板", "ic_sub_clipboard"}, {"复制", "ic_sub_clipboard"},
            {"自由窗口", "ic_sub_window"}, {"小窗", "ic_sub_window"},
            {"钱包", "ic_sub_wallet"}, {"卡包", "ic_sub_wallet"},
            {"录屏", "ic_sub_record"}, {"录制", "ic_sub_record"},
            {"桌面", "ic_sub_desktop"},
            {"内存", "ic_sub_memory"}, {"扩展", "ic_sub_memory"},
            {"家长", "ic_sub_parent"}, {"未成年", "ic_sub_parent"},
            {"儿童", "ic_sub_child"},
            {"边缘", "ic_sub_edge"},
            {"扬声器", "ic_sub_speaker"},
            {"用户", "ic_sub_user"},
            {"同步", "ic_sub_sync"},
            {"小爱", "ic_sub_ai"}, {"AI", "ic_sub_ai"}
    };

    private SubPageIcons() {
    }

    static void install(ClassLoader cl) {
        if (hooked) {
            return;
        }
        Class<?> pfc = XposedHelpers.findClassIfExists(
                "androidx.preference.PreferenceFragmentCompat", cl);
        if (pfc == null) {
            XLog.e("PreferenceFragmentCompat not found", null);
            return;
        }
        hooked = true;
        // Sub-page row icons are intentionally disabled (v8.3): the native
        // preference rows keep their original look, only the intro card is added.
        XC_MethodHook applyHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                // no-op
            }
        };
        XposedBridge.hookAllMethods(pfc, "onCreate", applyHook);
        XposedBridge.hookAllMethods(pfc, "onViewCreated", applyHook);
        XposedBridge.hookAllMethods(pfc, "onResume", applyHook);

        XposedBridge.hookAllMethods(pfc, "onViewCreated", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    IntroCard.attach(param.thisObject);
                    // About page is intentionally left native (iOS About page removed).
                } catch (Throwable t) {
                    XLog.e("intro card hook", t);
                }
            }
        });
        XLog.i("hooked PreferenceFragmentCompat (sub-page icons)");
    }

    private static void apply(Object fragment) {
        Object screen = call(fragment, "getPreferenceScreen");
        if (screen == null) {
            return;
        }
        Resources res = moduleResources(fragment);
        if (res == null) {
            return;
        }
        walk(screen, res);
    }

    private static void walk(Object preference, Resources res) {
        if (preference == null) {
            return;
        }
        Object count = call(preference, "getPreferenceCount");
        if (count instanceof Integer) {
            int n = (Integer) count;
            for (int i = 0; i < n; i++) {
                walk(call(preference, "getPreference", i), res);
            }
            return;
        }
        Drawable existing = (Drawable) call(preference, "getIcon");
        if (existing != null) {
            return;
        }
        String key = String.valueOf(call(preference, "getKey"));
        String title = String.valueOf(call(preference, "getTitle"));
        String icon = iconName(key, title);
        if (icon == null) {
            return;
        }
        int id = res.getIdentifier(icon, "drawable", MODULE_PKG);
        if (id == 0) {
            return;
        }
        try {
            Drawable d = res.getDrawable(id, null);
            call(preference, "setIcon", d);
        } catch (Throwable t) {
            XLog.e("setIcon failed for " + key, t);
        }
    }

    private static String iconName(String key, String title) {
        String k = key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
        for (String[] pair : KEY_ICONS) {
            if (k.equals(pair[0]) || k.contains(pair[0])) {
                return pair[1];
            }
        }
        String t = title == null ? "" : title;
        for (String[] pair : TITLE_ICONS) {
            if (t.contains(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    private static Resources moduleResources(Object fragment) {
        if (moduleResources != null) {
            return moduleResources;
        }
        try {
            Context ctx = (Context) call(fragment, "getContext");
            if (ctx == null) {
                return null;
            }
            moduleResources = ctx.getPackageManager().getResourcesForApplication(MODULE_PKG);
        } catch (Throwable t) {
            XLog.e("module resources unavailable", t);
        }
        return moduleResources;
    }

    /* ------------------------------ reflection ------------------------------ */

    static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                    continue;
                }
                if (!matches(m.getParameterTypes(), args)) {
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

    private static boolean matches(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            Class<?> want = types[i];
            Object got = args[i];
            if (want.isPrimitive()) {
                Class<?> box = box(want);
                if (got == null || !box.isInstance(got)) {
                    return false;
                }
            } else if (got != null && !want.isInstance(got)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> box(Class<?> primitive) {
        if (primitive == int.class) {
            return Integer.class;
        }
        if (primitive == long.class) {
            return Long.class;
        }
        if (primitive == boolean.class) {
            return Boolean.class;
        }
        if (primitive == float.class) {
            return Float.class;
        }
        if (primitive == double.class) {
            return Double.class;
        }
        if (primitive == short.class) {
            return Short.class;
        }
        if (primitive == byte.class) {
            return Byte.class;
        }
        if (primitive == char.class) {
            return Character.class;
        }
        return Void.class;
    }
}
