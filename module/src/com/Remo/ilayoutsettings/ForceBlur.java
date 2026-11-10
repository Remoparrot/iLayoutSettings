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
        boolean supported = readSupport(material);
        XLog.i("hyper material supported=" + supported);
        if (!supported) {
            XLog.w("device does not support hyper material, keep blur as-is");
            return;
        }
        setStaticBoolean(material, "isForceEnable", true);
        forceTrue(material, "isEnable");
        forceTrue(material, "isFeatureEnable");
        if (blur != null) {
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
                        Object target = HeaderData.field(param.thisObject, "mViewApplyBlur");
                        if (!(target instanceof View)) {
                            return;
                        }
                        View view = (View) target;
                        Object parent = view.getParent();
                        // Only the top bar: other blur helpers (search bar, bottom
                        // sheets) keep their own radius.
                        if (parent == null || !parent.getClass().getName()
                                .endsWith("NestedHeaderLayout")) {
                            return;
                        }
                        param.args[2] = Prefs.topBarBlurRadius(view.getContext());
                    } catch (Throwable t) {
                        XLog.w("header blur radius hook failed: " + t);
                    }
                }
            });
            XLog.i("hooked MiuiBlurUiHelper.setBlurParams (header radius)");
        } catch (Throwable t) {
            XLog.e("header blur radius hook failed", t);
        }
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
            call(layout, "setOverlayMode", true);
            XLog.i("nested header blur forced on");
        } catch (Throwable t) {
            XLog.e("force header blur failed", t);
        }
    }

    private static boolean readSupport(Class<?> material) {
        try {
            Field f = material.getDeclaredField("SUPPORT_HYPER_MATERIAL");
            f.setAccessible(true);
            Object v = f.get(null);
            return Boolean.TRUE.equals(v);
        } catch (Throwable t) {
            return false;
        }
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
