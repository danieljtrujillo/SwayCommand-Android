import com.gantasmo.swaycommand.UsbMidiPackets;
import java.util.*;
public class UsbMidiPacketsTest {
    public static void main(String[] args) {
        List<String> messages = new ArrayList<>();
        UsbMidiPackets packets = new UsbMidiPackets((bytes, time) -> messages.add(Arrays.toString(bytes)));
        // Fragmented USB packet plus CC, program change (padding must be ignored), and note off.
        packets.accept(new byte[]{9, (byte)0x90}, 2, 1);
        packets.accept(new byte[]{24, 100, 11, (byte)0xb0, 50, 127, 12, (byte)0xc0, 37, 99,
                8, (byte)0x80, 24, 0}, 14, 2);
        // Cable 1's SysEx must not interfere with cable 0's normal note.
        packets.accept(new byte[]{0x14, (byte)0xf0, 1, 2, 9, (byte)0x90, 25, 80,
                0x15, (byte)0xf7, 0, 0, 0, 0, 0, 0}, 16, 3);
        List<String> expected = Arrays.asList("[144, 24, 100]", "[176, 50, 127]", "[192, 37]", "[128, 24, 0]", "[144, 25, 80]");
        if (!messages.equals(expected)) throw new AssertionError(messages);
        System.out.println("USB MIDI packets: split frames, CIN lengths, padding, cables and SysEx passed.");
    }
}
