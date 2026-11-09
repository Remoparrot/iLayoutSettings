package com.abradant.iossettings;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Puts a native MIUI switch into the "控制中心" header row.
 *
 * <p>The switch toggles airplane mode, while the rest of the row keeps MIUI's
 * own click behaviour (opening the Control Center / 更多连接 page).</p>
 */
final class AirplaneSwitch {

    private static final String TAG = "ios_airplane_switch";

    private AirplaneSwitch() {
    }

    static void install(ClassLoader cl) {
        Class<?> holder = XposedHelpers.findClassIfExists(
                "com.android.settings.MiuiSettings$HeaderViewHolder", cl);
        Class<?> adapter = XposedHelpers.findClassIfExists(
                "com.android.settings.MiuiSettings$HeaderAdapter", cl);
        if (holder == null || adapter == null) {
            XLog.e("HeaderAdapter / HeaderViewHolder not found", null);
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(adapter, "onBindViewHolder", holder, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                inject(param.thisObject, param.args[0], (Integer) param.args[1]);
                            } catch (Throwable t) {
                                XLog.e("airplane switch bind", t);
                            }
                        }
                    });
            XLog.i("hooked HeaderAdapter.onBindViewHolder (airplane switch)");
        } catch (Throwable t) {
            XLog.e("hook HeaderAdapter failed", t);
        }
    }

    private static void inject(Object adapter, Object holder, int position) {
        Object listObject = HeaderData.field(adapter, "mHeaders");
        if (!(listObject instanceof List)) {
            return;
        }
        List<?> headers = (List<?>) listObject;
        if (position < 0 || position >= headers.size()) {
            return;
        }
        Object header = headers.get(position);
        View itemView = (View) HeaderData.field(holder, "itemView");
        if (header == null || itemView == null) {
            return;
        }
        ViewGroup frame = itemView.findViewById(android.R.id.widget_frame);
        UiTweaks.tweakHeaderRow(header, itemView);
        if (frame == null) {
            return;
        }
        Context context = itemView.getContext();
        boolean controlCenter = isAirplaneRow(header, context);
        View existing = frame.findViewWithTag(TAG);
        if (!controlCenter) {
            if (existing != null) {
                frame.removeView(existing);
            }
            // Recycled airplane rows keep the hidden arrow; put it back for rows
            // that normally show one.
            restoreArrow(header, holder, itemView, context);
            return;
        }
        frame.setVisibility(View.VISIBLE);
        applyPlaneIcon(context, itemView);
        frame.setMinimumWidth(0);
        frame.setPadding(0, 0, 0, 0);
        if (frame instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) frame).setGravity(
                    android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        }
        // Native switch rows have no chevron; hide it so the switch sits at the
        // same right edge as every other switch in Settings.
        hideArrow(holder, itemView, context);
        // Tapping the row itself also toggles airplane mode.
        final ViewGroup frameRef = frame;
        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleAirplane(context, frameRef);
            }
        });
        if (existing != null) {
            setChecked(existing, Airplane.isOn(context));
            final View reused = existing;
            itemView.post(new Runnable() {
                @Override
                public void run() {
                    recenter(itemView, reused);
                }
            });
            return;
        }
        View toggle = createSwitch(context);
        if (toggle == null) {
            XLog.w("could not create a switch view");
            return;
        }
        toggle.setTag(TAG);
        toggle.setClickable(true);
        toggle.setFocusable(true);
        toggle.setEnabled(true);
        frame.addView(toggle, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setChecked(toggle, Airplane.isOn(context));
        wire(toggle, context);
        final View switchView = toggle;
        // HyperiOS changes the row's padding after MIUI's bind; keep re-centering
        // the switch on every layout pass instead of doing it only once.
        itemView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                recenter(itemView, switchView);
            }
        });
        itemView.post(new Runnable() {
            @Override
            public void run() {
                recenter(itemView, switchView);
            }
        });
        itemView.postDelayed(new Runnable() {
            @Override
            public void run() {
                recenter(itemView, switchView);
            }
        }, 120);
        XLog.i("airplane switch attached to 飞行模式 row");
    }

    private static void recenter(View itemView, View switchView) {
        try {
            centerInParent(switchView);
            int[] swLoc = new int[2];
            int[] rowLoc = new int[2];
            switchView.getLocationOnScreen(swLoc);
            itemView.getLocationOnScreen(rowLoc);
            int rowRight = rowLoc[0] + itemView.getWidth();
            int swRight = swLoc[0] + switchView.getWidth();
            int rowCenterY = rowLoc[1] + itemView.getHeight() / 2;
            int swCenterY = swLoc[1] + switchView.getHeight() / 2;
            int dy = swCenterY - rowCenterY;
            if (Math.abs(dy) > dp(switchView.getContext(), 1)) {
                switchView.setTranslationY(switchView.getTranslationY() - dy);
                XLog.i("airplane switch recentered by " + (-dy)
                        + " (gap=" + (rowRight - swRight) + ", sw="
                        + switchView.getWidth() + "x" + switchView.getHeight() + ")");
            }
        } catch (Throwable t) {
            XLog.w("airplane switch recenter failed: " + t);
        }
    }

    /** Ask the layout itself to centre the switch vertically (HyperiOS-safe). */
    private static void centerInParent(View switchView) {
        try {
            Object parent = switchView.getParent();
            ViewGroup.LayoutParams params = switchView.getLayoutParams();
            if (parent instanceof android.widget.FrameLayout
                    && params instanceof android.widget.FrameLayout.LayoutParams) {
                ((android.widget.FrameLayout.LayoutParams) params).gravity =
                        android.view.Gravity.CENTER_VERTICAL;
                switchView.setLayoutParams(params);
            } else if (parent instanceof android.widget.RelativeLayout
                    && params instanceof android.widget.RelativeLayout.LayoutParams) {
                ((android.widget.RelativeLayout.LayoutParams) params)
                        .addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
                switchView.setLayoutParams(params);
            } else if (parent instanceof android.widget.LinearLayout
                    && params instanceof android.widget.LinearLayout.LayoutParams) {
                ((android.widget.LinearLayout.LayoutParams) params).gravity =
                        android.view.Gravity.CENTER_VERTICAL;
                switchView.setLayoutParams(params);
            }
        } catch (Throwable ignored) {
        }
    }

    private static ViewGroup.LayoutParams switchLayoutParams(Context context, ViewGroup parent) {
        int wrap = ViewGroup.LayoutParams.WRAP_CONTENT;
        int end = dp(context, 14);
        if (parent instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(wrap, wrap);
            lp.rightMargin = end;
            return lp;
        }
        if (parent instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(wrap, wrap);
            lp.rightMargin = end;
            return lp;
        }
        return new ViewGroup.LayoutParams(wrap, wrap);
    }

    private static void toggleAirplane(Context context, ViewGroup frame) {
        boolean next = !Airplane.isOn(context);
        Airplane.set(context, next);
        View sw = frame.findViewWithTag(TAG);
        if (sw != null) {
            setChecked(sw, next);
        }
        XLog.i("airplane row clicked -> " + next);
    }

    /** Use the light / dark airplane artwork bundled in the module resources. */
    private static void applyPlaneIcon(final Context context, final View itemView) {
        try {
            final View icon = itemView.findViewById(android.R.id.icon);
            if (!(icon instanceof android.widget.ImageView)) {
                return;
            }
            android.content.res.Resources modRes = context.getPackageManager()
                    .getResourcesForApplication("com.abradant.iossettings");
            String name = UiTweaks.isDarkMode(context) ? "ic_plane_dark" : "ic_plane_light";
            int id = modRes.getIdentifier(name, "drawable", "com.abradant.iossettings");
            if (id == 0) {
                return;
            }
            ((android.widget.ImageView) icon).setImageDrawable(modRes.getDrawable(id, null));
            ((android.widget.ImageView) icon).setScaleType(
                    android.widget.ImageView.ScaleType.FIT_CENTER);
            // Do not hardcode a size: copy the exact size of a sibling row icon
            // so the plane lands on the same grid as every other icon.
            itemView.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        int size = dp(context, 28);
                        android.view.ViewGroup.LayoutParams lp = icon.getLayoutParams();
                        if (lp != null) {
                            lp.width = size;
                            lp.height = size;
                            icon.setLayoutParams(lp);
                        }
                        XLog.i("airplane icon set: " + name + " size=" + size);
                    } catch (Throwable t) {
                        XLog.w("airplane icon sizing failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.e("airplane icon failed", t);
        }
    }

    private static int siblingIconSize(View itemView) {
        android.view.ViewParent parent = itemView.getParent();
        if (!(parent instanceof ViewGroup)) {
            return 0;
        }
        ViewGroup container = (ViewGroup) parent;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child == itemView) {
                continue;
            }
            View icon = child.findViewById(android.R.id.icon);
            if (icon != null && icon.getWidth() > 0) {
                best = Math.min(best, icon.getWidth());
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static void hideArrow(Object holder, View itemView, Context context) {
        View arrow = arrowView(holder, itemView, context);
        if (arrow != null) {
            arrow.setVisibility(View.GONE);
        } else {
            XLog.w("arrow view not found on 飞行模式 row");
        }
    }

    private static void restoreArrow(Object header, Object holder, View itemView, Context context) {
        Object fragment = HeaderData.field(header, "fragment");
        Object intent = HeaderData.field(header, "intent");
        if (fragment == null && intent == null) {
            return;
        }
        View arrow = arrowView(holder, itemView, context);
        if (arrow != null && arrow.getVisibility() != View.VISIBLE) {
            arrow.setVisibility(View.VISIBLE);
        }
    }

    private static View arrowView(Object holder, View itemView, Context context) {
        Object fromHolder = HeaderData.field(holder, "arrowRight");
        if (fromHolder instanceof View) {
            return (View) fromHolder;
        }
        try {
            int id = context.getResources().getIdentifier(
                    "arrow_right", "id", "com.android.settings");
            if (id != 0) {
                return itemView.findViewById(id);
            }
        } catch (Throwable t) {
            XLog.w("arrow lookup failed: " + t);
        }
        return null;
    }

    private static boolean isAirplaneRow(Object header, Context context) {
        Resources res = context.getResources();
        Object id = HeaderData.field(header, "id");
        long value = id instanceof Number ? ((Number) id).longValue() : -1L;
        if (value > 0 && value <= Integer.MAX_VALUE) {
            try {
                if (res.getResourceEntryName((int) value).contains("airplane")) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        Object title = HeaderData.field(header, "title");
        String text = title == null ? "" : title.toString();
        return text.contains("飞行模式");
    }

    private static View createSwitch(Context context) {
        Context themed = preferenceContext(context);
        try {
            int layoutId = context.getResources().getIdentifier(
                    "miuix_preference_widget_switch", "layout", "com.android.settings");
            if (layoutId != 0) {
                View inflated = android.view.LayoutInflater.from(themed)
                        .inflate(layoutId, null, false);
                if (inflated != null) {
                    XLog.i("switch inflated from miuix_preference_widget_switch");
                    return inflated;
                }
            }
        } catch (Throwable t) {
            XLog.w("inflate switch widget failed: " + t);
        }
        try {
            Class<?> c = Class.forName("miuix.slidingwidget.widget.SlidingSwitch",
                    false, context.getClassLoader());
            try {
                return (View) c.getConstructor(Context.class).newInstance(context);
            } catch (NoSuchMethodException ignored) {
                return (View) c.getConstructor(Context.class,
                        android.util.AttributeSet.class).newInstance(context, null);
            }
        } catch (Throwable t) {
            try {
                return new android.widget.Switch(context);
            } catch (Throwable t2) {
                return null;
            }
        }
    }

    /** ContextThemeWrapper with the same preferenceTheme MIUI uses for switch rows. */
    private static Context preferenceContext(Context context) {
        try {
            int attr = context.getResources().getIdentifier(
                    "preferenceTheme", "attr", "com.android.settings");
            if (attr != 0) {
                android.util.TypedValue tv = new android.util.TypedValue();
                if (context.getTheme().resolveAttribute(attr, tv, true) && tv.resourceId != 0) {
                    return new android.view.ContextThemeWrapper(context, tv.resourceId);
                }
            }
        } catch (Throwable t) {
            XLog.w("preferenceTheme lookup failed: " + t);
        }
        return context;
    }

    private static void setChecked(View view, boolean checked) {
        if (view instanceof GlassToggle) {
            ((GlassToggle) view).setChecked(checked);
            return;
        }
        try {
            Method m = view.getClass().getMethod("setChecked", boolean.class);
            m.setAccessible(true);
            m.invoke(view, checked);
        } catch (Throwable t) {
            XLog.w("setChecked failed: " + t);
        }
    }

    private static void wire(final View view, final Context context) {
        if (view instanceof GlassToggle) {
            ((GlassToggle) view).setOnCheckedChangeListener(
                    new GlassToggle.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(boolean checked) {
                            Airplane.set(context, checked);
                            XLog.i("glass toggle -> airplane " + checked);
                        }
                    });
            return;
        }
        for (Method m : view.getClass().getMethods()) {
            if (!m.getName().equals("setOnCheckedChangeListener") || m.getParameterCount() != 1) {
                continue;
            }
            final Class<?> listenerType = m.getParameterTypes()[0];
            if (!listenerType.isInterface()) {
                continue;
            }
            try {
                Object listener = Proxy.newProxyInstance(
                        view.getClass().getClassLoader(),
                        new Class<?>[]{listenerType},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) {
                                String name = method.getName();
                                if ("onCheckedChanged".equals(name)) {
                                    boolean checked = false;
                                    if (args != null) {
                                        for (Object a : args) {
                                            if (a instanceof Boolean) {
                                                checked = (Boolean) a;
                                                break;
                                            }
                                        }
                                    }
                                    Airplane.set(context, checked);
                                } else if ("hashCode".equals(name)) {
                                    return System.identityHashCode(proxy);
                                } else if ("equals".equals(name)) {
                                    return args != null && args.length == 1 && proxy == args[0];
                                } else if ("toString".equals(name)) {
                                    return "IosAirplaneSwitch";
                                }
                                return null;
                            }
                        });
                m.setAccessible(true);
                m.invoke(view, listener);
                return;
            } catch (Throwable t) {
                XLog.w("switch listener wiring failed: " + t);
            }
        }
        // Fallback: plain click toggles the state manually.
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !Airplane.isOn(context);
                Airplane.set(context, next);
                setChecked(view, next);
            }
        });
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
