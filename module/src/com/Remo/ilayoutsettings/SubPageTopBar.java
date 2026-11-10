package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

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
 */
final class SubPageTopBar {

    private static final String BLUR = "miuix.core.util.MiuiBlurUtils";
    /** Same id {@link UiTweaks} uses for the home page's action bar. */
    private static int sAppBarId;
    /** Last tint pushed into a bar, so scroll callbacks are cheap. */
    private static final Map<View, Integer> APPLIED_BG = new WeakHashMap<>();
    /** Last blur radius (px) pushed through {@code setBackgroundBlur}. */
    private static final Map<View, Integer> APPLIED_BLUR = new WeakHashMap<>();
    /** Activities whose decor tree already got the first passes. */
    private static final Set<Activity> WATCHING =
            java.util.Collections.newSetFromMap(new WeakHashMap<Activity, Boolean>());
    /** Activities whose scrollable already has the listener. */
    private static final Set<Activity> SCROLL_WATCHED =
            java.util.Collections.newSetFromMap(new WeakHashMap<Activity, Boolean>());
    /** Activity classes that already logged the missing-app-bar diagnostic. */
    private static final Set<String> LOGGED_MISSING = new java.util.HashSet<>();
    /** Activity classes that already logged the view tree. */
    private static final Set<String> LOGGED_TREE = new java.util.HashSet<>();
    /** ActionBarOverlayLayouts that already switched to overlay mode. */
    private static final Map<View, Boolean> OVERLAY_ON = new WeakHashMap<>();
    /** Lists that already got the expanded-bar padding (value = top inset). */
    private static final Map<View, Integer> LIST_PADDING = new WeakHashMap<>();

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
                            SCROLL_WATCHED.remove(param.thisObject);
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
            }
        });
    }

    /** Tint + blur + unclip; cheap enough to run on every scroll frame. */
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
            boolean overlay = enableOverlay(appBar);
            if (overlay) {
                padListUnderBar(appBar, decor);
            }

            Context context = appBar.getContext();
            boolean dark = UiTweaks.isDarkMode(context);
            applyDivider(activity, decor);
            int alphaPercent = Math.max(0, Math.min(100, Prefs.getInt(
                    context, Prefs.TOPBAR_ALPHA, Prefs.DEFAULT_TOPBAR_ALPHA)));
            int alpha = Math.round(255f * alphaPercent / 100f);
            int base = dark ? 0x000000 : 0xF2F2F7;
            int background = (alpha << 24) | base;

            Integer lastBg = APPLIED_BG.get(appBar);
            boolean tintChanged = lastBg == null || lastBg != background;
            if (tintChanged) {
                appBar.setBackgroundColor(background);
                APPLIED_BG.put(appBar, background);
            }

            int radius = Math.max(0, Prefs.topBarBlurRadius(context));
            int radiusPx = Math.max(0, Math.round(radius
                    * context.getResources().getDisplayMetrics().density));
            Integer lastBlur = APPLIED_BLUR.get(appBar);
            if (tintChanged || lastBlur == null || lastBlur != radiusPx) {
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
            attachScrollWatcher(activity, decor);
        } catch (Throwable t) {
            XLog.w("sub-page top bar refresh failed: " + t);
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
            // some MIUI lists, so this is the reliable test.
            boolean scrolled = list != null && list.canScrollVertically(-1);
            UiTweaks.applyDivider(appBar, dark ? 0x4DAAAAAA : 0x1F000000,
                    scrolled && Prefs.topBarDivider(context));
        } catch (Throwable t) {
            XLog.w("sub-page divider failed: " + t);
        }
    }

    /**
     * Lets the list draw under the bar. Sub pages are laid out as a plain
     * column (list top == bar bottom), so clearing the clip alone leaves the
     * blur nothing to sample. MIUI's ActionBarOverlayLayout has an overlay
     * mode for exactly this; flipping it makes the content view extend to the
     * window top, the way the home page's NestedHeaderLayout already works.
     */
    private static boolean enableOverlay(View appBar) {
        try {
            Object parent = appBar == null ? null : appBar.getParent();
            if (!(parent instanceof View)) {
                return false;
            }
            View overlay = (View) parent;
            if (!overlay.getClass().getName().endsWith("ActionBarOverlayLayout")) {
                return false;
            }
            Object inOverlay = UiTweaks.call(overlay, "isInOverlayMode");
            if (Boolean.TRUE.equals(inOverlay)) {
                return true;
            }
            if (OVERLAY_ON.put(overlay, Boolean.TRUE) != null) {
                return false;
            }
            UiTweaks.call(overlay, "setOverlayMode", Boolean.TRUE);
            overlay.requestLayout();
            XLog.i("sub-page action bar overlay mode on");
            return true;
        } catch (Throwable t) {
            XLog.w("sub-page overlay mode failed: " + t);
            return false;
        }
    }

    /**
     * Overlay mode lays the list out from the window top; without a top inset
     * the first card sits behind the large title. The padding is the expanded
     * bar height, so at rest the rows start below the bar and, while scrolling,
     * they slide under it (the bar collapses and the blur has content to sample).
     */
    private static void padListUnderBar(View appBar, View decor) {
        View list = findScrollable(decor);
        if (list == null || list.getHeight() <= 0) {
            return;
        }
        Integer applied = LIST_PADDING.get(list);
        if (applied != null) {
            return;
        }
        int top = appBar.getHeight();
        if (top <= 0) {
            return;
        }
        if (list instanceof ViewGroup) {
            ((ViewGroup) list).setClipToPadding(false);
        }
        list.setPadding(list.getPaddingLeft(), top, list.getPaddingRight(),
                list.getPaddingBottom());
        LIST_PADDING.put(list, top);
        XLog.i("sub-page list padded to " + top + "px (bar " + appBar.getHeight() + ")");
    }

    private static void attachScrollWatcher(final Activity activity, final View decor) {
        if (SCROLL_WATCHED.contains(activity)) {
            return;
        }
        View list = findScrollable(decor);
        if (list == null) {
            return;
        }
        SCROLL_WATCHED.add(activity);
        list.getViewTreeObserver().addOnScrollChangedListener(
                new android.view.ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        clearListClip(decor);
                        // The hairline appears as soon as a row slides under
                        // the bar and disappears again at the very top.
                        applyDivider(activity, decor);
                    }
                });
        XLog.i("sub-page top bar watching "
                + list.getClass().getName() + " clip=" + list.getClipBounds()
                + " top=" + windowTop(list));
    }

    private static View findScrollable(View root) {
        if (root == null) {
            return null;
        }
        if (root.getClass().getName().endsWith("RecyclerView")
                || root instanceof android.widget.ListView) {
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

    private static boolean isScrollable(View view) {
        return view.getClass().getName().endsWith("RecyclerView")
                || view instanceof android.widget.ListView;
    }

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
