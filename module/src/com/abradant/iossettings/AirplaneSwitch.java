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
    private static final String GLASS_TAG = "ios_airplane_glass";
    private static final String GLASS_SPACER_TAG = "ios_airplane_glass_spacer";

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
            removeGlassToggle(itemView);
            frame.setVisibility(View.VISIBLE);
            // Recycled airplane rows keep the hidden arrow; put it back for rows
            // that normally show one.
            restoreArrow(header, holder, itemView, context);
            return;
        }
        frame.setVisibility(View.VISIBLE);
        removeGlassToggle(itemView);
        applyPlaneIcon(context, itemView);
        frame.setMinimumWidth(0);
        frame.setPadding(0, 0, 0, 0);
        if (frame instanceof android.widget.LinearLayout) {
            // The frame still holds MIUI's text_right TextView. With baseline
            // alignment on, the horizontal LinearLayout shifts the switch down
            // until its baseline matches that label - the "sunken" switch.
            ((android.widget.LinearLayout) frame).setBaselineAligned(false);
        }
        // HyperiOS may dress the row in its own glass container; the switch is
        // allowed to leave the widget_frame bounds so the visual centre can be
        // corrected without being clipped.
        frame.setClipChildren(false);
        frame.setClipToPadding(false);
        if (itemView instanceof ViewGroup) {
            ((ViewGroup) itemView).setClipChildren(false);
        }
        if (frame instanceof android.widget.LinearLayout) {
            ((android.widget.LinearLayout) frame).setGravity(
                    android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END);
        }
        // Native switch rows have no chevron; hide it so the switch sits at the
        // same right edge as every other switch in Settings.
        hideArrow(holder, itemView, context);
        // Keep MIUI's own SlidingSwitch. HyperiOS draws its liquid-glass toggle
        // *for* that switch, so replacing it with a module-drawn toggle (the
        // 15.0 GlassToggle) loses the glass look and the knob geometry. What we
        // fix instead is the position: recenter() slides the widget_frame that
        // holds it, so the painted toggle lands on the title's visual centre.
        // No custom drawing is involved.
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
            watchPosition(itemView, existing);
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
        ViewGroup.LayoutParams toggleParams;
        if (frame instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END;
            toggleParams = lp;
        } else if (frame instanceof android.widget.RelativeLayout) {
            android.widget.RelativeLayout.LayoutParams lp =
                    new android.widget.RelativeLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_END);
            toggleParams = lp;
        } else {
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.END;
            toggleParams = lp;
        }
        frame.addView(toggle, toggleParams);
        setChecked(toggle, Airplane.isOn(context));
        wire(toggle, context);
        watchPosition(itemView, toggle);
        XLog.i("airplane switch attached to 飞行模式 row");
    }

    /**
     * Keeps the native switch centred on the title instead of replacing it.
     *
     * <p>HyperiOS changes the widget frame's padding and re-binds the switch
     * after MIUI's own bind, so one measurement is not enough: re-assert on
     * every layout pass plus a few posted passes (first layout, HyperiOS bind,
     * theme change).</p>
     */
    private static void watchPosition(final View itemView, final View switchView) {
        switchView.setTag(TAG);
        itemView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                recenter(itemView, switchView);
            }
        });
        final Runnable pass = new Runnable() {
            @Override
            public void run() {
                recenter(itemView, switchView);
            }
        };
        itemView.post(pass);
        itemView.postDelayed(pass, 120);
        itemView.postDelayed(pass, 450);
        itemView.postDelayed(pass, 900);
        itemView.postDelayed(pass, 1500);
    }

    /**
     * Removes the module-drawn toggle that 15.0 put into the row. The view is
     * only still there when the module reloads into a running Settings process;
     * a fresh process never creates it. Removing it restores MIUI's own switch.
     */
    private static void removeGlassToggle(View itemView) {
        if (!(itemView instanceof ViewGroup)) {
            return;
        }
        ViewGroup row = (ViewGroup) itemView;
        View glass = row.findViewWithTag(GLASS_TAG);
        if (glass != null) {
            row.removeView(glass);
        }
        View spacer = row.findViewWithTag(GLASS_SPACER_TAG);
        if (spacer != null) {
            row.removeView(spacer);
        }
    }

    private static void recenter(View itemView, View switchView) {
        try {
            ViewGroup frame = frameOf(itemView, switchView);
            if (frame == null) {
                return;
            }
            if (normalizeFrame(frame)) {
                // HyperiOS pads the widget frame after MIUI's bind; centring the
                // child inside a padded frame still lands ~10dp too low, so put
                // the frame back to zero padding and lay out again.
                final View item = itemView;
                final View sw = switchView;
                frame.post(new Runnable() {
                    @Override
                    public void run() {
                        recenter(item, sw);
                    }
                });
                return;
            }
            java.util.List<View> toggles = toggleChildren(frame);
            if (toggles.isEmpty()) {
                return;
            }
            int[] frameLoc = new int[2];
            int[] rowLoc = new int[2];
            frame.getLocationOnScreen(frameLoc);
            itemView.getLocationOnScreen(rowLoc);
            if (frameLoc[1] < 0 || rowLoc[1] < 0) {
                return;
            }
            View anchor = toggles.get(0);
            for (int i = 0; i < toggles.size(); i++) {
                View toggle = toggles.get(i);
                toggle.setTranslationY(0f);
                if (toggle.getHeight() > 0) {
                    anchor = toggle;
                }
            }

            int swCenterY = frameLoc[1] + anchor.getTop() + anchor.getHeight() / 2;
            // Anchor on the title instead of the row: HyperiOS's glass hook
            // changes the row's padding after MIUI binds it, and the row box
            // then no longer matches where the text/icon actually sit.
            View title = itemView.findViewById(android.R.id.title);
            int targetCenterY = rowLoc[1] + itemView.getHeight() / 2;
            if (title != null && title.getHeight() > 0) {
                int[] titleLoc = new int[2];
                title.getLocationOnScreen(titleLoc);
                int titleCenter = titleLoc[1] + title.getHeight() / 2;
                // A recycled row can still report the previous item's title
                // position for one pass; only trust it inside this row.
                if (titleCenter >= rowLoc[1] && titleCenter <= rowLoc[1] + itemView.getHeight()) {
                    targetCenterY = titleCenter;
                }
            }

            int dy = targetCenterY - swCenterY;
            int sane = dp(anchor.getContext(), 16);
            if (Math.abs(dy) > sane
                    || Math.abs(frame.getTranslationY() + dy) > sane) {
                XLog.w("airplane switch geometry unstable: dy=" + dy + " (frame="
                        + frameLoc[1] + ", switchTop=" + anchor.getTop()
                        + ", target=" + targetCenterY + ", translation="
                        + frame.getTranslationY() + ")");
                return;
            }
            // Move the whole widget_frame, not the switch inside it. HyperiOS
            // replaces SlidingSwitch.onDraw with its own painter and caches the
            // box it first saw, so moving that box (or its translation) leaves
            // the painted toggle behind; the frame's transform carries any
            // painting done inside it along.
            if (Math.abs(dy) > dp(anchor.getContext(), 1)) {
                frame.setTranslationY(frame.getTranslationY() + dy);
                XLog.i("airplane switch recentered by " + dy + " (frame translation)"
                        + " titleCenterY=" + targetCenterY
                        + " switchCenterY=" + swCenterY
                        + " frame=" + frameLoc[1] + ".." + (frameLoc[1] + frame.getHeight())
                        + " translation=" + frame.getTranslationY());
            }
            if (!geometryLogged) {
                geometryLogged = true;
                StringBuilder kids = new StringBuilder();
                for (int i = 0; i < frame.getChildCount(); i++) {
                    View child = frame.getChildAt(i);
                    kids.append(" [").append(i).append("] ")
                            .append(child.getClass().getSimpleName())
                            .append(' ').append(child.getWidth()).append('x')
                            .append(child.getHeight())
                            .append(" top=").append(child.getTop())
                            .append(" vis=").append(child.getVisibility());
                }
                XLog.i("airplane geometry: frame=" + frame.getWidth() + "x" + frame.getHeight()
                        + " pad=" + frame.getPaddingTop()
                        + " titleCenterY=" + targetCenterY
                        + " switchCenterY=" + swCenterY + " kids:" + kids);
            }
        } catch (Throwable t) {
            XLog.w("airplane switch recenter failed: " + t);
        }
    }

    /** The widget frame that holds the switch, even after HyperiOS swaps views. */
    private static ViewGroup frameOf(View itemView, View fallback) {
        if (fallback != null && fallback.getParent() instanceof ViewGroup) {
            return (ViewGroup) fallback.getParent();
        }
        View frame = itemView.findViewById(android.R.id.widget_frame);
        return frame instanceof ViewGroup ? (ViewGroup) frame : null;
    }

    /** Every child of the frame that can be the visible toggle. */
    private static java.util.List<View> toggleChildren(ViewGroup frame) {
        java.util.List<View> out = new java.util.ArrayList<>();
        for (int i = 0; i < frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            boolean toggle = child instanceof android.widget.Checkable
                    || child instanceof android.widget.CompoundButton
                    || child.getClass().getSimpleName().contains("Switch")
                    || child.getClass().getSimpleName().contains("Toggle");
            if (toggle && child.getVisibility() != View.GONE) {
                out.add(child);
            }
        }
        return out;
    }

    private static boolean geometryLogged;

    /** Clears padding HyperiOS adds to the widget frame. */
    private static boolean normalizeFrame(ViewGroup frame) {
        if (frame == null) {
            return false;
        }
        if (frame.getPaddingLeft() == 0 && frame.getPaddingTop() == 0
                && frame.getPaddingRight() == 0 && frame.getPaddingBottom() == 0) {
            return false;
        }
        frame.setPadding(0, 0, 0, 0);
        frame.requestLayout();
        return true;
    }

    /** Ask the layout itself to centre the switch vertically (HyperiOS-safe). */
    private static void centerInParent(View switchView) {
        try {
            Object parent = switchView.getParent();
            ViewGroup.LayoutParams params = switchView.getLayoutParams();
            if (parent instanceof android.widget.FrameLayout
                    && params instanceof android.widget.FrameLayout.LayoutParams) {
                android.widget.FrameLayout.LayoutParams lp =
                        (android.widget.FrameLayout.LayoutParams) params;
                if (lp.gravity != android.view.Gravity.CENTER_VERTICAL) {
                    lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                    switchView.setLayoutParams(lp);
                }
            } else if (parent instanceof android.widget.RelativeLayout
                    && params instanceof android.widget.RelativeLayout.LayoutParams) {
                android.widget.RelativeLayout.LayoutParams lp =
                        (android.widget.RelativeLayout.LayoutParams) params;
                if (lp.getRules()[android.widget.RelativeLayout.CENTER_VERTICAL]
                        != android.widget.RelativeLayout.TRUE) {
                    lp.addRule(android.widget.RelativeLayout.CENTER_VERTICAL);
                    switchView.setLayoutParams(lp);
                }
            } else if (parent instanceof android.widget.LinearLayout
                    && params instanceof android.widget.LinearLayout.LayoutParams) {
                android.widget.LinearLayout.LayoutParams lp =
                        (android.widget.LinearLayout.LayoutParams) params;
                android.widget.LinearLayout linear = (android.widget.LinearLayout) parent;
                boolean changed = false;
                if (lp.gravity != android.view.Gravity.CENTER_VERTICAL) {
                    lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                    changed = true;
                }
                if (linear.isBaselineAligned()) {
                    linear.setBaselineAligned(false);
                    changed = true;
                }
                if (changed) {
                    switchView.setLayoutParams(lp);
                }
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
