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
