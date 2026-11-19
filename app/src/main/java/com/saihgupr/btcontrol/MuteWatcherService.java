package com.saihgupr.btcontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.saihgupr.btcontrol.sony.SonyController;
import com.saihgupr.btcontrol.sony.SonySession;

public class MuteWatcherService extends Service {

    private static final String TAG = "MuteWatcher";
    private static final String CHANNEL_ID = "noise_control";
    private static final int NOTIFICATION_ID = 41;
    private static final long POLL_MS = 1000;
    private static final long DEBOUNCE_MS = 1500;

    private HandlerThread workerThread;
    private Handler worker;
    private AudioManager audioManager;

    private boolean lastMuted;
    private volatile boolean toggleInFlight;
    private long lastToggleAt;

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            boolean muted = audioManager != null && audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
            if (muted && !lastMuted) {
                long now = SystemClock.elapsedRealtime();
                if (!toggleInFlight && now - lastToggleAt >= DEBOUNCE_MS) {
                    lastToggleAt = now;
                    toggleAndUnmute();
                }
            }
            lastMuted = muted;
            if (worker != null) {
                worker.postDelayed(this, POLL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        startForeground(NOTIFICATION_ID, buildNotification());

        workerThread = new HandlerThread("mute-watch");
        workerThread.start();
        worker = new Handler(workerThread.getLooper());

        lastMuted = audioManager != null && audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
        worker.postDelayed(poll, POLL_MS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    private void toggleAndUnmute() {
        toggleInFlight = true;

        new Thread(() -> {
            SonySession.Result result = null;
            BluetoothDevice device = SonyController.resolveSonyDevice(this);
            if (device == null) {
                Log.w(TAG, "no Sony device");
            } else {
                try {
                    result = SonyController.toggle(device, DevicePrefs.getAmbientLevel(this));
                } catch (Exception e) {
                    Log.e(TAG, "toggle failed", e);
                }
                if (result != null && result.ok && result.mode != null) {
                    DevicePrefs.setLastAncMode(this, result.mode.name());
                } else {
                    Log.w(TAG, "toggle failed: " + (result != null ? result.error : "no result"));
                }
            }

            // Always restore the TV audio, otherwise the mute press silences everything.
            if (audioManager != null) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE, 0);
            }
            toggleInFlight = false;
        }, "mute-toggle").start();
    }

    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Noise control", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentTitle(getString(R.string.mute_watch_title))
                .setContentText(getString(R.string.mute_watch_text))
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
        }
        if (workerThread != null) {
            workerThread.quitSafely();
        }
        worker = null;
        workerThread = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
