# Noise control plan — Sony WH-1000XM3 transparency via the mute button

Status: plan only, nothing built. Written against this repo (`com.saihgupr.btcontrol`,
Java, minSdk 26, targetSdk 34).

## Goal

Add a one-press "transparency" (ambient sound) mode for the paired Sony
WH-1000XM3, triggered from the TV remote's mute button.

Because this app already ships the Button Mapper workflow, **single press is
achievable without any new permission or accessibility service.** Button Mapper
decides single vs long press; the app only needs a new invisible entry point.

## What already exists (and gets reused)

| Thing | Where | Use |
| --- | --- | --- |
| Invisible one-shot activity | `QuickConnectActivity.java` | Exact template for the new trigger: no UI, toast, watchdog, `finish()` |
| Button Mapper mapping | `README.md` "Quick Connect (Remote Button Mapping)" | Same mechanism, new activity |
| ADB broadcast API | `BluetoothControlReceiver.java` (DUMP-protected) | Add an `ACTION_ANC` action alongside connect/disconnect |
| Paired-device resolution | `QuickConnectActivity.resolveTargetDevice` | Find the XM3 by name/address |
| Connect helper | `BluetoothManagerHelper.java` | Connect the headphones first if they are not connected |
| Prefs | `DevicePrefs.java` | Store target mode + last known mode |
| Left-column card UI | `activity_main.xml`, `MainActivity.java` | New Noise Control card under the volume card |
| Reflection precedent | `BluetoothManagerHelper.java:318` | Only if the RFCOMM channel fallback is ever needed |

Permissions: **none new.** `BLUETOOTH_CONNECT` is already declared and handled.

## Why this is easier here than in a tweak app

Android TV normally swallows remote volume/mute keys before apps see them.
Intercepting them requires an AccessibilityService with key filtering, which has
competition and firmware caveats.

This app does not need that. Button Mapper already remaps the remote and
launches activities in this app. The mute key is Button Mapper's problem, not
ours.

- Single press → launch `ToggleTransparencyActivity` → app toggles the mode.
- Long press → leave unassigned (native mute) or assign mute behaviour.
- If Button Mapper cannot see the mute key on this particular remote, assign any
  other button. That is a config change, not code.

## The Sony protocol (ported from `../../CurrentProjects/xm3ctl`)

Reference: `xm3ctl/docs/rewrite-brief.md` and `xm3ctl/Sources/xm3ctl/main.swift:43-128,440-539,701-727`.
Known-good frame vectors: `xm3ctl --frames`.

- Transport: Bluetooth Classic RFCOMM to service UUID
  `96CC203E-5068-46AD-B32D-E316F5E069BA`.
- Android public API: `device.createRfcommSocketToServiceRecord(UUID)`.
  It performs the SDP lookup itself, so no channel constant and no hidden API.
- Frame: `3E | type:1 | seq:1 | len:4 BE | payload | checksum:1 | 3C`
  - `type 0x0C` = command, `0x01` = ack.
  - checksum = `(type + seq + len bytes + payload) & 0xFF`.
  - body bytes `3C/3D/3E` escaped as `3D (b & EF)`.
  - sequence toggles after each command.
- Every non-ack frame from the device is acked immediately with `seq ^ 1`,
  empty payload.
- Init: send payload `00 00`; wait up to 2 s for any reply (XM3 replies
  `01 00 40 10`); settle 150 ms.
- Read state before every set: payload `66 02`; reply `67 02` + 8 bytes:
  `[2]=effect [3]=ncType [4]=ncValue [5]=asmType [6]=asmId [7]=level`.
  `ncType` and `asmType` must be copied from the reply; they vary by firmware.
- Set transparency: `68 02 11 <ncType> 00 <asmType> 00 14`
  (`11` = adjustment-complete, `00` = ambient, `14` = level 20).
