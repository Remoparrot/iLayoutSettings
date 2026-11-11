package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Gives second-level Settings pages the same top bar treatment as the home
 * page: a translucent bar tint plus MIUI's native background blur, with the
 * scrolling list allowed to draw under the bar.
 *
 * <p>The home page gets this from {@code NestedHeaderLayout} (see
 * {@link UiTweaks}); sub pages are plain {@code ActionBar} activities, so their
 * bar never asked MIUI for the blur. They still use
 * {@code com.android.settings:id/action_bar_container}, which is what this
 * class decorates.</p>
 *
 * <p>16.7. The bar is tinted with the same {@code topbar_alpha} value as home
 * and the blur radius is {@code topbar_blur}, so both pages stay in sync with
 * the in-app sliders. A 0.5 dp semi-transparent hairline marks the bar's lower
 * edge once content slides underneath, exactly like the home page. The list
 * clip MIUI sets while the header collapses is dropped, otherwise the rows are
 * cut at the bar's lower edge and there is nothing for the blur to sample.</p>
 *
 * <p>16.8. Three things this class got wrong on real pages:</p>
 * <ul>
 *   <li>the first scrollable found in the decor tree is not necessarily the
 *       page list ("我的设备" nests a non-scrolling grid named
 *       {@code description_grid} under its real scroll view). Only the
 *       shallowest scroll container is padded now.</li>
 *   <li>changing the padding after overlay mode was switched on left the first
 *       row at its old local coordinate, which read as "already scrolled": the
 *       page opened with the bar and hairline showing. Row 0 is re-anchored
 *       after the padding change.</li>
 *   <li>"我的设备"/"通用" starts its scroll view below the bar and pins MIUI's
 *       version card on top of it, so nothing could ever slide under the bar.
 *       That wrapper is lifted by the bar height and its clip removed, which
 *       keeps the page identical at rest but lets scrolled rows reach the
 *       bar.</li>
 * </ul>
 */
final class SubPageTopBar {

    private static final String BLUR = "miuix.core.util.MiuiBlurUtils";
    /** {@link #ensureOverlay}: the page is not an overlay layout. */
    private static final int OVERLAY_NONE = 0;
    /** {@link #ensureOverlay}: overlay mode is (now) active. */
    private static final int OVERLAY_ON = 1;
    /** {@link #ensureOverlay}: this call flipped overlay mode on. */
    private static final int OVERLAY_SWITCHED = 2;

    /** Same id {@link UiTweaks} uses for the home page's action bar. */
    private static int sAppBarId;
    /** {@code com.android.settings:id/miui_version_card_view}, resolved once. */
    private static int sVersionCardId;

    /** Translucent tint drawable per bar, so MIUI cannot silently replace it. */
    private static final Map<View, ColorDrawable> BAR_BG = new WeakHashMap<>();
    /** Bars whose replaced background has already been logged. */
    private static final Set<View> LOGGED_BG_SWAP =
            java.util.Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    /** Last blur radius (px) pushed through {@code setBackgroundBlur}. */
    private static final Map<View, Integer> APPLIED_BLUR = new WeakHashMap<>();
    /** Activities whose decor tree already got the first passes. */
    private static final Set<Activity> WATCHING =
            java.util.Collections.newSetFromMap(new WeakHashMap<Activity, Boolean>());
    /** Scrollables that already have the scroll/pre-draw listeners. */
    private static final Set<View> SCROLL_WATCHED =
            java.util.Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    /** Activity classes that already logged the missing-app-bar diagnostic. */
    private static final Set<String> LOGGED_MISSING = new java.util.HashSet<>();
    /** Activity classes that already logged the view tree. */
    private static final Set<String> LOGGED_TREE = new java.util.HashSet<>();
    /** ActionBarOverlayLayouts that already switched to overlay mode. */
    private static final Map<View, Boolean> OVERLAY_ON_MAP = new WeakHashMap<>();
    /** Views that got the expanded-bar top padding (value = top inset). */
    private static final Map<View, Integer> LIST_PADDING = new WeakHashMap<>();
    /** Lists whose wrapper was lifted under the bar (clip must stay off). */
    private static final Set<View> LIFTED_LISTS =
            java.util.Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    /** Cached "this RecyclerView scrolls vertically" answer (reflection is slow). */
    private static final Map<View, Boolean> VERTICAL_LISTS = new WeakHashMap<>();

