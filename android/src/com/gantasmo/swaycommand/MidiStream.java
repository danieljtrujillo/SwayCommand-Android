package com.gantasmo.swaycommand;

/** MIDI 1 byte stream framing, independent of Android and USB packet boundaries. */
public final class MidiStream {
    public interface Sink { void message(int[] data, long timestampNanos); }
    private final Sink sink;
    private int status, needed, used;
    private final int[] data = new int[2];
    private boolean sysex;
    public MidiStream(Sink sink) { this.sink = sink; }
    public void accept(byte[] bytes, int offset, int count, long timestamp) {
        for (int i = offset; i < offset + count; i++) {
            int b = bytes[i] & 255;
            if (b >= 0xf8) continue; // Realtime may interrupt any message.
            if ((b & 128) != 0) {
                used = 0;
                if (b >= 0xf0) {
                    status = 0;
                    sysex = b == 0xf0;
                    continue;
                }
                sysex = false;
                status = b;
                needed = (b & 0xe0) == 0xc0 ? 1 : 2;
            } else if (!sysex && status != 0) {
                data[used++] = b;
                if (used == needed) {
                    sink.message(needed == 1 ? new int[]{status, data[0]} : new int[]{status, data[0], data[1]}, timestamp);
                    used = 0;
                }
            }
        }
    }
}