- Set noise cancelling: `68 02 11 <ncType> 02 <asmType> 00 00`.
- Set off: `68 02 00 <ncType> 00 <asmType> 00 00`.
- Sets are fire-and-forget in the reference: send, wait ~500 ms, close.

Exact frames (seq 0) to unit-test against:

```
init          3E 0C 00 00 00 00 02 00 00 0E 3C
ack           3E 01 01 00 00 00 00 02 3C
GET state     3E 0C 00 00 00 00 02 66 02 76 3C
SET NC        3E 0C 00 00 00 00 08 68 02 11 02 02 01 00 00 94 3C
SET ambient   3E 0C 00 00 00 00 08 68 02 11 02 00 01 00 14 A6 3C
```

Recompute the checksum from the rule; do not hardcode those two SET values.

## New code

```
com/saihgupr/btcontrol/sony/SonyFrame.java       encode/decode, escape, checksum
com/saihgupr/btcontrol/sony/SonySession.java     RFCOMM connect, init, get, set, ack
com/saihgupr/btcontrol/sony/SonyMode.java        NC / AMBIENT / OFF enum + parse
com/saihgupr/btcontrol/ToggleTransparencyActivity.java
```

`SonySession` is blocking, called from a worker thread, with hard timeouts
(connect 6 s, init 2 s, get 2 s, settle 0.5 s). It closes the socket in
`finally` and returns a result object, so the activity can toast success or a
reason.

`ToggleTransparencyActivity` mirrors `QuickConnectActivity`:

1. Bluetooth on? `BLUETOOTH_CONNECT` granted? else toast + finish.
2. Resolve the target device: explicit `address`/`name` extras, else default
   device, else most recent, else first bonded device whose name looks Sony
   (`WH-1000XM`, `WF-1000XM`, `MDR-`).
3. If not connected, connect it first (reuse `BluetoothManagerHelper`).
4. Worker thread: `SonySession.getMode()`, flip NC ⇄ AMBIENT, `setMode()`.
5. Toast "Transparency on" / "Noise cancelling on" / the failure reason.
6. `finish()`; watchdog at 10 s.

Static `AtomicBoolean` in-flight guard plus a 750 ms debounce, so a double press
does not open two sockets.

## Toggle rules

- Current = noise cancelling → transparency.
- Current = transparency → noise cancelling.
- Current = off or unknown → transparency.
- Ambient level = 20 (max). Stored in prefs so it can become a setting later.

## Entry points

1. **Button Mapper (primary):** activity label `Toggle Transparency`.
   Single press on mute → this activity. Long press → native mute (or nothing).
2. **ADB / Tasker / Home Assistant (secondary):** extend
   `BluetoothControlReceiver` with `com.saihgupr.btcontrol.ACTION_ANC`,
   extras `mode=toggle|nc|transparency|off`, same DUMP protection as the
   existing actions:
   ```bash
   adb shell am broadcast -a com.saihgupr.btcontrol.ACTION_ANC \
     -n com.saihgupr.btcontrol/.BluetoothControlReceiver -e mode toggle
   ```
3. **In-app card:** a "Noise Control" card in the left column under Safe
   Startup Volume. Badge shows `NC` / `AMB` / `OFF` / `--`. Click cycles modes,
   same worker path. Disabled when the connected device is not a Sony headset.

## Manifest changes

```xml
<activity
    android:name=".ToggleTransparencyActivity"
    android:exported="true"
    android:label="Toggle Transparency"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:theme="@style/Theme.AndroidTVBluetooth.QuickConnect">
    <intent-filter>
        <action android:name="com.saihgupr.btcontrol.ACTION_TOGGLE_TRANSPARENCY" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

Plus the new action inside the existing receiver's `intent-filter`.
No service, no new permission.

## Latency and the warm-link option

A cold RFCOMM connect is roughly 2-4 s. For a button press that is noticeable.

- **v1:** accept it. Toast immediately ("Switching..."), then the result. This
  matches how `QuickConnectActivity` already behaves.
- **v2 (only if it feels slow):** keep the socket open 60-120 s after a toggle
  via a small foreground service, then drop it. Warm presses become ~100 ms.
  Adds `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
  Not in the first pass.

