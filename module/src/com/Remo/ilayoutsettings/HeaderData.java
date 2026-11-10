package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reorders MIUI's live header list into the iOS Settings grouping and rewrites
 * {@code Header.groupId} so miuix's CardGroupAdapter draws the native cards.
 *
 * <p>Nothing is drawn by the module: background, title, search bar, row layout,
 * icons and click handling all stay MIUI's own. Only the list order and the
 * card grouping change.</p>
 */
final class HeaderData {

    /** Extra card with every header that has no iOS slot. */
    static final int EXTRAS = 9;

    /** Synthetic row above 屏幕时间; opens the system Do Not Disturb settings. */
    private static final String FOCUS_TITLE = "专注模式";
    private static final String FOCUS_ACTION = "android.settings.ZEN_MODE_SETTINGS";

    /** Synthetic row below 外观; opens the camera app's own settings page. */
    private static final String CAMERA_TITLE = "相机";
    private static final String CAMERA_PKG = "com.android.camera";
    private static final String CAMERA_CLS = "com.android.camera.CameraPreferenceActivity";

    /** Synthetic row below 墙纸; opens 小米搜索's settings page. */
    private static final String SEARCH_TITLE = "搜索";
    private static final String SEARCH_PKG = "com.android.quicksearchbox";
    private static final String SEARCH_CLS =
            "com.android.quicksearchbox.preferences.SearchSettingsPreferenceActivity";

    /** Synthetic iCloud row below Game Center; opens 小米云服务 (MiCloudMainActivity). */
    private static final String ICLOUD_TITLE = "iCloud";
    private static final String CLOUD_ACTION = "com.xiaomi.action.MICLOUD_MAIN";
    private static final String CLOUD_PKG = "com.miui.cloudservice";
    private static final String CLOUD_CLS = "com.miui.cloudservice.ui.MiCloudMainActivity";

    /** Software-update card; only when the updater has stored a new version. */
    private static final String UPDATE_TITLE = "有软件更新可用";
    private static final String UPDATE_PKG = "com.android.updater";
    private static final String UPDATE_CLS = "com.android.updater.MainActivity";
    private static final String UPDATE_KEY = "miui_new_version";

    /** AirPods card; only while a headset is connected, title = device name. */
    private static final String AIRPODS_ICON = "ic_sub_airpods";
    /** iCloud row icon name, looked up in Settings first and in the module second. */
    private static final String ICLOUD_ICON = "ic_cloud";
    /** Debug override so the conditional card can be previewed without earbuds. */
    private static final String FAKE_HEADSET_KEY = "ilayout_fake_headset";
    /** Debug override for the headset address used by the detail-page intent. */
    private static final String FAKE_HEADSET_ADDRESS_KEY = "ilayout_fake_headset_address";

    /** MIUI's headset settings page (battery / EQ / gesture detail), not the BT list. */
    private static final String HEADSET_ACTION = "miui.bluetooth.action.HEADSET_SETTINGS";
    private static final String HEADSET_CLS =
            "com.android.settings.bluetooth.MiuiHeadsetActivity";
    private static final String HEADSET_COME_FROM = "MIUI_BLUETOOTH_SETTINGS";
    /** Intent extras read by MiuiHeadsetActivity. */
    private static final String EXTRA_HEADSET_DEVICE = "android.bluetooth.device.extra.DEVICE";
    private static final String EXTRA_HEADSET_ADDRESS = "bluetoothaddress";
    private static final String EXTRA_HEADSET_FROM = "COME_FROM";

    /** Card groups 1 and 2 are unused by {@link #SLOT_GROUP}; 0 is the account card. */
    private static final int GROUP_UPDATE = 1;
    private static final int GROUP_AIRPODS = 2;

    /** This module's package, used to borrow the bundled drawables. */
    static final String MODULE_PKG = "com.Remo.ilayoutsettings";

    /** Bundle marker so synthetic rows can be recognised after MIUI rebuilds. */
    private static final String MARK_KEY = "ios_synthetic";

    /**
     * iOS groups. Each slot is a list of header id aliases; the first alias that
     * exists in the live list wins. Group 0 keeps the native order
     * account -> 关于本机 -> 我的 Airpods.
     */
    private static final String[][] SLOT_IDS = {
            // 0: account card (account / airpods)
            {"mi_account_settings"},
            {"sub_screen"},

            // 1: about device, its own card
            {"my_device", "about_device", "my_device_settings"},

            // 2: wifi / bt / cellular / hotspot / battery / vpn (airplane row is synthetic)
            {"wifi_settings"},
            {"bluetooth_settings"},
            {"msim_settings", "sim_management_settings"},
            {"wifi_tether_settings"},
            {"battery_settings_new", "battery_settings", "battery"},
            {"vpn_settings_multiple", "vpn_settings", "vpn_settings2"},

            // 3: general / standby / control center / wallpaper / airplay / appearance / home / super xiaoai / siri
            {"other_advanced_settings", "advanced_settings"},
            {"screen_settings", "lock_screen_settings", "aod_and_lockscreen_settings"},
            // 16.7: 控制中心 sits directly above 墙纸 (the native order is the other way round).
            {"wireless_settings"},
            {"wallpaper_settings", "personalize_title"},
            {"interconnection_settings", "interconnection", "milink_settings", "mi_link_settings"},
            {"display_settings"},
            {"launcher_settings"},
            {"xiao_mi_hyperos_ai", "hyperos_ai"},
            {"voice_assist"},

            // 4: notifications / sounds / focus / screen time
            {"notification_status_bar", "notification_settings", "notifications"},
            {"sound_settings", "sound_haptic_settings"},
            {"focus_mode_settings", "focus_mode"},
            {"app_timer", "digital_wellbeing"},

            // 5: face id / accessibility / sos / privacy
            {"security_settings", "security_privacy_settings"},
            {"accessibility_function", "accessibility_settings"},
            {"safety_emergency_settings", "emergency_settings"},
            {"privacy_protection_settings", "privacy_settings2", "privacy_settings"},

            // 6: wallet / game center
            {"wallet_header", "wallet_settings", "wallet", "mi_wallet"},
            {"minors_control", "minors_mode", "game_center"},

            // 7: apps
            {"application_settings", "apps"}
    };

    private static final int[] SLOT_GROUP = {
            0, 0,
            4,
            3, 3, 3, 3, 3, 3,
            4, 4, 4, 4, 4, 4, 4, 4, 4,
            5, 5, 5, 5,
            6, 6, 6, 6,
            7, 7,
            8
    };

