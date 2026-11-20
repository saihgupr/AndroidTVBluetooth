# Changelog

## 1.3.3

### Fixed

- A single mute press no longer toggles noise control. The long press now only fires on
  proof of a real hold: a key repeat while the button is down, or a release after the
  threshold. Remotes that never report the release no longer trigger on every tap.
- The mute key is never consumed, so a tap always mutes the TV even when the accessibility
  service is enabled.

### Added

- `MuteKey` debug logs for every mute press and release, including the hold length.
- `ToggleTransparencyActivity` and the `ACTION_ANC` broadcast now unmute the TV when they
  run, so a mute-key trigger from Button Mapper or tvQuickActions does not leave the TV
  silent. Android handles the mute key below the app layer, so the mute cannot be blocked
  itself, only undone straight away.

## 1.3.2

### Changed

- **Mute remap is a long press.** Turn the switch on and hold mute for half a second to
  toggle noise cancelling and transparency without muting the TV. A short press still mutes
  the TV exactly like before.
- **New `MuteKeyService`** accessibility service that reads the remote's mute key. Android
  TV sends volume and mute keys to its audio service instead of the focused app, so this is
  the only way to tell a tap from a hold. It only looks at `KEYCODE_VOLUME_MUTE` and reads
  no screen content. Turning the switch on opens the accessibility settings the first time.
- With Accessibility off, the mute-state watcher stays as a fallback: a double press of mute
  toggles the mode, a single press mutes normally.

### Notes

- The key service takes over from the mute-state watcher as soon as it connects, so the two
  never handle the same press. Turning Accessibility off starts the watcher again.

## 1.3.1

### Added

- **Mute Button Remap toggle.** A new card with a switch turns the mute remap on or off.
  Off stops the background watcher and returns mute to its normal TV behaviour.

### Notes

- Default is off. Turn the switch on to use the remap.

## 1.3.0

### Added

- **Noise Control card** for Sony headsets (WH-1000XM, WF-1000XM, MDR-). One click cycles
  noise cancelling, ambient sound and off, and the badge shows the last known mode.
- **Mute button remap:** a single mute press toggles noise cancelling and transparency on
  the headset, then restores the TV volume. Needs the Google TV remote's volume control
  set to Chromecast so the key reaches Android.
- **`ToggleTransparencyActivity`**, an invisible activity for Button Mapper on remotes
  where the mute key is mappable.
- **`ACTION_ANC` broadcast** for ADB, Tasker and Home Assistant, with
  `mode=toggle|nc|transparency|ambient|off`.
- Background mute watcher that starts on launch and after boot. One audio state check
  every second, no accessibility service.

### Changed

- Transparency uses the headset's voice focus mode, so speech comes through while other
  ambient noise is reduced.
- The Noise Control card sits at the top of the right column, above the paired devices.
- Version pill shows v1.3.

### Notes

- While the mute watcher is running, mute no longer mutes the TV. It toggles the headset
  instead, and the volume returns to its previous level.
- On a remote whose volume control is set to the TV, the mute key is sent over IR and
  never reaches Android, so the app cannot see it.

## 1.2.0

- Auto-connect on launch.
- Battery indicator on device cards.
- Safe startup volume presets.
- Mobile Quick Settings tile and home screen widget.

## 1.1.0 and earlier

- Initial releases with 1-click connect, ADB connect/disconnect and default device
  support.
