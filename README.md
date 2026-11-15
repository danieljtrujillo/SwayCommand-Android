# SwayCommand for Android

SwayCommand for Android connects an Audima Labs Sway controller to an Android phone through USB. A Java host receives MIDI and runs the bundled SwayCommand synthesizer, sampler, effects, timeline, and visual engine in a local WebView.

The application runs without a desktop server. The audio and visual engine remains JavaScript. Android handles USB access, file selection, audio focus, and application lifecycle.

## Requirements

The application declares Android 8.0 or later, USB host support, MIDI support, and OpenGL ES 3.0 as minimum requirements. Use a current Android System WebView. Hardware playback has been reported working on a Samsung Galaxy S26 Ultra with a powered USB hub. Other devices have not been verified.

Connect the hub to the phone's USB data port. Connect the Sway to the hub with a data-capable cable and provide the required power. Simultaneous phone charging depends on the hub and phone.

## Play

1. Build and install `dist/SwayCommand-debug.apk` using the instructions below.
2. Open SwayCommand and connect the Sway through the powered USB hub.
3. Tap the play icon in the floating controls to enable audio.
4. If MIDI does not connect, open the menu, expand the connection status, and select **Check USB**. Approve the Android USB-access prompt.
5. Play the Sway and check that the received MIDI count increases.

The application opens in full-screen easy mode. The floating controls provide a menu, play/pause, and scene advance. They hide after 1.5 seconds of inactivity. Tap the stage to restore them. Tap outside a menu or instrument panel to dismiss it.

The menu provides Synth, Effects, Samples, scene selection, and USB diagnostics. Select **Studio** to open the timeline and assignment panels. Select **Easy mode** to return to the stage. Swipe from the screen edge to reveal Android's system bars.

Keep the application in the foreground. Backgrounding the application or losing audio focus stops playback. Use phone speakers or wired audio for initial testing.

## USB MIDI

The application first uses Android's MIDI service. If the service does not expose an input, the application can read USB MIDI 1 event packets directly from the Sway's MIDI interface.

The fallback is restricted to USB identity `0483:52a4`. Its displayed device name ends with `(USB direct)`. It does not access the serial configuration interface or firmware-update mode.

The First Flight template routes pad notes to the synthesizer and visual assignments. Other projects retain their own routing settings.

## Projects and audio files

Import samples through the Android document picker. Imported media is copied into application storage and survives restarts. Saving a project also opens Android's file export dialog.

A `.sway` export contains settings and media references. It does not include imported audio. Retain the original media when transferring a project to another device. Uninstalling the application or clearing its data removes locally stored projects and media.

## Build

Use Windows PowerShell with Node.js 22 or later, JDK 17 or later, Android SDK platform 36, and Android build-tools 36.0.0.

```powershell
npm ci
.\scripts\build-apk.ps1 -AndroidSdk C:\Android\Sdk -Jdk C:\Java\jdk-17
```

The build script also accepts `ANDROID_HOME` and `JAVA_HOME`. If either is unset, it checks for Android tools bundled with Unity under the standard Windows installation directory.

The script compiles Java, builds the renderer, runs D8, packages the APK, aligns it, and verifies its signature. It writes the APK to `dist/SwayCommand-debug.apk`. It creates a local debug signing key in `build/debug.keystore`. Keep that key to install subsequent builds over an existing installation. Do not publish the key.

Install the APK with the Android SDK's `adb` executable:

```powershell
adb devices -l
adb -s DEVICE_SERIAL install -r dist\SwayCommand-debug.apk
adb -s DEVICE_SERIAL shell am start -n com.gantasmo.swaycommand/.MainActivity
```

Replace `DEVICE_SERIAL` with the identifier printed by `adb devices -l`.

## Verification

Run the JavaScript checks and renderer build:

```powershell
npm test
npm run build:web
```

Run the Java MIDI parser checks:

```powershell
New-Item -ItemType Directory -Force build\tests | Out-Null
javac -d build/tests android/src/com/gantasmo/swaycommand/MidiStream.java android/src/com/gantasmo/swaycommand/UsbMidiPackets.java tests/MidiStreamTest.java tests/UsbMidiPacketsTest.java
java -cp build/tests MidiStreamTest
java -cp build/tests UsbMidiPacketsTest
```

An optional Electron installation can run the renderer smoke test:

```powershell
electron scripts/smoke-renderer.cjs
```

The checks cover MIDI stream framing, USB event packets, connection state, note dispatch, media persistence, and mobile control behavior. Physical playback has been reported working. End-to-end latency, charging behavior, and sustained thermal performance have not been measured.

## Limitations

Desktop VST hosting, Python plugins, system-loopback audio, firmware updates, and the Audima desktop companion are not included. Some visual scenes retain desktop shaders and may require performance tuning on mobile hardware.

SA3 generation is not included. Saved SA3 audio can be imported when WebView supports its codec. See [SA3 integration](docs/SA3-INTEGRATION.md) for interface boundaries.

## Source and license

The renderer snapshot comes from [SwayCommand](https://github.com/gantasmo/SwayCommand) at commit `5fb092d3641f2d279f5ca13b010e96a370d9d32c`. Android-specific changes add USB transport, file persistence, project export, and mobile controls.

The project uses the MIT license. Copyright and third-party notices remain in [LICENSE](LICENSE) and the vendored source files. No Audima firmware, installer, or proprietary companion binary is included. This project is not affiliated with Audima Labs.
