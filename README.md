# BT Control (Android TV Bluetooth)

A lightweight utility for Google TV (Android TV) that allows you to connect and disconnect specific Bluetooth devices using simple ADB shell commands.

## Features

- **Low Overhead:** Minimal impact on system resources.
- **Background Active:** Works immediately upon boot once initialized.
- **Restart Persistent:** Once activated, the service stays ready across device restarts. No need to open the app again.
- **Easy Automation:** Perfect for Home Assistant, Alfred, or custom automation scripts.

## Usage

Control your Bluetooth devices by sending broadcast intents through ADB.

### Connect a Device

**By Name (Recommended):**
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_CONNECT \
  -n com.saihgupr.btcontrol/.BluetoothControlReceiver \
  -e name "Your Device Name"
```
*Note: Name matching is case-insensitive (e.g., "WH-1000XM4").*

**By MAC Address:**
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_CONNECT \
  -n com.saihgupr.btcontrol/.BluetoothControlReceiver \
  -e address "CC:98:8B:F4:97:BA"
```

### Disconnect a Device

```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_DISCONNECT \
  -n com.saihgupr.btcontrol/.BluetoothControlReceiver \
  -e name "Your Device Name"
```

## Installation

### 1. Download & Install APK
Download the latest APK from the [Releases](https://github.com/saihgupr/AndroidTVBluetooth/releases) page and install it:
```bash
adb install -r path/to/app-debug.apk
```

### 2. Grant Permissions
Required for Android 12+ (Google TV):
```bash
adb shell pm grant com.saihgupr.btcontrol android.permission.BLUETOOTH_CONNECT
```

### 3. Initialize
Launch the **BT Control** app manually on your TV **one time** to move it out of the system's "stopped" state. You can close it immediately after.

---

### Alternative: Build from Source
If you prefer to build it yourself:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

If connection fails, check `logcat` to see which devices the TV detects:
```bash
adb logcat -d | grep "BtManagerHelper"
```
This will print a list of paired devices if your specified name was not found.

## Technical Note on Persistence

The app uses a `RECEIVE_BOOT_COMPLETED` intent filter and a standard `BroadcastReceiver`. When you restart your TV, Android keeps this receiver registered. When an ADB broadcast is sent, the system automatically wakes up the app to handle the command. No manual intervention is required after a reboot.

---

## Support the Project

If this tool helped you automate your Google TV setup, please consider giving it a ⭐ on [GitHub](https://github.com/saihgupr/AndroidTVBluetooth) or [buying me a coffee](https://ko-fi.com/saihgupr)! It helps others find the project and keeps me motivated to maintain it.
