## 2024-05-23 - [Restricting Exported BroadcastReceiver for ADB Usage]
**Vulnerability:** Unprotected `exported=true` receiver allows any malicious app to trigger Bluetooth actions.
**Learning:** For apps intended to be controlled via ADB, the receiver must be exported, but can be secured by requiring `android.permission.DUMP`, which is held by the `shell` user but not standard apps.
**Prevention:** Always add `android:permission="android.permission.DUMP"` (or similar shell-only permission) to exported receivers designed for ADB automation.
