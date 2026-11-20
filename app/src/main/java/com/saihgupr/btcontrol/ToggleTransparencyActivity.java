package com.saihgupr.btcontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.saihgupr.btcontrol.sony.SonyController;
import com.saihgupr.btcontrol.sony.SonySession;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressLint("MissingPermission")
public class ToggleTransparencyActivity extends AppCompatActivity {

    private static final String TAG = "ToggleTransparency";
    public static final String ACTION_TOGGLE_TRANSPARENCY = "com.saihgupr.btcontrol.ACTION_TOGGLE_TRANSPARENCY";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_NAME = "name";

    private static final long CONNECT_TIMEOUT_MS = 2500;
    private static final long CONNECT_POLL_MS = 150;
    private static final long WATCHDOG_MS = 12000;
    private static final long DEBOUNCE_MS = 750;

    // Guards against a double press opening two RFCOMM sockets in a row.
    private static final AtomicBoolean sInFlight = new AtomicBoolean(false);
    private static long sLastFinishTime = 0;
    private static volatile long sLastRunAt = 0;

    /** True right after this activity muted or unmuted the TV, so the mute watcher stays out. */
    static boolean ranRecently() {
        long at = sLastRunAt;
        return at != 0 && SystemClock.elapsedRealtime() - at < 4000;
    }

    private BluetoothManagerHelper btHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isFinished = false;
    private boolean inFlightHeld = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Remapped remote buttons start this activity after the TV has already handled the
        // mute press. Put the audio back before doing anything slow.
        restoreTvVolume();

