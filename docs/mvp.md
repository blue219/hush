# Hush MVP

## Running the MVP

1. Install the debug APK on a physical Android device and enable Bluetooth.
2. On the Hush Meditation tab, grant Bluetooth and notification permissions, scan for Muse 2, and connect it.
3. Choose 10, 20, or 30 minutes and a music track, then tap `Start meditation`.
4. While the session is active, the foreground service continues timing, collection, and audio after the screen is locked. The session can be paused, ended early, and have its volume changed.
5. After completion, open the History tab to inspect the result, Mindprint, relative trend chart, and draggable replay timeline.

Bluetooth, lock-screen, reconnect, and long-session checks must be run on a physical device with the Android SDK configured.

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

## Meditation galaxy

Active sessions (connecting, running, or paused) use a full-screen 720-star spiral galaxy. App navigation and system bars are hidden; system bars can be revealed by swiping. Elapsed time, Pause/Resume, Finish, and a volume icon stay visible inside safe drawing insets. The volume icon expands a slider. Back dismisses the slider and never ends the session. The home, completion, and history visuals retain their existing behavior.

The artistic mapping uses Beta / (Alpha + Theta + Beta): shares at or below 0.2 produce slow spiral rotation; shares at or above 0.6 produce maximum independent drift. Intermediate values interpolate continuously. This is not a validated measure of thoughts or meditation quality. Rising agitation has a 2.5-second exponential time constant; regrouping has a 4-second time constant. These are smoothing rates, not hard transition deadlines.

`StateSample.eegBandsAvailable` is a live-only flag, defaulting to false. `SignalProcessor` sets it only when all three relative bands have finite measurements in the 0–1 range within the current second. Default-filled fields and stale samples from earlier seconds never establish availability; no database migration is needed. Historical samples keep the default flag and continue using their original renderer.

Disconnected, missing, or unavailable EEG holds the galaxy shape and fades it to 25% brightness with a short status indicator. Recovered data resumes motion smoothly. Pause freezes the entire visual state. The frame coroutine runs only while the view lifecycle is STARTED; background timing, audio, and data collection remain service-owned. Resuming resets the frame timestamp rather than catching up missed time.

### Focused validation

Run `./gradlew :app:testDebugUnitTest --tests com.blue.hush.GalaxyMotionTest --tests com.blue.hush.SignalProcessorTest :app:compileDebugKotlin`.

On a physical Android device, check calm → scattered → regrouped motion, signal loss/recovery, pause/resume, background/foreground, portrait/landscape, system-bar restoration, and volume/Back/Finish controls. Confirm readable controls at small screen sizes and larger font settings. Measure frame timing before claiming near-60 FPS; compilation and deterministic motion tests do not establish visual performance.

The focused on-device UI test can be run with `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blue.hush.MeditationGalaxyScreenTest`. It uses synthetic samples without starting a real meditation service, covers controls, frozen particles, signal messages, lifecycle resume, and landscape layout, and writes screenshots to the target app's external files directory. These checks do not validate a physical Muse signal or long-session frame pacing.
