package com.saihgupr.btcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.annotation.SuppressLint;

import java.lang.reflect.Method;

@SuppressLint("MissingPermission")
public class BluetoothManagerHelper {

    private static final String TAG = "BtManagerHelper";
    private Context mContext;
    private BluetoothAdapter mAdapter;
    private BluetoothProfile mA2dpProfile;
    private BluetoothProfile mHeadsetProfile;
    private BluetoothProfile mInputDeviceProfile;
    
    // Lock to ensure we don't try to use profiles before they are ready
    private final Object mProfileLock = new Object();
    
    private java.util.concurrent.CopyOnWriteArrayList<OnProfileProxyListener> mListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public interface OnProfileProxyListener {
        void onProxyConnected();
        void onProxyDisconnected();
    }

    public void addProfileProxyListener(OnProfileProxyListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            // If already connected, notify immediately
            synchronized (mProfileLock) {
                if (mA2dpProfile != null || mHeadsetProfile != null || mInputDeviceProfile != null) {
                    listener.onProxyConnected();
                }
            }
        }
    }

    public void removeProfileProxyListener(OnProfileProxyListener listener) {
        mListeners.remove(listener);
    }

    private void notifyProxyConnected() {
        for (OnProfileProxyListener listener : mListeners) {
            listener.onProxyConnected();
        }
    }

    private void notifyProxyDisconnected() {
        for (OnProfileProxyListener listener : mListeners) {
            listener.onProxyDisconnected();
        }
    }

    public BluetoothManagerHelper(Context context) {
        mContext = context;
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
                notifyProxyConnected();
            }

            @Override
            public void onServiceDisconnected(int profile) {
                synchronized (mProfileLock) {
                    mA2dpProfile = null;
                }
                notifyProxyDisconnected();
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
                notifyProxyConnected();
            }

            @Override
            public void onServiceDisconnected(int profile) {
                synchronized (mProfileLock) {
                    mHeadsetProfile = null;
                }
                notifyProxyDisconnected();
            }
        }, BluetoothProfile.HEADSET);

        // Get Input Device Profile (HID) - API 28+ uses HID_DEVICE/HID_HOST but INPUT_DEVICE is standard
        mAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                synchronized (mProfileLock) {
                    mInputDeviceProfile = proxy;
                    Log.d(TAG, "Input Device Profile Connected");
                }
                notifyProxyConnected();
            }

            @Override
            public void onServiceDisconnected(int profile) {
                synchronized (mProfileLock) {
                    mInputDeviceProfile = null;
                }
                notifyProxyDisconnected();
            }
        }, 4); // BluetoothProfile.INPUT_DEVICE is 4, using constant for compatibility
    }

    public void connect(String address) {
        connect(address, null);
    }

    public void connect(String address, Runnable onComplete) {
        if (!hasPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
            if (onComplete != null) onComplete.run();
            return;
        }
        if (address == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (mAdapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e(TAG, "Invalid address or BT not supported: " + address);
            if (onComplete != null) onComplete.run();
            return;
        }

        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        Log.i(TAG, "Attempting to connect to: " + device.getName() + " [" + address + "]");

        performAction(device, "connect", onComplete);
    }

    public void connectByName(String name) {
        connectByName(name, null);
    }

    public void connectByName(String name, Runnable onComplete) {
        BluetoothDevice device = findBondedDeviceByName(name);
        if (device != null) {
            Log.i(TAG, "Found device '" + name + "' -> " + device.getAddress());
            connect(device.getAddress(), onComplete);
        } else {
            Log.e(TAG, "Device not found in paired list: " + name);
            if (onComplete != null) onComplete.run();
        }
    }

    public void disconnect(String address) {
        disconnect(address, null);
    }

    public void disconnect(String address, Runnable onComplete) {
        if (!hasPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
            if (onComplete != null) onComplete.run();
            return;
        }
        if (address == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        if (mAdapter == null || !BluetoothAdapter.checkBluetoothAddress(address)) {
            Log.e(TAG, "Invalid address: " + address);
            if (onComplete != null) onComplete.run();
            return;
        }
        BluetoothDevice device = mAdapter.getRemoteDevice(address);
        Log.i(TAG, "Attempting to disconnect: " + device.getName() + " [" + address + "]");
        
        performAction(device, "disconnect", onComplete);
    }

    public void disconnectByName(String name) {
        disconnectByName(name, null);
    }

    public void disconnectByName(String name, Runnable onComplete) {
        BluetoothDevice device = findBondedDeviceByName(name);
        if (device != null) {
            Log.i(TAG, "Found device '" + name + "' -> " + device.getAddress());
            disconnect(device.getAddress(), onComplete);
        } else {
            Log.e(TAG, "Device not found in paired list: " + name);
            if (onComplete != null) onComplete.run();
        }
    }

    public int getConnectionState(BluetoothDevice device) {
        if (!hasPermission() || device == null) return BluetoothProfile.STATE_DISCONNECTED;
        
        synchronized (mProfileLock) {
            int a2dpState = mA2dpProfile != null ? mA2dpProfile.getConnectionState(device) : BluetoothProfile.STATE_DISCONNECTED;
            int hfpState = mHeadsetProfile != null ? mHeadsetProfile.getConnectionState(device) : BluetoothProfile.STATE_DISCONNECTED;
            int inputState = mInputDeviceProfile != null ? mInputDeviceProfile.getConnectionState(device) : BluetoothProfile.STATE_DISCONNECTED;
            
            if (a2dpState == BluetoothProfile.STATE_CONNECTED || hfpState == BluetoothProfile.STATE_CONNECTED || inputState == BluetoothProfile.STATE_CONNECTED) {
                return BluetoothProfile.STATE_CONNECTED;
            }
            if (a2dpState == BluetoothProfile.STATE_CONNECTING || hfpState == BluetoothProfile.STATE_CONNECTING || inputState == BluetoothProfile.STATE_CONNECTING) {
                return BluetoothProfile.STATE_CONNECTING;
            }
            return BluetoothProfile.STATE_DISCONNECTED;
        }
    }

    private BluetoothDevice findBondedDeviceByName(String name) {
        if (!hasPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
            return null;
        }
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

    private void performAction(BluetoothDevice device, String methodName, Runnable onComplete) {
        new Thread(() -> {
            try {
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
                    } else if (methodName.equals("connect")) {
                        Log.w(TAG, "A2DP Profile not ready after wait");
                    }

                    if (mHeadsetProfile != null) {
                        invokeHiddenMethod(mHeadsetProfile, methodName, device);
                    }

                    if (mInputDeviceProfile != null) {
                        invokeHiddenMethod(mInputDeviceProfile, methodName, device);
                    }
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (onComplete != null) onComplete.run();
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

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
}
