# LibMuse Android SDK

The project includes LibMuse Android SDK 8.0.9 for Muse 2 integration.

## Layout

- `app/libs/libmuse_android.jar` contains the Java/Kotlin API.
- `app/src/main/jniLibs/<abi>/libmuse_android.so` contains the native runtime for supported ABIs.
- `app/src/main/java/com/blue/hush/muse/MuseDeviceManager.kt` provides the application-facing adapter.
- `third_party/libmuse_android_8.0.9/` preserves the SDK README and license.

## Runtime requirements

The app must request the Bluetooth permissions declared in `AndroidManifest.xml` before scanning:

- Android 12 and newer: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`.
- Android 11 and older: `ACCESS_FINE_LOCATION` (and the legacy Bluetooth permissions).

`MuseDeviceManager` must be created after permission is granted. It initializes `MuseManagerAndroid`, discovers nearby devices with `startScanning()`, and connects to a selected device with `connect(...)`.

LibMuse callbacks are delivered from SDK worker threads. UI consumers must switch to the main thread before updating Compose state or views.

## Scanning and connection

The launcher activity exposes the MVP meditation flow. The Meditation tab owns the connection entry point:

1. Grant the Bluetooth permission shown by the page.
2. Tap `Scan` and put the Muse 2 into pairing mode if it is not already discoverable.
3. Select the discovered device and tap `Connect`.
4. Choose a duration and music track, then tap `Start meditation`.

## MVP session flow

The visible meditation page starts `MeditationService` only after a connected Muse device has been selected. The service is declared with `connectedDevice|mediaPlayback`, owns the Muse adapter and generated ambient audio, and continues its monotonic timer while the activity is not visible. A disconnect leaves the timer running, writes explicit invalid per-second samples, and restarts scanning for the same MAC address.

`SignalProcessor` consumes only Alpha, Theta, and Beta relative-power packets, accelerometer packets, and `IS_GOOD`. It emits one smoothed `StateSample` per second. The database stores that state sample and the completed session metadata; it does not store raw EEG, PPG, or other packet payloads. PPG remains registered in the Muse adapter for later processing, but heart-rate extraction is deliberately outside this MVP.

The History tab uses the same saved samples for the Alpha/Theta/Beta/stillness relative-trend chart, the particle Mindprint, and the draggable replay cursor. Invalid samples remain gaps in the chart and replay instead of being filled from neighboring values.

`MuseDeviceManager` registers the available Muse 2 streams exposed by LibMuse:

- raw EEG, absolute/relative Delta, Theta, Alpha, Beta, and Gamma bands, and band scores;
- accelerometer and gyroscope data;
- PPG plus PPG/heart-signal quality flags;
- EEG signal quality (`IS_GOOD`), HSI, and HSI precision;
- battery and artifact packets.

The session service consumes the relevant band, accelerometer, and signal-quality callbacks. PPG is the raw optical pulse signal; heart-rate extraction still requires a processing step after receiving PPG. Some packet types are generic LibMuse types and may not emit values on every Muse model. A physical Muse 2 and a real Android device with Bluetooth are required; an emulator cannot validate the Bluetooth/data path.

The connection path selects `PRESET_50` so the Muse 2 optical/PPG channels are enabled. If PPG still does not appear, make sure the band is worn correctly, the optical sensor on the right forehead is in contact with skin, and reconnect after installing the latest debug APK.
