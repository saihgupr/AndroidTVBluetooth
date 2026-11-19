package com.saihgupr.btcontrol.sony;

import android.bluetooth.BluetoothDevice;

import java.io.IOException;
import java.util.Locale;

public final class SonyController {

    private SonyController() {
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
            return SonySession.Result.error("Bluetooth permission missing");
        } catch (IOException e) {
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
