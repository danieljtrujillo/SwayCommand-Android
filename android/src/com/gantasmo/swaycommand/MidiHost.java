package com.gantasmo.swaycommand;

import android.content.Context;
import android.media.midi.*;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.*;

/** Owns Android MIDI ports; all device lifecycle operations run on the main thread. */
public final class MidiHost {
    public interface Listener {
        void ports(List<String[]> ports);
        void message(String id, int[] bytes, long timestamp);
        void disconnected();
        void error(String message);
        void status(String message);
    }
    private final MidiManager manager;
    private final Listener listener;
    private final SwayUsbMidi usbFallback;
    private String[] usbPort;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<Integer, MidiDevice> devices = new HashMap<>();
    private final Map<String, MidiOutputPort> ports = new LinkedHashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<Integer, Integer> pending = new HashMap<>();
    private boolean active;
    private int generation;
    private int requestSequence;
    private final MidiManager.DeviceCallback callback = new MidiManager.DeviceCallback() {
        @Override public void onDeviceAdded(MidiDeviceInfo info) { open(info); }
        @Override public void onDeviceRemoved(MidiDeviceInfo info) { remove(info.getId()); }
    };
    public MidiHost(Context context, Listener listener) {
        manager = (MidiManager) context.getSystemService(Context.MIDI_SERVICE);
        this.listener = listener;
        usbFallback = new SwayUsbMidi(context, () -> active && ports.isEmpty() && pending.isEmpty(), new SwayUsbMidi.Listener() {
            public void connected(String id, String name) { usbPort = new String[]{id, name}; publish(); }
            public void disconnected() { usbPort = null; listener.disconnected(); publish(); }
            public void message(String id, int[] bytes, long timestamp) { listener.message(id, bytes, timestamp); }
            public void status(String text) { listener.status(text); }
        });
    }
    public void start() {
        if (active || manager == null) return;
        active = true;
        usbFallback.start();
        manager.registerDeviceCallback(callback, main);
        for (MidiDeviceInfo info : manager.getDevices()) open(info);
        publish();
    }
    private void open(MidiDeviceInfo info) {
        if (!active || usbFallback.isConnected() || info.getType() != MidiDeviceInfo.TYPE_USB || info.getOutputPortCount() == 0
                || devices.containsKey(info.getId()) || pending.containsKey(info.getId())) return;
        int ticket = generation;
        int request = ++requestSequence;
        pending.put(info.getId(), request);
        manager.openDevice(info, device -> {
            if (!active || ticket != generation || !Integer.valueOf(request).equals(pending.get(info.getId()))) {
                close(device); return;
            }
            pending.remove(info.getId());
            if (device == null) { listener.error("Cannot open USB MIDI device. Reconnect it and try again."); return; }
            devices.put(info.getId(), device);
            String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME, "USB MIDI");
            for (MidiDeviceInfo.PortInfo portInfo : info.getPorts()) {
                if (portInfo.getType() != MidiDeviceInfo.PortInfo.TYPE_OUTPUT) continue;
                int n = portInfo.getPortNumber();
                MidiOutputPort port = device.openOutputPort(n);
                if (port == null) { listener.error("MIDI port is busy: " + name); continue; }
                String id = info.getId() + ":" + n;
                MidiStream stream = new MidiStream((bytes, time) -> listener.message(id, bytes, time));
                port.connect(new MidiReceiver() {
                    @Override public void onSend(byte[] bytes, int offset, int count, long timestamp) {
                        stream.accept(bytes, offset, count, timestamp);
                    }
                });
                ports.put(id, port);
                names.put(id, name);
            }
            publish();
        }, main);
    }
    private void remove(int deviceId) {
        pending.remove(deviceId);
        String prefix = deviceId + ":";
        for (String id : new ArrayList<>(ports.keySet())) {
            if (id.startsWith(prefix)) { close(ports.remove(id)); names.remove(id); }
        }
        close(devices.remove(deviceId));
        listener.disconnected();
        publish();
    }
    public void publish() {
        List<String[]> result = new ArrayList<>();
        names.forEach((id, name) -> result.add(new String[]{id, name}));
        if (usbPort != null) result.add(usbPort);
        listener.ports(result);
    }
    public void requestUsbAccess() { usbFallback.scan(true); }
    public void stop() {
        if (!active) return;
        active = false;
        generation++;
        manager.unregisterDeviceCallback(callback);
        pending.clear();
        usbFallback.stop();
        for (MidiOutputPort port : ports.values()) close(port);
        for (MidiDevice device : devices.values()) close(device);
        ports.clear(); devices.clear(); names.clear();
        listener.disconnected();
        publish();
    }
    private static void close(java.io.Closeable resource) {
        if (resource != null) try { resource.close(); } catch (IOException ignored) { }
    }
}