    private SubPageTopBar() {
    }

    static void install(ClassLoader cl) {
        try {
            // Most sub pages are Settings$XxxActivity, which extends
            // SettingsActivity (onResume lives there, not on the base class).
            // SettingsBaseActivity is hooked too in case a page overrides it.
            String[] bases = {
                    "com.android.settings.SettingsActivity",
                    "com.android.settings.core.SettingsBaseActivity"
            };
            int hooked = 0;
            for (String name : bases) {
                Class<?> base = XposedHelpers.findClassIfExists(name, cl);
                if (base == null) {
                    continue;
                }
                XposedBridge.hookAllMethods(base, "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof Activity) {
                            watch((Activity) param.thisObject);
                        }
                    }
                });
                XposedBridge.hookAllMethods(base, "onDestroy", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof Activity) {
                            WATCHING.remove(param.thisObject);
                        }
                    }
                });
                hooked++;
            }
            if (hooked == 0) {
                XLog.w("no settings activity base found, sub-page top bar stays native");
                return;
            }
            XLog.i("hooked sub-page activity bases: " + hooked);
        } catch (Throwable t) {
            XLog.e("sub-page top bar hook failed", t);
        }
    }

    /**
     * Wires the page up. The decor view is posted twice: the first pass lands
     * after the activity's own {@code onResume} work, but MIUI rebuilds the
     * collapse-title views right after that, so the second pass (next frame)
     * re-applies against the final tree.
     */
    private static void watch(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final View decor = activity.getWindow() == null
                ? null : activity.getWindow().getDecorView();
        if (decor == null) {
            return;
        }
        if (!WATCHING.add(activity)) {
            refresh(activity);
            scheduleLateRefreshes(activity, decor);
            return;
        }
        decor.post(new Runnable() {
            @Override
            public void run() {
                refresh(activity);
                decor.post(new Runnable() {
                    @Override
                    public void run() {
                        refresh(activity);
                    }
                });
                scheduleLateRefreshes(activity, decor);
            }
        });
    }

    /**
     * "我的设备"/"通用" inflates its scroll view a second time when the async
     * device data lands (the first tree we decorated is thrown away), which
     * loses the list padding and the wrapper lift. A few cheap late passes make
     * sure whichever tree is current gets decorated; each pass is idempotent.
     */
    private static void scheduleLateRefreshes(final Activity activity, View decor) {
        for (final long delay : new long[]{700L, 1600L, 3200L}) {
            decor.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        refresh(activity);
                    }
                }
            }, delay);
        }
    }

    /** Tint + blur + unclip; cheap enough to run on every onResume. */
    static void refresh(Activity activity) {
        try {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            View decor = activity.getWindow().getDecorView();
            View appBar = appBarOf(activity, decor);
            if (appBar == null) {
                logMissing(activity, decor);
                return;
            }
            logTreeOnce(activity, decor);
            clearListClip(decor);
            int overlay = ensureOverlay(appBar);
            if (overlay == OVERLAY_SWITCHED) {
                // The relayout has not happened yet, so the list still measures
                // at its old place below the bar. Pad by the bar height now; the
                // scroll reset inside removes the offset the LM would otherwise
                // keep once overlay mode moves the list to the window top.
                padListUnderBar(appBar, decor, true);
            } else if (overlay == OVERLAY_ON) {
                // Already overlay: pad only when the list really is under the
                // bar (e.g. "我的设备" starts below it and is lifted instead).
                padListUnderBar(appBar, decor, false);
            }

            applyDivider(activity, decor);
            applyBarStyle(activity, appBar);
            attachScrollWatcher(activity, decor);
        } catch (Throwable t) {
            XLog.w("sub-page top bar refresh failed: " + t);
        }
    }

    /* -------------------------------- bar style -------------------------------- */

    /**
     * Translucent tint + native blur, shared by every sub page. The tint is one
     * drawable per bar so it can be put back if MIUI swaps in its own opaque
     * background while the collapse title changes.
     */
    private static void applyBarStyle(Activity activity, View appBar) {
        if (appBar == null) {
            return;
        }
        Context context = appBar.getContext();
        boolean dark = UiTweaks.isDarkMode(context);
        int alphaPercent = Math.max(0, Math.min(100, Prefs.getInt(
                context, Prefs.TOPBAR_ALPHA, Prefs.DEFAULT_TOPBAR_ALPHA)));
        int alpha = Math.round(255f * alphaPercent / 100f);
        int base = dark ? 0x000000 : 0xF2F2F7;
        int background = (alpha << 24) | base;

        ColorDrawable tint = BAR_BG.get(appBar);
        if (tint == null) {
            tint = new ColorDrawable(background);
            BAR_BG.put(appBar, tint);
            appBar.setBackground(tint);
        } else {
            if (tint.getColor() != background) {
                tint.setColor(background);
            }
            if (appBar.getBackground() != tint) {
                android.graphics.drawable.Drawable previous = appBar.getBackground();
                appBar.setBackground(tint);
                if (LOGGED_BG_SWAP.add(appBar)) {
                    XLog.i("sub-page bar background re-applied (was "
                            + (previous == null ? "null"
                            : previous.getClass().getSimpleName()) + ")");
                }
            }
        }

        int radius = Math.max(0, Prefs.topBarBlurRadius(context));
        int radiusPx = Math.max(0, Math.round(radius
                * context.getResources().getDisplayMetrics().density));
        Integer lastBlur = APPLIED_BLUR.get(appBar);
        if (lastBlur != null && lastBlur == radiusPx) {
            return;
        }
        APPLIED_BLUR.put(appBar, radiusPx);
        ClassLoader loader = context.getClassLoader();
        if (radiusPx > 0) {
            UiTweaks.callStatic(loader, BLUR, "setBackgroundBlur", appBar,
                    Integer.valueOf(radiusPx));
            UiTweaks.callStatic(loader, BLUR, "setBackgroundBlurRadius", appBar,
                    Integer.valueOf(radiusPx));
        } else {
            UiTweaks.callStatic(loader, BLUR, "clearBackgroundBlur", appBar);
        }
    }

    /** Puts our tint back if MIUI replaced the bar background mid-scroll. */
    private static void ensureBarBackground(View appBar) {
        ColorDrawable tint = appBar == null ? null : BAR_BG.get(appBar);
        if (tint != null && appBar.getBackground() != tint) {
            appBar.setBackground(tint);
        }
    }

    /**
     * The 0.5 dp semi-transparent hairline under the bar, the same iOS rule the
     * home page follows: nothing at rest (the bar sits on the page background),
     * a line the moment content scrolls beneath it. Colour is re-read every
     * pass so a light/dark switch does not leave a black line on a dark bar.
     */
    private static void applyDivider(Activity activity, View decor) {
        try {
            View appBar = appBarOf(activity, decor);
            if (appBar == null || appBar.getHeight() <= 0) {
                return;
            }
            Context context = appBar.getContext();
            boolean dark = UiTweaks.isDarkMode(context);
            View list = findScrollable(decor);
            // canScrollVertically(-1) is true exactly when the first row has
            // moved up under the bar; computeVerticalScrollOffset() stays 0 on
            // some MIUI lists, so this is the reliable test. Pages whose list
            // never reaches the bar must not get the line either.
            boolean scrolled = list != null && list.canScrollVertically(-1);
            boolean underBar = overlapUnderBar(appBar, list) > 0;
            UiTweaks.applyDivider(appBar, dark ? 0x4DAAAAAA : 0x1F000000,
                    scrolled && underBar && Prefs.topBarDivider(context));
        } catch (Throwable t) {
            XLog.w("sub-page divider failed: " + t);
        }
    }

    /* -------------------------------- layout ---------------------------------- */

    /**
     * Lets the list draw under the bar. Sub pages are laid out as a plain
     * column (list top == bar bottom), so clearing the clip alone leaves the
     * blur nothing to sample. MIUI's ActionBarOverlayLayout has an overlay
     * mode for exactly this; flipping it makes the content view extend to the
     * window top, the way the home page's NestedHeaderLayout already works.
     */
    private static int ensureOverlay(View appBar) {
        try {
            Object parent = appBar == null ? null : appBar.getParent();
            if (!(parent instanceof View)) {
                return OVERLAY_NONE;
            }
            View overlay = (View) parent;
            if (!overlay.getClass().getName().endsWith("ActionBarOverlayLayout")) {
                return OVERLAY_NONE;
            }
            Object inOverlay = UiTweaks.call(overlay, "isInOverlayMode");
            if (Boolean.TRUE.equals(inOverlay)) {
                return OVERLAY_ON;
            }
            if (OVERLAY_ON_MAP.put(overlay, Boolean.TRUE) != null) {
                return OVERLAY_NONE;
            }
            UiTweaks.call(overlay, "setOverlayMode", Boolean.TRUE);
            overlay.requestLayout();
            XLog.i("sub-page action bar overlay mode on");
            return OVERLAY_SWITCHED;
        } catch (Throwable t) {
            XLog.w("sub-page overlay mode failed: " + t);
            return OVERLAY_NONE;
        }
    }

    /**
     * Overlay mode lays the list out from the window top; without a top inset
     * the first card sits behind the large title. The padding is the expanded
     * bar height, so at rest the rows start below the bar and, while scrolling,
     * they slide under it (the bar collapses and the blur has content to sample).
     *
     * <p>{@code justSwitched} is true when overlay mode was flipped in this
     * pass: the list has not been relaid out yet, so measuring "list top vs bar
     * bottom" would wrongly report no overlap.</p>
     */
    private static void padListUnderBar(View appBar, View decor, boolean justSwitched) {
        View list = findScrollable(decor);
        if (list == null || list.getHeight() <= 0) {
            return;
        }
        // A ScrollView's own padding is re-applied by MIUI's inset pass, which
        // wiped ours as soon as the activity relaid out. Pad its single content
        // child instead: same at-rest layout, but the padding now scrolls with
        // the rows so they still pass under the bar.
        View padTarget = list;
        if (isScrollView(list) && list instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) list;
            group.setClipToPadding(false);
            if (group.getChildCount() > 0) {
                padTarget = group.getChildAt(0);
            }
        }
        // Rows have to be drawn in the padding band for the bar to blur them.
        if (padTarget instanceof ViewGroup) {
            ((ViewGroup) padTarget).setClipToPadding(false);
        }
        if (LIST_PADDING.containsKey(padTarget)) {
            // Already decorated once. MIUI may rebuild this page when its async
            // data lands, which drops the wrapper lift and/or the padding; only
            // the values we already chose are restored here (recomputing from
            // the current bar height would shrink the inset while the bar is
            // collapsed mid-scroll).
            Integer applied = LIST_PADDING.get(padTarget);
            boolean padOk = applied != null && padTarget.getPaddingTop() == applied;
            boolean lifted = overlapUnderBar(appBar, list) > 0;
            if (padOk && lifted) {
                return;
            }
            if (!lifted) {
                liftWrapperUnderBar(appBar, list);
            }
            if (!padOk && applied != null) {
                padTarget.setPadding(padTarget.getPaddingLeft(), applied,
                        padTarget.getPaddingRight(), padTarget.getPaddingBottom());
                XLog.i("sub-page list padding restored to " + applied + "px");
            }
            return;
        }
        int top = justSwitched ? appBar.getHeight() : overlapUnderBar(appBar, list);
        if (top <= 0) {
            top = liftWrapperUnderBar(appBar, list);
        }
        if (top <= 0) {
            return;
        }
        padTarget.setPadding(padTarget.getPaddingLeft(), top, padTarget.getPaddingRight(),
                padTarget.getPaddingBottom());
        LIST_PADDING.put(padTarget, top);
        resetScrollToTop(list);
        XLog.i("sub-page list padded to " + top + "px (bar " + appBar.getHeight()
                + ", " + padTarget.getClass().getSimpleName() + ")");
    }

    private static boolean isScrollView(View view) {
        return view instanceof android.widget.ScrollView
                || view.getClass().getName().endsWith("NestedScrollView");
    }

    /**
     * "我的设备" (also reached as "通用") lays its scroll view out below the bar
     * and draws MIUI's version card as a sibling on top of it — nothing can
     * slide under the bar, so the shared bar treatment has nothing to show.
     * Lifting that wrapper by the bar height (a negative top margin; the
     * match-parent height grows with it) and padding the list by the same
     * amount keeps the page identical at rest, but gives scrolled rows a place
     * under the bar. The version card itself is not moved: MIUI keeps fading it
     * on its own.
     *
     * <p>Only pages that really host the version card are touched.</p>
     */
    private static int liftWrapperUnderBar(View appBar, View list) {
        try {
            View host = null;
            View wrapper = list;
            while (wrapper.getParent() instanceof View) {
                View parent = (View) wrapper.getParent();
                if (parent.findViewById(versionCardId(parent.getContext())) != null) {
                    host = parent;
                    break;
                }
                wrapper = parent;
            }
            if (host == null) {
                return 0;
            }
            int barHeight = appBar.getHeight();
            if (barHeight <= 0) {
                return 0;
            }
            int[] barLoc = new int[2];
            int[] wrapLoc = new int[2];
            appBar.getLocationOnScreen(barLoc);
            wrapper.getLocationOnScreen(wrapLoc);
            int barBottom = barLoc[1] + barHeight;
            if (wrapLoc[1] < barBottom - 1 || wrapLoc[1] > barBottom + 1) {
                // The wrapper is not exactly at the bar's lower edge; leave it.
                return 0;
            }
            ViewGroup.LayoutParams params = wrapper.getLayoutParams();
            if (!(params instanceof ViewGroup.MarginLayoutParams)) {
                return 0;
            }
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            margins.topMargin -= barHeight;
            if (margins.height > 0) {
                margins.height += barHeight;
            }
            wrapper.setLayoutParams(margins);
            LIFTED_LISTS.add(list);
            // The miuix frame layout keeps the child's top but grows it with the
            // negative margin, so the list ends up above its parent's bounds.
            // Drop the parent clips; they are re-applied by MIUI on every
            // layout, so the pre-draw listener takes them off again per frame.
            for (View v = wrapper; v != null; v = v.getParent() instanceof View
                    ? (View) v.getParent() : null) {
                if (v instanceof ViewGroup) {
                    ((ViewGroup) v).setClipChildren(false);
                    ((ViewGroup) v).setClipToPadding(false);
                }
                if (v == host) {
                    break;
                }
            }
            XLog.i("sub-page wrapper lifted " + barHeight + "px (margin "
                    + margins.topMargin + ", height " + margins.height + ")");
            return barHeight;
        } catch (Throwable t) {
            XLog.w("sub-page wrapper lift failed: " + t);
            return 0;
        }
    }

    private static int versionCardId(Context context) {
        if (sVersionCardId == 0 && context != null) {
            sVersionCardId = context.getResources().getIdentifier(
                    "miui_version_card_view", "id", "com.android.settings");
        }
        return sVersionCardId;
    }

    /** How far the list's top is above the bar's lower edge, in pixels. */
    private static int overlapUnderBar(View appBar, View list) {
        if (appBar == null || list == null || appBar.getHeight() <= 0) {
            return 0;
        }
        int[] barLoc = new int[2];
        int[] listLoc = new int[2];
        appBar.getLocationOnScreen(barLoc);
        list.getLocationOnScreen(listLoc);
        return Math.max(0, barLoc[1] + appBar.getHeight() - listLoc[1]);
    }

    /**
     * A padding change alone keeps the already attached first row at its old
     * local coordinate. Once overlay mode drops the list to the window top that
     * leftover coordinate reads as a scroll offset: the page opens already
     * scrolled, so the top bar and its hairline are visible before the user
     * touches anything. Anchoring row 0 to the new top padding clears it.
     */
    private static void resetScrollToTop(View list) {
        try {
            if (list.getClass().getName().endsWith("RecyclerView")) {
                UiTweaks.call(list, "scrollToPosition", Integer.valueOf(0));
            } else {
                list.scrollTo(0, 0);
            }
        } catch (Throwable t) {
            XLog.w("sub-page scroll reset failed: " + t);
        }
    }

    /* -------------------------------- watcher --------------------------------- */

    private static void attachScrollWatcher(final Activity activity, final View decor) {
        View list = findScrollable(decor);
        if (list == null || !SCROLL_WATCHED.add(list)) {
            return;
        }
        list.getViewTreeObserver().addOnScrollChangedListener(
                new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        clearListClip(decor);
                        // The hairline appears as soon as a row slides under
                        // the bar and disappears again at the very top.
                        applyDivider(activity, decor);
                    }
                });
        list.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        // MIUI re-applies the clip (and, on the device page, the
                        // clipChildren) after every layout; take them off right
                        // before the frame is drawn.
                        if (list.getClipBounds() != null) {
                            list.setClipBounds(null);
                        }
                        if (LIFTED_LISTS.contains(list)) {
                            clearAncestorClip(list);
                        }
                        ensureBarBackground(appBarOf(activity, decor));
                        return true;
                    }
                });
        XLog.i("sub-page top bar watching "
                + list.getClass().getName() + " clip=" + list.getClipBounds()
                + " top=" + windowTop(list));
    }

    /** Clears clips MIUI puts on the wrappers around the list. */
    private static void clearAncestorClip(View list) {
        Object current = list == null ? null : list.getParent();
        for (int i = 0; i < 6 && current instanceof View; i++) {
            View view = (View) current;
            if (view.getClipBounds() != null) {
                view.setClipBounds(null);
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                if (group.getClipChildren()) {
                    group.setClipChildren(false);
                }
                if (group.getClipToPadding()) {
                    group.setClipToPadding(false);
                }
            }
            current = view.getParent();
        }
    }

    /**
     * The page's own scrolling container: the shallowest supported scrollable
     * in the decor tree. Taking the first RecyclerView anywhere in the tree is
     * wrong — the "我的设备" page has a non-scrolling grid
     * ({@code description_grid}) below its real scroll view, and padding that
     * grid pushed the whole lower card down.
     */
    private static View findScrollable(View root) {
        if (root == null) {
            return null;
        }
        if (isScrollable(root)) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findScrollable(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Vertical scroll containers MIUI uses for settings pages. */
    private static boolean isScrollable(View view) {
        // AboutIos replaces the "关于本机" page with its own ScrollView and pads
        // it itself; tagging keeps this class away from it.
        if ("ios_about_page".equals(view.getTag())) {
            return false;
        }
        if (view instanceof android.widget.ScrollView
                || view instanceof android.widget.ListView) {
            return true;
        }
        String name = view.getClass().getName();
        if (name.endsWith("NestedScrollView")) {
            return true;
        }
        if (!name.endsWith("RecyclerView")) {
            return false;
        }
        // Tab strips and icon carousels are horizontal RecyclerViews; they are
        // never the page list that has to slide under the bar.
        Boolean cached = VERTICAL_LISTS.get(view);
        if (cached != null) {
            return cached;
        }
        boolean vertical = true;
        try {
            Object layoutManager = UiTweaks.call(view, "getLayoutManager");
            Object canScroll = layoutManager == null
                    ? null : UiTweaks.call(layoutManager, "canScrollVertically");
            if (Boolean.FALSE.equals(canScroll)) {
                vertical = false;
            }
        } catch (Throwable ignored) {
        }
        VERTICAL_LISTS.put(view, Boolean.valueOf(vertical));
        return vertical;
    }

    /** Drops the clip MIUI puts on the list while the header collapses. */
    private static void clearListClip(View root) {
        if (root == null) {
            return;
        }
        if (isScrollable(root) && root.getClipBounds() != null) {
            root.setClipBounds(null);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (isScrollable(child) && child.getClipBounds() != null) {
                    child.setClipBounds(null);
                } else if (child instanceof ViewGroup) {
                    // MIUI sometimes puts the clip on the wrapper around the list.
                    clearListClip(child);
                }
            }
        }
    }

    /* --------------------------------- helpers -------------------------------- */

    private static int windowTop(View view) {
        int top = 0;
        View current = view;
        while (current != null) {
            top += current.getTop();
            if (!(current.getParent() instanceof View)) {
                break;
            }
            current = (View) current.getParent();
        }
        return top;
    }

    private static View appBarOf(Activity activity, View decor) {
        if (sAppBarId == 0) {
            sAppBarId = activity.getResources().getIdentifier(
                    "action_bar_container", "id", "com.android.settings");
        }
        if (sAppBarId == 0) {
            return null;
        }
        return decor.findViewById(sAppBarId);
    }

    private static void logMissing(Activity activity, View decor) {
        String name = activity.getClass().getName();
        if (!LOGGED_MISSING.add(name)) {
            return;
        }
        XLog.w("sub-page top bar: no action_bar_container on " + name
                + "\n" + dump(decor, 0, 4));
    }

    private static void logTreeOnce(Activity activity, View decor) {
        String name = activity.getClass().getName();
        if (!LOGGED_TREE.add(name)) {
            return;
        }
        View bar = appBarOf(activity, decor);
        Object parent = bar == null ? null : bar.getParent();
        XLog.i("sub-page top bar tree " + name
                + "\nappBar=" + describe(bar)
                + "\nparent=" + (parent instanceof View ? describe((View) parent) : "null")
                + "\nlist=" + describe(findScrollable(decor))
                + "\n" + dump(decor, 0, 3));
    }

    private static String describe(View view) {
        if (!(view instanceof View)) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(view.getClass().getName());
        int id = view.getId();
        if (id != View.NO_ID) {
            try {
                sb.append('#').append(view.getResources().getResourceEntryName(id));
            } catch (Throwable ignored) {
            }
        }
        sb.append(" bounds=[").append(view.getLeft()).append(',').append(view.getTop())
                .append("][").append(view.getRight()).append(',').append(view.getBottom())
                .append("] clip=").append(view.getClipBounds());
        return sb.toString();
    }

    private static String dump(View view, int depth, int maxDepth) {
        StringBuilder sb = new StringBuilder();
        if (view == null || depth > maxDepth) {
            return sb.toString();
        }
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        sb.append(describe(view));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = Math.min(group.getChildCount(), 24);
            for (int i = 0; i < count; i++) {
                sb.append('\n').append(dump(group.getChildAt(i), depth + 1, maxDepth));
            }
        }
        return sb.toString();
    }
}
