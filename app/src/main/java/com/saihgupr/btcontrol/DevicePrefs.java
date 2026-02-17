package com.saihgupr.btcontrol;

import android.content.Context;
import android.content.SharedPreferences;

public class DevicePrefs {
    private static final String PREFS_NAME = "device_activity";
    private static final String KEY_PREFIX = "last_used_";

    public static void updateLastUsed(Context context, String address) {
        if (address == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_PREFIX + address, System.currentTimeMillis()).apply();
    }

    public static long getLastUsed(Context context, String address) {
        if (address == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_PREFIX + address, 0);
    }
}
