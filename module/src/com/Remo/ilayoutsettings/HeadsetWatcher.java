package com.Remo.ilayoutsettings;

import android.app.Activity;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps the conditional headset card in sync with the live audio outputs.
 *
 * <p>Without this, {@link HeaderData#apply} only ran from {@code buildAdapter}
 * and {@code onResume}: connecting earbuds while the Settings home page was
 * already open left the card missing until the app was restarted. The watcher
 * debounces the burst of A2DP / SCO device callbacks a single connection
 * produces into one list rebuild.</p>
 */
final class HeadsetWatcher {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final long DEBOUNCE_MS = 350;

    private static final Map<Object, Registration> REGISTRATIONS = new WeakHashMap<>();
    private static final Map<Object, Runnable> PENDING = new WeakHashMap<>();

    private HeadsetWatcher() {
    }

    /** A registered callback plus the manager it must be removed from. */
    private static final class Registration {
        final AudioManager manager;
        final AudioDeviceCallback callback;

        Registration(AudioManager manager, AudioDeviceCallback callback) {
            this.manager = manager;
            this.callback = callback;
        }
    }

    static void attach(final Object fragment) {
        if (fragment == null || REGISTRATIONS.containsKey(fragment)) {
            return;
        }
        Activity activity = HeaderData.activity(fragment);
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AudioManager am = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        final WeakReference<Object> ref = new WeakReference<>(fragment);
        AudioDeviceCallback callback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
                if (hasHeadset(addedDevices)) {
                    schedule(ref);
                }
            }

            @Override
            public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
                if (hasHeadset(removedDevices)) {
                    schedule(ref);
                }
            }
        };
        try {
            am.registerAudioDeviceCallback(callback, MAIN);
            REGISTRATIONS.put(fragment, new Registration(am, callback));
            XLog.i("headset watcher attached");
        } catch (Throwable t) {
            XLog.e("headset watcher attach failed", t);
        }
    }

    static void detach(Object fragment) {
        if (fragment == null) {
            return;
        }
        Runnable pending = PENDING.remove(fragment);
        if (pending != null) {
            MAIN.removeCallbacks(pending);
        }
        Registration registration = REGISTRATIONS.remove(fragment);
        if (registration == null) {
            return;
        }
        try {
            registration.manager.unregisterAudioDeviceCallback(registration.callback);
            XLog.i("headset watcher detached");
        } catch (Throwable t) {
            XLog.w("headset watcher detach failed: " + t);
        }
    }

    private static boolean hasHeadset(AudioDeviceInfo[] devices) {
        if (devices == null) {
            return false;
        }
        for (AudioDeviceInfo device : devices) {
            if (device != null && HeaderData.isHeadsetType(device.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * One connection reports several devices (A2DP, then SCO or BLE), so every
     * callback only re-arms the same rebuild.
     */
    private static void schedule(final WeakReference<Object> ref) {
        Object fragment = ref.get();
        if (fragment == null) {
            return;
        }
        Runnable previous = PENDING.remove(fragment);
        if (previous != null) {
            MAIN.removeCallbacks(previous);
        }
        final Object key = fragment;
        Runnable rebuild = new Runnable() {
            @Override
            public void run() {
                PENDING.remove(key);
                Object current = ref.get();
                if (current == null) {
                    return;
                }
                try {
                    XLog.i("headset devices changed, refresh cards");
                    HeaderData.refresh(current);
                } catch (Throwable t) {
                    XLog.e("headset card refresh failed", t);
                }
            }
        };
        PENDING.put(fragment, rebuild);
        MAIN.postDelayed(rebuild, DEBOUNCE_MS);
    }
}
