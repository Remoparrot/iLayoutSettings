package com.abradant.iossettings;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

/** Logs to logcat, the LSPosed log and (best effort) to /data/local/tmp. */
final class XLog {

    static final String TAG = "IosSettings";

    private static PrintWriter writer;
    private static boolean fileTried;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private XLog() {}

    static void i(String msg) {
        Log.i(TAG, msg);
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
        toFile("I " + msg);
    }

    static void d(String msg) {
        Log.d(TAG, msg);
        toFile("D " + msg);
    }

    static void w(String msg) {
        Log.w(TAG, msg);
        try {
            XposedBridge.log(TAG + " W: " + msg);
        } catch (Throwable ignored) {
        }
        toFile("W " + msg);
    }

    static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try {
            XposedBridge.log(TAG + " E: " + msg);
            if (t != null) {
                XposedBridge.log(t);
            }
        } catch (Throwable ignored) {
        }
        toFile("E " + msg + (t == null ? "" : " :: " + Log.getStackTraceString(t)));
    }

    private static synchronized void toFile(String line) {
        try {
            if (!fileTried) {
                fileTried = true;
                File f = new File("/data/local/tmp/ios_settings.log");
                writer = new PrintWriter(new FileWriter(f, true));
            }
            if (writer != null) {
                writer.println(TIME.format(new Date()) + "  " + line);
                writer.flush();
            }
        } catch (Throwable ignored) {
            writer = null;
        }
    }
}
