package com.Remo.ilayoutsettings;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed entry point (legacy Xposed API 82, works on LSPosed 1.x / 2.x). */
public final class Module implements IXposedHookLoadPackage {

    private static final String SETTINGS = "com.android.settings";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SETTINGS.equals(lpparam.packageName)) {
            return;
        }
        if (lpparam.processName != null && !SETTINGS.equals(lpparam.processName)) {
            return; // ignore secondary processes
        }
        try {
            XLog.i("loaded in " + lpparam.processName);
            Hook.install(lpparam.classLoader);
        } catch (Throwable t) {
            XLog.e("install failed", t);
        }
    }
}
