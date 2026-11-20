package com.saihgupr.btcontrol;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import com.saihgupr.btcontrol.sony.SonyController;
import com.saihgupr.btcontrol.sony.SonySession;

/**
 * Watches the remote's mute key so a long press can toggle Sony noise control while a
 * short press keeps muting the TV.
 *
 * <p>This has to be an accessibility service because Android TV sends remote volume and
 * mute keys to the audio service rather than to the focused app. The key is never
 * consumed, so a tap mutes the TV exactly like before, whether or not the event reaches
 * this service.</p>
 *
 * <p>Some remotes report key repeats while the button is held, some report the release,
 * and some report neither. The long press only fires on evidence of a real hold: a key
 * repeat, or a release after the threshold. A tap therefore never triggers the toggle.</p>
 */
public class MuteKeyService extends AccessibilityService {

    private static final String TAG = "MuteKey";
    private static final long HOLD_MS = 500;
    private static final long DEBOUNCE_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;

    private static volatile long sLastMuteKeyAt;

    private long pressStartedAt;
    private boolean longPressFired;
    private boolean upDelivered;
    private boolean sawMuteKey;
    private boolean sawAnyKey;
    private long lastToggleAt;
    private volatile boolean toggleInFlight;

    /** True while the accessibility service is handling a mute press, so the state watcher
     *  does not treat the same press as its own gesture. */
    static boolean handledKeyRecently() {
        long at = sLastMuteKeyAt;
        return at != 0 && SystemClock.elapsedRealtime() - at < 3000;
    }

    private final Runnable holdTimer = new Runnable() {
        @Override
        public void run() {
            fireLongPress();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        // Once this service is on it owns the mute key; the state watcher would only
        // treat the same press as a second event.
        stopService(new Intent(this, MuteWatcherService.class));
        Log.d(TAG, "connected");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }
        if (!sawAnyKey) {
            sawAnyKey = true;
            Log.d(TAG, "key events arriving, first code=" + event.getKeyCode());
        }
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_MUTE) {
            return false;
        }
        if (!DevicePrefs.isMuteRemapEnabled(this)) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        sLastMuteKeyAt = now;
        if (!sawMuteKey) {
            sawMuteKey = true;
            Log.d(TAG, "mute key visible to accessibility");
            // From here on the key itself is the better signal, so the state watcher goes.
            stopService(new Intent(this, MuteWatcherService.class));
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                onMuteDown(now);
            } else if (!longPressFired && pressStartedAt != 0
                    && now - pressStartedAt >= HOLD_MS) {
                Log.d(TAG, "hold confirmed by key repeat");
                fireLongPress();
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            onMuteUp(now);
        }

        // Never consume: a tap must mute the TV exactly as it did before.
        return false;
    }

    private void onMuteDown(long now) {
        handler.removeCallbacks(holdTimer);
        pressStartedAt = now;
        longPressFired = false;
        if (upDelivered) {
            // Only arm the timer once this remote has proven it reports releases. Without
            // that proof a tap and a hold look identical but for a later release event,
            // and a timer would fire on every tap.
            handler.postDelayed(holdTimer, HOLD_MS);
        }
        Log.d(TAG, "mute down");
    }

    private void onMuteUp(long now) {
        handler.removeCallbacks(holdTimer);
        upDelivered = true;

        long held = pressStartedAt == 0 ? 0 : now - pressStartedAt;
        pressStartedAt = 0;

        if (!longPressFired && held >= HOLD_MS) {
            Log.d(TAG, "hold confirmed by release after " + held + "ms");
            fireLongPress();
        } else if (longPressFired) {
            unmute();
        }
        Log.d(TAG, "mute up after " + held + "ms");
    }

    private void fireLongPress() {
        long now = SystemClock.elapsedRealtime();
        if (toggleInFlight || now - lastToggleAt < DEBOUNCE_MS) {
            return;
        }
        longPressFired = true;
        lastToggleAt = now;
        Log.d(TAG, "mute long press");
        // If the firmware muted before the key reached us, bring the audio back now.
        unmute();
        toggleNoiseControl();
    }

    private void toggleNoiseControl() {
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
            }

            final SonySession.Result finalResult = result;
            final boolean noDevice = device == null;
            handler.post(() -> {
                if (finalResult != null && finalResult.ok && finalResult.mode != null) {
                    DevicePrefs.setLastAncMode(this, finalResult.mode.name());
                    Toast.makeText(this, getModeToast(finalResult.mode), Toast.LENGTH_SHORT).show();
                } else if (noDevice) {
                    Toast.makeText(this, R.string.noise_control_no_device, Toast.LENGTH_LONG).show();
                } else {
                    String error = (finalResult != null && finalResult.error != null)
                            ? finalResult.error
                            : getString(R.string.noise_control_failed);
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                }
                unmute();
                toggleInFlight = false;
            });
        }, "mute-key-toggle").start();
    }

    private boolean isMuted() {
        return audioManager != null && audioManager.isStreamMute(AudioManager.STREAM_MUSIC);
    }

    private void unmute() {
        if (audioManager == null) return;
        if (isMuted()) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE, 0);
        }
    }

    private int getModeToast(SonySession.Mode mode) {
        if (mode == SonySession.Mode.NC) return R.string.noise_control_toast_nc;
        if (mode == SonySession.Mode.AMBIENT) return R.string.noise_control_toast_ambient;
        if (mode == SonySession.Mode.OFF) return R.string.noise_control_toast_off;
        return R.string.noise_control_toast_updated;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        handler.removeCallbacksAndMessages(null);
        pressStartedAt = 0;
        // The state watcher cannot be started from the background, so it comes back the
        // next time the app or the TV starts.
        Log.d(TAG, "unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Only key events are used.
    }

    @Override
    public void onInterrupt() {
    }

    /** True when this service is switched on in the system accessibility settings. */
    public static boolean isEnabled(Context context) {
        if (context == null) return false;
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        for (AccessibilityServiceInfo info :
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) {
                continue;
            }
            if (context.getPackageName().equals(info.getResolveInfo().serviceInfo.packageName)
                    && MuteKeyService.class.getName()
                    .equals(info.getResolveInfo().serviceInfo.name)) {
                return true;
            }
        }
        return false;
    }
}
