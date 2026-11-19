# Changelog

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
