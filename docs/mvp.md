# Hush MVP

## Running the MVP

1. Install the debug APK on a physical Android device and enable Bluetooth.
2. On the Hush Meditation tab, grant Bluetooth and notification permissions, scan for Muse 2, and connect it.
3. Choose 10, 20, or 30 minutes and a music track, then tap `Start meditation`.
4. While the session is active, the foreground service continues timing, collection, and audio after the screen is locked. The session can be paused, ended early, and have its volume changed.
5. After completion, open the History tab to inspect the result, Mindprint, relative trend chart, and draggable replay timeline.

`adb` is not available in the current environment. Bluetooth, lock-screen, reconnect, and long-session checks must be run on a physical device with the Android SDK configured.

## Data meaning

- Alpha, Theta, and Beta relative power plus acceleration are aggregated once per second by `SignalProcessor` and smoothed with EMA.
- If `IS_GOOD` is false, or a required band/acceleration stream is missing, that second is stored as an invalid sample. Null values mean a data gap; the previous state is not copied forward.
- Stillness describes the relative trend of acceleration near 1 g. Alpha, Theta, and Beta remain relative-power trends. None of these values is a medical metric or an absolute quality score.
- Raw EEG, PPG, and IMU packets are not stored. PPG remains registered by the Muse adapter for later processing; heart-rate extraction is deferred.

## Architecture

- `MuseDeviceManager`: LibMuse scanning, connection, and raw callback adapter.
- `MeditationService`: `connectedDevice|mediaPlayback` foreground service; owns the Muse adapter, monotonic timer, reconnect scan, and ambient audio.
- `SignalProcessor`: pure processing boundary from raw packets to per-second `StateSample` values.
- `HushDatabase`: SQLite session metadata and per-second downsampled samples; excluded from cloud backup and device transfer.
- Compose: consumes processed per-second values for particles, relative trends, completion, and replay.

## Physical-device acceptance checklist

- [ ] Muse 2 scans and connects from the Meditation tab, and the session produces valid samples.
- [ ] A 20-minute session keeps timing, audio, and valid samples running.
- [ ] After locking the screen, timing, Muse collection, and audio continue; the foreground-service notification remains visible.
- [ ] A Muse disconnect keeps the timer running and shows a data gap; reconnect resumes collection without fabricating samples.
- [ ] Pause, resume, early finish, and automatic finish all create a durable record.
- [ ] After restarting the app, history, the relative trend chart, the particle Mindprint, and draggable replay remain available.
- [ ] Record particle rendering performance on the target device; the Compose Canvas first pass targets near-60 FPS.

## Audio

`Mist` and `Tide` are locally generated original PCM ambient beds. They loop without network access or an account. They are session music only; pause, finish, and lock-screen behavior are coordinated by the foreground service.
