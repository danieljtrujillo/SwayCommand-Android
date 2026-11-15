import com.gantasmo.swaycommand.MidiStream;
import java.util.*;
public class MidiStreamTest {
    public static void main(String[] args) {
        List<String> messages = new ArrayList<>();
        MidiStream parser = new MidiStream((data, time) -> messages.add(Arrays.toString(data) + "@" + time));
        parser.accept(new byte[]{0, (byte)0x90, 24}, 1, 2, 1);
        parser.accept(new byte[]{(byte)0xf8, 100, 25, 0, (byte)0xc0, 37, 38}, 0, 7, 2);
        parser.accept(new byte[]{(byte)0xf0, 1, 2, (byte)0xf8, 3, (byte)0xf7, 40, 50, (byte)0xb0, 50, 127}, 0, 11, 3);
        parser.accept(new byte[]{(byte)0xe0, 0, 64, (byte)0xf2, 1, 2, 3}, 0, 7, 4);
        List<String> expected = Arrays.asList("[144, 24, 100]@2", "[144, 25, 0]@2", "[192, 37]@2", "[192, 38]@2", "[176, 50, 127]@3", "[224, 0, 64]@4");
        if (!messages.equals(expected)) throw new AssertionError(messages);
        System.out.println("MIDI parser: fragmentation, running status, realtime, SysEx and system-common tests passed.");
    }
}