        if (!acquireGuard()) {
            Log.d(TAG, "Toggle already in flight or debounced");
            finish();
            return;
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, R.string.status_bt_off, Toast.LENGTH_SHORT).show();
            finishAndCleanUp();
            return;
        }

        if (!hasConnectPermission()) {
            Toast.makeText(this, R.string.noise_control_permission, Toast.LENGTH_LONG).show();
            finishAndCleanUp();
            return;
        }

        // Safety timeout so the activity never lingers on screen
        mainHandler.postDelayed(this::onWatchdog, WATCHDOG_MS);

        btHelper = new BluetoothManagerHelper(getApplicationContext());

        Intent intent = getIntent();
        String targetAddress = (intent != null) ? intent.getStringExtra(EXTRA_ADDRESS) : null;
        String targetName = (intent != null) ? intent.getStringExtra(EXTRA_NAME) : null;

        BluetoothDevice targetDevice = resolveTargetDevice(adapter, targetAddress, targetName);
        if (targetDevice == null) {
            Toast.makeText(this, R.string.noise_control_no_device, Toast.LENGTH_LONG).show();
            finishAndCleanUp();
            return;
        }

        int state = btHelper.getConnectionState(targetDevice);
        if (state == BluetoothProfile.STATE_CONNECTED) {
            toggleAsync(targetDevice);
        } else {
            String displayName = targetDevice.getName() != null ? targetDevice.getName() : targetDevice.getAddress();
            Toast.makeText(this, getString(R.string.noise_control_connecting, displayName), Toast.LENGTH_SHORT).show();
            if (state != BluetoothProfile.STATE_CONNECTING) {
                btHelper.connect(targetDevice.getAddress());
            }
            pollForConnection(targetDevice, SystemClock.elapsedRealtime() + CONNECT_TIMEOUT_MS);
        }
    }

    private boolean acquireGuard() {
        if (!sInFlight.compareAndSet(false, true)) {
            return false;
        }
        if (SystemClock.elapsedRealtime() - sLastFinishTime < DEBOUNCE_MS) {
            sInFlight.set(false);
            return false;
        }
        inFlightHeld = true;
        return true;
    }

    private void releaseGuard() {
        if (!inFlightHeld) return;
        inFlightHeld = false;
        sLastFinishTime = SystemClock.elapsedRealtime();
        sInFlight.set(false);
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private BluetoothDevice resolveTargetDevice(BluetoothAdapter adapter, String explicitAddress, String explicitName) {
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null || bondedDevices.isEmpty()) {
                return null;
            }

            // 1. Explicit Address from Intent
            if (explicitAddress != null && BluetoothAdapter.checkBluetoothAddress(explicitAddress)) {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getAddress().equalsIgnoreCase(explicitAddress)) {
                        return device;
                    }
                }
            }

            // 2. Explicit Name from Intent
            if (explicitName != null) {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getName() != null && device.getName().equalsIgnoreCase(explicitName)) {
                        return device;
                    }
                }
            }

            // 3. User's configured Default Device
            String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);
            if (defaultAddress != null) {
                for (BluetoothDevice device : bondedDevices) {
                    if (device.getAddress().equalsIgnoreCase(defaultAddress)) {
                        return device;
                    }
                }
            }

            // 4. Most recently used device
            BluetoothDevice mostRecent = null;
            long latestTime = -1;
            for (BluetoothDevice device : bondedDevices) {
                long lastUsed = DevicePrefs.getLastUsed(this, device.getAddress());
                if (lastUsed > latestTime) {
                    latestTime = lastUsed;
                    mostRecent = device;
                }
            }
            if (mostRecent != null && latestTime > 0 && SonyController.looksLikeSony(mostRecent)) {
                return mostRecent;
            }

            // 5. A bonded Sony headset: prefer one that is already connected, then the
            //    classic name (not the "LE_..." BLE twin, which has no control service).
            for (BluetoothDevice device : bondedDevices) {
                try {
                    if (SonyController.looksLikeSony(device)
                            && btHelper.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                        return device;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sony check failed for " + device.getAddress(), e);
                }
            }
            for (BluetoothDevice device : bondedDevices) {
                try {
                    String name = device.getName();
                    if (SonyController.looksLikeSony(device) && name != null) {
                        String upper = name.toUpperCase(Locale.US);
                        if (upper.startsWith("WH-") || upper.startsWith("WF-") || upper.startsWith("MDR-")) {
                            return device;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sony check failed for " + device.getAddress(), e);
                }
            }
            for (BluetoothDevice device : bondedDevices) {
                try {
                    if (SonyController.looksLikeSony(device)) {
                        return device;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sony check failed for " + device.getAddress(), e);
                }
            }

            return null;
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while resolving target device", e);
            return null;
        }
    }

    private void pollForConnection(BluetoothDevice device, long deadline) {
        if (isFinished) return;

        if (btHelper.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
            toggleAsync(device);
            return;
        }

        if (SystemClock.elapsedRealtime() >= deadline) {
            // Let the Sony session decide: the RFCOMM link can work before the
            // A2DP proxy reports connected.
            Log.w(TAG, "Device not reported connected in time, attempting toggle anyway");
            toggleAsync(device);
            return;
        }

        mainHandler.postDelayed(() -> pollForConnection(device, deadline), CONNECT_POLL_MS);
    }

    private void toggleAsync(BluetoothDevice device) {
        final int ambientLevel = DevicePrefs.getAmbientLevel(this);

        new Thread(() -> {
            SonySession.Result result = null;
            try {
                result = SonyController.toggle(device, ambientLevel);
            } catch (Exception e) {
                Log.e(TAG, "Sony toggle failed", e);
            }
            final SonySession.Result finalResult = result;
            runOnUiThread(() -> finishWithResult(finalResult));
        }, "toggle-transparency").start();
    }

    private void finishWithResult(SonySession.Result result) {
        if (isFinished) return;

        if (result != null && result.ok) {
            SonySession.Mode mode = result.mode != null ? result.mode : SonySession.Mode.UNKNOWN;
            DevicePrefs.setLastAncMode(this, mode.name());
            Toast.makeText(this, getModeToast(mode), Toast.LENGTH_SHORT).show();
        } else {
            String error = (result != null && result.error != null)
                    ? result.error
                    : getString(R.string.noise_control_failed);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }

        finishAndCleanUp();
    }

    private int getModeToast(SonySession.Mode mode) {
        if (mode == SonySession.Mode.NC) return R.string.noise_control_toast_nc;
        if (mode == SonySession.Mode.AMBIENT) return R.string.noise_control_toast_ambient;
        if (mode == SonySession.Mode.OFF) return R.string.noise_control_toast_off;
        return R.string.noise_control_toast_updated;
    }

    private void onWatchdog() {
        if (isFinished) return;
        Toast.makeText(this, R.string.noise_control_timeout, Toast.LENGTH_SHORT).show();
        finishAndCleanUp();
    }

    private synchronized void finishAndCleanUp() {
        if (isFinished) return;
        isFinished = true;
        restoreTvVolume();
        mainHandler.removeCallbacksAndMessages(null);
        releaseGuard();
        finish();
    }

    /**
     * Android TV handles the remote mute key below the app layer, so no app can stop that
     * mute. Undoing it as soon as we run is the closest we can get.
     */
    private void restoreTvVolume() {
        sLastRunAt = SystemClock.elapsedRealtime();
        try {
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audioManager != null && audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE, 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "could not restore TV volume", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        releaseGuard();
    }
}
