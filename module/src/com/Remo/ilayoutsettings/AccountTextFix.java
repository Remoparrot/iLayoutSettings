package com.Remo.ilayoutsettings;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Last line of defence for the account subtitle: whenever any TextView is about
 * to display MIUI's original wording it is replaced with the iOS one, no matter
 * which code path (bind, async refresh, recycling) produced it.
 */
final class AccountTextFix {

    private static final String IOS_TEXT = "Apple账户、iCloud+等";

    private AccountTextFix() {
    }

    static void install(ClassLoader cl) {
        try {
            Class<?> textView = XposedHelpers.findClass("android.widget.TextView", cl);
            XposedBridge.hookAllMethods(textView, "setText", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 0 || !(param.args[0] instanceof CharSequence)) {
                            return;
                        }
                        String value = param.args[0].toString();
                        if (value.contains("云服务") || value.contains("会员权益")) {
                            param.args[0] = IOS_TEXT;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XLog.i("hooked TextView.setText (account subtitle)");
        } catch (Throwable t) {
            XLog.e("account text hook failed", t);
        }
    }
}
