package com.Remo.ilayoutsettings;

import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Keeps the small sub-page title always visible: MIUI hides the CollapseTitle
 * again as soon as the page scrolls back to the top, which makes the title
 * depend on the scroll position. Views tagged by {@link UiTweaks} are forced
 * visible.
 */
final class TitleFix {

    static final Object TAG = new Object();

    private TitleFix() {
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> collapse = XposedHelpers.findClassIfExists(
                    "miuix.appcompat.app.CollapseTitle", cl);
            if (collapse == null) {
                XLog.w("CollapseTitle not found");
                return;
            }
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!(param.thisObject instanceof View)) {
                            return;
                        }
                        View view = (View) param.thisObject;
                        if (view.getTag() != TAG) {
                            return;
                        }
                        boolean hide = false;
                        if (param.args.length == 1 && param.args[0] instanceof Integer) {
                            hide = (Integer) param.args[0] != View.VISIBLE;
                        } else if (param.args.length == 1 && param.args[0] instanceof Boolean) {
                            hide = !(Boolean) param.args[0];
                        }
                        if (hide) {
                            view.setVisibility(View.VISIBLE);
                            view.setAlpha(1f);
                            param.setResult(null);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            };
            XposedBridge.hookAllMethods(collapse, "setVisibility", hook);
            XposedBridge.hookAllMethods(collapse, "setVisible", hook);
            XLog.i("hooked CollapseTitle visibility (always visible)");
        } catch (Throwable t) {
            XLog.e("title fix hook failed", t);
        }
    }
}
