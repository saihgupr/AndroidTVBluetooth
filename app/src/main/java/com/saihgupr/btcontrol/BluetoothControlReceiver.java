package com.saihgupr.btcontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;
import com.saihgupr.btcontrol.sony.SonyController;
import com.saihgupr.btcontrol.sony.SonySession;
import java.util.Locale;
import java.util.Set;

public class BluetoothControlReceiver extends BroadcastReceiver {

    private static final String TAG = "BtReceiver";
    private static final String ACTION_CONNECT = "com.saihgupr.btcontrol.ACTION_CONNECT";
    private static final String ACTION_DISCONNECT = "com.saihgupr.btcontrol.ACTION_DISCONNECT";
    private static final String ACTION_ANC = "com.saihgupr.btcontrol.ACTION_ANC";
    private static final String EXTRA_ADDRESS = "address";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_MODE = "mode";
    
    // We keep a static instance to keep the helper/proxies alive if possible,
    // though Manifest receivers have short lifecycles.
    // Ideally, this should be a Service, but for a simple "fire and forget" tool,
    // re-initializing the helper (and thus the proxies) mostly works if we give it time to bind.
    private static BluetoothManagerHelper sHelper;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        
        final PendingResult pendingResult = goAsync();
        
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            if (DevicePrefs.isMuteRemapEnabled(context)) {
                try {
                    context.startForegroundService(new Intent(context, MuteWatcherService.class));
                } catch (Exception e) {
                    Log.w(TAG, "could not start mute watcher on boot", e);
                }
            }
            pendingResult.finish();
            return;
        }

        if (ACTION_ANC.equals(action)) {
            handleAnc(context.getApplicationContext(), intent, pendingResult);
            return;
        }

        if (sHelper == null) {
            sHelper = new BluetoothManagerHelper(context.getApplicationContext());
        }

        String address = intent.getStringExtra(EXTRA_ADDRESS);
        String name = intent.getStringExtra(EXTRA_NAME);

        if (address == null && name == null) {
            Log.e(TAG, "No address or name provided");
            pendingResult.finish();
            return;
        }

        // Normalize address just in case (upper case and fix separators)
        if (address != null) {
            address = address.toUpperCase().replace("-", ":");
        }

        Runnable onComplete = () -> {
            Log.d(TAG, "Action completed, releasing PendingResult");
            if (ACTION_CONNECT.equals(action)) {
                DevicePrefs.applyStartupVolume(context.getApplicationContext());
            }
            pendingResult.finish();
        };


        if (ACTION_CONNECT.equals(action)) {
            if (address != null) sHelper.connect(address, onComplete);
            else sHelper.connectByName(name, onComplete);
        } else if (ACTION_DISCONNECT.equals(action)) {
            if (address != null) sHelper.disconnect(address, onComplete);
            else sHelper.disconnectByName(name, onComplete);
        } else {
            pendingResult.finish();
        }
    }

    @SuppressLint("MissingPermission")
    private void handleAnc(Context context, Intent intent, PendingResult pendingResult) {
        final String rawAddress = intent.getStringExtra(EXTRA_ADDRESS);
        final String name = intent.getStringExtra(EXTRA_NAME);
        String modeExtra = intent.getStringExtra(EXTRA_MODE);
        final String mode = (modeExtra != null && modeExtra.trim().length() > 0)
                ? modeExtra.trim().toLowerCase(Locale.ROOT)
                : "toggle";

        new Thread(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    Log.e(TAG, "ANC: Bluetooth is off");
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "ANC: missing BLUETOOTH_CONNECT permission");
                    return;
                }

                BluetoothDevice device = resolveAncDevice(context, adapter, rawAddress, name);
                if (device == null) {
                    Log.e(TAG, "ANC: no target device found");
                    return;
                }

                int ambientLevel = DevicePrefs.getAmbientLevel(context);
                SonySession.Result result;
                if ("toggle".equals(mode)) {
                    result = SonyController.toggle(device, ambientLevel);
                } else {
                    result = SonyController.setMode(device, parseAncMode(mode), ambientLevel);
                }

                if (result != null && result.ok) {
                    String applied = result.mode != null ? result.mode.name() : SonySession.Mode.UNKNOWN.name();
                    DevicePrefs.setLastAncMode(context, applied);
                    Log.i(TAG, "ANC: mode is now " + applied);
                } else {
                    Log.e(TAG, "ANC failed: " + (result != null ? result.error : "no result"));
                }
            } catch (Exception e) {
                Log.e(TAG, "ANC failed", e);
            } finally {
                pendingResult.finish();
            }
        }, "anc-broadcast").start();
    }

    private SonySession.Mode parseAncMode(String mode) {
        if ("nc".equals(mode)) return SonySession.Mode.NC;
        if ("transparency".equals(mode) || "ambient".equals(mode)) return SonySession.Mode.AMBIENT;
        if ("off".equals(mode)) return SonySession.Mode.OFF;
        return SonySession.Mode.UNKNOWN;
    }

    private BluetoothDevice resolveAncDevice(Context context, BluetoothAdapter adapter,
                                             String rawAddress, String explicitName) {
        if (rawAddress != null) {
            String address = rawAddress.toUpperCase(Locale.ROOT).replace("-", ":");
            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                return adapter.getRemoteDevice(address);
            }
        }

        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        if (bondedDevices == null || bondedDevices.isEmpty()) {
            return null;
        }

        if (explicitName != null) {
            for (BluetoothDevice device : bondedDevices) {
                if (device.getName() != null && device.getName().equalsIgnoreCase(explicitName)) {
                    return device;
                }
            }
        }

        String defaultAddress = DevicePrefs.getDefaultDeviceAddress(context);
        if (defaultAddress != null) {
            for (BluetoothDevice device : bondedDevices) {
                if (device.getAddress().equalsIgnoreCase(defaultAddress)) {
                    return device;
                }
            }
        }

        return null;
    }
}
