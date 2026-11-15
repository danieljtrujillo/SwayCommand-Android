# SA3 integration

SwayCommand uses Android package `com.gantasmo.swaycommand`. SA3_onDevice uses `dev.sa3.ondevice`. Both applications can be installed independently. This repository does not contain SA3 source code, model files, or native libraries.

## Audio interchange

Import saved SA3 audio through Android's document picker. PCM WAV provides a common interchange format. WebView must support the codec used by other formats.

This integration currently transfers files. There is no live connection between the applications and no Android share receiver in SwayCommand.

## Interfaces

`MidiHost.Listener.message(id, bytes, timestamp)` receives MIDI channel messages. Android MIDI timestamps use monotonic nanoseconds. The direct USB reader assigns a monotonic timestamp when it receives a packet.

`MidiStream.Sink` provides an Android-independent MIDI receiver interface. `window.__swaycommand.importAudio(paths)` imports registered files into the sampler and timeline. `MainActivity` owns document selection, audio focus, and foreground lifecycle.

## Future implementation

Run model generation outside the MIDI input path. Continue playing the current instrument while generated audio loads into a sampler slot or timeline track.

A content-URI integration should require a temporary read grant, copy the audio into application storage, and import it asynchronously. An in-process integration requires a defined JNI interface, native library lifecycle, and supported ABI list.

Define sample rate, channel count, sample format, buffer ownership, and cancellation before transferring audio buffers. Convert generated audio to the output AudioContext format before playback. Convert MIDI timestamps before scheduling events against AudioContext time.

Validate native library compatibility, memory use, GPU contention, and playback latency under model load before enabling runtime generation.
