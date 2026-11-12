package com.Remo.ilayoutsettings;

import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Forces MIUI's "hyper material" blur on, even when the user/theme turned the
 * background-blur switch off.
 *
 * <p>{@code HyperMaterialUtils.isEnable()} is false while
 * {@code HyperMaterialUtils.isForceEnable} is false, and
 * {@code isFeatureEnable()} reads {@code Settings.Secure.background_blur_enable}
 * (which a theme can reset). Forcing those three entry points makes
 * {@code MiuiBlurUtils} report blur as enabled, so MIUI's own blur surfaces
 * (header / nested header / search) render again.</p>
 */
final class ForceBlur {

    private static boolean installed;

    private ForceBlur() {
    }

    static void install(ClassLoader cl) {
        if (installed) {
            return;
        }
        Class<?> material = XposedHelpers.findClassIfExists(
                "miuix.core.util.HyperMaterialUtils", cl);
        Class<?> blur = XposedHelpers.findClassIfExists(
                "miuix.core.util.MiuiBlurUtils", cl);
        if (material == null) {
            XLog.e("HyperMaterialUtils not found, blur stays as-is", null);
            return;
        }
        installed = true;
        // HyperOS 3 exposed the capability as the static field
        // SUPPORT_HYPER_MATERIAL; HyperOS 4 replaced it with the private
        // static field mIsSupport and the public isSupport() accessor, and
        // dropped the field entirely. Probe every shape, and when the probe
        // is inconclusive keep going instead of bailing out (the old check
        // silently disabled every blur hook on OS4).
        boolean supported = probeSupport(material);
        XLog.i("hyper material supported=" + supported);
        setStaticBoolean(material, "isForceEnable", true);
        setStaticBoolean(material, "SUPPORT_HYPER_MATERIAL", true);
        setStaticBoolean(material, "mIsSupport", true);
        forceTrue(material, "isSupport");
        forceTrue(material, "isEnable");
        forceTrue(material, "isFeatureEnable");
        forceTrue(material, "isDefaultFeatureEnable");
        // HyperOS 4: MiuiBlurUiHelper.setEnableBlur() only turns the real blur
        // on when hasMaterialPermission(view, package, 2) returns true, so the
        // header stayed flat even with every other switch forced.
        forceTrue(material, "hasMaterialPermission");
        if (blur != null) {
            forceTrue(blur, "isEnable");
            forceTrue(blur, "isEffectEnable");
            // The action bar's own background blur was not what the header shows:
            // NestedHeaderLayout paints a separate over-background view, and its
            // MiuiBlurUiHelper reads a fixed theme radius (Blur.ExtraHeavy). The
            // old slider therefore changed a layer hidden behind the native blur.
            // Replacing the radius on its way into the native helper makes the
            // slider control the blur the user actually sees.
            hookNestedHeaderRadius(cl);
        }
        XLog.i("forced MIUI material blur on");
    }

