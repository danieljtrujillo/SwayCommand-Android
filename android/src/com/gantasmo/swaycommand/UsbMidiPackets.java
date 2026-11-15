package com.gantasmo.swaycommand;

/** USB MIDI 1.0 event packets. Each virtual cable owns an independent byte stream. */
public final class UsbMidiPackets {
    private static final int[] LENGTHS = {0, 0, 2, 3, 3, 1, 2, 3, 3, 3, 3, 3, 2, 2, 3, 1};
    private final MidiStream[] cables = new MidiStream[16];
    private final byte[] packet = new byte[4];
    private int used;
    public UsbMidiPackets(MidiStream.Sink sink) {
        for (int i = 0; i < cables.length; i++) cables[i] = new MidiStream(sink);
    }
    public void accept(byte[] bytes, int count, long timestamp) {
        for (int i = 0; i < count; i++) {
            packet[used++] = bytes[i];
            if (used == 4) {
                int header = packet[0] & 255;
                int length = LENGTHS[header & 15];
                if (length > 0) cables[header >>> 4].accept(packet, 1, length, timestamp);
                used = 0;
            }
        }
    }
}
