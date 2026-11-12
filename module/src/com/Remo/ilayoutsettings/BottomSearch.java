package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
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
    /** 16.6: Xiao Ai's exported voice-trigger service (power-key long press path). */
    private static final String XIAOAI_PKG = "com.miui.voiceassist";
    private static final String XIAOAI_SERVICE = "com.xiaomi.voiceassistant.VoiceService";
    private static final String XIAOAI_ACTION =
            "com.miui.voicetrigger.ACTION_VOICE_TRIGGER_START_VOICEASSIST";
    private static final java.util.Map<View, Integer> BASE_BOTTOM =
            new java.util.WeakHashMap<>();
    private static final java.util.Set<ViewGroup> WATCHED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<ViewGroup, Boolean>());
    private static long suppressShowUntil;
    private static int lastNightMode = -1;
    /** While in the future the bar's enter/exit animation owns its visibility. */
    private static long animUntil;
    private static final int ANIM_MS = 220;
    /** Extra head start before MIUI takes over the bar (fraction of ANIM_MS). */
    private static final int ANIM_OPEN_DELAY = 120;
    /** The single live bar and the anchor it was built for. */
    private static View sWrap;
    private static View sAnchor;

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
            if (existing == sWrap && sAnchor == anchor && night == lastNightMode) {
                // Same fragment, same theme: keep the live bar. Tearing it down
                // on every resume killed the enter/exit animation and left two
                // copies on screen while the old one was still finishing.
                existing.bringToFront();
                if (System.currentTimeMillis() >= animUntil) {
                    if (isSearching(fragment)) {
                        existing.setVisibility(View.GONE);
                    } else if (existing.getVisibility() != View.VISIBLE) {
                        animateBar(existing, true);
                    }
                }
                return;
            }
            if (existing != null) {
                // Theme / fragment changed: the old glass mask and listeners are
                // stale, so rebuild.
                content.removeView(existing);
            }
            if (sWrap != null && sWrap != existing) {
                android.view.ViewParent parent = sWrap.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(sWrap);
                }
            }
            sWrap = null;
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
            // 15.3: the wrapper no longer casts an elevation shadow. On light
            // pages that shadow was the hard grey halo around the pill; the
            // visible capsule itself is still MIUI's own light/dark drawable.
            wrap.setElevation(0);

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
            // The native capsule is the inputArea LinearLayout (44dp, 9-patch
            // background) inside the 56dp stub; clipping the glass to the stub
            // itself made the lens wider and taller than the bar it sits in.
            final View pill = pickNativePill(activity, bar);
            glass.setMaskView(pill);
            glass.setCaptureHost(wrap);
            final Drawable pillBackground = pill == null ? null : pill.getBackground();
            if (pill != null) {
                // 16.6: no more miuix_appcompat_search_mode_edit_text_bg_*.9
                // under the liquid glass — SearchGlass is the only surface.
                // Keep the drawable: the visible pill is inset from its view
                // (transparent margins are baked into the 9-patch), and that
                // inset is measured after the first layout below.
                pill.setBackground(null);
                XLog.i("pill 9-patch removed: " + pill.getClass().getSimpleName());
            }
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
                styleGlassContent(activity, bar, pill);
            } catch (Throwable t) {
                XLog.w("search bar text styling skipped: " + t);
            }
            final View.OnClickListener openSearch = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    XLog.i("bottom search clicked");
                    suppressShowUntil = System.currentTimeMillis() + 1200;
                    // Fly the bar towards the top *first*; then let MIUI swap in
                    // its own search field, so the two do not overlap instantly.
                    animateBar(wrap, false);
                    v.postDelayed(new Runnable() {
                        @Override
                        public void run() {
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
                        }
                    }, ANIM_OPEN_DELAY);
                }
            };
            bar.setClickable(true);
            bar.setOnClickListener(openSearch);

            anchor.setVisibility(View.GONE);
            wrap.setOnClickListener(openSearch);
            wrap.addView(bar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            final View barFinal = bar;

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.BOTTOM);
            lp.leftMargin = dp(activity, 8);
            lp.rightMargin = dp(activity, 8);
            lp.bottomMargin = dp(activity, 12);
            wrap.setVisibility(isSearching(fragment) ? View.GONE : View.VISIBLE);
            content.addView(wrap, lp);
            wrap.bringToFront();
            sWrap = wrap;
            sAnchor = anchor;
            hookCallback(fragment);
            wrap.post(new Runnable() {
                @Override
                public void run() {
                    wrap.bringToFront();
                }
            });
            if (pill != null && pillBackground != null) {
                // The pill has no size during install. Measure the saved
                // 9-patch on the first pre-draw, when layout has run but nothing
                // has been painted yet, so the glass never flashes its full
                // (taller) stub-sized shape.
                wrap.getViewTreeObserver().addOnPreDrawListener(
                        new android.view.ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                if (pill.getWidth() <= 0 || pill.getHeight() <= 0) {
                                    return true;
                                }
                                try {
                                    wrap.getViewTreeObserver().removeOnPreDrawListener(this);
                                } catch (Throwable ignored) {
                                }
                                android.graphics.Rect visible = SearchGlass.paintedBounds(
                                        pillBackground, pill.getWidth(), pill.getHeight());
                                glass.setPillVisibleRect(visible);
                                glass.invalidate();
                                return true;
                            }
                        });
            }

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
                    if (DEBUG_TREE) {
                        dumpTree(barFinal, 0);
                    }
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
                            long now = System.currentTimeMillis();
                            if (now < animUntil) {
                                return; // the enter / exit animation owns visibility
                            }
                            boolean searching = isSearching(fragment);
                            if (!searching && now < suppressShowUntil) {
                                return; // search is starting; keep the bar hidden
                            }
                            if (!searching && !isInActionMode(fragment)) {
                                // MIUI re-shows its own top bar once the action
                                // mode is gone; hide it again as soon as the
                                // layout settles.
                                hideAnchor(fragment);
                            }
                            boolean shown = wrap.getVisibility() == View.VISIBLE;
                            if (searching && shown) {
                                animateBar(wrap, false);
                            } else if (!searching && !shown) {
                                animateBar(wrap, true);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
    }

    private static final java.util.Set<Object> HOOKED_CALLBACKS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());
    private static final java.util.Set<Object> HOOKED_MODES =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());
    private static final android.os.Handler MAIN =
            new android.os.Handler(android.os.Looper.getMainLooper());

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
                    // Our own animation listener is the only reliable "MIUI has
                    // finished collapsing" signal; the callback's onDestroy
                    // fires while the search field is still animating out.
                    addExitListener(fragment, param.args[0]);
                } else if (name.contains("onDestroyActionMode")) {
                    // Keep the bar hidden while MIUI collapses its search field,
                    // otherwise the screen shows the top search bar and the
                    // bottom pill at the same time for ~200ms.
                    setBarVisible(fragment, false);
                    scheduleFallbackShow(fragment);
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

    /**
     * Adds a listener to the live {@code miuix.view.SearchActionMode} so the
     * bottom bar can return exactly when the exit animation ends (the callback
     * instance itself is anonymous, so a dynamic proxy keeps this ROM-agnostic).
     */
    private static void addExitListener(final Object fragment, Object actionMode) {
        if (actionMode == null) {
            return;
        }
        synchronized (HOOKED_MODES) {
            if (!HOOKED_MODES.add(actionMode)) {
                return;
            }
        }
        try {
            ClassLoader cl = actionMode.getClass().getClassLoader();
            Class<?> listener = de.robv.android.xposed.XposedHelpers.findClassIfExists(
                    "miuix.view.ActionModeAnimationListener", cl);
            if (listener == null) {
                return;
            }
            Object proxy = java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[]{listener},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object self, java.lang.reflect.Method method,
                                             Object[] args) {
                            String methodName = method.getName();
                            if ("onStop".equals(methodName)) {
                                // miuix reports the animation that just ended:
                                // true = opening, false = closing. Only the
                                // closing pass may bring the bar back.
                                boolean opening = args != null && args.length == 1
                                        && Boolean.TRUE.equals(args[0]);
                                XLog.i("action mode onStop opening=" + opening
                                        + " inMode=" + HeaderData.field(fragment, "mIsInActionMode"));
                                if (opening) {
                                    return null;
                                }
                                MAIN.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isInActionMode(fragment) && isResumed(fragment)) {
                                            setBarVisible(fragment, true);
                                        }
                                    }
                                });
                                return null;
                            }
                            if ("equals".equals(methodName)) {
                                return self == (args == null ? null : args[0]);
                            }
                            if ("hashCode".equals(methodName)) {
                                return System.identityHashCode(self);
                            }
                            if ("toString".equals(methodName)) {
                                return "iLayoutSettings-search-animation";
                            }
                            return null;
                        }
                    });
            java.lang.reflect.Method add = actionMode.getClass()
                    .getMethod("addAnimationListener", listener);
            add.setAccessible(true);
            add.invoke(actionMode, proxy);
            XLog.i("search exit listener installed");
        } catch (Throwable t) {
            XLog.w("search exit listener failed: " + t);
        }
    }

    /** Safety net: if the animation listener never fires, bring the bar back. */
    private static void scheduleFallbackShow(final Object fragment) {
        try {
            Activity activity = activity(fragment);
            View root = activity == null ? null : activity.findViewById(android.R.id.content);
            if (root == null) {
                return;
            }
            root.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!isInActionMode(fragment) && isResumed(fragment)) {
                        setBarVisible(fragment, true);
                    }
                }
            }, 700);
        } catch (Throwable t) {
            XLog.w("search exit fallback failed: " + t);
        }
    }

    /**
     * Keeps MIUI's own top search bar hidden. HyperOS 4 brings it back when the
     * search action mode is destroyed, which is why the home page showed the
     * native top pill and our bottom pill at the same time after leaving
     * search. Calling this on every layout pass that finds no search mode
     * active keeps a single search bar on screen.
     */
    private static void hideAnchor(Object fragment) {
        try {
            Object raw = HeaderData.field(fragment, "mAnchorView");
            View anchor = raw instanceof View ? (View) raw : sAnchor;
            if (anchor == null) {
                return;
            }
            if (anchor.getVisibility() != View.GONE) {
                anchor.setVisibility(View.GONE);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isInActionMode(Object fragment) {
        Object value = HeaderData.field(fragment, "mIsInActionMode");
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isResumed(Object fragment) {
        try {
            java.lang.reflect.Method m = fragment.getClass().getMethod("isResumed");
            Object result = m.invoke(fragment);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void setBarVisible(Object fragment, boolean visible) {
        try {
            Activity activity = activity(fragment);
            if (activity == null) {
                return;
            }
            // HyperOS 4 re-shows the native header search bar when the search
            // action mode ends (the anchor is no longer kept GONE by MIUI), so
            // re-hide it every time we own the bar's visibility.
            hideAnchor(fragment);
            ViewGroup content = (ViewGroup) activity.findViewById(android.R.id.content);
            View wrap = content == null ? null : content.findViewWithTag(TAG);
            if (wrap == null) {
                return;
            }
            if (visible && wrap.getVisibility() == View.VISIBLE
                    && wrap.getAlpha() >= 1f && wrap.getTranslationY() == 0f) {
                return; // already at rest
            }
            animateBar(wrap, visible);
        } catch (Throwable ignored) {
        }
    }

    /**
     * iOS-style transition between the bottom bar and the native top search
     * field: the bar slides up a little while fading and shrinking away, and
     * comes back with a short slide-up when the search mode is dismissed.
     *
     * <p>Deliberately a short travel: flying the live bar across the page
     * smeared the captured backdrop and left a second copy visible while MIUI
     * was still collapsing its own search field.</p>
     */
    private static void animateBar(final View wrap, final boolean show) {
        if (wrap == null) {
            return;
        }
        try {
            if (!show && wrap.getVisibility() != View.VISIBLE) {
                return; // already gone
            }
            wrap.animate().cancel();
            final float travel = dp(wrap.getContext(), 28);
            animUntil = System.currentTimeMillis() + ANIM_MS;
            // The glass paints a captured backdrop; moving it smears that
            // backdrop. Fall back to the native capsule while the bar moves.
            setGlassVisible(wrap, false);
            if (show) {
                wrap.setVisibility(View.VISIBLE);
                wrap.setTranslationY(travel);
                wrap.setAlpha(0f);
                wrap.setScaleX(0.96f);
                wrap.setScaleY(0.96f);
                wrap.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(ANIM_MS)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                clearBarTransforms(wrap);
                                setGlassVisible(wrap, true);
                                animUntil = 0;
                            }
                        })
                        .start();
            } else {
                wrap.animate().translationY(-travel).alpha(0f)
                        .scaleX(0.96f).scaleY(0.96f)
                        .setDuration(ANIM_MS)
                        .setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                clearBarTransforms(wrap);
                                wrap.setVisibility(View.GONE);
                                setGlassVisible(wrap, true);
                                animUntil = 0;
                            }
                        })
                        .start();
            }
        } catch (Throwable t) {
            XLog.w("search bar animation failed: " + t);
            wrap.setVisibility(show ? View.VISIBLE : View.GONE);
            clearBarTransforms(wrap);
            setGlassVisible(wrap, true);
            animUntil = 0;
        }
    }

    private static void setGlassVisible(View view, boolean visible) {
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof SearchGlass) {
                child.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            } else {
                setGlassVisible(child, visible);
            }
        }
    }

    private static void clearBarTransforms(View wrap) {
        try {
            wrap.setTranslationY(0f);
            wrap.setAlpha(1f);
            wrap.setScaleX(1f);
            wrap.setScaleY(1f);
        } catch (Throwable ignored) {
        }
    }

    /**
     * True while MIUI's search action mode is alive.
     *
     * <p>The view probe alone was unreliable: {@code search_result_ll} is a
     * full-screen container that keeps a non-zero height after the first layout
     * pass, so every later resume looked like "search is open" and the bottom
     * bar was hidden for good. SettingsFragment keeps the authoritative flag in
     * {@code mIsInActionMode}; the probe is only the fallback.</p>
     */
    private static boolean isSearching(Object fragment) {
        Object inActionMode = HeaderData.field(fragment, "mIsInActionMode");
        if (inActionMode instanceof Boolean) {
            return (Boolean) inActionMode;
        }
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

    /**
     * The visible capsule is the stub's {@code inputArea} LinearLayout: the
     * stub root is 56dp tall and mostly padding, the pill inside it carries the
     * 9-patch. Falls back to {@code widget_frame} and then to the smallest view
     * with a background for other MIUI versions.
     */
    private static View pickNativePill(Activity activity, View bar) {
        for (String name : new String[]{"inputArea", "widget_frame"}) {
            try {
                int id = activity.getResources().getIdentifier(name, "id", "android");
                if (id != 0) {
                    View pill = bar.findViewById(id);
                    if (pill != null) {
                        return pill;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return findBackgroundView(bar);
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

    /** Diagnostic switch: print the inflated stub tree (bounds + backgrounds). */
    private static final boolean DEBUG_TREE = false;

    /** Diagnostic: prints the stub tree so the pill can be identified by bounds. */
    private static void dumpTree(View view, int depth) {
        if (view == null || depth > 6) {
            return;
        }
        try {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                line.append("  ");
            }
            line.append(view.getClass().getSimpleName());
            if (view.getId() != View.NO_ID) {
                try {
                    line.append('#').append(view.getResources()
                            .getResourceEntryName(view.getId()));
                } catch (Throwable ignored) {
                    line.append('#').append(Integer.toHexString(view.getId()));
                }
            }
            line.append(' ').append(view.getWidth()).append('x').append(view.getHeight());
            if (view.getBackground() != null) {
                line.append(" bg=").append(view.getBackground().getClass().getSimpleName());
            }
            XLog.i("bar tree: " + line);
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    dumpTree(group.getChildAt(i), depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
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

    /**
     * 16.6: replace MIUI's search/mic glyphs with the supplied iOS 27 icons,
     * drop the 9-patch's baked-in voice icon (on this ROM it is the hint
     * TextView's drawableEnd), and add a clickable microphone that wakes Xiao Ai
     * the same way the power-key long press does.
     */
    private static void styleGlassContent(final Activity activity, View bar, View pill) {
        boolean dark = UiTweaks.isDarkMode(activity);
        int tint = dark ? 0xFFFFFFFF : 0xFF1C1C1E;

        TextView input = bar.findViewById(android.R.id.input);
        if (input != null) {
            // Clearing the compound drawables removes MIUI's own mic; leaving
            // it in place would draw a second, non-clickable glyph next to ours.
            input.setCompoundDrawablesRelative(null, null, null, null);
            input.setPadding(input.getPaddingLeft(), input.getPaddingTop(),
                    Math.max(input.getPaddingRight(), dp(activity, 34)),
                    input.getPaddingBottom());
        }

        Drawable search = HeaderData.moduleDrawable(activity, "ic_search_glass");
        View icon = firstImageView(pill != null ? pill : bar);
        if (icon instanceof ImageView) {
            ImageView searchIcon = (ImageView) icon;
            // The stub's ImageView is wrap_content: assigning a 59dp-intrinsic
            // module drawable would widen the view to 84dp and push the hint to
            // the right. Pin the original 44x24dp box back. MIUI's own drawable
            // was 14dp inside asymmetric 17/9dp padding; ours is 16dp in an
            // 18/10dp padding, so the glyph centre does not move.
            ViewGroup.LayoutParams vlp = searchIcon.getLayoutParams();
            if (vlp != null) {
                vlp.width = dp(activity, 44);
                vlp.height = dp(activity, 24);
                searchIcon.setLayoutParams(vlp);
            }
            // 16.6: 14dp glyph instead of 16dp, and 2dp further inside.
            searchIcon.setPadding(dp(activity, 21), dp(activity, 5),
                    dp(activity, 9), dp(activity, 5));
            if (search != null) {
                searchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                searchIcon.setImageDrawable(search);
                searchIcon.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
                XLog.i("search icon <- ic_search_glass");
            } else {
                searchIcon.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
            }
        }

        if (!(bar instanceof ViewGroup)) {
            return;
        }
        ImageView mic = new ImageView(activity);
        Drawable micDrawable = HeaderData.moduleDrawable(activity, "ic_mic_glass");
        if (micDrawable != null) {
            mic.setImageDrawable(micDrawable);
            mic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mic.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        }
        mic.setContentDescription("小爱同学");
        mic.setClickable(true);
        mic.setFocusable(true);
        // HyperOS 4 inflates the search stub as a LinearLayout, so a
        // FrameLayout.LayoutParams addView() throws and the mic silently
        // disappeared (the whole styling call was caught). Host it on the
        // inner search_mode_stub (a FrameLayout) and pick the params by type.
        ViewGroup micHost = bar instanceof ViewGroup ? (ViewGroup) bar : null;
        int stubId = activity.getResources().getIdentifier(
                "search_mode_stub", "id", "com.android.settings");
        View stub = stubId == 0 ? null : bar.findViewById(stubId);
        if (stub instanceof FrameLayout) {
            micHost = (FrameLayout) stub;
        }
        if (micHost == null) {
            return;
        }
        ViewGroup.LayoutParams lp;
        if (micHost instanceof FrameLayout) {
            FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                    dp(activity, 12), dp(activity, 16),
                    Gravity.END | Gravity.CENTER_VERTICAL);
            // OS4 hosts the mic on search_mode_stub (26..1254), whose right edge
            // is 26px left of the bar root the OS3 build measured against, so
            // the OS3 36dp margin lands 38px too far left. 23dp puts the glyph
            // back at the OS3 x centre (measured 1137 vs 1099 px right edge).
            flp.rightMargin = dp(activity, 23);
            lp = flp;
        } else if (micHost instanceof android.widget.LinearLayout) {
            android.widget.LinearLayout.LayoutParams llp =
                    new android.widget.LinearLayout.LayoutParams(
                            dp(activity, 12), dp(activity, 16));
            llp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
            lp = llp;
        } else {
            lp = new ViewGroup.LayoutParams(dp(activity, 12), dp(activity, 16));
        }
        // The stub root has no padding (the inner stub layout is the one that is
        // inset), so 36dp from the root's right edge puts the smaller mic 3dp
        // further inside than the first pass, matching the reference.
        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                v.animate().cancel();
                v.setScaleX(0.86f);
                v.setScaleY(0.86f);
                v.animate().scaleX(1f).scaleY(1f).setDuration(170)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                        .start();
                wakeXiaoAi(activity);
            }
        });
        micHost.addView(mic, lp);
        XLog.i("mic icon added (tap -> Xiao Ai)");
    }

    private static View firstImageView(View root) {
        if (root instanceof ImageView) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstImageView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Wakes Xiao Ai the way the power-key long press does: the voice trigger
     * starts com.miui.voiceassist's exported VoiceService with
     * ACTION_VOICE_TRIGGER_START_VOICEASSIST, which brings up the same
     * voice_assist_root overlay. Verified on 25053RT47C (HyperOS 3 / Android 16);
     * the first tap after a cold start takes a few seconds to show the overlay.
     */
    private static void wakeXiaoAi(Activity activity) {
        // HyperOS 4 accepts the VoiceService start but never raises the
        // voice_assist_root overlay (verified: focus stays on Settings). The
        // assist intent is the one that actually opens Xiao Ai there; keep the
        // VoiceService path for OS3, where it is the power-key short cut.
        if (Build.VERSION.SDK_INT >= 37) {
            try {
                Intent assist = new Intent(Intent.ACTION_ASSIST);
                assist.setPackage(XIAOAI_PKG);
                assist.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(assist);
                XLog.i("xiaoai: ACTION_ASSIST (OS4)");
                return;
            } catch (Throwable t) {
                XLog.w("xiaoai: assist intent failed: " + t);
            }
        }
        try {
            Intent intent = new Intent(XIAOAI_ACTION);
            intent.setComponent(new ComponentName(XIAOAI_PKG, XIAOAI_SERVICE));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(intent);
            } else {
                activity.startService(intent);
            }
            XLog.i("xiaoai: VoiceService start requested");
            return;
        } catch (Throwable t) {
            XLog.w("xiaoai: VoiceService failed: " + t);
        }
        try {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.setPackage(XIAOAI_PKG);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            XLog.i("xiaoai: fallback ACTION_ASSIST");
        } catch (Throwable t) {
            XLog.e("xiaoai: fallback failed", t);
        }
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
