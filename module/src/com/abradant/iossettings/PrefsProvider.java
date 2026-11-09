package com.abradant.iossettings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.Map;

/**
 * Serves the module settings to the hooked Settings process.
 *
 * <p>The hooked process runs under {@code com.android.settings}'s UID and
 * cannot read this app's {@code shared_prefs} file (it is 0600, and LSPosed's
 * XSharedPreferences mirror is 0660 with the module's own UID). A tiny exported
 * provider is the reliable way to hand three numbers over: the Settings side
 * calls {@link #call} and caches the result for a few hundred ms.</p>
 */
public final class PrefsProvider extends ContentProvider {

    static final String AUTHORITY = "com.abradant.iossettings.prefs";
    static final Uri URI = Uri.parse("content://" + AUTHORITY + "/settings");
    static final String METHOD_GET = "get";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if (!METHOD_GET.equals(method)) {
            return out;
        }
        try {
            Context context = getContext();
            if (context == null) {
                return out;
            }
            SharedPreferences prefs = context.getSharedPreferences(
                    Prefs.FILE, Context.MODE_PRIVATE);
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Float) {
                    out.putFloat(key, (Float) value);
                } else if (value instanceof Integer) {
                    out.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    out.putLong(key, (Long) value);
                } else if (value instanceof Boolean) {
                    out.putBoolean(key, (Boolean) value);
                } else if (value != null) {
                    out.putString(key, value.toString());
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
