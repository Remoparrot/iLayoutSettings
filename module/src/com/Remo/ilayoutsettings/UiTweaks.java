package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Small visual tweaks that ride on the native widgets:
 *
 * <ul>
 *   <li>bigger Xiaomi account avatar on the home page (like the iOS reference)</li>
 *   <li>iOS style title on second-level pages: hide MIUI's large (expand) title
 *       and show the collapsed title centred</li>
 *   <li>top bar blur that only appears while the list is scrolled</li>
 * </ul>
 */
final class UiTweaks {

    private static final String BLUR = "miuix.core.util.MiuiBlurUtils";
    private static final java.util.Set<View> REFRESHING =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());
    /** One bottom hairline drawable per action-bar container (drawn in its overlay). */
    private static final java.util.Map<View, android.graphics.drawable.ColorDrawable> DIVIDERS =
            new java.util.WeakHashMap<>();
    /** Last background colour pushed into an action bar, so idle polls are free. */
    private static final java.util.Map<View, Integer> APPLIED_BG =
            new java.util.WeakHashMap<>();
    /** Last radius pushed through {@code MiuiBlurUtils.setBackgroundBlur}. */
    private static final java.util.Map<View, Integer> APPLIED_APPBAR_BLUR =
            new java.util.WeakHashMap<>();
    /** Header layouts that already have the slow pref poll running. */
    private static final java.util.Map<View, Boolean> POLLING =
            new java.util.WeakHashMap<>();

    /** {@code com.android.settings:id/action_bar_container}, resolved once. */
    private static int sAppBarId;

    private UiTweaks() {
    }

    /* ------------------------- home: account avatar ------------------------- */

    /** Every header icon gets the same box, so mixed theme drawables line up. */
    private static void uniformIcon(Context context, View itemView) {
        try {
            View icon = itemView.findViewById(android.R.id.icon);
            if (!(icon instanceof android.widget.ImageView)) {
                return;
            }
            int size = dp(context, 28);
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            if (lp != null) {
                lp.width = size;
                lp.height = size;
                icon.setLayoutParams(lp);
            }
            icon.setMinimumWidth(size);
            icon.setMinimumHeight(size);
            ((android.widget.ImageView) icon).setScaleType(
                    android.widget.ImageView.ScaleType.FIT_CENTER);
            icon.setTranslationX(dp(context, 3));
            shiftText(context, itemView);
        } catch (Throwable t) {
            XLog.w("uniform icon failed: " + t);
        }
    }

    /** Keeps title / summary aligned with the shifted icon. */
    private static void shiftText(Context context, View itemView) {
        int shift = dp(context, 3);
        View title = itemView.findViewById(android.R.id.title);
        if (title != null) {
            title.setTranslationX(shift);
        }
        View summary = itemView.findViewById(android.R.id.summary);
        if (summary != null) {
            summary.setTranslationX(shift);
        }
    }

    static void tweakHeaderRow(Object header, View itemView) {
        try {
            Context context = itemView.getContext();
            Resources res = context.getResources();
            String name = "";
            Object id = HeaderData.field(header, "id");
            long value = id instanceof Number ? ((Number) id).longValue() : -1L;
            if (value > 0 && value <= Integer.MAX_VALUE) {
                try {
                    name = res.getResourceEntryName((int) value);
                } catch (Throwable ignored) {
                }
            }
            String title = String.valueOf(HeaderData.field(header, "title"));
            String summary = String.valueOf(HeaderData.field(header, "summary"));
            boolean account = "mi_account_settings".equals(name)
                    || summary.contains("云服务") || summary.contains("会员权益")
                    || (title.contains("账号") && title.length() < 12);
            if (!account) {
                // Fallback: look at what MIUI actually bound into the summary row.
                View summaryView = itemView.findViewById(android.R.id.summary);
                if (summaryView instanceof android.widget.TextView) {
                    String bound = ((android.widget.TextView) summaryView).getText().toString();
                    account = bound.contains("云服务") || bound.contains("会员权益");
                }
            }
            if (!account) {
                uniformIcon(context, itemView);
                return;
            }
            View icon = itemView.findViewById(android.R.id.icon);
            if (icon == null) {
                XLog.w("account row icon view not found");
                return;
            }
            int size = dp(context, 56);
            ViewGroup.LayoutParams lp = icon.getLayoutParams();
            if (lp != null) {
                lp.width = size;
                lp.height = size;
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) lp;
                    margins.topMargin = dp(context, 12);
                    margins.bottomMargin = dp(context, 12);
                }
                icon.setLayoutParams(lp);
            }
            icon.setMinimumWidth(size);
            icon.setMinimumHeight(size);
            if (icon instanceof android.widget.ImageView) {
                ((android.widget.ImageView) icon).setScaleType(
                        android.widget.ImageView.ScaleType.CENTER_CROP);
            }
            // iOS style round avatar.
            icon.setClipToOutline(true);
            icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
            icon.setTranslationX(dp(context, 3));
            shiftText(context, itemView);
            // iOS wording for the account subtitle. Also write it into the header
            // so every later rebind (resume / scroll recycling) keeps it.
            HeaderData.setField(header, "summary", "Apple账户、iCloud+等");
            View summaryView = itemView.findViewById(android.R.id.summary);
            if (summaryView instanceof android.widget.TextView) {
                ((android.widget.TextView) summaryView).setText("Apple账户、iCloud+等");
            }
            itemView.post(new Runnable() {
                @Override
                public void run() {
                    // MIUI may write the summary right after our hook; re-apply once.
                    View v = itemView.findViewById(android.R.id.summary);
                    if (v instanceof android.widget.TextView) {
                        ((android.widget.TextView) v).setText("Apple账户、iCloud+等");
                    }
                }
            });
            // Bigger account name, like the iOS reference.
            View titleView = itemView.findViewById(android.R.id.title);
            if (titleView instanceof android.widget.TextView) {
                android.widget.TextView tv = (android.widget.TextView) titleView;
                tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
                tv.setTypeface(android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.NORMAL));
            }
            XLog.i("account avatar resized to " + size + "px, id=" + name);
        } catch (Throwable t) {
            XLog.e("account avatar tweak failed", t);
        }
    }

    /* ---------------------- sub pages: centred small title ------------------- */

    static void restyleSubPageTitle(Activity activity) {
        try {
            if (activity == null || activity.isFinishing()) {
                return;
            }
            if ("com.android.settings.MiuiSettings".equals(activity.getClass().getName())) {
                return; // keep the big "设置" title on the home page
            }
            final CharSequence title = activity.getTitle();
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Object actionBar = call(activity, "getSupportActionBar");
                        if (actionBar == null) {
                            return;
                        }
                        Object collapse = findOfType(actionBar, "CollapseTitle", 4);
                        if (collapse == null) {
                            XLog.w("CollapseTitle not found, leave the app bar untoched");
                            return;
                        }
                        // Resolve the page name: activity title, then action bar
                        // title, then whatever the compact title already has.
                        CharSequence resolved = title;
                        if (resolved == null || resolved.length() == 0) {
                            Object barTitle = call(actionBar, "getTitle");
                            if (barTitle instanceof CharSequence) {
                                resolved = (CharSequence) barTitle;
                            }
                        }
                        if (resolved == null || resolved.length() == 0) {
                            Object current = call(collapse, "getTitle");
                            if (current instanceof CharSequence) {
                                resolved = (CharSequence) current;
                            }
                        }
                        if (resolved == null || resolved.length() == 0) {
                            // Last resort: the big title text (even if hidden) and
                            // finally a generic label, so the bar is never empty.
                            Object expandTitle = call(call(actionBar, "getExpandTitle"), "getTitle");
                            if (expandTitle instanceof CharSequence
                                    && ((CharSequence) expandTitle).length() > 0) {
                                resolved = (CharSequence) expandTitle;
                            }
                        }
                        if (resolved == null || resolved.length() == 0) {
                            resolved = "设置";
                        }
                        final CharSequence finalTitle = resolved;
                        XLog.i("sub-page title resolved: " + finalTitle);
                        // Only hide MIUI's big title once we have a compact one
                        // to show, otherwise the page would lose its header.
                        Object expand = call(actionBar, "getExpandTitle");
                        if (expand != null) {
                            call(expand, "setVisible", Boolean.FALSE);
                            call(expand, "setVisibility", Integer.valueOf(View.GONE));
                        }
                        call(collapse, "setTitle", finalTitle);
                        call(collapse, "setVisibility", Integer.valueOf(View.VISIBLE));
                        call(collapse, "setTitleVisibility", Integer.valueOf(View.VISIBLE));
                        call(collapse, "updateTitleCenter", Boolean.TRUE);
                        if (collapse instanceof View) {
                            // Keep it visible regardless of the scroll position.
                            ((View) collapse).setTag(TitleFix.TAG);
                            ((View) collapse).setAlpha(1f);
                        }
                        Object layout = call(collapse, "getLayout");
                        if (layout instanceof View) {
                            ((View) layout).setVisibility(View.VISIBLE);
                        }
                        call(actionBar, "setTitle", title);
                        Object visible = call(collapse, "getVisibility");
                        if (visible instanceof Integer && (Integer) visible != View.VISIBLE) {
                            // Could not make the compact title visible: put the
                            // native big title back so the page still has a name.
                            if (expand != null) {
                                call(expand, "setVisible", Boolean.TRUE);
                                call(expand, "setVisibility", Integer.valueOf(View.VISIBLE));
                            }
                            XLog.w("compact title still hidden, restored expand title");
                            return;
                        }
                        XLog.i("sub-page title restyled: " + title);
                        final View collapseView = (View) collapse;
                        final Activity activityRef = activity;
                        // The activity title may only be set after onPostCreate;
                        // keep the compact title in sync with the real page name.
                        if (REFRESHING.add(collapseView)) {
                            collapseView.getViewTreeObserver().addOnGlobalLayoutListener(
                                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                                        @Override
                                        public void onGlobalLayout() {
                                            try {
                                                CharSequence current = activityRef.getTitle();
                                                if (current != null && current.length() > 0) {
                                                    call(collapseView, "setTitle", current);
                                                }
                                            } catch (Throwable ignored) {
                                            }
                                        }
                                    });
                        }
                        collapseView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                CharSequence current = activityRef.getTitle();
                                if (current != null && current.length() > 0) {
                                    call(collapseView, "setTitle", current);
                                }
                            }
                        }, 300);
                    } catch (Throwable t) {
                        XLog.e("sub-page title restyle failed", t);
                    }
                }
            });
        } catch (Throwable t) {
            XLog.e("restyleSubPageTitle failed", t);
        }
    }

    /* ------------------ top bar: blur / opacity / hairline ------------------- */

    /**
     * Installs the top-bar hooks.
     *
     * <p>They hang off {@code NestedHeaderLayout} instead of the home fragment.
     * The fragment view — and with it the action bar container — is rebuilt on
     * every uiMode change, and a listener captured for the old view silently
     * stopped receiving scroll events; that is exactly why the hairline
     * vanished after switching light/dark inside Settings. A class-level hook
     * also catches the very first scroll frame, before the old 500 ms poll had
     * a chance to apply the blur radius.</p>
     */
    static void installTopBar(ClassLoader cl) {
        try {
            Class<?> nested = XposedHelpers.findClassIfExists(
                    "miuix.nestedheader.widget.NestedHeaderLayout", cl);
            if (nested == null) {
                XLog.w("NestedHeaderLayout not found, top bar overlay disabled");
                return;
            }
            XC_MethodHook refresh = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object self = param.thisObject;
                    if (!isHeaderLayout(self)) {
                        return;
                    }
                    View header = (View) self;
                    refreshTopBar(header);
                    ensurePoll(header);
                }
            };
            XposedBridge.hookAllMethods(nested, "onScrollingProgressUpdated", refresh);
            XposedBridge.hookAllMethods(nested, "onAttachedToWindow", refresh);
            XposedBridge.hookAllMethods(nested, "onConfigurationChanged", refresh);
            XLog.i("top bar hooks installed");
        } catch (Throwable t) {
            XLog.e("top bar hooks failed", t);
        }
    }

    private static boolean isHeaderLayout(Object value) {
        return value instanceof View
                && value.getClass().getName().endsWith("NestedHeaderLayout");
    }

    /** Re-applies the overlays for the home fragment (called from its hooks). */
    static void refreshTopBar(Object fragment) {
        Object header = HeaderData.field(fragment, "mNestedHeaderLayout");
        if (header instanceof View) {
            refreshTopBar((View) header);
            ensurePoll((View) header);
        }
    }

    /**
     * One pass over the top bar: hairline (with the <em>current</em> theme
     * colour), background tint and native blur radius.
     */
    private static void refreshTopBar(View header) {
        try {
            Context context = header.getContext();
            if (context == null) {
                return;
            }
            View appBar = appBarOf(header, context);
            if (appBar == null) {
                return;
            }
            boolean scrolled = isScrolled(scrollList(header, context), dp(context, 4));
            boolean headerOpen = Boolean.TRUE.equals(call(header, "isHeaderOpen"));
            boolean compact = scrolled && !headerOpen;
            boolean dark = isDark(context);

            int alphaPercent = Math.max(0, Math.min(100, Prefs.getInt(
                    context, Prefs.TOPBAR_ALPHA, Prefs.DEFAULT_TOPBAR_ALPHA)));
            int alpha = Math.round(255f * alphaPercent / 100f);
            int base = dark ? 0x000000 : 0xF2F2F7;
            int solid = dark ? 0xFF000000 : 0xFFF2F2F7;
            int background = scrolled ? ((alpha << 24) | base) : solid;

            // Re-read the theme every pass: toggling dark mode inside Settings
            // is a configuration change, not a new scroll, so a colour captured
            // once in attachScrollBlur() went stale (black hairline on a black
            // bar = "the divider disappeared").
            applyDivider(appBar, dark ? 0x4DAAAAAA : 0x1F000000,
                    compact && Prefs.topBarDivider(context));

            Integer lastBackground = APPLIED_BG.get(appBar);
            if (lastBackground == null || lastBackground.intValue() != background) {
                appBar.setBackgroundColor(background);
                APPLIED_BG.put(appBar, background);
            }

            // Only touch the helper's radius while the bar is actually visible.
            // Writing 0 when idle would fight the refreshBlur() hook (which
            // always primes the pref radius) and re-run refreshBlur forever.
            int radius = scrolled ? Prefs.topBarBlurRadius(context) : 0;
            Object helper = HeaderData.field(header, "mBlurUiHelper");
            if (scrolled && helper != null) {
                Object current = HeaderData.field(helper, "mBlurEffect");
                int applied = current instanceof Integer ? (Integer) current : Integer.MIN_VALUE;
                if (applied != radius) {
                    HeaderData.setIntField(helper, "mBlurEffect", radius);
                    call(helper, "refreshBlur");
                }
            }
            // Keep the action bar's own background blur in sync too. It sits
            // behind the native over-bg layer, but some themes show it alone.
            Integer lastAppBarBlur = APPLIED_APPBAR_BLUR.get(appBar);
            if (lastAppBarBlur == null || lastAppBarBlur.intValue() != radius) {
                APPLIED_APPBAR_BLUR.put(appBar, radius);
                ClassLoader hostLoader = context.getClassLoader();
                if (radius > 0) {
                    callStatic(hostLoader, BLUR, "setBackgroundBlur", appBar,
                            Integer.valueOf(radius));
                    callStatic(hostLoader, BLUR, "setBackgroundBlurRadius", appBar,
                            Integer.valueOf(radius));
                } else {
                    callStatic(hostLoader, BLUR, "clearBackgroundBlur", appBar);
                }
            }
        } catch (Throwable t) {
            // The header can be detached mid-pass; the next scroll recomputes.
            XLog.w("top bar refresh failed: " + t);
        }
    }

    /** Slow poll so slider changes land even while the list does not scroll. */
    private static void ensurePoll(final View header) {
        if (POLLING.put(header, Boolean.TRUE) != null) {
            return;
        }
        final Runnable[] poll = new Runnable[1];
        poll[0] = new Runnable() {
            @Override
            public void run() {
                if (!header.isAttachedToWindow()) {
                    POLLING.remove(header);
                    return;
                }
                refreshTopBar(header);
                header.postDelayed(poll[0], 700);
            }
        };
        header.postDelayed(poll[0], 700);
    }

    /** Resolves the action-bar container through the settings resources. */
    private static View appBarOf(View header, Context context) {
        try {
            if (sAppBarId == 0) {
                sAppBarId = context.getResources().getIdentifier(
                        "action_bar_container", "id", "com.android.settings");
            }
            if (sAppBarId == 0) {
                return null;
            }
            return header.getRootView().findViewById(sAppBarId);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The view that actually scrolls. {@code NestedHeaderLayout.mScrollableView}
     * is the {@code SpringBackLayout} wrapper on HyperOS 3, whose
     * {@code computeVerticalScrollOffset()} is always 0, so prefer the
     * {@code scroll_headers} RecyclerView and unwrap the spring-back shell.
     */
    private static Object scrollList(View header, Context context) {
        try {
            int id = context.getResources().getIdentifier(
                    "scroll_headers", "id", "com.android.settings");
            if (id != 0) {
                View list = header.getRootView().findViewById(id);
                if (list != null) {
                    return list;
                }
            }
        } catch (Throwable t) {
            XLog.w("scroll_headers lookup failed: " + t);
        }
        Object fallback = HeaderData.field(header, "mScrollableView");
        if (fallback instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) fallback;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                String name = child.getClass().getSimpleName();
                if (name.contains("RecyclerView") || name.contains("ListView")) {
                    return child;
                }
            }
        }
        return fallback;
    }

    private static boolean isScrolled(Object list, int threshold) {        if (!(list instanceof View)) {
            return false;
        }
        int offset = 0;
        Object value = call(list, "computeVerticalScrollOffset");
        if (value instanceof Integer) {
            offset = (Integer) value;
        }
        if (offset > threshold) {
            return true;
        }
        // Some MIUI RecyclerViews report 0 but still move their first child up.
        if (list instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) list;
            if (group.getChildCount() > 0
                    && group.getChildAt(0).getTop() < -threshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws the iOS-style hairline at the very bottom of the action bar.
     *
     * <p>A {@link android.graphics.drawable.ColorDrawable} in the view overlay is
     * used instead of a child view so the line sits on the real bottom edge even
     * when the action bar has padding, and never takes part in layout / touch.
     * The overlay is drawn after the bar's content, so the divider stays visible
     * above the native header and the blur surface.</p>
     */
    private static void applyDivider(View appBar, int color, boolean visible) {
        try {
            int width = appBar.getWidth();
            int height = appBar.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            // The ActionBarContainer in MIUI is drawn by its own code path and
            // never paints its ViewOverlay, so the line lives in the overlay of
            // its parent instead (a plain FrameLayout that does draw it).
            View parent = appBar.getParent() instanceof View
                    ? (View) appBar.getParent() : appBar;
            android.graphics.drawable.ColorDrawable divider = DIVIDERS.get(parent);
            if (divider == null) {
                divider = new android.graphics.drawable.ColorDrawable(color);
                DIVIDERS.put(parent, divider);
                parent.getOverlay().add(divider);
            } else {
                divider.setColor(color);
            }
            int thickness = Math.max(1, dp(appBar.getContext(), 0.5f));
            int top = appBar.getTop() + height - thickness;
            android.graphics.Rect want = visible
                    ? new android.graphics.Rect(appBar.getLeft(), top,
                    appBar.getLeft() + width, top + thickness)
                    : new android.graphics.Rect(0, 0, 0, 0);
            if (!want.equals(divider.getBounds())) {
                divider.setBounds(want);
                parent.invalidate();
            }
        } catch (Throwable t) {
            XLog.w("top bar divider failed: " + t);
        }
    }

    /* ------------------------------- helpers -------------------------------- */

    static Object findOfType(Object root, String typeName, int depth) {
        if (root == null || depth < 0) {
            return null;
        }
        if (root.getClass().getSimpleName().contains(typeName)) {
            return root;
        }
        for (Class<?> c = root.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType().isPrimitive() || f.getType().isArray()) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    Object value = f.get(root);
                    if (value == null) {
                        continue;
                    }
                    if (value.getClass().getSimpleName().contains(typeName)) {
                        return value;
                    }
                    String cn = value.getClass().getName();
                    if (cn.startsWith("miuix.") || cn.startsWith("androidx.appcompat.")) {
                        Object nested = findOfType(value, typeName, depth - 1);
                        if (nested != null) {
                            return nested;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    static Object call(Object target, String name, Object... args) {
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

    private static Object callStatic(ClassLoader loader, String className, String name,
                                     Object... args) {
        try {
            Class<?> c;
            try {
                c = Class.forName(className, false, loader);
            } catch (Throwable notFound) {
                c = XposedHelpers.findClassIfExists(className, loader);
            }
            if (c == null) {
                XLog.w("static " + className + "." + name + ": class not found via host loader");
                return null;
            }
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
                m.setAccessible(true);
                return m.invoke(null, args);
            }
            XLog.w("static " + className + "." + name + ": no matching method");
        } catch (Throwable t) {
            XLog.w("static " + className + "." + name + " failed: " + t);
        }
        return null;
    }

    static boolean isDarkMode(Context c) {
        int mode = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean isDark(Context c) {
        int mode = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
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
