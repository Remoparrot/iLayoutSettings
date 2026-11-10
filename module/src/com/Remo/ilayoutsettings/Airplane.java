package com.Remo.ilayoutsettings;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Reads / writes the system airplane mode switch. */
final class Airplane {

    private Airplane() {
    }

    static boolean isOn(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    static void set(Context context, boolean on) {
        try {
            if (isOn(context) == on) {
                return;
            }
            Settings.Global.putInt(context.getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, on ? 1 : 0);
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", on);
            context.sendBroadcast(intent);
            XLog.i("airplane mode -> " + on);
        } catch (Throwable t) {
            XLog.e("airplane mode write failed", t);
        }
    }
}
