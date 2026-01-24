# Android TV Bluetooth Control

A lightweight Android utility that allows you to connect and disconnect specific Bluetooth devices on Google TV (Android TV) using simple ADB shell commands.

## Installation

1.  **Build the APK**:
    ```bash
    ./gradlew assembleDebug
    ```
2.  **Install via ADB**:
    ```bash
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    ```
3.  **Grant Permissions** (Required for Android 12+):
    ```bash
    adb shell pm grant com.saihgupr.btcontrol android.permission.BLUETOOTH_CONNECT
    ```
4.  **Initialize**:
    Launch the app **once** manually on your TV to move it out of the "stopped" state. You can close it immediately after.

## Usage

Control your Bluetooth devices by sending broadcast intents through ADB.

### Connect a Device

**By Name (Recommended):**
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_CONNECT \
  -n com.saihgupr.btcontrol/.BluetoothControlReceiver \
  -e name "Your Device Name"
```
*Note: Name matching is case-insensitive (e.g., "LE_WH-1000XM3").*

**By MAC Address:**
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_CONNECT \
  -n com.saihgupr.btcontrol/.BluetoothControlReceiver \
  -e address "CC:98:8B:F4:97:BA"
```
*Note: MAC addresses can use colons (`:`) or dashes (`-`).*

### Disconnect a Device

```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_DISCONNECT \
  -n com.saihgupr.btcontrol/.BluetoothControlReceiver \
  -e name "Your Device Name"
```

## Troubleshooting

If connection fails, check `logcat` to see what devices the TV detects:
```bash
adb logcat -d | grep "BtManagerHelper"
```
This will print a list of available paired devices if your specified name was not found.
