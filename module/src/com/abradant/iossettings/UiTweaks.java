package com.abradant.iossettings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

    /* --------------------- home: blur only while scrolling ------------------ */

    static void attachScrollBlur(Object fragment) {
        try {
            Activity activity = activityOf(fragment);
            if (activity == null || activity.isFinishing()) {
                return;
            }
            View appBar = activity.findViewById(activity.getResources().getIdentifier(
                    "action_bar_container", "id", "com.android.settings"));
            Object listObject = HeaderData.field(fragment, "mListView");
            if (appBar == null || !(listObject instanceof View)) {
                XLog.w("scroll blur: appBar=" + appBar + " list=" + listObject);
                return;
            }
            final View appBarView = appBar;
            final View list = (View) listObject;
            final boolean dark = isDark(activity);
            final int solid = dark ? 0xFF000000 : 0xFFF2F2F7;
            final int translucent = dark ? 0xCC000000 : 0xCCF2F2F7;
            final int[] state = {-1};

            final Runnable update = new Runnable() {
                @Override
                public void run() {
                    try {
                        int offset = ((Integer) call(list, "computeVerticalScrollOffset")).intValue();
                        boolean scrolled = offset > dp(appBarView.getContext(), 4);
                        int s = scrolled ? 1 : 0;
                        if (state[0] == s) {
                            return;
                        }
                        state[0] = s;
                        if (scrolled) {
                            int blurRadius = Prefs.getInt(appBarView.getContext(),
                                    Prefs.TOPBAR_BLUR, Prefs.DEFAULT_TOPBAR_BLUR);
                            appBarView.setBackgroundColor(translucent);
                            if (blurRadius > 0) {
                                callStatic(BLUR, "setBackgroundBlur", appBarView,
                                        Integer.valueOf(blurRadius));
                                callStatic(BLUR, "setBackgroundBlurRadius", appBarView,
                                        Integer.valueOf(blurRadius));
                            } else {
                                callStatic(BLUR, "clearBackgroundBlur", appBarView);
                            }
                        } else {
                            appBarView.setBackgroundColor(solid);
                            callStatic(BLUR, "clearBackgroundBlur", appBarView);
                        }
                        XLog.i("top bar blur=" + scrolled + " offset=" + offset
                                + " radius=" + Prefs.getInt(appBarView.getContext(),
                                Prefs.TOPBAR_BLUR, Prefs.DEFAULT_TOPBAR_BLUR));
                    } catch (Throwable t) {
                        XLog.e("scroll blur update failed", t);
                    }
                }
            };
            list.getViewTreeObserver().addOnScrollChangedListener(
                    new android.view.ViewTreeObserver.OnScrollChangedListener() {
                        @Override
                        public void onScrollChanged() {
                            update.run();
                        }
                    });
            update.run();
            XLog.i("scroll blur attached");
        } catch (Throwable t) {
            XLog.e("attachScrollBlur failed", t);
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

    private static Object callStatic(String className, String name, Object... args) {
        try {
            Class<?> c = Class.forName(className, false,
                    UiTweaks.class.getClassLoader());
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
        } catch (Throwable t) {
            XLog.w("static " + className + "." + name + " failed: " + t);
        }
        return null;
    }

    static boolean isDarkMode(Context c) {        int mode = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static boolean isDark(Context c) {
        int mode = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static Activity activityOf(Object fragment) {
        try {
            Method m = fragment.getClass().getMethod("getActivity");
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