    /** Fallback matching by visible title (both MIUI default and theme-renamed). */
    private static final String[][] SLOT_TITLES = {
            {"账号", "账户", "Apple 账户"},
            {"Airpods", "ipods", "耳机"},
            {"关于设备", "关于本机", "我的设备", "关于手机"},
            {"无线局域网", "WLAN", "Wi-Fi", "WIFI", "无线网络"},
            {"蓝牙"},
            {"蜂窝网络", "双卡与移动网络", "移动网络", "移动数据", "流量"},
            {"个人热点", "热点"},
            {"电池", "省电与电池", "电量"},
            {"VPN"},
            {"通用", "更多设置", "高级设置"},
            {"待机显示", "锁屏", "息屏"},
            {"控制中心"},
            {"墙纸", "壁纸", "主题壁纸"},
            {"隔空播放", "连续互通", "互传", "MiLink", "投屏"},
            {"外观", "显示", "显示与亮度"},
            {"主屏幕", "桌面"},
            {"超级小爱", "小爱", "AI"},
            {"Siri", "语音助手"},
            {"通知"},
            {"声音", "触感", "振动"},
            {"专注模式", "专注"},
            {"屏幕时间", "屏幕使用时间"},
            {"面容", "密码与安全", "指纹", "生物识别"},
            {"无障碍"},
            {"SOS", "紧急"},
            {"隐私"},
            {"钱包"},
            {"Game", "游戏", "未成年"},
            {"应用设置", "App"}
    };

    /** Group id written into Header.groupId; > 0 and unique per card. */
    private static final int GROUP_ID_BASE = 101;
    private static final java.util.Map<Object, List<Object>> LAST_ORDER =
            new java.util.WeakHashMap<>();

    private HeaderData() {
    }

    /* ------------------------------ reflection ------------------------------ */

