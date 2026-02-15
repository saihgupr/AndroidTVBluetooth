package com.saihgupr.btcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

public class BluetoothManagerHelper {

    private static final String TAG = "BtManagerHelper";
    private BluetoothAdapter mAdapter;
    private BluetoothProfile mA2dpProfile;
    private BluetoothProfile mHeadsetProfile;
    
    // Lock to ensure we don't try to use profiles before they are ready
    private final Object mProfileLock = new Object();

    public BluetoothManagerHelper(Context context) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mAdapter == null) {
            Log.e(TAG, "Bluetooth not supported on this device");
            return;
        }

        // Get A2DP Profile
        mAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                synchronized (mProfileLock) {
                    mA2dpProfile = proxy;
                    Log.d(TAG, "A2DP Profile Connected");
                    mProfileLock.notifyAll();
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                synchronized (mProfileLock) {
                    mA2dpProfile = null;
                }
            }
        }, BluetoothProfile.A2DP);

        // Get Headset Profile (HFP)
        mAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                synchronized (mProfileLock) {
                    mHeadsetProfile = proxy;
                    Log.d(TAG, "Headset Profile Connected");
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                synchronized (mProfileLock) {
                    mHeadsetProfile = null;
                }
            }
        }, BluetoothProfile.HEADSET);
    }

    public void connect(String address) {
        if (address == null) return;
        if (mAdapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e(TAG, "Invalid address or BT not supported: " + address);
            return;
        }

        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        Log.i(TAG, "Attempting to connect to: " + device.getName() + " [" + address + "]");

        performAction(device, "connect");
    }

    public void connectByName(String name) {
        BluetoothDevice device = findBondedDeviceByName(name);
        if (device != null) {
            Log.i(TAG, "Found device '" + name + "' -> " + device.getAddress());
            connect(device.getAddress());
        } else {
            Log.e(TAG, "Device not found in paired list: " + name);
        }
    }

    public void disconnect(String address) {
        if (address == null) return;
        if (mAdapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e(TAG, "Invalid address: " + address);
            return;
        }
        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        Log.i(TAG, "Attempting to disconnect: " + device.getName() + " [" + address + "]");
        
        performAction(device, "disconnect");
    }

    public void disconnectByName(String name) {
        BluetoothDevice device = findBondedDeviceByName(name);
        if (device != null) {
            Log.i(TAG, "Found device '" + name + "' -> " + device.getAddress());
            disconnect(device.getAddress());
        } else {
            Log.e(TAG, "Device not found in paired list: " + name);
        }
    }

    private BluetoothDevice findBondedDeviceByName(String name) {
        if (mAdapter == null || name == null) return null;
        java.util.Set<BluetoothDevice> bonded = mAdapter.getBondedDevices();
        if (bonded == null) return null;

        for (BluetoothDevice device : bonded) {
            String deviceName = device.getName(); // Can be null
            if (deviceName != null && deviceName.equalsIgnoreCase(name)) {
                return device;
            }
        }
        
        // If not found, log available devices for debugging
        Log.w(TAG, "Device '" + name + "' not found. Available devices:");
        for (BluetoothDevice device : bonded) {
            Log.w(TAG, " - " + device.getName() + " [" + device.getAddress() + "]");
        }
        return null;
    }

    private void performAction(BluetoothDevice device, String methodName) {
        new Thread(() -> {
            // Wait for A2DP profile with timeout (2000ms total, same as original 10 * 200ms)
            // Using wait/notify avoids unnecessary sleep latency.
            synchronized (mProfileLock) {
                long timeout = 2000;
                long start = System.currentTimeMillis();
                while (mA2dpProfile == null) {
                    long elapsed = System.currentTimeMillis() - start;
                    if (elapsed >= timeout) break;
                    try {
                        mProfileLock.wait(timeout - elapsed);
                    } catch (InterruptedException e) {
                        break;
                    }
                }

                if (mA2dpProfile != null) {
                    invokeHiddenMethod(mA2dpProfile, methodName, device);
                } else {
                    Log.w(TAG, "A2DP Profile not ready after wait");
                }

                if (mHeadsetProfile != null) {
                    invokeHiddenMethod(mHeadsetProfile, methodName, device);
                }
            }
        }).start();
    }

    private void invokeHiddenMethod(BluetoothProfile profile, String methodName, BluetoothDevice device) {
        try {
            Method method = profile.getClass().getMethod(methodName, BluetoothDevice.class);
            method.setAccessible(true);
            Boolean result = (Boolean) method.invoke(profile, device);
            Log.d(TAG, profile.getClass().getSimpleName() + "." + methodName + " returned: " + result);
        } catch (Exception e) {
            Log.e(TAG, "Reflection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
