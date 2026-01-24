# Android TV Bluetooth Controller Usage Guide

## Concept: ADB Broadcasts
You asked about "listening for adb broadcasts". Here is how it works:
1.  **The Receiver**: The app has a "BroadcastReceiver" component that sits dormant on your Android TV headers `com.saihgupr.btcontrol.ACTION_CONNECT`.
2.  **The Trigger**: specific terminal command (`am broadcast`) sends a signal to the Android system.
3.  **The Action**: The system sees that our app is listening for this signal and wakes it up to execute the Bluetooth connection logic.

This allows you to control the app **without opening it on the TV screen**.

## Build & Install
Since this is a raw project, you need to compile it.
1.  Open the project in **Android Studio**.
2.  Build > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
3.  Install via ADB:
    ```bash
    # First connect to your TV
    adb connect 192.168.1.105
    
    # Then install
    adb install app-debug.apk
    
    # CRITICAL: Grant Permissions (Required for Android 12+)
    adb shell pm grant com.saihgupr.btcontrol android.permission.BLUETOOTH_CONNECT
    ```

## Usage Commands

### Connect a Device (by MAC)
**Ensure you are connected:** `adb connect 192.168.1.105`

Replace `AA:BB:CC:DD:EE:FF` with your device's MAC Address.
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_CONNECT -n com.saihgupr.btcontrol/.BluetoothControlReceiver -e address "AA:BB:CC:DD:EE:FF"
```

### Connect a Device (by Name)
Replace `Headphones` with your device's exact paired name.
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_CONNECT -n com.saihgupr.btcontrol/.BluetoothControlReceiver -e name "Headphones"
```

### Disconnect a Device
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_DISCONNECT -n com.saihgupr.btcontrol/.BluetoothControlReceiver -e address "AA:BB:CC:DD:EE:FF"
# OR by name
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_DISCONNECT -n com.saihgupr.btcontrol/.BluetoothControlReceiver -e name "Headphones"
```
