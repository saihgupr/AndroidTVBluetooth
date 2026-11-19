package com.saihgupr.btcontrol.sony;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.saihgupr.btcontrol.DevicePrefs;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public final class SonyController {

    private static final String TAG = "SonyController";

    private SonyController() {
    }

    public static BluetoothDevice resolveSonyDevice(Context context) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return null;
        }

        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null || bonded.isEmpty()) {
            return null;
        }

        String defaultAddress = DevicePrefs.getDefaultDeviceAddress(context);
        if (defaultAddress != null) {
            for (BluetoothDevice device : bonded) {
                if (device.getAddress().equalsIgnoreCase(defaultAddress) && looksLikeSony(device)) {
                    return device;
                }
            }
        }

        BluetoothDevice mostRecent = null;
        long latest = 0;
        for (BluetoothDevice device : bonded) {
            if (!looksLikeSony(device)) {
                continue;
            }
            long lastUsed = DevicePrefs.getLastUsed(context, device.getAddress());
            if (lastUsed > latest) {
                latest = lastUsed;
                mostRecent = device;
            }
        }
        if (mostRecent != null) {
            return mostRecent;
        }

        for (BluetoothDevice device : bonded) {
            if (looksLikeSony(device)) {
                return device;
            }
        }
        return null;
    }

    public static boolean looksLikeSony(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        try {
            String name = device.getName();
            if (name == null) {
                return false;
            }
            String upper = name.toUpperCase(Locale.US);
            // Bonded name is sometimes "LE_WH-1000XM3" for the BLE twin.
            return upper.contains("WH-1000XM")
                    || upper.contains("WF-1000XM")
                    || upper.contains("MDR-");
        } catch (SecurityException e) {
            return false;
        }
    }

    public static SonySession.Result toggle(BluetoothDevice device, int ambientLevel) {
        return run(device, null, ambientLevel);
    }

    public static SonySession.Result setMode(BluetoothDevice device, SonySession.Mode mode, int ambientLevel) {
        if (mode == null || mode == SonySession.Mode.UNKNOWN) {
            return SonySession.Result.error("Unknown mode");
        }
        return run(device, mode, ambientLevel);
    }

    private static SonySession.Result run(BluetoothDevice device, SonySession.Mode requested, int ambientLevel) {
        if (device == null) {
            return SonySession.Result.error("Headphones unreachable");
        }

        SonySession session;
        try {
            session = new SonySession(device);
        } catch (SecurityException e) {
            Log.e(TAG, "connect denied", e);
            return SonySession.Result.error("Bluetooth permission missing");
        } catch (IOException e) {
            Log.e(TAG, "connect failed: " + e.getMessage(), e);
            return SonySession.Result.error("Headphones unreachable");
        }

        try {
            SonySession.Result current = session.readMode();
            if (!current.ok) {
                return current;
            }
            SonySession.Mode target;
            if (requested == null) {
                target = current.mode == SonySession.Mode.NC
                        ? SonySession.Mode.AMBIENT
                        : SonySession.Mode.NC;
            } else {
                target = requested;
            }
            return session.writeMode(target, ambientLevel);
        } catch (SecurityException e) {
            return SonySession.Result.error("Bluetooth permission missing");
        } catch (IOException e) {
            return SonySession.Result.error("No reply from headphones");
        } finally {
            session.close();
        }
    }
}
