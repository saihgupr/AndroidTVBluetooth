# AndroidTVBluetooth

A lightweight utility for Android TV that makes it easy to **quick connect/disconnect** paired Bluetooth devices via ADB.

## Features

- **Low Overhead:** Minimal impact on system resources.
- **Restart Persistent:** Works immediately after boot once initialized. The service stays ready across device restarts—no need to open the app again.
- **Zero Interaction:** No persistent background process or app icons to manage; only wakes up when a command is received.

## Usage

Connect or disconnect your Bluetooth devices by sending broadcast intents through ADB.

### Connect a Device

**By Name (Recommended):**
```bash
adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_CONNECT \
  -n com.saihgupr.btcontrol/.BluetoothControlReceiver \
  -e name "Your Device Name"
```
*Note: Name matching is case-insensitive (e.g., "LE_WH-1000XM4").*

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
adb install -r path/to/AndroidTVBluetooth.apk
```

### 2. Grant Permissions
Required for Android 12+ (Android TV):
```bash
adb shell pm grant com.saihgupr.btcontrol android.permission.BLUETOOTH_CONNECT
```

### 3. Initialize
Launch the **AndroidTVBluetooth** app manually on your TV **one time** to move it out of the system's "stopped" state. You can close it immediately after.

---

### Alternative: Build from Source
If you prefer to build it yourself:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/AndroidTVBluetooth.apk
```

## Troubleshooting

If connection fails, check `logcat` to see which devices the TV detects:
```bash
adb logcat -d | grep "BtManagerHelper"
```
This will print a list of paired devices if your specified name was not found.

---

## Support the Project

Please consider giving it a ⭐ on [GitHub](https://github.com/saihgupr/AndroidTVBluetooth) or [buying me a coffee](https://ko-fi.com/saihgupr)! It helps others find the project and keeps me motivated to maintain it.