    static Object field(Object target, String name) {
        if (target == null) {
            return null;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    static boolean setIntField(Object target, String name, int value) {
        if (target == null) {
            return false;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.setInt(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    static boolean setField(Object target, String name, Object value) {
        if (target == null) {
            return false;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    static boolean setLongField(Object target, String name, long value) {
        if (target == null) {
            return false;
        }
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.setLong(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    static List<Object> readHeaders(Object fragment) {
        Object h = field(fragment, "mHeaders");
        return h instanceof List ? (List<Object>) h : null;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    /** True for untitled <header/> separators and section labels. */
    private static boolean isSeparator(Object header) {        Object fragment = field(header, "fragment");
        Object intent = field(header, "intent");
        if (fragment != null || intent != null) {
            return false;
        }
        Object title = field(header, "title");
        return title == null || title.toString().trim().length() == 0;
    }

    /* -------------------------------- parse -------------------------------- */

    static final class Item {
        Object header;
        String idName = "";
        String title = "";
        int group = EXTRAS;
        int slot = 999;
        int originalIndex = 0;
    }

    static List<Item> parse(Activity activity, List<Object> headers) {
        List<Item> items = new ArrayList<>();
        if (headers == null) {
            return items;
        }
        android.content.res.Resources res = activity.getResources();
        for (int i = 0; i < headers.size(); i++) {
            Object h = headers.get(i);
            if (h == null || isSeparator(h)) {
                continue;
            }
            Item it = new Item();
            it.header = h;
            it.originalIndex = i;
            it.title = text(field(h, "title"));
            if (it.title.length() == 0) {
                Object titleRes = field(h, "titleRes");
                if (titleRes instanceof Integer && (Integer) titleRes != 0) {
                    try {
                        it.title = res.getText((Integer) titleRes).toString().trim();
                    } catch (Throwable ignored) {
                    }
                }
            }
            Object id = field(h, "id");
            long idValue = id instanceof Number ? ((Number) id).longValue() : -1L;
            if (idValue > 0 && idValue <= Integer.MAX_VALUE) {
                try {
                    it.idName = res.getResourceEntryName((int) idValue);
                } catch (Throwable ignored) {
                }
            }
            items.add(it);
        }
        assign(items);
        overrideSlots(items);
        addAirplaneRow(activity, items);
        addFocusModeRow(activity, items);
        addCameraRow(activity, items);
        addSearchRow(activity, items);
        addUpdateRow(activity, items);
        addAirpodsRow(activity, items);
        addICloudRow(activity, items);
        removeSyntheticAdvanced(items);
        ensureInterconnection(activity, items);
        return items;
    }

    private static void assign(List<Item> items) {
        boolean[] taken = new boolean[items.size()];
        StringBuilder missing = new StringBuilder();
        for (int slot = 0; slot < SLOT_IDS.length; slot++) {
            boolean hit = false;
            for (String id : SLOT_IDS[slot]) {
                int idx = indexById(items, id, taken);
                if (idx >= 0) {
                    setSlot(items.get(idx), slot);
                    taken[idx] = true;
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                missing.append(SLOT_IDS[slot][0]).append(' ');
            }
        }
        for (int i = 0; i < items.size(); i++) {
            if (taken[i]) {
                continue;
            }
            int slot = slotByTitle(items.get(i).title);
            if (slot >= 0) {
                setSlot(items.get(i), slot);
                taken[i] = true;
            }
        }
        if (missing.length() > 0) {
            XLog.i("no header found for slot ids: " + missing.toString().trim()
                    + " (they simply do not exist on this device)");
        }
    }

    private static boolean isSyntheticHyperios(Object header) {
        if (header == null) {
            return false;
        }
        Object intent = field(header, "intent");
        if (intent instanceof android.content.Intent) {
            android.content.ComponentName cn = ((android.content.Intent) intent).getComponent();
            if (cn != null && "dev.hyperios.app".equals(cn.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /** Adds the HyperiOS settings entry (the module that hosts the glass sliders). */
    private static void addHyperiosRow(Activity activity, List<Item> items) {
        try {
            for (Item existing : items) {
                if ("HyperiOS".equals(existing.title)) {
                    return; // already present (native entry or previous insert)
                }
            }
            android.content.Intent intent = new android.content.Intent();
            intent.setClassName("dev.hyperios.app", "dev.hyperios.app.ui.MainActivity");
            if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                XLog.w("HyperiOS app not installed, skip entry");
                return;
            }
            Class<?> headerClass = Class.forName(
                    "com.android.settingslib.miuisettings.preference.PreferenceActivity$Header",
                    false, activity.getClassLoader());
            Object header = headerClass.getDeclaredConstructor().newInstance();
            setField(header, "title", "HyperiOS");
            setField(header, "intent", intent);
            try {
                android.content.res.Resources hyperRes = activity.getPackageManager()
                        .getResourcesForApplication("dev.hyperios.app");
                int iconId = hyperRes.getIdentifier("ic_settings_entry_flat",
                        "drawable", "dev.hyperios.app");
                if (iconId == 0) {
                    iconId = hyperRes.getIdentifier("ic_settings_entry_monet",
                            "drawable", "dev.hyperios.app");
                }
                if (iconId != 0) {
                    setField(header, "icon", hyperRes.getDrawable(iconId, null));
                }
            } catch (Throwable t) {
                XLog.w("HyperiOS entry icon failed: " + t);
            }
            Item it = new Item();
            it.header = header;
            it.idName = "hyperios_entry";
            it.title = "HyperiOS";
            it.group = 2;
            it.slot = 0;
            it.originalIndex = Integer.MAX_VALUE - 1;
            items.add(it);
            XLog.i("HyperiOS entry added");
        } catch (Throwable t) {
            XLog.e("HyperiOS entry failed", t);
        }
    }

    private static void overrideSlots(List<Item> items) {
        for (Item it : items) {
            if (it.idName == null) {
                continue;
            }
            if (it.idName.startsWith("wallet")) {
                it.group = 7;
                it.slot = 1;
            } else if (it.idName.equals("other_advanced_settings")) {
                it.group = 7;
                it.slot = 2;
            } else if (it.idName.startsWith("minors") || it.idName.contains("game_center")) {
                it.group = 7;
                it.slot = 4;
            } else if (it.title != null && it.title.contains("高级设置")) {
                // The native 高级设置 header sits right below 通用.
                it.group = 7;
                it.slot = 3;
            } else if (it.idName.startsWith("accessibility")) {
                it.group = 4;
                it.slot = 3;
            } else if (it.idName.startsWith("interconnection")) {
                it.group = 4;
                it.slot = 4;
            }
        }
    }

    /** Drops the synthetic row; the native 高级设置 header is the one we keep. */
    private static void removeSyntheticAdvanced(List<Item> items) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if ("advanced_settings_synth".equals(items.get(i).idName)) {
                items.remove(i);
            }
        }
    }

    private static boolean isSyntheticAdvanced(Object header) {
        if (header == null) {
            return false;
        }
        Object title = field(header, "title");
        Object fragment = field(header, "fragment");
        return title != null && "高级设置".equals(title.toString())
                && "com.android.settings.personal.OtherPersonalSettings".equals(fragment);
    }

    /** Synthetic 高级设置 row placed right below 通用 in the minors card. */
    private static void addAdvancedRow(Activity activity, List<Item> items) {
        try {
            android.content.res.Resources res = activity.getResources();
            Class<?> headerClass = Class.forName(
                    "com.android.settingslib.miuisettings.preference.PreferenceActivity$Header",
                    false, activity.getClassLoader());
            Object header = headerClass.getDeclaredConstructor().newInstance();
            setField(header, "title", "高级设置");
            int iconId = res.getIdentifier("ic_other_advanced_settings",
                    "drawable", "com.android.settings");
            if (iconId != 0) {
                setField(header, "iconRes", iconId);
            }
            setField(header, "fragment", "com.android.settings.personal.OtherPersonalSettings");
            Item it = new Item();
            it.header = header;
            it.idName = "advanced_settings_synth";
            it.title = "高级设置";
            it.group = 7;
            it.slot = 3;
            it.originalIndex = Integer.MAX_VALUE - 3;
            items.add(it);
            XLog.i("advanced settings row added");
        } catch (Throwable t) {
            XLog.e("advanced settings row failed", t);
        }
    }

    private static void removeItemsByTitle(List<Item> items, String title) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item item = items.get(i);
            if (item.title != null && item.title.contains(title)) {
                items.remove(i);
            }
        }
    }

    private static void removeItems(List<Item> items, String idPrefix) {
        for (int i = items.size() - 1; i >= 0; i--) {
            Item item = items.get(i);
            if (item.idName != null && item.idName.startsWith(idPrefix)) {
                items.remove(i);
            }
        }
    }

    private static void setSlot(Item it, int slot) {
        it.slot = slot;
        it.group = SLOT_GROUP[slot];
    }

    private static boolean isSyntheticAirplane(Object header) {
        if (header == null) {
            return false;
        }
        Object intent = field(header, "intent");
        if (intent instanceof android.content.Intent
                && "android.settings.AIRPLANE_MODE_SETTINGS".equals(
                        ((android.content.Intent) intent).getAction())) {
            return true;
        }
        Object title = field(header, "title");
        return title != null && "飞行模式".equals(title.toString())
                && field(header, "fragment") == null;
    }

    /** Synthetic "飞行模式" row placed as the first item of the network card. */
    private static void addAirplaneRow(Activity activity, List<Item> items) {
        try {
            android.content.res.Resources res = activity.getResources();
            Class<?> headerClass = Class.forName(
                    "com.android.settingslib.miuisettings.preference.PreferenceActivity$Header",
                    false, activity.getClassLoader());
            Object header = headerClass.getDeclaredConstructor().newInstance();
            CharSequence title = "飞行模式";
            int titleId = res.getIdentifier("airplane_mode", "string", "com.android.settings");
            if (titleId == 0) {
                titleId = res.getIdentifier("airplane_mode_title", "string", "com.android.settings");
            }
            if (titleId != 0) {
                title = res.getText(titleId);
            }
            int iconId = res.getIdentifier("ic_device_connection",
                    "drawable", "com.android.settings");
            if (iconId == 0) {
                iconId = res.getIdentifier("ic_airplane_mode_settings",
                        "drawable", "com.android.settings");
            }
            setField(header, "title", title);
            if (iconId != 0) {
                setField(header, "iconRes", iconId);
            }
            // A non-null intent makes MIUI render this as a normal header row
            // (type 1) instead of a category row; the click is replaced later.
            setField(header, "intent",
                    new android.content.Intent("android.settings.AIRPLANE_MODE_SETTINGS"));
            Item it = new Item();
            it.header = header;
            it.idName = "airplane_mode";
            it.title = title.toString();
            it.group = 3;
            it.slot = -1;
            it.originalIndex = Integer.MAX_VALUE - 2;
            items.add(it);
            XLog.i("airplane row added");
        } catch (Throwable t) {
            XLog.e("airplane row failed", t);
        }
    }

    private static boolean isSyntheticFocus(Object header) {
        if (header == null) {
            return false;
        }
        Object title = field(header, "title");
        if (title == null || !FOCUS_TITLE.equals(title.toString())) {
            return false;
        }
        Object intent = field(header, "intent");
        return intent instanceof android.content.Intent
                && FOCUS_ACTION.equals(((android.content.Intent) intent).getAction());
    }

    /**
     * Synthetic "专注模式" row directly above 屏幕时间. iOS has a standalone
     * Focus entry there; MIUI has no such header, so the row opens the system
     * Do Not Disturb page and reuses Settings' own
     * {@code ic_do_not_disturb_mode_settings} icon (theme-overridable and
     * density-aware, exactly like the airplane row).
     */
    private static void addFocusModeRow(Activity activity, List<Item> items) {
        for (Item it : items) {
            if (FOCUS_TITLE.equals(it.title) || "focus_mode".equals(it.idName)) {
                return; // already present (native entry or previous insert)
            }
        }
        try {
            android.content.Intent intent = new android.content.Intent(FOCUS_ACTION);
            if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                XLog.i("focus mode: no activity for " + FOCUS_ACTION + ", skip row");
                return;
            }
            android.content.res.Resources res = activity.getResources();
            Class<?> headerClass = Class.forName(
                    "com.android.settingslib.miuisettings.preference.PreferenceActivity$Header",
                    false, activity.getClassLoader());
            Object header = headerClass.getDeclaredConstructor().newInstance();
            setField(header, "title", FOCUS_TITLE);
            int iconId = res.getIdentifier("ic_do_not_disturb_mode_settings",
                    "drawable", "com.android.settings");
            if (iconId == 0) {
                iconId = res.getIdentifier("ic_do_not_disturb_mode",
                        "drawable", "com.android.settings");
            }
            if (iconId != 0) {
                setField(header, "iconRes", iconId);
            } else {
                XLog.w("focus mode: ic_do_not_disturb_mode_settings missing, text-only row");
            }
            setField(header, "intent", intent);
            int slot = slotIndex("focus_mode_settings");
            Item it = new Item();
            it.header = header;
            it.idName = "focus_mode";
            it.title = FOCUS_TITLE;
            it.slot = slot >= 0 ? slot : 20;
            it.group = slot >= 0 ? SLOT_GROUP[slot] : 5;
            it.originalIndex = Integer.MAX_VALUE - 2;
            items.add(it);
            XLog.i("focus mode row added");
        } catch (Throwable t) {
            XLog.e("focus mode row failed", t);
        }
    }

    private static boolean isIntentRow(Object header, String pkg, String cls) {
        Object intent = field(header, "intent");
        if (!(intent instanceof android.content.Intent)) {
            return false;
        }
        android.content.ComponentName cn = ((android.content.Intent) intent).getComponent();
        return cn != null && pkg.equals(cn.getPackageName()) && cls.equals(cn.getClassName());
    }

    private static boolean isSyntheticCamera(Object header) {
        return isIntentRow(header, CAMERA_PKG, CAMERA_CLS);
    }

    private static boolean isSyntheticSearch(Object header) {
        return isIntentRow(header, SEARCH_PKG, SEARCH_CLS);
    }

    /**
     * Synthetic "相机" row directly below 外观. MIUI keeps the camera settings
     * inside the camera app, so the row opens
     * {@code com.android.camera/.CameraPreferenceActivity} with Settings' own
     * {@code ic_settings_camera} artwork.
     */
    private static void addCameraRow(Activity activity, List<Item> items) {
        addIntentRow(activity, items, CAMERA_TITLE, "camera_entry", CAMERA_PKG, CAMERA_CLS,
                new String[]{"ic_settings_camera", "ic_camera_settings"}, 14);
    }

    /**
     * Synthetic "搜索" row directly below 墙纸. 小米搜索 keeps its options in
     * {@code com.android.quicksearchbox/.preferences.SearchSettingsPreferenceActivity};
     * the icon is Settings' {@code ic_search_24dp}.
     *
     * <p>16.7: 墙纸 moved from slot 11 to slot 12 (控制中心 now takes 11 and sits
     * above it), so the anchor follows it — the row must stay under 墙纸.</p>
     */
    private static void addSearchRow(Activity activity, List<Item> items) {
        addIntentRow(activity, items, SEARCH_TITLE, "search_entry", SEARCH_PKG, SEARCH_CLS,
                new String[]{"ic_search_24dp", "ic_search"}, 12);
    }

    /**
     * Adds a row that launches an activity of another package. The row takes
     * {@code anchorSlot} (so it inherits the card) and a later original index
     * than any native row, which puts it directly below that row without
     * renumbering {@link #SLOT_IDS}.
     */
    private static void addIntentRow(Activity activity, List<Item> items, String title,
                                     String idName, String pkg, String cls,
                                     String[] iconNames, int anchorSlot) {
        for (Item it : items) {
            if (isIntentRow(it.header, pkg, cls)) {
                return; // previous insert survived cleanup, do not duplicate
            }
        }
        try {
            android.content.Intent intent = new android.content.Intent();
            intent.setClassName(pkg, cls);
            if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                XLog.i(idName + ": " + pkg + "/" + cls + " missing, skip row");
                return;
            }
            android.content.res.Resources res = activity.getResources();
            Class<?> headerClass = Class.forName(
                    "com.android.settingslib.miuisettings.preference.PreferenceActivity$Header",
                    false, activity.getClassLoader());
            Object header = headerClass.getDeclaredConstructor().newInstance();
            setField(header, "title", title);
            setField(header, "intent", intent);
            int iconId = 0;
            for (String name : iconNames) {
                iconId = res.getIdentifier(name, "drawable", "com.android.settings");
                if (iconId != 0) {
                    break;
                }
            }
            if (iconId != 0) {
                setField(header, "iconRes", iconId);
            } else {
                XLog.w(idName + ": no Settings drawable " + iconNames[0] + ", text-only row");
            }
            Item it = new Item();
            it.header = header;
            it.idName = idName;
            it.title = title;
            it.slot = anchorSlot;
            it.group = SLOT_GROUP[anchorSlot];
            it.originalIndex = Integer.MAX_VALUE - 4;
            items.add(it);
            XLog.i(idName + " row added below slot " + anchorSlot);
        } catch (Throwable t) {
            XLog.e(idName + " row failed", t);
        }
    }

    /* ------------------------ conditional cards (v16) ----------------------- */

    /** Marks a synthetic header so the next {@link #apply} can drop it again. */
    private static void mark(Object header, String tag) {
        try {
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString(MARK_KEY, tag);
            setField(header, "extras", extras);
        } catch (Throwable t) {
            XLog.w("mark synthetic header failed: " + t);
        }
    }

    static boolean isMarked(Object header, String tag) {
        Object extras = field(header, "extras");
        return extras instanceof android.os.Bundle
                && tag.equals(((android.os.Bundle) extras).getString(MARK_KEY));
    }

    static boolean isUpdateHeader(Object header) {
        return isMarked(header, "update");
    }

    /**
     * The "通用" row (16.6). It is the one MIUI decorates with a blue count while
     * a system update is waiting; the title is checked instead of an id because
     * themes rename both.
     */
    static boolean isGeneralHeader(Object header) {
        Object title = field(header, "title");
        if (!(title instanceof CharSequence)) {
            return false;
        }
        String value = title.toString().trim();
        return "通用".equals(value) || "更多设置".equals(value);
    }

    static boolean isAirpodsHeader(Object header) {
        return isMarked(header, "airpods");
    }

    private static boolean isSyntheticUpdate(Object header) {
        return isUpdateHeader(header);
    }

    private static boolean isSyntheticAirpods(Object header) {
        return isAirpodsHeader(header);
    }

    private static boolean isSyntheticICloud(Object header) {
        return isICloudHeader(header);
    }

    static boolean isICloudHeader(Object header) {
        return isMarked(header, "icloud");
    }

    /**
     * Drawable for a synthetic card icon.
     *
     * <p>16.2: the module copy wins. Both bundled PNGs were taken from the
     * supplied Settings resource archive ({@code D:\下载\com.android.settings}:
     * the iOS-style grey AirPods squircle and the white iCloud squircle), so the
     * headset / iCloud rows use exactly those files. A same-named drawable in
     * {@code com.android.settings} is only the fallback for a broken module
     * install; the previous order let a third-party theme override the icons the
     * user explicitly asked for.</p>
     */
    static Drawable cardDrawable(Context context, String name) {
        Drawable bundled = moduleDrawable(context, name);
        if (bundled != null) {
            XLog.i("card icon " + name + " <- module (supplied theme)");
            return bundled;
        }
        try {
            int id = context.getResources().getIdentifier(name, "drawable", "com.android.settings");
            if (id != 0) {
                Drawable drawable = context.getDrawable(id);
                if (drawable != null) {
                    XLog.i("card icon " + name + " <- settings (module copy unavailable)");
                    return drawable;
                }
            }
        } catch (Throwable t) {
            XLog.w("settings icon " + name + " unavailable: " + t);
        }
        XLog.w("card icon " + name + " missing");
        return null;
    }

    /** Loads one of the module's own drawables (ic_cloud, ic_sub_airpods, ...). */
    static android.graphics.drawable.Drawable moduleDrawable(android.content.Context context,
                                                            String name) {
        try {
            android.content.res.Resources res = context.getPackageManager()
                    .getResourcesForApplication(MODULE_PKG);
            int id = res.getIdentifier(name, "drawable", MODULE_PKG);
            return id == 0 ? null : res.getDrawable(id, null);
        } catch (Throwable t) {
            XLog.w("module drawable " + name + " unavailable: " + t);
            return null;
        }
    }

    private static Object newHeader(Activity activity, String title) throws Exception {
        Class<?> headerClass = Class.forName(
                "com.android.settingslib.miuisettings.preference.PreferenceActivity$Header",
                false, activity.getClassLoader());
        Object header = headerClass.getDeclaredConstructor().newInstance();
        setField(header, "title", title);
        return header;
    }

    /**
     * "有软件更新可用" card. Xiaomi's updater stores the found version in
     * {@code Settings.Global.miui_new_version} (and clears it when up to date),
     * which is exactly the "update available" state iOS shows as its badge row.
     */
    private static void addUpdateRow(Activity activity, List<Item> items) {
        for (Item it : items) {
            if (isSyntheticUpdate(it.header)) {
                return;
            }
        }
        try {
            String version = android.provider.Settings.Global.getString(
                    activity.getContentResolver(), UPDATE_KEY);
            if (version == null || version.trim().length() == 0) {
                XLog.i("update card: no new version, skip");
                return;
            }
            android.content.Intent intent = new android.content.Intent();
            intent.setClassName(UPDATE_PKG, UPDATE_CLS);
            if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                XLog.i("update card: updater activity missing, skip");
                return;
            }
            Object header = newHeader(activity, UPDATE_TITLE);
            setField(header, "intent", intent);
            mark(header, "update");
            Item it = new Item();
            it.header = header;
            it.idName = "update_entry";
            it.title = UPDATE_TITLE;
            it.group = GROUP_UPDATE;
            it.slot = 0;
            it.originalIndex = 0;
            items.add(it);
            XLog.i("update card added: " + version);
        } catch (Throwable t) {
            XLog.e("update card failed", t);
        }
    }

    /**
     * "我的 AirPods" card. Only exists while a headset is connected; the row
     * title is the device name so it matches what iOS shows above the network
     * card. Tapping it opens MIUI's own headset detail page (battery, rename,
     * noise control, ...), not the Bluetooth list.
     */
    private static void addAirpodsRow(Activity activity, List<Item> items) {
        for (Item it : items) {
            if (isSyntheticAirpods(it.header)) {
                return;
            }
        }
        try {
            String name = connectedHeadsetName(activity);
            if (name == null) {
                XLog.i("airpods card: no headset connected, skip");
                return;
            }
            android.bluetooth.BluetoothDevice device = connectedHeadsetDevice(activity);
            Object header = newHeader(activity, name);
            setField(header, "intent", headsetIntent(activity, device));
            android.graphics.drawable.Drawable icon = cardDrawable(activity, AIRPODS_ICON);
            if (icon != null) {
                setField(header, "icon", icon);
            } else {
                XLog.w("airpods card: " + AIRPODS_ICON + " missing, text-only row");
            }
            mark(header, "airpods");
            Item it = new Item();
            it.header = header;
            it.idName = "airpods_entry";
            it.title = name;
            it.group = GROUP_AIRPODS;
            it.slot = 0;
            it.originalIndex = 0;
            items.add(it);
            XLog.i("airpods card added: " + name);
        } catch (Throwable t) {
            XLog.e("airpods card failed", t);
        }
    }

    /**
     * Intent behind the headset card: MIUI's headset page when it can be
     * resolved, otherwise the Bluetooth list so the row always does something.
     */
    private static Intent headsetIntent(Activity activity, android.bluetooth.BluetoothDevice device) {
        if (device != null) {
            Intent detail = new Intent(HEADSET_ACTION);
            detail.putExtra(EXTRA_HEADSET_DEVICE, device);
            detail.putExtra(EXTRA_HEADSET_ADDRESS, device.getAddress());
            detail.putExtra(EXTRA_HEADSET_FROM, HEADSET_COME_FROM);
            detail.addCategory(Intent.CATEGORY_DEFAULT);
            try {
                if (activity.getPackageManager().resolveActivity(detail, 0) != null) {
                    XLog.i("headset card -> headset detail " + device.getAddress());
                    return detail;
                }
                detail.setClassName("com.android.settings", HEADSET_CLS);
                if (activity.getPackageManager().resolveActivity(detail, 0) != null) {
                    XLog.i("headset card -> headset detail (by class)");
                    return detail;
                }
            } catch (Throwable t) {
                XLog.w("headset detail intent failed: " + t);
            }
        }
        XLog.i("headset card -> bluetooth settings"
                + (device == null ? " (no bluetooth device)" : ""));
        return new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    /**
     * iCloud row directly below Game Center, inside the same card. Opens 小米云服务
     * through its own {@code com.xiaomi.action.MICLOUD_MAIN} entry point.
     */
    private static void addICloudRow(Activity activity, List<Item> items) {
        for (Item it : items) {
            if (isSyntheticICloud(it.header)) {
                return;
            }
        }
        try {
            android.content.Intent intent = new android.content.Intent(CLOUD_ACTION);
            if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                intent = new android.content.Intent();
                intent.setClassName(CLOUD_PKG, CLOUD_CLS);
                if (activity.getPackageManager().resolveActivity(intent, 0) == null) {
                    XLog.i("icloud: no MiCloud activity, skip row");
                    return;
                }
            }
            Object header = newHeader(activity, ICLOUD_TITLE);
            setField(header, "intent", intent);
            android.graphics.drawable.Drawable icon = cardDrawable(activity, ICLOUD_ICON);
            if (icon != null) {
                setField(header, "icon", icon);
            } else {
                XLog.w("icloud: ic_cloud missing, text-only row");
            }
            mark(header, "icloud");
            Item it = new Item();
            it.header = header;
            it.idName = "icloud_entry";
            it.title = ICLOUD_TITLE;
            // Same card as Game Center: slot 27 is Game Center, 28 sorts after it.
            // (Group 8 also uses slot 28 but is a different card, so no clash.)
            it.slot = 28;
            it.group = 7;
            it.originalIndex = 0;
            items.add(it);
            XLog.i("icloud row added below Game Center");
        } catch (Throwable t) {
            XLog.e("icloud row failed", t);
        }
    }

    /** Test hook: fake headset name, or {@code null} when unset. */
    private static String fakeHeadsetName(Activity activity) {
        try {
            String fake = android.provider.Settings.Global.getString(
                    activity.getContentResolver(), FAKE_HEADSET_KEY);
            if (fake != null && fake.trim().length() > 0) {
                return fake.trim();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Name of the currently connected headset, or {@code null} when none.
     *
     * <p>Bluetooth A2DP/SCO/BLE headsets carry their address, so the alias the
     * user gave the device is available; wired and USB headsets fall back to
     * the product name reported by audio policy.</p>
     */
    static String connectedHeadsetName(Activity activity) {
        String fake = fakeHeadsetName(activity);
        if (fake != null) {
            return fake;
        }
        try {
            android.media.AudioDeviceInfo device = bestHeadsetOutput(activity);
            if (device == null) {
                return null;
            }
            String name = bluetoothDeviceName(device);
            if (name == null || name.trim().length() == 0) {
                CharSequence product = device.getProductName();
                name = product == null ? null : product.toString();
            }
            if (name == null || name.trim().length() == 0) {
                name = "耳机";
            }
            return name.trim();
        } catch (Throwable t) {
            XLog.e("headset detection failed", t);
        }
        return null;
    }

    /**
     * Bluetooth device behind the connected headset, or {@code null} for wired
     * and USB headsets (and when nothing is connected). The headset detail page
     * needs it; the visible card only needs the name.
     */
    static android.bluetooth.BluetoothDevice connectedHeadsetDevice(Activity activity) {
        try {
            String fakeAddress = android.provider.Settings.Global.getString(
                    activity.getContentResolver(), FAKE_HEADSET_ADDRESS_KEY);
            if (fakeAddress != null && fakeAddress.trim().length() > 0) {
                return remoteDevice(fakeAddress.trim());
            }
        } catch (Throwable ignored) {
        }
        try {
            android.media.AudioDeviceInfo device = bestHeadsetOutput(activity);
            if (device == null) {
                return null;
            }
            android.bluetooth.BluetoothDevice remote = remoteDevice(device);
            if (remote == null) {
                XLog.i("headset audio device has no usable address: type="
                        + device.getType() + " addr=" + device.getAddress());
            }
            return remote;
        } catch (Throwable t) {
            XLog.e("headset device detection failed", t);
        }
        return null;
    }

    /**
     * Best connected headset output. A2DP wins over SCO/BLE: only the A2DP
     * device carries the real MAC, and some stacks also expose an SCO entry
     * with an all-zero address while the headset is connected.
     */
    private static android.media.AudioDeviceInfo bestHeadsetOutput(Activity activity) {
        android.media.AudioManager am = (android.media.AudioManager)
                activity.getSystemService(android.content.Context.AUDIO_SERVICE);
        if (am == null) {
            return null;
        }
        android.media.AudioDeviceInfo[] devices =
                am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS);
        android.media.AudioDeviceInfo best = null;
        int bestScore = -1;
        for (android.media.AudioDeviceInfo device : devices) {
            int type = device.getType();
            if (!isHeadsetType(type)) {
                continue;
            }
            int score = headsetPriority(type);
            if (score > bestScore) {
                best = device;
                bestScore = score;
            }
        }
        return best;
    }

    private static int headsetPriority(int type) {
        switch (type) {
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                return 50;
            case android.media.AudioDeviceInfo.TYPE_BLE_HEADSET:
                return 40;
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                return 30;
            case android.media.AudioDeviceInfo.TYPE_HEARING_AID:
                return 20;
            case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
                return 10;
            default:
                return 0;
        }
    }

    static boolean isHeadsetType(int type) {
        switch (type) {
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET:
            case android.media.AudioDeviceInfo.TYPE_USB_HEADSET:
            case android.media.AudioDeviceInfo.TYPE_HEARING_AID:
                return true;
            default:
                break;
        }
        return android.os.Build.VERSION.SDK_INT >= 31
                && type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    private static android.bluetooth.BluetoothDevice remoteDevice(
            android.media.AudioDeviceInfo device) {
        if (device == null) {
            return null;
        }
        String address = device.getAddress();
        if (!isUsableBluetoothAddress(address)) {
            // MIUI's audio policy sometimes exposes the placeholder address for
            // the A2DP output; the real one is still on the device object.
            address = hiddenAddress(device);
        }
        return remoteDevice(address);
    }

    private static boolean isUsableBluetoothAddress(String address) {
        if (address == null) {
            return false;
        }
        String value = address.trim().toUpperCase(java.util.Locale.ROOT);
        return value.length() == 17
                && !"00:00:00:00:00:00".equals(value)
                && !"FF:FF:FF:FF:FF:FF".equals(value);
    }

    private static String hiddenAddress(android.media.AudioDeviceInfo device) {
        try {
            Object value = de.robv.android.xposed.XposedHelpers.getObjectField(device, "mAddress");
            if (value instanceof String && isUsableBluetoothAddress((String) value)) {
                return (String) value;
            }
        } catch (Throwable ignored) {
        }
        try {
            String text = device.toString();
            int at = text.indexOf("addr:");
            if (at >= 0 && text.length() >= at + 22) {
                String candidate = text.substring(at + 5, at + 22);
                if (isUsableBluetoothAddress(candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static android.bluetooth.BluetoothDevice remoteDevice(String address) {
        if (!isUsableBluetoothAddress(address)) {
            return null;
        }
        try {
            android.bluetooth.BluetoothAdapter adapter =
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            return adapter == null ? null : adapter.getRemoteDevice(address.trim());
        } catch (Throwable t) {
            return null;
        }
    }

    private static String bluetoothDeviceName(android.media.AudioDeviceInfo device) {
        return bluetoothDeviceName(remoteDevice(device));
    }

    private static String bluetoothDeviceName(android.bluetooth.BluetoothDevice remote) {
        try {
            if (remote == null) {
                return null;
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                String alias = remote.getAlias();
                if (alias != null && alias.length() > 0) {
                    return alias;
                }
            }
            return remote.getName();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Index of a slot id in {@link #SLOT_IDS}, or -1 when the id is unknown. */
    private static int slotIndex(String id) {
        for (int i = 0; i < SLOT_IDS.length; i++) {
            for (String alias : SLOT_IDS[i]) {
                if (alias.equals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * MIUI drops the "隔空播放与连续互通" header on devices where the MiLink
     * service is missing. When the user has the service installed but the header
     * is still absent, add it back (same intent as settings_headers.xml) so it
     * can sit in the Control Center card.
     */
    private static void ensureInterconnection(Activity activity, List<Item> items) {
        for (Item it : items) {
            String t = it.title == null ? "" : it.title;
            if (it.idName.contains("interconnection") || t.contains("隔空播放")
                    || t.contains("连续互通") || t.contains("互传")) {
                return;
            }
        }
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            android.content.Intent intent = new android.content.Intent(
                    "miui.intent.action.MIRROR_SETTING");
            intent.setPackage("com.milink.service");
            if (pm.resolveActivity(intent, 0) == null) {
                XLog.i("interconnection header missing and MiLink not installed; skip synth");
                return;
            }
            android.content.res.Resources res = activity.getResources();
            int titleId = res.getIdentifier("interconnection", "string", "com.android.settings");
            int iconId = res.getIdentifier("ic_interconnection", "drawable", "com.android.settings");
            int headerId = res.getIdentifier("interconnection_settings", "id", "com.android.settings");
            if (titleId == 0) {
                return;
            }
            Class<?> headerClass = Class.forName(
                    "com.android.settingslib.miuisettings.preference.PreferenceActivity$Header",
                    false, activity.getClassLoader());
            Object header = headerClass.getDeclaredConstructor().newInstance();
            if (!setField(header, "title", res.getText(titleId))
                    || !setField(header, "iconRes", iconId)
                    || !setField(header, "intent", intent)) {
                XLog.w("could not fill synthetic interconnection header");
                return;
            }
            if (headerId != 0) {
                setLongField(header, "id", Math.max(0, headerId));
            }
            Item it = new Item();
            it.header = header;
            it.idName = "interconnection_settings";
            it.title = res.getText(titleId).toString();
            it.slot = 4;      // right below 无障碍 in the general card
            it.group = 4;
            it.originalIndex = Integer.MAX_VALUE - 1;
            items.add(it);
            XLog.i("synthesized missing interconnection header");
        } catch (Throwable t) {
            XLog.e("synthesize interconnection failed", t);
        }
    }

    private static int indexById(List<Item> items, String id, boolean[] taken) {
        for (int i = 0; i < items.size(); i++) {
            if (!taken[i] && items.get(i).idName.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static int slotByTitle(String title) {
        if (title == null || title.length() == 0) {
            return -1;
        }
        String t = title.replace(" ", "");
        for (int slot = 0; slot < SLOT_TITLES.length; slot++) {
            for (String alias : SLOT_TITLES[slot]) {
                String a = alias.replace(" ", "");
                if (a.length() >= 3 ? t.contains(a) : t.equalsIgnoreCase(a)) {
                    return slot;
                }
            }
        }
        return -1;
    }

    /* -------------------------------- reorder ------------------------------- */

    /**
     * Forces a rebuild of the synthetic cards (used when a headset connects or
     * disconnects while the page is open). Drops the memoised order first,
     * otherwise {@link #apply} would return early for an unchanged native list.
     */
    static void refresh(Object fragment) {
        if (fragment == null) {
            return;
        }
        LAST_ORDER.remove(fragment);
        apply(fragment);
    }

    /**
     * Rewrites the live list order and Header.groupId, then asks the miuix card
     * adapter to recompute card groups. Safe to call repeatedly.
     */
    static boolean apply(Object fragment) {
        try {
            Activity activity = activity(fragment);
            List<Object> headers = readHeaders(fragment);
            if (activity == null || headers == null || headers.size() < 2) {
                return false;
            }
            // If the list already matches the order we produced last time, do
            // nothing at all. Re-running the reorder on every resume is what made
            // the cards shrink row by row (MIUI re-judged groups between calls).
            List<Object> lastOrder = LAST_ORDER.get(fragment);
            if (lastOrder != null && sameOrder(headers, lastOrder)) {
                return false;
            }
            // apply() runs on every resume: drop the synthetic rows we added last
            // time so they do not accumulate at the bottom of the list.
            for (int i = headers.size() - 1; i >= 0; i--) {
                if (isSyntheticAirplane(headers.get(i)) || isSyntheticAdvanced(headers.get(i))
                        || isSyntheticHyperios(headers.get(i)) || isSyntheticFocus(headers.get(i))
                        || isSyntheticCamera(headers.get(i)) || isSyntheticSearch(headers.get(i))
                        || isSyntheticUpdate(headers.get(i)) || isSyntheticAirpods(headers.get(i))
                        || isSyntheticICloud(headers.get(i))) {
                    headers.remove(i);
                }
            }
            List<Item> items = parse(activity, headers);
            if (items.size() < 2) {
                return false;
            }
            List<Item> ordered = new ArrayList<>(items);
            Collections.sort(ordered, new Comparator<Item>() {
                @Override
                public int compare(Item a, Item b) {
                    if (a.group != b.group) {
                        return a.group < b.group ? -1 : 1;
                    }
                    if (a.slot != b.slot) {
                        return a.slot < b.slot ? -1 : 1;
                    }
                    return a.originalIndex < b.originalIndex ? -1
                            : (a.originalIndex == b.originalIndex ? 0 : 1);
                }
            });

            List<Object> newOrder = new ArrayList<>(ordered.size());
            boolean groupIdsOk = true;
            for (Item it : ordered) {
                // Group ids are assigned per contiguous run again, otherwise MIUI
                // keeps the original ids and every row ends up as its own card.
                if (!setIntField(it.header, "groupId", GROUP_ID_BASE + it.group)) {
                    groupIdsOk = false;
                }
                Object existing = field(it.header, "groupId");
                if (existing instanceof Integer && (Integer) existing != GROUP_ID_BASE + it.group) {
                    groupIdsOk = false;
                }
                newOrder.add(it.header);
            }

            if (headers.size() == newOrder.size()
                    && sameOrder(headers, newOrder)
                    && groupIdsOk) {
                // Order unchanged, but MIUI may have re-judged the groups when
                // the page resumed: refresh the decoration anyway, otherwise the
                // card ends up shorter on every return from a sub page.
                Object lv = field(fragment, "mListView");
                Object p = field(fragment, "mProxyAdapter");
                if (lv != null) {
                    call(p, "updateGroupInfo");
                    call(lv, "invalidateItemDecorations");
                    call(lv, "invalidate");
                }
                return false;   // already in place
            }

            if (!replaceContent(headers, newOrder)) {
                // The list may be immutable: point fragment + adapter at a new one.
                List<Object> replacement = new ArrayList<>(newOrder);
                setField(fragment, "mHeaders", replacement);
                Object base = field(fragment, "mHeaderAdapter");
                setField(base, "mHeaders", replacement);
            }

            Object proxy = field(fragment, "mProxyAdapter");
            notifyAdapter(proxy);
            Object base = field(fragment, "mHeaderAdapter");
            if (base != proxy) {
                notifyAdapter(base);
            }
            Object listView = field(fragment, "mListView");
            if (listView != null) {
                call(listView, "invalidateItemDecorations");
                call(listView, "requestLayout");
                // Re-run the group info once more after the next layout pass:
                // the card decoration (and with it the "last row has no divider"
                // logic) reads the type map when it draws.
                final Object proxyForLater = proxy;
                if (listView instanceof android.view.View) {
                    ((android.view.View) listView).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                call(proxyForLater, "updateGroupInfo");
                                call(listView, "invalidateItemDecorations");
                                call(listView, "invalidate");
                            } catch (Throwable ignored) {
                            }
                        }
                    });
                }
            }

            LAST_ORDER.put(fragment, new ArrayList<>(newOrder));
            XLog.i("reordered " + newOrder.size() + " headers\n" + dump(ordered));
            return true;
        } catch (Throwable t) {
            XLog.e("reorder failed, keeping native order", t);
            return false;
        }
    }

    private static boolean sameOrder(List<Object> current, List<Object> desired) {
        if (current.size() != desired.size()) {
            return false;
        }
        for (int i = 0; i < desired.size(); i++) {
            if (current.get(i) != desired.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean replaceContent(List<Object> list, List<Object> newOrder) {
        try {
            list.clear();
            list.addAll(newOrder);
            return true;
        } catch (Throwable t) {
            XLog.w("mHeaders is not mutable: " + t);
            return false;
        }
    }

    private static void notifyAdapter(Object adapter) {
        if (adapter == null) {
            return;
        }
        try {
            call(adapter, "updateGroupInfo");
            call(adapter, "notifyDataSetChanged");
        } catch (Throwable t) {
            XLog.e("notify adapter failed", t);
        }
    }

    private static Object call(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            } catch (Throwable t) {
                XLog.e("call " + name + " failed", t);
                return null;
            }
        }
        return null;
    }

    static Activity activity(Object fragment) {
        try {
            java.lang.reflect.Method m = fragment.getClass().getMethod("getActivity");
            Object r = m.invoke(fragment);
            if (r instanceof Activity) {
                return (Activity) r;
            }
        } catch (Throwable ignored) {
        }
        Object ctx = field(fragment, "mContext");
        return ctx instanceof Activity ? (Activity) ctx : null;
    }

    /* --------------------------------- dump -------------------------------- */

    static String dump(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            sb.append("  g").append(it.group)
                    .append(" s").append(it.slot)
                    .append(" id=").append(it.idName)
                    .append(" title=").append(it.title)
                    .append('\n');
        }
        return sb.toString();
    }
}
