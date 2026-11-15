package com.gantasmo.swaycommand;

import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import java.util.function.BooleanSupplier;

/** Receive-only fallback for Sway firmware whose composite MIDI port is absent in MidiManager. */
public final class SwayUsbMidi {
    public interface Listener {
        void connected(String id, String name);
        void disconnected();
        void message(String id, int[] bytes, long timestamp);
        void status(String text);
    }
    private final Context context;
    private final UsbManager usb;
    private final Listener listener;
    private final BooleanSupplier mayOpen;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final String permissionAction;
    private boolean active, permissionPending;
    private Session session;
    private static final class Session {
        final UsbDevice device;
        final UsbDeviceConnection connection;
        final UsbInterface intf;
        volatile boolean running = true;
        Session(UsbDevice device, UsbDeviceConnection connection, UsbInterface intf) {
            this.device = device; this.connection = connection; this.intf = intf;
        }
    }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (permissionAction.equals(intent.getAction())) {
                permissionPending = false;
                // Check the OS permission, not caller-provided extras.
                scan(false);
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                if (session != null && !usb.getDeviceList().containsKey(session.device.getDeviceName())) stopReading();
                listener.status("USB device detached. Reconnect the Sway and tap CHECK USB.");
            } else {
                main.postDelayed(() -> scan(false), 1500);
            }
        }
    };
    public SwayUsbMidi(Context context, BooleanSupplier mayOpen, Listener listener) {
        this.context = context; this.mayOpen = mayOpen; this.listener = listener;
        usb = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        permissionAction = context.getPackageName() + ".USB_PERMISSION";
    }
    public boolean isConnected() { return session != null; }
    public void start() {
        if (active || usb == null) return;
        active = true;
        IntentFilter filter = new IntentFilter(permissionAction);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
        main.postDelayed(() -> scan(false), 1500);
    }
    public void scan(boolean requestPermission) {
        if (!active || session != null || !mayOpen.getAsBoolean()) return;
        for (UsbDevice device : usb.getDeviceList().values()) {
            // Never claim the serial interface, unrelated controllers, or the DFU bootloader.
            if (device.getVendorId() != 0x0483 || device.getProductId() != 0x52a4) continue;
            if (!usb.hasPermission(device)) {
                listener.status("Sway detected; tap CHECK USB and allow USB access.");
                if (requestPermission && !permissionPending) {
                    permissionPending = true;
                    PendingIntent permission = PendingIntent.getBroadcast(context, 0,
                            new Intent(permissionAction).setPackage(context.getPackageName()), PendingIntent.FLAG_IMMUTABLE);
                    usb.requestPermission(device, permission);
                }
                return;
            }
            open(device);
            return;
        }
    }
    private void open(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() != UsbConstants.USB_CLASS_AUDIO || intf.getInterfaceSubclass() != 3
                    || intf.getInterfaceProtocol() != 0) continue;
            for (int e = 0; e < intf.getEndpointCount(); e++) {
                UsbEndpoint endpoint = intf.getEndpoint(e);
                if (endpoint.getDirection() != UsbConstants.USB_DIR_IN || endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                UsbDeviceConnection connection = usb.openDevice(device);
                if (connection == null) { listener.status("Sway USB access failed. Tap CHECK USB to retry."); return; }
                if (!connection.claimInterface(intf, true)) {
                    connection.close(); listener.status("Sway MIDI interface is busy. Close other MIDI apps and retry."); return;
                }
                if (intf.getAlternateSetting() != 0 && !connection.setInterface(intf)) {
                    connection.releaseInterface(intf); connection.close();
                    listener.status("Could not select the Sway MIDI interface."); return;
                }
                Session opened = new Session(device, connection, intf);
                session = opened;
                String id = "sway-usb:" + device.getDeviceId();
                listener.connected(id, "Audima Labs The Sway (USB direct)");
                new Thread(() -> read(opened, endpoint, id), "sway-usb-midi").start();
                return;
            }
        }
        listener.status("Sway detected but no USB MIDI 1 input endpoint found. Interface count: " + device.getInterfaceCount());
    }
    private void read(Session opened, UsbEndpoint endpoint, String id) {
        UsbMidiPackets decoder = new UsbMidiPackets((bytes, timestamp) -> {
            if (opened.running) listener.message(id, bytes, timestamp);
        });
        byte[] buffer = new byte[Math.max(64, endpoint.getMaxPacketSize())];
        try {
            while (opened.running) {
                long before = SystemClock.elapsedRealtime();
                int count = opened.connection.bulkTransfer(endpoint, buffer, buffer.length, 200);
                if (count > 0) decoder.accept(buffer, count, System.nanoTime());
                // A detached/stalled endpoint can fail immediately; do not spin at full CPU.
                else if (SystemClock.elapsedRealtime() - before < 10) Thread.sleep(20);
            }
        } catch (RuntimeException | InterruptedException exception) {
            main.post(() -> {
                if (session == opened) {
                    stopReading();
                    listener.status("Sway USB read stopped. Tap CHECK USB to reconnect.");
                }
            });
        } finally {
            opened.connection.releaseInterface(opened.intf);
            opened.connection.close();
        }
    }
    private void stopReading() {
        if (session == null) return;
        session.running = false;
        session = null;
        // The read thread releases and closes its own connection after the bounded read finishes.
        listener.disconnected();
    }
    public void stop() {
        if (!active) return;
        active = false;
        main.removeCallbacksAndMessages(null);
        context.unregisterReceiver(receiver);
        permissionPending = false;
        stopReading();
    }
}
