package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

/**
 * Adds an iOS style intro card (big icon + page name + description) at the top
 * of second-level settings pages, like the "通用" header in the reference shot.
 *
 * <p>The card is a sibling of the preference RecyclerView: the list gets a top
 * padding equal to the card height and the card is translated up while the list
 * scrolls, so it scrolls away together with the content.</p>
 */
final class IntroCard {

    private static final String TAG = "ios_intro_card";
    private static final java.util.Map<Object, Integer> RETRIES = new java.util.WeakHashMap<>();

    private IntroCard() {
    }

    static void attach(Object fragment) {
        try {
            if (fragment == null) {
                return;
            }
            if (AboutIos.isAboutFragment(fragment)) {
                return; // the About page has its own dedicated layout
            }
            Activity activity = activityOf(fragment);
            if (activity == null || activity.isFinishing()) {
                return;
            }
            final Object listObject = call(fragment, "getListView");
            if (!(listObject instanceof View)) {
                return;
            }
            final View list = (View) listObject;
            final ViewParent parent = list.getParent();
            if (!(parent instanceof FrameLayout)) {
                // onViewCreated may run before the fragment view is attached.
                Integer tries = RETRIES.get(fragment);
                int n = tries == null ? 0 : tries;
                if (n < 8) {
                    RETRIES.put(fragment, n + 1);
                    list.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            attach(fragment);
                        }
                    }, 120);
                } else {
                    XLog.w("intro card: parent is not a FrameLayout, skipped");
                }
                return;
            }
            final FrameLayout container = (FrameLayout) parent;
            if (container.findViewWithTag(TAG) != null) {
                return;
            }
            CharSequence label = activity.getTitle();
            final String title = label == null ? "" : label.toString().trim();
            if (title.length() == 0) {
                return;
            }

            final View card = buildCard(activity, title);
            card.setTag(TAG);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(activity, 12);
            lp.rightMargin = dp(activity, 12);
            lp.topMargin = dp(activity, 8);
            container.addView(card, lp);

            final int originalPadding = list.getPaddingTop();
            final int gap = dp(activity, 12);
            card.post(new Runnable() {
                @Override
                public void run() {
                    int height = card.getHeight();
                    list.setPadding(list.getPaddingLeft(), originalPadding + height + gap,
                            list.getPaddingRight(), list.getPaddingBottom());
                }
            });
            list.getViewTreeObserver().addOnScrollChangedListener(
                    new android.view.ViewTreeObserver.OnScrollChangedListener() {
                        @Override
                        public void onScrollChanged() {
                            try {
                                int offset = ((Integer) call(list,
                                        "computeVerticalScrollOffset")).intValue();
                                float shift = Math.min(Math.max(offset, 0),
                                        card.getHeight() + gap);
                                card.setTranslationY(-shift);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            XLog.i("intro card added: " + title);
        } catch (Throwable t) {
            XLog.e("intro card failed", t);
        }
    }

    private static View buildCard(Activity activity, String title) {
        boolean dark = UiTweaks.isDarkMode(activity);
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(dark ? 0xFF1C1C1E : 0xFFFFFFFF);
        bg.setCornerRadius(dp(activity, 16));
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setPadding(dp(activity, 18), dp(activity, 18), dp(activity, 18), dp(activity, 18));

        ImageView icon = new ImageView(activity);
        int iconSize = dp(activity, 62);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable drawable = pageIcon(activity, title);
        if (drawable != null) {
            icon.setImageDrawable(drawable);
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        card.addView(icon, iconLp);

        TextView name = new TextView(activity);
        name.setText(title);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(activity, 12);
        card.addView(name, nameLp);

        TextView desc = new TextView(activity);
        desc.setText(description(activity, title));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setTextColor(dark ? 0xFF98989F : 0xFF8E8E93);
        desc.setLineSpacing(dp(activity, 3), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(activity, 6);
        card.addView(desc, descLp);
        return card;
    }

    /* ----------------------------- contents map ----------------------------- */

    private static final String[][] PAGE_ICONS = {
            {"通用", "ic_other_advanced_settings"},
            {"更多设置", "ic_other_advanced_settings"},
            {"外观", "ic_display_settings"},
            {"显示", "ic_display_settings"},
            {"声音", "ic_sound_settings"},
            {"通知", "ic_notification_center"},
            {"无线局域网", "ic_wifi_settings"},
            {"WLAN", "ic_wifi_settings"},
            {"蓝牙", "ic_bluetooth_settings"},
            {"电池", "ic_battery_settings"},
            {"VPN", "ic_vpn"},
            {"隐私", "ic_privacy_protection"},
            {"安全", "ic_security_settings_settings"},
            {"面容", "ic_security_settings_settings"},
            {"无障碍", "ic_accessibility_function"},
            {"桌面", "ic_miui_home_settings"},
            {"主屏幕", "ic_miui_home_settings"},
            {"墙纸", "com_android_thememanager"},
            {"壁纸", "com_android_thememanager"},
            {"待机显示", "ic_unlock_set_settings"},
            {"锁屏", "ic_unlock_set_settings"},
            {"热点", "ic_portable_wlan_hotspot_settings"},
            {"蜂窝", "ic_settings_dualsim"},
            {"应用", "ic_system_app_settings"},
            {"关于设备", "ic_my_device"},
            {"控制中心", "ic_device_connection"},
            {"隔空播放", "ic_interconnection"}
    };

    private static final String[][] PAGE_DESCRIPTIONS = {
            {"通用", "管理设备的整体设置和偏好设置，例如软件更新、设备语言、车载、隔空投送等。"},
            {"外观", "调整屏幕亮度、色彩、刷新率和显示效果。"},
            {"显示", "调整屏幕亮度、色彩、刷新率和显示效果。"},
            {"声音", "管理铃声、通知音、媒体音量和触感反馈。"},
            {"通知", "管理通知提醒方式、状态栏和控制中心显示。"},
            {"无线局域网", "连接无线网络，管理已保存的网络和网络偏好。"},
            {"WLAN", "连接无线网络，管理已保存的网络和网络偏好。"},
            {"蓝牙", "连接和管理蓝牙设备，查看已配对设备。"},
            {"电池", "查看电量使用情况，管理省电与充电设置。"},
            {"VPN", "配置和管理虚拟专用网络连接。"},
            {"隐私", "控制应用权限、敏感信息访问和隐私保护。"},
            {"安全", "管理锁屏密码、指纹、面部识别和安全设置。"},
            {"面容", "管理锁屏密码、指纹、面部识别和安全设置。"},
            {"无障碍", "为视力、听力、行动不便等提供辅助功能。"},
            {"桌面", "设置主屏幕布局、图标样式和动画效果。"},
            {"主屏幕", "设置主屏幕布局、图标样式和动画效果。"},
            {"墙纸", "选择壁纸、主题和个性化外观。"},
            {"壁纸", "选择壁纸、主题和个性化外观。"},
            {"待机显示", "设置锁屏时的显示内容，例如息屏显示、通知和快捷功能。"},
            {"锁屏", "设置锁屏时的显示内容，例如息屏显示、通知和快捷功能。"},
            {"热点", "共享网络给其它设备，设置热点名称和密码。"},
            {"蜂窝", "管理移动网络、双卡和流量使用。"},
            {"应用", "管理已安装应用、默认应用和应用权限。"},
            {"关于设备", "查看设备型号、系统版本和硬件信息。"},
            {"控制中心", "选择控制中心显示哪些快捷开关，以及它们的排列方式。"},
            {"隔空播放", "设置投屏、互传和跨设备协同的相关选项。"}
    };

    private static String description(Activity activity, String title) {
        for (String[] pair : PAGE_DESCRIPTIONS) {
            if (title.contains(pair[0])) {
                return pair[1];
            }
        }
        return "管理与“" + title + "”相关的设置和偏好。";
    }

    private static Drawable pageIcon(Activity activity, String title) {
        try {
            Resources res = activity.getResources();
            String name = null;
            for (String[] pair : PAGE_ICONS) {
                if (title.contains(pair[0])) {
                    name = pair[1];
                    break;
                }
            }
            if (name == null) {
                name = "ic_other_advanced_settings";
            }
            int id = res.getIdentifier(name, "drawable", "com.android.settings");
            if (id != 0) {
                return res.getDrawable(id, activity.getTheme());
            }
        } catch (Throwable ignored) {
        }
        return null;
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
