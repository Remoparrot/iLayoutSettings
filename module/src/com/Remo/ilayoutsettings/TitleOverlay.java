package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Last-resort page title. Some Settings pages (e.g. 关于设备) have no
 * CollapseTitle at all, so nothing can be made visible — a TextView is added to
 * the app bar container instead, centred like the iOS navigation title.
 */
final class TitleOverlay {

    private static final String TAG = "ios_title_overlay";

    private TitleOverlay() {
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> activity = Class.forName("android.app.Activity", false, cl);
            XposedBridge.hookAllMethods(activity, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        apply((Activity) param.thisObject);
                    } catch (Throwable t) {
                        XLog.e("title overlay failed", t);
                    }
                }
            });
            XLog.i("hooked Activity.onResume (title overlay)");
        } catch (Throwable t) {
            XLog.e("title overlay hook failed", t);
        }
    }

    private static void apply(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if ("com.android.settings.MiuiSettings".equals(activity.getClass().getName())) {
            return;
        }
        Resources res = activity.getResources();
        int containerId = res.getIdentifier("action_bar_container", "id", "com.android.settings");
        if (containerId == 0) {
            return;
        }
        View container = activity.findViewById(containerId);
        if (!(container instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) container;
        if (hasNativeTitle(activity)) {
            // MIUI already shows its own compact title; nothing to add.
            return;
        }
        String title = resolveTitle(activity);
        if (title.length() == 0) {
            return;
        }
        View existing = group.findViewWithTag(TAG);
        if (existing instanceof TextView) {
            ((TextView) existing).setText(title);
            return;
        }
        TextView view = new TextView(activity);
        view.setText(title);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(primaryTextColor(activity));
        view.setTag(TAG);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(activity, 12);
        group.addView(view, lp);
        XLog.i("title overlay added: " + title);
    }

    private static boolean hasNativeTitle(Activity activity) {
        try {
            Object actionBar = call(activity, "getSupportActionBar");
            if (actionBar == null) {
                return false;
            }
            Object collapse = findOfType(actionBar, "CollapseTitle", 4);
            return collapse instanceof View && ((View) collapse).getVisibility() == View.VISIBLE
                    && ((View) collapse).getWidth() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String resolveTitle(Activity activity) {
        CharSequence title = activity.getTitle();
        if (title != null && title.length() > 0) {
            return title.toString();
        }
        try {
            Object actionBar = call(activity, "getSupportActionBar");
            Object barTitle = call(actionBar, "getTitle");
            if (barTitle instanceof CharSequence && ((CharSequence) barTitle).length() > 0) {
                return barTitle.toString();
            }
        } catch (Throwable ignored) {
        }
        String name = activity.getClass().getSimpleName();
        return name == null || name.length() == 0 ? "" : name;
    }

    private static int primaryTextColor(Activity activity) {
        try {
            TypedValue value = new TypedValue();
            if (activity.getTheme().resolveAttribute(
                    android.R.attr.textColorPrimary, value, true)
                    && value.resourceId != 0) {
                return activity.getResources().getColor(value.resourceId, activity.getTheme());
            }
        } catch (Throwable ignored) {
        }
        return UiTweaks.isDarkMode(activity) ? 0xFFFFFFFF : 0xFF000000;
    }

    private static Object findOfType(Object root, String simpleName, int depth) {
        if (root == null || depth < 0) {
            return null;
        }
        if (root.getClass().getSimpleName().equals(simpleName)) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                Object found = findOfType(group.getChildAt(i), simpleName, depth - 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private static int dp(Activity activity, float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
