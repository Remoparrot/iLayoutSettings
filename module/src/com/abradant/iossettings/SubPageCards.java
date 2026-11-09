package com.abradant.iossettings;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Same last-row treatment as {@link CardFix}, but for the second-level pages:
 * those use androidx preferences instead of the header adapter, so the entry
 * point is {@code androidx.preference.Preference.onBindViewHolder}.
 *
 * <p>The last preference of every card gets its background / foreground
 * cleared, which removes the theme's card-shaped 9-patch overlay
 * (miuix_preference_item_background_*, miuix_recyclerview_card_item_press_fg_*)
 * from the bottom row.</p>
 */
final class SubPageCards {

    private static final Map<View, Drawable> BACKGROUNDS = new WeakHashMap<>();
    private static final Map<View, Drawable> FOREGROUNDS = new WeakHashMap<>();

    private SubPageCards() {
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> preference = XposedHelpers.findClass("androidx.preference.Preference", cl);
            XposedBridge.hookAllMethods(preference, "onBindViewHolder", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        apply(param.thisObject, param.args[0]);
                    } catch (Throwable t) {
                        XLog.e("sub-page last row fix failed", t);
                    }
                }
            });
            XLog.i("hooked Preference.onBindViewHolder (sub-page card bottom fix)");
        } catch (Throwable t) {
            XLog.e("sub-page card hook failed", t);
        }
    }

    private static void apply(Object preference, Object holder) {
        View itemView = (View) HeaderData.field(holder, "itemView");
        if (itemView == null || preference == null) {
            return;
        }
        if (preference.getClass().getName().contains("PreferenceCategory")) {
            return;
        }
        if (!isLastOfCard(preference)) {
            Drawable bg = BACKGROUNDS.remove(itemView);
            if (bg != null) {
                itemView.setBackground(bg);
            }
            Drawable fg = FOREGROUNDS.remove(itemView);
            if (fg != null) {
                itemView.setForeground(fg);
            }
            return;
        }
        if (!BACKGROUNDS.containsKey(itemView)) {
            BACKGROUNDS.put(itemView, itemView.getBackground());
        }
        if (!FOREGROUNDS.containsKey(itemView)) {
            FOREGROUNDS.put(itemView, itemView.getForeground());
        }
        itemView.setBackground(null);
        itemView.setForeground(null);
    }

    private static boolean isLastOfCard(Object preference) {
        Object parent = call(preference, "getParent");
        if (parent == null) {
            return false;
        }
        Object countObject = call(parent, "getPreferenceCount");
        int count = countObject instanceof Integer ? (Integer) countObject : 0;
        if (count <= 0) {
            return false;
        }
        int index = -1;
        for (int i = 0; i < count; i++) {
            if (call(parent, "getPreference", i) == preference) {
                index = i;
                break;
            }
        }
        return index >= 0 && index == count - 1;
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