Do not keep the link open permanently: it costs battery and can block the phone
from owning the headset.

## Phases

**Phase 0 — spike on the TV.** `SonySession` with heavy logging, driven by a
temporary debug button or the new activity. Prove: SDP finds the UUID, init
replies, GET returns 8 bytes. Everything else depends on this.

**Phase 1 — protocol code + byte tests.** `SonyFrame` and parsing, with JUnit
(`testImplementation 'junit:junit:4.13.2'`, the repo has no tests yet) asserting
the frames above, escaping, checksum, ack generation, and GET parsing.

**Phase 2 — activity + prefs.** `ToggleTransparencyActivity`, target device
resolution, in-flight guard, toasts. Test with ADB:
`am start -n com.saihgupr.btcontrol/.ToggleTransparencyActivity`.

**Phase 3 — Button Mapper.** Assign mute single press to `Toggle Transparency`
on the TV. Confirm single press fires and audio mode audibly changes. If mute
is not remappable, try long press, then another key.

**Phase 4 — card + broadcast.** Noise Control card in MainActivity, badge state,
`ACTION_ANC` in the receiver, README section.

**Phase 5 — verification.** Unit tests, then the manual matrix below.

## Manual test matrix

- Mute press while watching video: mode flips, TV audio keeps playing.
- Press twice quickly: one toggle only, no crash, no second socket.
- Headphones disconnected: connect-then-toggle path, or a clear "not connected"
  toast.
- Headphones connected to a phone as well: see the risk row below.
- Headphones off: "headphones unreachable" within the timeout.
- After TV sleep/wake: first press still works.
- Mode changed with the headphone's own NC/AMB button: the next toggle reads
  the real state, not a stale cache.

## Risks

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| Button Mapper cannot remap mute on this remote | Medium | Use long press, or any other spare key. No app change. |
| SDP does not expose the Sony UUID on this TV's Bluetooth stack | Medium | Retry after `fetchUuidsWithSdp()`. Fallback: RFCOMM channel 15 via reflection (the app already uses reflection for `connect`), which may be blocked on Android 12+. |
| RFCOMM link fails while A2DP is already streaming to the same pair | Low-medium | Test in Phase 0. If it fails, connect the link only on demand and close immediately; surface "headphones busy". |
| Headset already at its 2-device multipoint limit (TV + phone) | Medium | Same as above; report it rather than silently doing nothing. |
| Firmware changes opcodes | Low | `ncType`/`asmType` are read live; abort with "unsupported" if the GET reply is short or missing. |
| Cold-press latency 2-4 s | Certain | Toast, and the optional warm-link v2. |
| Single press mapping removes TV mute | Certain by design | Long press keeps mute; documented in the card subtitle. |
| Activity killed mid-toggle | Low | SET is fire-and-forget once sent; do not hold the socket open waiting for confirmation. |

## Out of scope

- Battery / EQ / speak-to-chat / touch panel settings.
- Multipoint management.
- Root, Xposed, accessibility key filtering (documented only as the fallback if
  Button Mapper is not wanted).
- Changing the headset's own NC/AMB button.
- Always-on background link.

## Setup the user does once (no code)

1. Install the new APK.
2. Open Button Mapper → add a button → mute → single press → Applications →
   `Toggle Transparency`.
3. Leave long press unassigned if mute should still work.

## Reuse

- Protocol: `../../CurrentProjects/xm3ctl/docs/rewrite-brief.md`,
  `../../CurrentProjects/xm3ctl/Sources/xm3ctl/main.swift:43-128,440-539,701-727`.
- Tap-vs-hold precedent (iOS, for reference only):
  `../../CurrentProjects/RemoteCompanion/Tweak/Tweak.x:16953-17238`.