    /** Makes {@code topbar_blur} the radius of NestedHeaderLayout's native blur. */
    private static void hookNestedHeaderRadius(ClassLoader cl) {
        Class<?> helper = XposedHelpers.findClassIfExists("miuix.view.MiuiBlurUiHelper", cl);
        if (helper == null) {
            XLog.w("MiuiBlurUiHelper not found, native header radius stays fixed");
            return;
        }
        try {
            XposedBridge.hookAllMethods(helper, "setBlurParams", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length != 3) {
                            return;
                        }
                        View view = headerView(param.thisObject);
                        if (view == null) {
                            return;
                        }
                        param.args[2] = Prefs.topBarBlurRadius(view.getContext());
                    } catch (Throwable t) {
                        XLog.w("header blur radius hook failed: " + t);
                    }
                }
            });
            // setBlurParams only writes the field; refreshBlur() is what hands the
            // radius to the native blur node. Priming mBlurEffect here means the
            // very first condensed frame is already blurred - previously the field
            // was still the theme radius (or the 0 written at startup) until the
            // UiTweaks poll happened to run.
            XposedBridge.hookAllMethods(helper, "refreshBlur", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        View view = headerView(param.thisObject);
                        if (view == null) {
                            return;
                        }
                        int radius = Prefs.topBarBlurRadius(view.getContext());
                        // HyperOS 3 kept the radius in mBlurEffect; HyperOS 4
                        // renamed it to mBlurRadius and reads that field in
                        // refreshBlur(). Write both, ignore the missing one.
                        HeaderData.setIntField(param.thisObject, "mBlurEffect", radius);
                        HeaderData.setIntField(param.thisObject, "mBlurRadius", radius);
                    } catch (Throwable t) {
                        XLog.w("header blur refresh hook failed: " + t);
                    }
                }
            });
            XLog.i("hooked MiuiBlurUiHelper (header radius + refreshBlur)");
        } catch (Throwable t) {
            XLog.e("header blur radius hook failed", t);
        }
    }

    /** The view a helper blurs, when that helper belongs to the top bar. */
    private static View headerView(Object helper) {
        Object target = HeaderData.field(helper, "mViewApplyBlur");
        if (!(target instanceof View)) {
            return null;
        }
        View view = (View) target;
        Object parent = view.getParent();
        // Only the top bar: other blur helpers (search bar, bottom sheets)
        // keep their own radius.
        if (parent == null || !parent.getClass().getName().endsWith("NestedHeaderLayout")) {
            return null;
        }
        return view;
    }

    /** Turns on NestedHeaderLayout blur/overlay for the home fragment. */
    static void forceHeaderBlur(Object fragment) {
        try {
            Object layout = HeaderData.field(fragment, "mNestedHeaderLayout");
            if (layout == null) {
                return;
            }
            call(layout, "setSupportBlur", true);
            call(layout, "setEnableBlur", true);
            // OS4 only records mExpectedEnable in setEnableBlur(); the blur is
            // applied from applyBlur(), which MIUI calls later on its own. Ask
            // for it explicitly so the first condensed frame is already blurred.
            call(layout, "applyBlur", true);
            call(layout, "setOverlayMode", true);
            // OS4's setOverlayMode() only flips a flag; without a fresh layout
            // the list keeps its non-overlay position (content never reaches the
            // bar, so the bar has nothing to blur).
            if (layout instanceof View) {
                View view = (View) layout;
                view.requestLayout();
                view.invalidate();
            }
            XLog.i("nested header blur forced on");
        } catch (Throwable t) {
            XLog.e("force header blur failed", t);
        }
    }

    /**
     * Best-effort "does this ROM support hyper material" probe. OS3: static
     * field SUPPORT_HYPER_MATERIAL. OS4: private static mIsSupport + public
     * isSupport(). Returns true when nothing can be read so the caller still
     * installs the hooks — a wrong "false" here silently turns every blur
     * effect off (the OS4 regression).
     */
    private static boolean probeSupport(Class<?> material) {
        for (String name : new String[]{"SUPPORT_HYPER_MATERIAL", "mIsSupport"}) {
            try {
                Field f = material.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof Boolean) {
                    return (Boolean) v;
                }
            } catch (Throwable ignored) {
                // try the next shape
            }
        }
        try {
            Method m = material.getDeclaredMethod("isSupport");
            m.setAccessible(true);
            Object v = m.invoke(null);
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
        } catch (Throwable ignored) {
            // OS3 only
        }
        XLog.i("hyper material support probe inconclusive, forcing hooks anyway");
        return true;
    }

    private static void setStaticBoolean(Class<?> cls, String name, boolean value) {
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(null, value);
        } catch (Throwable t) {
            XLog.w("cannot set " + cls.getSimpleName() + "." + name + ": " + t);
        }
    }

    private static void forceTrue(Class<?> cls, String methodName) {
        try {
            XposedBridge.hookAllMethods(cls, methodName, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    return Boolean.TRUE;
                }
            });
        } catch (Throwable t) {
            XLog.w("cannot force " + methodName + ": " + t);
        }
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
                Class<?>[] types = m.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < types.length; i++) {
                    if (types[i].isPrimitive()) {
                        ok = args[i] != null && box(types[i]).isInstance(args[i]);
                    } else {
                        ok = args[i] == null || types[i].isInstance(args[i]);
                    }
                    if (!ok) {
                        break;
                    }
                }
                if (!ok) {
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
