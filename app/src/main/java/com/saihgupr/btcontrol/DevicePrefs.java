package com.saihgupr.btcontrol;

import android.content.Context;
import android.content.SharedPreferences;

public class DevicePrefs {
    private static final String PREFS_NAME = "device_activity";
    private static final String KEY_PREFIX = "last_used_";
    private static final String KEY_AUTO_CONNECT = "auto_connect_enabled";
    private static final String KEY_DEFAULT_DEVICE_ADDRESS = "default_device_address";
    private static final String KEY_DEFAULT_DEVICE_NAME = "default_device_name";
    private static final String KEY_STARTUP_VOLUME_ENABLED = "startup_volume_enabled";
    private static final String KEY_STARTUP_VOLUME_PERCENT = "startup_volume_percent";
    private static final String KEY_AMBIENT_LEVEL = "ambient_level";
    private static final String KEY_LAST_ANC_MODE = "last_anc_mode";
    private static final int DEFAULT_AMBIENT_LEVEL = 20;
    private static final int MAX_AMBIENT_LEVEL = 20;

    public static void updateLastUsed(Context context, String address) {
        if (address == null || context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_PREFIX + address, System.currentTimeMillis()).apply();
    }

    public static long getLastUsed(Context context, String address) {
        if (address == null || context == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_PREFIX + address, 0);
    }

    public static boolean isAutoConnectEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_CONNECT, false);
    }

    public static void setAutoConnectEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_CONNECT, enabled).apply();
    }

    public static String getDefaultDeviceAddress(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEFAULT_DEVICE_ADDRESS, null);
    }

    public static String getDefaultDeviceName(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEFAULT_DEVICE_NAME, null);
    }

    public static void setDefaultDevice(Context context, String address, String name) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_DEFAULT_DEVICE_ADDRESS, address)
                .putString(KEY_DEFAULT_DEVICE_NAME, name)
                .apply();
    }

    public static void clearDefaultDevice(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_DEFAULT_DEVICE_ADDRESS)
                .remove(KEY_DEFAULT_DEVICE_NAME)
                .apply();
    }

    public static boolean isStartupVolumeEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_STARTUP_VOLUME_ENABLED, false);
    }

    public static void setStartupVolumeEnabled(Context context, boolean enabled) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_STARTUP_VOLUME_ENABLED, enabled).apply();
    }

    public static int getStartupVolumePercent(Context context) {
        if (context == null) return 40;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_STARTUP_VOLUME_PERCENT, 40);
    }

    public static void setStartupVolumePercent(Context context, int percent) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_STARTUP_VOLUME_PERCENT, percent).apply();
    }

    public static int getAmbientLevel(Context context) {
        if (context == null) return DEFAULT_AMBIENT_LEVEL;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int level = prefs.getInt(KEY_AMBIENT_LEVEL, DEFAULT_AMBIENT_LEVEL);
        return Math.max(0, Math.min(MAX_AMBIENT_LEVEL, level));
    }

    public static void setAmbientLevel(Context context, int level) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int clamped = Math.max(0, Math.min(MAX_AMBIENT_LEVEL, level));
        prefs.edit().putInt(KEY_AMBIENT_LEVEL, clamped).apply();
    }

    public static String getLastAncMode(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LAST_ANC_MODE, null);
    }

    public static void setLastAncMode(Context context, String mode) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LAST_ANC_MODE, mode).apply();
    }

    public static void applyStartupVolume(Context context) {
        if (context == null || !isStartupVolumeEnabled(context)) return;
        try {
            android.media.AudioManager audioManager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                int percent = getStartupVolumePercent(context);
                int targetVolume = Math.max(1, Math.round((percent / 100.0f) * maxVolume));
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0);
            }
        } catch (Exception e) {
            android.util.Log.e("DevicePrefs", "Failed to apply startup volume", e);
        }
    }
}


