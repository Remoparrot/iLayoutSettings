package com.Remo.ilayoutsettings;

import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Installs the hooks inside the Settings process:
 *
 * <ul>
 *   <li>{@code SettingsFragment.buildAdapter} → reorder the native header list
 *       (and regroup it) right after MIUI built it.</li>
 *   <li>{@code SettingsFragment.onResume} → re-apply after MIUI refreshed the
 *       headers, and keep the bottom search bar alive.</li>
 *   <li>{@code PreferenceFragmentCompat.onCreate} → add icons to second-level
 *       preference rows.</li>
 * </ul>
 */
final class Hook {

    private static boolean installed;

    private Hook() {
    }

    static void install(ClassLoader cl) {
        if (installed) {
            return;
        }
        installed = true;

        Class<?> fragment = XposedHelpers.findClassIfExists(
                "com.android.settings.SettingsFragment", cl);
        if (fragment == null) {
            XLog.e("com.android.settings.SettingsFragment not found", null);
            return;
        }

        XposedBridge.hookAllMethods(fragment, "buildAdapter", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object self = param.thisObject;
                    HeaderData.apply(self);
                    BottomSearch.install(self);
                } catch (Throwable t) {
                    XLog.e("after buildAdapter", t);
                }
            }
        });

        XposedBridge.hookAllMethods(fragment, "onViewCreated", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    // mAnchorView (the native search bar) is assigned inside
                    // SettingsFragment.onViewCreated, so install afterwards.
                    BottomSearch.install(param.thisObject);
                    ForceBlur.forceHeaderBlur(param.thisObject);
                    UiTweaks.refreshTopBar(param.thisObject);
                    // Watch audio outputs so a headset connected while this page
                    // is open adds the conditional card without a restart.
                    HeadsetWatcher.attach(param.thisObject);
                } catch (Throwable t) {
                    XLog.e("after onViewCreated", t);
                }
            }
        });

        XposedBridge.hookAllMethods(fragment, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Object self = param.thisObject;
                    HeaderData.apply(self);
                    BottomSearch.install(self);
                    HeadsetWatcher.attach(self);
                    // After a uiMode change MIUI may reuse the fragment view; make
                    // sure the hairline picks up the new theme straight away.
                    UiTweaks.refreshTopBar(self);
                } catch (Throwable t) {
                    XLog.e("after onResume", t);
                }
            }
        });

        XposedBridge.hookAllMethods(fragment, "onPause", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    HeadsetWatcher.detach(param.thisObject);
                } catch (Throwable t) {
                    XLog.e("after onPause", t);
                }
            }
        });

        XposedBridge.hookAllMethods(fragment, "onDestroy", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    HeadsetWatcher.detach(param.thisObject);
                } catch (Throwable t) {
                    XLog.e("after onDestroy", t);
                }
            }
        });

        SubPageIcons.install(cl);
        AirplaneSwitch.install(cl);
        // Badge on the update card + guaranteed AirPods glyph on the headset card.
        HeaderOverlay.install(cl);
        // Last-row divider removal (home). Position is resolved by title so the
        // list's header views can no longer shift it onto a normal row.
        CardFix.install(cl);
        // Sub-page rows: position is resolved from the preference's parent, so
        // it is not affected by the adapter header views.
        SubPageCards.install(cl);
        WirelessPage.install(cl);
        AccountTextFix.install(cl);
        AppearanceTweak.install(cl);
        // Title restyling removed: sub pages keep MIUI's native title behaviour.
        ForceBlur.install(cl);
        // 16.7: second-level pages get the same translucent + blurred top bar
        // as the home page (the action bar there never asked for the blur).
        SubPageTopBar.install(cl);
        // Top-bar overlays (blur / opacity / hairline) are driven from the
        // NestedHeaderLayout class so they survive uiMode recreation.
        UiTweaks.installTopBar(cl);

        try {
            // sub-page title hooks removed (no more forced titles)
            XLog.i("hooked AppCompatActivity (sub-page titles disabled)");
        } catch (Throwable t) {
            XLog.w("cannot hook AppCompatActivity.onCreate: " + t);
        }

        XLog.i("hooks installed");
    }
}
