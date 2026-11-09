package com.abradant.iossettings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the native "关于设备 / 关于本机" page with an iOS style About page:
 * label/value rows grouped in cards, plus a "证书信任设置" entry.
 */
final class AboutIos {

    private static final String TAG = "ios_about_page";

    private static final String[] ABOUT_FRAGMENTS = {
            "com.android.settings.device.MiuiMyDeviceSettings",
            "com.android.settings.deviceinfo.aboutphone.MyDeviceInfoFragment",
            "com.android.settings.deviceinfo.aboutphone.MyDeviceInfoSettings"
    };

    private AboutIos() {
    }

    static boolean isAboutFragment(Object fragment) {
        if (fragment == null) {
            return false;
        }
        String name = fragment.getClass().getName();
        for (String candidate : ABOUT_FRAGMENTS) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }

    static void attach(Object fragment) {
        try {
            if (!isAboutFragment(fragment)) {
                return;
            }
            Activity activity = activityOf(fragment);
            if (activity == null || activity.isFinishing()) {
                return;
            }
            View root = (View) call(fragment, "getView");
            ViewParent parent = root == null ? null : root.getParent();
            if (!(parent instanceof FrameLayout)) {
                return;
            }
            FrameLayout container = (FrameLayout) parent;
            if (container.findViewWithTag(TAG) != null) {
                return;
            }
            View page = buildPage(activity, fragment);
            page.setTag(TAG);
            container.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (root != null) {
                root.setVisibility(View.GONE);
            }
            final View pageView = page;
            page.post(new Runnable() {
                @Override
                public void run() {
                    adjustForAppBar(activity, pageView);
                }
            });
            XLog.i("iOS about page installed (native entries recreated below)");
        } catch (Throwable t) {
            XLog.e("about page failed", t);
        }
    }

    /** Measures the real overlap with MIUI's app bar and pads the page. */
    private static void adjustForAppBar(Activity activity, View page) {
        try {
            if (!(page instanceof ScrollView)) {
                return;
            }
            ScrollView scroll = (ScrollView) page;
            if (scroll.getChildCount() == 0) {
                return;
            }
            View content = scroll.getChildAt(0);
            int overlap = 0;
            int[] pageLoc = new int[2];
            page.getLocationOnScreen(pageLoc);
            View ab = activity.findViewById(activity.getResources().getIdentifier(
                    "action_bar_container", "id", "com.android.settings"));
            if (ab != null && ab.getHeight() > 0) {
                int[] abLoc = new int[2];
                ab.getLocationOnScreen(abLoc);
                overlap = (abLoc[1] + ab.getHeight()) - pageLoc[1];
            }
            if (overlap <= 0) {
                try {
                    android.view.WindowInsets insets = page.getRootWindowInsets();
                    if (insets != null) {
                        overlap = insets.getInsets(
                                android.view.WindowInsets.Type.statusBars()).top;
                    }
                } catch (Throwable ignored) {
                }
            }
            int top = Math.max(0, overlap) + dp(activity, 12);
            content.setPadding(content.getPaddingLeft(), top,
                    content.getPaddingRight(), dp(activity, 40));
            XLog.i("about page top padding = " + top + " (overlap=" + overlap + ")");
        } catch (Throwable t) {
            XLog.e("about appbar adjust failed", t);
        }
    }

    /** MIUI puts the app bar on top of the content; reuse its top padding. */
    private static int topPadding(Activity activity, View root) {
        try {
            if (root != null && root.getPaddingTop() > 0) {
                return root.getPaddingTop();
            }
            int appBar = 0;
            View ab = activity.findViewById(activity.getResources().getIdentifier(
                    "action_bar_container", "id", "com.android.settings"));
            if (ab != null) {
                appBar = ab.getHeight();
            }
            int status = 0;
            try {
                android.view.WindowInsets insets = root == null ? null : root.getRootWindowInsets();
                if (insets != null) {
                    status = insets.getInsets(
                            android.view.WindowInsets.Type.statusBars()
                                    | android.view.WindowInsets.Type.displayCutout()).top;
                }
            } catch (Throwable ignored) {
            }
            return appBar + status;
        } catch (Throwable t) {
            return dp(activity, 96);
        }
    }

