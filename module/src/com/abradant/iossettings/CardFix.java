package com.abradant.iossettings;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * MIUI draws every card row with its own background / foreground drawable on
 * top of the card decoration. On the last row of a card those drawables
 * overflow the card's rounded bottom edge and show up as a double rounded
 * rectangle, and they also make the last icon look bigger than the others.
 *
 * <p>While a last-in-group row is being bound we suppress the four theme
 * drawables that cause it:</p>
 * <pre>
 *   miuix_preference_item_background_light/dark
 *   miuix_recyclerview_card_item_press_fg_light/dark
 * </pre>
 * <p>They are replaced with a fully transparent drawable for that one bind,
 * and the row's background / foreground are cleared afterwards as well.</p>
 */
final class CardFix {

    private static final ThreadLocal<Boolean> SUPPRESS = new ThreadLocal<>();
    private static final Map<View, Drawable> BACKGROUNDS = new WeakHashMap<>();
    private static final Map<View, Drawable> FOREGROUNDS = new WeakHashMap<>();

    private CardFix() {
    }

    static void install(ClassLoader cl) {
        hookAdapter(cl);
        hookResources(cl);
    }

    private static void hookAdapter(ClassLoader cl) {
        try {
            Class<?> adapter = XposedHelpers.findClass(
                    "com.android.settings.MiuiSettings$HeaderAdapter", cl);
            Class<?> holder = XposedHelpers.findClass(
                    "com.android.settings.MiuiSettings$HeaderViewHolder", cl);
            XposedBridge.hookAllMethods(adapter, "onBindViewHolder", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Object header = headerAt(param.thisObject, param.args);
                        rewriteAccountSummary(header);
                        if (isLastRow(param.thisObject, param.args)) {
                            SUPPRESS.set(Boolean.TRUE);
                        }
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        SUPPRESS.remove();
                        if (param.args.length < 2 || !(param.args[1] instanceof Integer)) {
                            return;
                        }
                        apply(param.thisObject, param.args[0],
                                ((Integer) param.args[1]).intValue());
                    } catch (Throwable t) {
                        XLog.e("card background fix failed", t);
                    }
                }
            });
            XLog.i("hooked HeaderAdapter.onBindViewHolder (card bottom fix)");
        } catch (Throwable t) {
            XLog.e("card background hook failed", t);
        }
    }

    private static void hookResources(ClassLoader cl) {
        try {
            Class<?> impl = XposedHelpers.findClass(
                    "android.content.res.ResourcesImpl", cl);
            XposedBridge.hookAllMethods(impl, "loadDrawable", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (!Boolean.TRUE.equals(SUPPRESS.get())) {
                            return;
                        }
                        String name = resourceName(param.args);
                        if (name != null && isBlocked(name)) {
                            param.setResult(new ColorDrawable(0));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            XLog.i("hooked ResourcesImpl.loadDrawable (last row drawable suppression)");
        } catch (Throwable t) {
            XLog.e("card resource hook failed", t);
        }
    }

    private static boolean isBlocked(String name) {
        return name.contains("miuix_preference_item_background_")
                || name.contains("miuix_recyclerview_card_item_press_fg_");
    }

    /** Extracts the resource name from a ResourcesImpl.loadDrawable call. */
    private static String resourceName(Object[] args) {
        if (args == null) {
            return null;
        }
        android.content.res.Resources resources = null;
        int id = 0;
        android.util.TypedValue value = null;
        for (Object arg : args) {
            if (arg instanceof android.content.res.Resources) {
                resources = (android.content.res.Resources) arg;
            } else if (arg instanceof android.util.TypedValue) {
                value = (android.util.TypedValue) arg;
            } else if (arg instanceof Integer && id == 0) {
                int candidate = (Integer) arg;
                if (candidate > 0) {
                    id = candidate;
                }
            }
        }
        if (resources == null) {
            return null;
        }
        int resourceId = value != null && value.resourceId != 0 ? value.resourceId : id;
        if (resourceId == 0) {
            return null;
        }
        try {
            return resources.getResourceName(resourceId);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object headerAt(Object adapter, Object[] args) {
        if (args.length < 2 || !(args[1] instanceof Integer)) {
            return null;
        }
        List<?> headers = headerList(adapter);
        int position = ((Integer) args[1]).intValue();
        if (headers == null || position < 0 || position >= headers.size()) {
            return null;
        }
        return headers.get(position);
    }

    /**
     * The account subtitle has to be written into the header before MIUI binds
     * it, otherwise a fresh header list (resume, refresh) shows the old text
     * until the next manual rebind.
     */
    private static void rewriteAccountSummary(Object header) {
        if (header == null) {
            return;
        }
        Object summaryObject = HeaderData.field(header, "summary");
        String summary = summaryObject == null ? "" : summaryObject.toString();
        boolean account = summary.contains("云服务") || summary.contains("会员权益")
                || summary.contains("Apple账户");
        if (account) {
            HeaderData.setField(header, "summary", "Apple账户、iCloud+等");
            // Drop any resource-based summary so MIUI always uses the string.
            HeaderData.setField(header, "summaryRes", Integer.valueOf(0));
        }
    }

    private static boolean isLastRow(Object adapter, Object[] args) {
        if (args.length < 2 || !(args[1] instanceof Integer)) {
            return false;
        }
        List<?> headers = headerList(adapter);
        int position = ((Integer) args[1]).intValue();
        if (headers == null || position < 0 || position >= headers.size()) {
            return false;
        }
        return isLastOfGroup(headers, position);
    }

    private static void apply(Object adapter, Object holder, int position) {
        View itemView = (View) HeaderData.field(holder, "itemView");
        if (itemView == null) {
            return;
        }
        List<?> headers = headerList(adapter);
        if (headers == null || headers.isEmpty()) {
            return;
        }
        int index = resolveIndex(headers, itemView, position);
        if (index < 0 || index >= headers.size()) {
            return;
        }
        boolean last = isLastOfGroup(headers, index);
        if (last) {
            if (!BACKGROUNDS.containsKey(itemView)) {
                BACKGROUNDS.put(itemView, itemView.getBackground());
            }
            if (!FOREGROUNDS.containsKey(itemView)) {
                FOREGROUNDS.put(itemView, itemView.getForeground());
            }
            itemView.setBackground(null);
            itemView.setForeground(null);
            XLog.i("last row cleared: " + HeaderData.field(headers.get(position), "title"));
        } else {
            Drawable bg = BACKGROUNDS.remove(itemView);
            if (bg != null) {
                itemView.setBackground(bg);
            }
            Drawable fg = FOREGROUNDS.remove(itemView);
            if (fg != null) {
                itemView.setForeground(fg);
            }
        }
    }

    private static List<?> headerList(Object adapter) {
        if (adapter == null) {
            return null;
        }
        for (String name : new String[]{"mHeaders", "mHeaderList", "headers"}) {
            Object value = HeaderData.field(adapter, name);
            if (value instanceof List) {
                return (List<?>) value;
            }
        }
        return null;
    }

    /**
     * Adapter positions are offset by the list's header views, so look the row
     * up by its title text instead of trusting the position blindly.
     */
    private static int resolveIndex(List<?> headers, View itemView, int position) {
        try {
            View titleView = itemView.findViewById(android.R.id.title);
            if (titleView instanceof android.widget.TextView) {
                String title = ((android.widget.TextView) titleView).getText().toString();
                if (title.length() > 0) {
                    for (int i = 0; i < headers.size(); i++) {
                        Object t = HeaderData.field(headers.get(i), "title");
                        if (t != null && title.equals(t.toString())) {
                            return i;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return position >= 0 && position < headers.size() ? position : -1;
    }

    private static boolean isLastOfGroup(List<?> headers, int position) {
        Object current = headers.get(position);
        Object groupId = HeaderData.field(current, "groupId");
        if (position + 1 >= headers.size()) {
            return true;
        }
        Object nextGroupId = HeaderData.field(headers.get(position + 1), "groupId");
        return groupId != null && nextGroupId != null && !groupId.equals(nextGroupId);
    }
}
