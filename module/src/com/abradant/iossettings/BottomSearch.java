package com.abradant.iossettings;

import android.app.Activity;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Moves the native Settings search bar to the bottom of the home page.
 *
 * <p>The original {@code header_view} is hidden and kept as the click target.
 * A native {@code miuix_appcompat_search_stub} is inflated into a floating
 * bottom bar (with a simple fallback pill if MIUI's stub cannot be inflated);
 * tapping it performs the original anchor's click, which starts MIUI's own
 * search action mode.</p>
 */
final class BottomSearch {

    private static final String TAG = "ios_bottom_search";
    private static final java.util.Map<View, Integer> BASE_BOTTOM =
            new java.util.WeakHashMap<>();
    private static final java.util.Set<ViewGroup> WATCHED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<ViewGroup, Boolean>());
    private static long suppressShowUntil;
    private static int lastNightMode = -1;

    private static int nightMode(android.content.Context context) {
        return context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
    }

    private BottomSearch() {
    }

    static void install(Object fragment) {
        try {
            Activity activity = activity(fragment);
            if (activity == null || activity.isFinishing()) {
                return;
            }
            View anchor = (View) HeaderData.field(fragment, "mAnchorView");
            if (anchor == null) {
                return;
            }
            ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
            if (content == null) {
                return;
            }
            final int night = nightMode(activity);
            View existing = content.findViewWithTag(TAG);
            if (existing != null) {
                // Always drop and rebuild: after a theme/configuration change the
                // old glass carries a stale mask, colours and click listeners.
                content.removeView(existing);
            }
            lastNightMode = night;

            String hint = "搜索";
            int hintId = activity.getResources().getIdentifier(
                    "search_input_hint", "string", "com.android.settings");
            if (hintId != 0) {
                hint = activity.getString(hintId);
            }

            final int height = dp(activity, 50);
            final SearchHost wrap = new SearchHost(activity);
            wrap.setTag(TAG);
            wrap.setClickable(true);
            wrap.setFocusable(true);
            wrap.setElevation(dp(activity, 40));

            View bar = inflateNativeStub(activity, content, hint);
            boolean nativeStub = bar != null;
            if (!nativeStub) {
                bar = fallbackBar(activity, hint);
                XLog.w("native search stub unavailable, using fallback pill");
            }

            // The glass must sit *above* the native capsule fill and *below*
            // the icon/text, otherwise the fill always covers it. Painting it
            // behind the whole stub (15.8) hid it; clearing the fills removed
            // the bar (15.9); alpha-ing them stayed opaque (15.10). So embed
            // the glass into the stub's FrameLayout at index 0.
            // Ported verbatim from lattice-3.72 (dev.settingslab.LiquidGlassView,
            // assembled from smali); falls back to our own SearchGlass.
            // Reverted to our own SearchGlass (kyant refraction shader): the
            // lattice port produced ghosted text and missing icons on device.
            final SearchGlass glass = new SearchGlass(activity);
            glass.setMaskView(null);
            glass.setCaptureHost(wrap);
            boolean embedded = false;
            if (nativeStub) {
                try {
                    int stubId = activity.getResources().getIdentifier(
                            "search_mode_stub", "id", "com.android.settings");
                    View stub = stubId == 0 ? null : bar.findViewById(stubId);
                    if (stub instanceof android.widget.FrameLayout) {
                        ((android.widget.FrameLayout) stub).addView(glass, 0,
                                new android.widget.FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT));
                        embedded = true;
                        XLog.i("glass embedded in native stub");
                    }
                } catch (Throwable t) {
                    XLog.w("embed glass failed: " + t);
                }
            }
            if (!embedded) {
                // Fallback pill: our own GradientDrawable, safe to drop so the
                // glass behind the label provides the capsule.
                bar.setBackground(null);
                wrap.addView(glass, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            // Styling must never abort install(): an aborted install means
            // "no search bar at all" (exactly what happened in 15.9).
            try {
                disableChildTouches(bar);
                tintSearchText(bar);
            } catch (Throwable t) {
                XLog.w("search bar text styling skipped: " + t);
            }
            final View.OnClickListener openSearch = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    XLog.i("bottom search clicked");
                    suppressShowUntil = System.currentTimeMillis() + 600;
                    boolean handled = false;
                    try {
                        handled = anchor.performClick();
                        XLog.i("bottom search performClick -> " + handled);
                    } catch (Throwable t) {
                        XLog.e("bottom search click", t);
                    }
                    if (!handled) {
                        startSearch(fragment, activity);
                    }
                    wrap.setVisibility(View.GONE);
                }
            };
            bar.setClickable(true);
            bar.setOnClickListener(openSearch);

            anchor.setVisibility(View.GONE);
            wrap.setOnClickListener(openSearch);
            wrap.addView(bar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
            lp.leftMargin = dp(activity, 8);
            lp.rightMargin = dp(activity, 8);
            lp.bottomMargin = dp(activity, 12);
            wrap.setVisibility(View.VISIBLE);
            content.addView(wrap, lp);
            wrap.bringToFront();
            hookCallback(fragment);
            wrap.post(new Runnable() {
                @Override
                public void run() {
                    wrap.bringToFront();
                }
            });

            Object listObject = HeaderData.field(fragment, "mListView");
            final View listView = listObject instanceof View ? (View) listObject : null;
            // Base padding must be captured once: on every resume the list is
            // rebuilt and adding the extra bottom padding again would push the
            // content (and the card decoration) shorter and shorter.
            final int originalBottom;
            if (listView == null) {
                originalBottom = 0;
            } else if (BASE_BOTTOM.containsKey(listView)) {
                originalBottom = BASE_BOTTOM.get(listView);
            } else {
                originalBottom = listView.getPaddingBottom();
                BASE_BOTTOM.put(listView, originalBottom);
            }
            final int extraBottom = dp(activity, 24);

            wrap.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    try {
                        int nav = 0;
                        try {
                            Insets bars = insets.getInsets(WindowInsets.Type.navigationBars());
                            nav = bars.bottom;
                        } catch (Throwable t) {
                            nav = insets.getSystemWindowInsetBottom();
                        }
                        ViewGroup.LayoutParams p = wrap.getLayoutParams();
                        if (p instanceof FrameLayout.LayoutParams) {
                            ((FrameLayout.LayoutParams) p).bottomMargin = dp(v.getContext(), 14) + nav;
                            wrap.setLayoutParams(p);
                        }
                        if (listView != null) {
                            listView.setPadding(listView.getPaddingLeft(),
                                    listView.getPaddingTop(),
                                    listView.getPaddingRight(),
                                    originalBottom + height + nav + extraBottom);
                        }
                    } catch (Throwable t) {
                        XLog.e("bottom search insets", t);
                    }
                    return insets;
                }
            });
            wrap.requestApplyInsets();

            wrap.post(new Runnable() {
                @Override
                public void run() {
                    int[] loc = new int[2];
                    wrap.getLocationOnScreen(loc);
                    XLog.i("bottom bar: vis=" + wrap.getVisibility()
                            + " w=" + wrap.getWidth() + " h=" + wrap.getHeight()
                            + " y=" + loc[1] + " rootH=" + wrap.getRootView().getHeight());
                }
            });
            XLog.i("bottom search installed");
        } catch (Throwable t) {
            XLog.e("bottom search failed", t);
        }
    }

    private static View inflateNativeStub(Activity activity, ViewGroup content, String hint) {
        try {
            int layoutId = activity.getResources().getIdentifier(
                    "miuix_appcompat_search_stub", "layout", "com.android.settings");
            if (layoutId == 0) {
                return null;
            }
            View bar = LayoutInflater.from(activity).inflate(layoutId, content, false);
            TextView input = bar.findViewById(android.R.id.input);
            if (input != null) {
                input.setHint(hint);
                input.setFocusable(false);
                input.setClickable(false);
            }
            return bar;
        } catch (Throwable t) {
            XLog.e("inflate native search stub failed", t);
            return null;
        }
    }

    private static void watch(final ViewGroup content, final Object fragment) {
        if (!WATCHED.add(content)) {
            return;
        }
        content.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        try {
                            View wrap = content.findViewWithTag(TAG);
                            if (wrap == null) {
                                return;
                            }
                            boolean searching = isSearching(fragment);
                            if (!searching && System.currentTimeMillis() < suppressShowUntil) {
                                return; // search is starting; keep the bar hidden
                            }
                            wrap.setVisibility(searching ? View.GONE : View.VISIBLE);
                        } catch (Throwable ignored) {
                        }
                    }
                });
    }

    private static final java.util.Set<Object> HOOKED_CALLBACKS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());

    /** Hide the bottom bar while the native search action mode is alive. */
    private static void hookCallback(final Object fragment) {
        final Object callback = HeaderData.field(fragment, "mSearchCallback");
        if (callback == null) {
            return;
        }
        synchronized (HOOKED_CALLBACKS) {
            if (!HOOKED_CALLBACKS.add(callback)) {
                return;
            }
        }
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                String name = param.method.getName();
                if (name.contains("onCreateActionMode")) {
                    setBarVisible(fragment, false);
                } else if (name.contains("onDestroyActionMode")) {
                    setBarVisible(fragment, true);
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(callback.getClass(), "onCreateActionMode", hook);
            XposedBridge.hookAllMethods(callback.getClass(), "onDestroyActionMode", hook);
            XLog.i("hooked search action mode callback");
        } catch (Throwable t) {
            XLog.e("hook search callback failed", t);
        }
    }

    private static void setBarVisible(Object fragment, boolean visible) {
        try {
            Activity activity = activity(fragment);
            if (activity == null) {
                return;
            }
            ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
            View wrap = content == null ? null : content.findViewWithTag(TAG);
            if (wrap != null) {
                wrap.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isSearching(Object fragment) {
        try {
            View root = rootView(fragment);
            if (root == null) {
                return false;
            }
            String[] ids = {"search_result_ll", "search_action_mode", "search_mode_stub"};
            for (String name : ids) {
                int id = root.getResources().getIdentifier(name, "id", "com.android.settings");
                if (id == 0) {
                    continue;
                }
                View view = root.findViewById(id);
                if (view != null && view.getVisibility() == View.VISIBLE && view.getHeight() > 0) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static View rootView(Object fragment) {
        try {
            Method m = fragment.getClass().getMethod("getView");
            Object root = m.invoke(fragment);
            return root instanceof View ? (View) root : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void startSearch(Object fragment, Activity activity) {
        try {
            Object callback = HeaderData.field(fragment, "mSearchCallback");
            if (callback == null) {
                return;
            }
            for (Method m : fragment.getClass().getMethods()) {
                if (!m.getName().equals("startActionMode") || m.getParameterCount() != 1) {
                    continue;
                }
                if (!m.getParameterTypes()[0].isInstance(callback)) {
                    continue;
                }
                m.setAccessible(true);
                m.invoke(fragment, callback);
                XLog.i("started search via startActionMode");
                return;
            }
        } catch (Throwable t) {
            XLog.e("startActionMode fallback failed", t);
        }
    }

    private static View findBackgroundView(View root) {
        java.util.List<View> candidates = new java.util.ArrayList<>();
        collectBackgroundViews(root, candidates);
        // Prefer the actual 9-patch holder (the small pill), then the smallest one.
        View smallest = null;
        for (View v : candidates) {
            if (hasNinePatch(v.getBackground())) {
                return v;
            }
            if (smallest == null || area(v) < area(smallest)) {
                smallest = v;
            }
        }
        return smallest;
    }

    private static boolean hasNinePatch(android.graphics.drawable.Drawable drawable) {
        if (drawable instanceof android.graphics.drawable.NinePatchDrawable) {
            return true;
        }
        if (drawable instanceof android.graphics.drawable.RippleDrawable) {
            android.graphics.drawable.RippleDrawable ripple =
                    (android.graphics.drawable.RippleDrawable) drawable;
            for (int i = 0; i < ripple.getNumberOfLayers(); i++) {
                if (hasNinePatch(ripple.getDrawable(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void collectBackgroundViews(View view, java.util.List<View> out) {
        if (view == null) {
            return;
        }
        if (view.getBackground() != null) {
            out.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectBackgroundViews(group.getChildAt(i), out);
            }
        }
    }

    private static int area(View v) {
        return Math.max(0, v.getWidth()) * Math.max(0, v.getHeight());
    }

    /** "搜索" hint: black 70% in light mode, white 70% in dark mode. */
    private static void tintSearchText(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof TextView) {
            boolean dark = UiTweaks.isDarkMode(view.getContext());
            ((TextView) view).setTextColor(dark ? 0xB3FFFFFF : 0xB3000000);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                tintSearchText(group.getChildAt(i));
            }
        }
    }

    /** Creates lattice's LiquidGlassView via reflection, falling back to SearchGlass. */
    private static View createGlass(Activity activity, View content) {
        try {
            Class<?> cls = Class.forName("dev.settingslab.LiquidGlassView");
            Object view = cls.getConstructor(Context.class, View.class, boolean.class)
                    .newInstance(activity, content, true);
            cls.getMethod("setBarStyle", float.class, int.class)
                    .invoke(view, 0f, 0);
            XLog.i("lattice LiquidGlassView created");
            return (View) view;
        } catch (Throwable t) {
            XLog.w("lattice LiquidGlassView unavailable, using SearchGlass: " + t);
            return new SearchGlass(activity);
        }
    }

    /**
     * LiquidGlassView only re-captures when it is invalidated, which caps the
     * glass at a few frames per second. Pump it every display frame (120Hz on
     * this device) through its public invalidateBackdrop().
     */
    private static void startGlassFrameLoop(final View glass) {
        if (glass == null || !"dev.settingslab.LiquidGlassView".equals(
                glass.getClass().getName())) {
            return;
        }
        try {
            final java.lang.reflect.Method invalidate =
                    glass.getClass().getMethod("invalidateBackdrop");
            final android.view.Choreographer choreographer =
                    android.view.Choreographer.getInstance();
            final android.view.Choreographer.FrameCallback callback =
                    new android.view.Choreographer.FrameCallback() {
                        @Override
                        public void doFrame(long frameTimeNanos) {
                            try {
                                invalidate.invoke(glass);
                            } catch (Throwable ignored) {
                            }
                            if (glass.isAttachedToWindow()) {
                                choreographer.postFrameCallback(this);
                            }
                        }
                    };
            glass.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(View v) {
                            choreographer.postFrameCallback(callback);
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                            choreographer.removeFrameCallback(callback);
                        }
                    });
            if (glass.isAttachedToWindow()) {
                choreographer.postFrameCallback(callback);
            }
            XLog.i("glass frame loop started (120Hz)");
        } catch (Throwable t) {
            XLog.w("glass frame loop failed: " + t);
        }
    }

    private static void disableChildTouches(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                disableChildTouches(group.getChildAt(i));
            }
        }
        view.setClickable(false);
        view.setLongClickable(false);
        view.setFocusable(false);
        view.setFocusableInTouchMode(false);
    }

    private static View fallbackBar(Activity activity, String hint) {
        TextView tv = new TextView(activity);
        tv.setText(hint);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTextColor(0xFF8E8E93);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF2F2F7);
        bg.setCornerRadius(dp(activity, 27));
        tv.setBackground(bg);
        tv.setPadding(dp(activity, 20), 0, dp(activity, 20), 0);
        return tv;
    }

    private static Activity activity(Object fragment) {
        try {
            java.lang.reflect.Method m = fragment.getClass().getMethod("getActivity");
            Object r = m.invoke(fragment);
            if (r instanceof Activity) {
                return (Activity) r;
            }
        } catch (Throwable ignored) {
        }
        Object ctx = HeaderData.field(fragment, "mContext");
        return ctx instanceof Activity ? (Activity) ctx : null;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