    /* --------------------------------- page --------------------------------- */

    private static View buildPage(Activity activity, Object fragment) {
        boolean dark = UiTweaks.isDarkMode(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        // Same surface colour MIUI uses for the settings window (background_grey),
        // so the About page matches the main page exactly.
        scroll.setBackgroundColor(pageBackground(activity, dark));
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(activity, 12), 0, dp(activity, 40));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<String[]> basics = new ArrayList<>();
        basics.add(new String[]{"系统版本", osVersion()});
        basics.add(new String[]{"型号名称", marketName()});
        basics.add(new String[]{"型号", safe(Build.MODEL)});
        basics.add(new String[]{"序列号", serial()});

        // First card: editable "名称" first, then the read-only values.
        LinearLayout basicsCard = newCard(activity, dark);
        basicsCard.addView(editableNameRow(activity, deviceName(activity), dark));
        for (String[] pair : basics) {
            basicsCard.addView(separator(activity, dark));
            basicsCard.addView(row(activity, pair[0], pair[1], false, dark));
        }

        List<String[]> media = new ArrayList<>();
        media.add(new String[]{"歌曲", String.valueOf(count(activity, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI))});
        media.add(new String[]{"视频", String.valueOf(count(activity, MediaStore.Video.Media.EXTERNAL_CONTENT_URI))});
        media.add(new String[]{"照片", String.valueOf(count(activity, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))});
        media.add(new String[]{"应用程序", String.valueOf(appCount(activity))});
        long[] storage = storage();
        media.add(new String[]{"总容量", human(storage[0])});
        media.add(new String[]{"可用容量", human(storage[1])});

        content.addView(basicsCard);
        addGap(activity, content);
        content.addView(card(activity, media, dark));
        addGap(activity, content);

        LinearLayout trustCard = newCard(activity, dark);
        trustCard.addView(row(activity, "证书信任设置", null, true, dark));
        content.addView(trustCard);

        // Recreate MIUI's own entries as cards at the very bottom, keeping their
        // click behaviour by performing the original preference's click.
        List<Object> natives = nativePreferences(fragment);
        if (!natives.isEmpty()) {
            addGap(activity, content);
            LinearLayout nativeCard = newCard(activity, dark);
            boolean first = true;
            for (Object pref : natives) {
                CharSequence title = (CharSequence) call(pref, "getTitle");
                CharSequence summary = (CharSequence) call(pref, "getSummary");
                if (title == null || title.toString().trim().length() == 0) {
                    continue;
                }
                if (!first) {
                    nativeCard.addView(separator(activity, dark));
                }
                final Object target = pref;
                View row = row(activity, title.toString(),
                        summary == null ? null : summary.toString(), true, dark);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        call(target, "performClick");
                    }
                });
                nativeCard.addView(row);
                first = false;
            }
            if (!first) {
                content.addView(nativeCard);
            }
        }
        return scroll;
    }

    private static List<Object> nativePreferences(Object fragment) {
        List<Object> result = new ArrayList<>();
        Object screen = call(fragment, "getPreferenceScreen");
        if (screen == null) {
            return result;
        }
        Object countObject = call(screen, "getPreferenceCount");
        int count = countObject instanceof Integer ? (Integer) countObject : 0;
        for (int i = 0; i < count; i++) {
            Object pref = call(screen, "getPreference", i);
            if (pref == null) {
                continue;
            }
            Object screenChild = call(pref, "getPreferenceCount");
            if (screenChild instanceof Integer) {
                // PreferenceCategory: use its visible children instead
                int n = (Integer) screenChild;
                for (int j = 0; j < n; j++) {
                    Object child = call(pref, "getPreference", j);
                    if (child != null) {
                        result.add(child);
                    }
                }
                continue;
            }
            result.add(pref);
        }
        return result;
    }

    private static int pageBackground(Activity activity, boolean dark) {
        try {
            android.content.res.Resources res = activity.getResources();
            int id = res.getIdentifier("background_grey", "color", "com.android.settings");
            if (id != 0) {
                return res.getColor(id, activity.getTheme());
            }
        } catch (Throwable ignored) {
        }
        try {
            android.util.TypedValue tv = new android.util.TypedValue();
            if (activity.getTheme().resolveAttribute(android.R.attr.windowBackground, tv, true)) {
                int type = tv.type;
                if (type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                        && type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                if (tv.resourceId != 0) {
                    android.graphics.drawable.Drawable d = activity.getDrawable(tv.resourceId);
                    if (d instanceof android.graphics.drawable.ColorDrawable) {
                        return ((android.graphics.drawable.ColorDrawable) d).getColor();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return dark ? 0xFF000000 : 0xFFF2F2F7;
    }

    private static void addGap(Activity activity, LinearLayout content) {
        View gap = new View(activity);
        content.addView(gap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 34)));
    }

    private static LinearLayout newCard(Activity activity, boolean dark) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor(activity, dark));
        bg.setCornerRadius(dp(activity, 16));
        card.setBackground(bg);
        card.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(activity, 16);
        lp.rightMargin = dp(activity, 16);
        card.setLayoutParams(lp);
        return card;
    }

    /** Exactly the colour MIUI uses for its own cards on the home page. */
    private static int cardColor(Activity activity, boolean dark) {
        try {
            Resources res = activity.getResources();
            String name = dark
                    ? "miuix_recyclerview_card_group_background_dark"
                    : "miuix_recyclerview_card_group_background_light";
            int id = res.getIdentifier(name, "color", "com.android.settings");
            if (id != 0) {
                return res.getColor(id, activity.getTheme());
            }
        } catch (Throwable ignored) {
        }
        return dark ? 0xFF1C1C1E : 0xFFFFFFFF;
    }

    private static LinearLayout card(Activity activity, List<String[]> rows, boolean dark) {
        LinearLayout card = newCard(activity, dark);
        boolean first = true;
        for (String[] pair : rows) {
            if (!first) {
                card.addView(separator(activity, dark));
            }
            card.addView(row(activity, pair[0], pair[1], false, dark));
            first = false;
        }
        return card;
    }

    private static View row(Activity activity, String label, String value,
                            boolean chevron, boolean dark) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        row.setMinimumHeight(dp(activity, 50));

        TextView title = new TextView(activity);
        title.setText(label);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (value != null) {
            TextView summary = new TextView(activity);
            summary.setText(value);
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            summary.setTextColor(dark ? 0xFF98989F : 0xFF8E8E93);
            summary.setSingleLine(true);
            summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(activity, 12);
            row.addView(summary, lp);
        }
        if (chevron) {
            View arrow;
            Drawable chevronDrawable = null;
            try {
                Resources res = activity.getResources();
                int id = res.getIdentifier("miuix_appcompat_intent_arrow_right",
                        "drawable", "com.android.settings");
                if (id != 0) {
                    chevronDrawable = res.getDrawable(id, activity.getTheme());
                }
            } catch (Throwable ignored) {
            }
            if (chevronDrawable != null) {
                ImageView image = new ImageView(activity);
                image.setImageDrawable(chevronDrawable);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        dp(activity, 9), dp(activity, 15));
                lp.leftMargin = dp(activity, 8);
                arrow = image;
                row.addView(arrow, lp);
            } else {
                TextView glyph = new TextView(activity);
                glyph.setText("›");
                glyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                glyph.setTextColor(dark ? 0xFF5A5A5F : 0xFFC4C4C6);
                glyph.setPadding(dp(activity, 8), 0, 0, 0);
                arrow = glyph;
                row.addView(arrow);
            }
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openTrustSettings(activity);
                }
            });
        }
        return row;
    }

    private static View editableNameRow(final Activity activity, String value, boolean dark) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(activity, 16), 0, dp(activity, 16), 0);
        row.setMinimumHeight(dp(activity, 50));
        row.setClickable(true);

        TextView title = new TextView(activity);
        title.setText("名称");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
        row.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valueView = new TextView(activity);
        valueView.setText(value);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        valueView.setTextColor(dark ? 0xFF98989F : 0xFF8E8E93);
        valueView.setSingleLine(true);
        valueView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(activity, 12);
        row.addView(valueView, lp);

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRenameDialog(activity, valueView);
            }
        });
        return row;
    }

    private static void showRenameDialog(final Activity activity, final TextView valueView) {
        try {
            final android.widget.EditText input = new android.widget.EditText(activity);
            input.setText(deviceName(activity));
            input.setSingleLine(true);
            input.setSelectAllOnFocus(true);
            int pad = dp(activity, 20);
            input.setPadding(pad, pad, pad, pad);
            new android.app.AlertDialog.Builder(activity)
                    .setTitle("名称")
                    .setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface dialog, int which) {
                            String name = input.getText() == null
                                    ? "" : input.getText().toString().trim();
                            if (name.length() == 0) {
                                return;
                            }
                            writeDeviceName(activity, name);
                            valueView.setText(name);
                            XLog.i("device name changed to " + name);
                        }
                    })
                    .show();
        } catch (Throwable t) {
            XLog.e("rename dialog failed", t);
        }
    }

    private static void writeDeviceName(Activity activity, String name) {
        try {
            Settings.Global.putString(activity.getContentResolver(), "device_name", name);
        } catch (Throwable t) {
            XLog.e("write device_name failed", t);
        }
        try {
            Settings.Secure.putString(activity.getContentResolver(), "bluetooth_name", name);
        } catch (Throwable ignored) {
        }
        try {
            android.bluetooth.BluetoothAdapter adapter =
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) {
                adapter.setName(name);
            }
        } catch (Throwable ignored) {
        }
    }

    private static View separator(Activity activity, boolean dark) {
        View sep = new View(activity);
        sep.setBackgroundColor(dark ? 0xFF38383A : 0xFFE5E5EA);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(activity, 0.6f)));
        lp.leftMargin = dp(activity, 16);
        sep.setLayoutParams(lp);
        return sep;
    }

    /* -------------------------------- content -------------------------------- */

    private static String deviceName(Activity activity) {
        try {
            String name = Settings.Global.getString(activity.getContentResolver(), "device_name");
            if (name != null && name.trim().length() > 0) {
                return name;
            }
        } catch (Throwable ignored) {
        }
        return marketName();
    }

    private static String osVersion() {
        String hyper = prop("ro.mi.os.version.name");
        String miui = prop("ro.miui.ui.version.name");
        String android = "Android " + Build.VERSION.RELEASE;
        if (hyper != null && hyper.length() > 0) {
            return "HyperOS " + hyper + "  ·  " + android;
        }
        if (miui != null && miui.length() > 0) {
            return "MIUI " + miui + "  ·  " + android;
        }
        return android;
    }

    private static String marketName() {
        String market = prop("ro.product.marketname");
        if (market != null && market.trim().length() > 0) {
            return market.trim();
        }
        String model = Build.MODEL;
        return model == null ? "—" : model;
    }

    private static String serial() {
        try {
            String serial = Build.getSerial();
            return serial == null || serial.length() == 0 ? "—" : serial;
        } catch (Throwable t) {
            return "—";
        }
    }

    private static String prop(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method get = cls.getMethod("get", String.class);
            Object value = get.invoke(null, key);
            return value instanceof String ? (String) value : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static int count(Context context, android.net.Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{"count(*)"},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    private static int appCount(Context context) {
        try {
            return context.getPackageManager().getInstalledApplications(0).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long[] storage() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getTotalBytes();
            long free = stat.getAvailableBytes();
            return new long[]{total, free};
        } catch (Throwable t) {
            return new long[]{0, 0};
        }
    }

    private static String human(long bytes) {
        if (bytes <= 0) {
            return "—";
        }
        double gb = bytes / 1024d / 1024d / 1024d;
        if (gb >= 100) {
            return String.format(java.util.Locale.US, "%.0f GB", gb);
        }
        return String.format(java.util.Locale.US, "%.2f GB", gb);
    }

    private static String safe(String value) {
        return value == null || value.length() == 0 ? "—" : value;
    }

    private static void openTrustSettings(Activity activity) {
        String[] candidates = {
                "com.android.settings.Settings$TrustedCredentialsSettingsActivity",
                "com.android.settings.TrustedCredentialsSettingsActivity"
        };
        for (String cls : candidates) {
            try {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings", cls);
                if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                    continue;
                }
                activity.startActivity(intent);
                return;
            } catch (Throwable ignored) {
            }
        }
        android.widget.Toast.makeText(activity, "找不到证书信任设置页面",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    /* ------------------------------- helpers -------------------------------- */

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

    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
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

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
