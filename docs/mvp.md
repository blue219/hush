# Hush MVP

## Running the MVP

1. Install the debug APK on a physical Android device and enable Bluetooth.
2. On Home, tap `Connect Muse` to grant permission. While Home is visible, Hush discovers and automatically connects the remembered Muse. On first use, one discovered device connects automatically; multiple devices require a selection in the device sheet.
3. Choose 10, 20, or 30 minutes and select/preview Mist or Tide from the soundscape sheet. Tap `Start meditation` after connection.
4. The full-screen session shows remaining time, Pause/Resume, volume, and Finish. Finish or Back opens a confirmation; Back first dismisses an open volume panel. Confirming ends and saves the session. Dismissing confirmation does not change its running/paused state.
5. Completion opens a separate summary. History contains saved sessions, relative trends and a draggable replay. Home and History are the only navigation tabs.

Discovery stops when Home is hidden, the app goes into the background, a connection starts, or simulation is enabled. Existing connections remain alive when merely leaving Home. Active sessions are service-owned and may reconnect the original Muse in the background. A remembered device is never silently replaced by another nearby device. Connection failures retry after 2, 4, 8, 16, then 30 seconds; connection attempts time out after 20 seconds. Device selection waits 1.5 seconds for discovery results. Native callback generations prevent disposed managers from publishing into a new connection attempt.

`Disconnect` pauses automatic connection for the current app process (including activity recreation). Tap `Connect Muse` to resume. Permission dialogs require a user action; turning Bluetooth off exposes a settings entry point. A denied permission never triggers repeated automatic prompts.

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

Active sessions (connecting, running, or paused) use a full-screen 720-star spiral galaxy. App navigation and system bars are hidden; system bars can be revealed by swiping. Remaining time, Pause/Resume, Finish, and volume remain within safe drawing insets. Landscape places controls beside the galaxy. Controls can scroll on constrained screens. Back dismisses the volume panel first; otherwise it opens the finish confirmation. Colors come from the shared fixed-dark theme.

The artistic mapping uses Beta / (Alpha + Theta + Beta): shares at or below 0.2 produce slow spiral rotation; shares at or above 0.6 produce maximum independent drift. Intermediate values interpolate continuously. This is not a validated measure of thoughts or meditation quality. Rising agitation has a 2.5-second exponential time constant; regrouping has a 4-second time constant. These are smoothing rates, not hard transition deadlines.

`StateSample.eegBandsAvailable` is a live-only flag, defaulting to false. `SignalProcessor` sets it only when all three relative bands have finite measurements in the 0–1 range within the current second. Default-filled fields and stale samples from earlier seconds never establish availability; no database migration is needed. Historical samples keep the default flag and continue using their original renderer.

Disconnected, missing, or unavailable EEG holds the galaxy shape and fades it to 25% brightness with a short status indicator. Recovered data resumes motion smoothly. Pause freezes the entire visual state. The frame coroutine runs only while the view lifecycle is STARTED; background timing, audio, and data collection remain service-owned. Resuming resets the frame timestamp rather than catching up missed time.

### Focused validation

Run `./gradlew :app:testDebugUnitTest --tests com.blue.hush.GalaxyMotionTest --tests com.blue.hush.SignalProcessorTest :app:compileDebugKotlin`.

On a physical Android device, check calm → scattered → regrouped motion, signal loss/recovery, pause/resume, background/foreground, portrait/landscape, system-bar restoration, and volume/Back/Finish controls. Confirm readable controls at small screen sizes and larger font settings. Measure frame timing before claiming near-60 FPS; compilation and deterministic motion tests do not establish visual performance.

The focused on-device UI test can be run with `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blue.hush.MeditationGalaxyScreenTest`. It uses synthetic samples without starting a real meditation service, covers controls, frozen particles, signal messages, lifecycle resume, and landscape layout, and writes screenshots to the target app's external files directory. These checks do not validate a physical Muse signal or long-session frame pacing.

## Saved simulation data

The Home device sheet includes `Try a simulation` (accessibility label: `Use saved simulation data`). It replays the checked-in ten-minute sample at `app/src/main/assets/simulation/muse_last_10m.csv` through the same foreground-session state path used by a live Muse connection. The file was exported from the latest complete ten-minute session available on the development phone; it contains 600 valid one-second samples and no device identifiers or timestamps.

Enabling simulation stops discovery and disconnects the idle Muse. Simulation mode does not scan Bluetooth, always uses the ten-minute duration, plays the selected soundscape, and saves the replay as a normal local session. If the asset is missing or malformed, the option remains unavailable and the real Muse connection path is unchanged.

## UI validation

The visual system and component conventions are in [Design system](design-system.md).

Run focused unit checks with `./gradlew :app:testDebugUnitTest --tests com.blue.hush.AutoConnectPolicyTest --tests com.blue.hush.GalaxyMotionTest --tests com.blue.hush.SignalProcessorTest :app:compileDebugKotlin`.

Run UI checks against an already running device with `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.blue.hush.MeditationGalaxyScreenTest,com.blue.hush.HushAppScreenTest,com.blue.hush.SimulationFlowTest` (set `ANDROID_SERIAL` when multiple devices are connected). They cover confirmation, countdown, pause, signal loss, landscape controls, simulation selection, soundscape dismissal, empty history, completion without signal, and large text. Screenshots are written to the app external files directory. The simulation integration test starts the real media-only foreground service without Bluetooth permission, pauses, saves and opens replay. Bluetooth discovery, remembered-device selection, background reconnection, and sustained frame pacing still require physical-device verification.

Simulation uses only the media-playback foreground-service type; live Muse sessions also use connected-device. This allows the simulation entry point to work without Bluetooth permission.
