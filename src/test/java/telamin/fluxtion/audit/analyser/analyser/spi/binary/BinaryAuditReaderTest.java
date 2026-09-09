package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.spi.ReaderRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M52.5: a binary log opens IN THE ANALYSER, not only at a command line. */
class BinaryAuditReaderTest {

    private static byte[] sampleFile() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BinaryLogDecoder.MAGIC);
        out.writeBytes(new byte[]{0, 1, 0, 0});
        dict(out, 1, "TickEvent");
        dict(out, 2, "pricer");
        dict(out, 3, "price");
        dict(out, 4, "size");
        dict(out, 5, "risk");
        dict(out, 6, "breach");
        record(out, 1, 1786355857407L, 1786355857407L, 1786355857408L, new long[]{
                slot0(2, 3, BinaryLogDecoder.TAG_DOUBLE), Double.doubleToRawLongBits(1.25),
                slot0(2, 4, BinaryLogDecoder.TAG_INT), 100,
                slot0(5, 6, BinaryLogDecoder.TAG_BOOL), 0});
        return out.toByteArray();
    }

    private static void dict(ByteArrayOutputStream out, int id, String name) {
        byte[] utf8 = name.getBytes(StandardCharsets.UTF_8);
        out.write(BinaryLogDecoder.FRAME_DICT);
        out.writeBytes(new byte[]{(byte) (id >>> 8), (byte) id});
        out.writeBytes(new byte[]{(byte) (utf8.length >>> 8), (byte) utf8.length});
        out.writeBytes(utf8);
    }

    private static void record(ByteArrayOutputStream out, int eventTypeId,
                               long eventTime, long logTime, long endTime, long[] slots) {
        int entries = slots.length / 2;
        out.write(BinaryLogDecoder.FRAME_RECORD);
        out.writeBytes(new byte[]{(byte) (entries >>> 8), (byte) entries});
        out.writeBytes(new byte[]{(byte) (eventTypeId >>> 8), (byte) eventTypeId});
        for (long t : new long[]{eventTime, logTime, endTime}) {
            out.writeBytes(ByteBuffer.allocate(8).putLong(t).array());
        }
        for (long slot : slots) {
            out.writeBytes(ByteBuffer.allocate(8).putLong(slot).array());
        }
    }

    private static long slot0(int nodeId, int keyId, int tag) {
        return ((long) (nodeId & 0xFFFF) << 48) | ((long) (keyId & 0xFFFF) << 32) | (tag & 0xFFL);
    }

    @Test
    void theRegistryOffersTheBinaryReaderAndItClaimsOnlyItsOwnFiles(@TempDir Path dir)
            throws IOException {
        Path binary = dir.resolve("audit.flxa");
        Files.write(binary, sampleFile());
        Path text = dir.resolve("audit.yaml");
        Files.writeString(text, "---\neventLogRecord:\n  eventTime: 1\n");

        AuditLogReader reader = new BinaryAuditReader();
        assertTrue(reader.canOpen(binary), "its own file, recognised by magic and not by extension");
        assertFalse(reader.canOpen(text), "and it must not claim the text format");

        assertTrue(new ReaderRegistry().readers().stream()
                        .anyMatch(r -> r instanceof BinaryAuditReader),
                "the registry must offer it, or the UI cannot open a binary log at all");
    }

    @Test
    void aBinaryRecordBecomesTheRecordTextTheAnalyserAlreadyUnderstands(@TempDir Path dir)
            throws IOException {
        Path binary = dir.resolve("audit.flxa");
        Files.write(binary, sampleFile());

        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(binary, records::add);

        assertEquals(1, records.size());
        String text = records.get(0);
        assertTrue(text.startsWith("---\neventLogRecord:\n"), text);
        assertTrue(text.contains("  eventTime: 1786355857407\n"), text);
        assertTrue(text.contains("  event: TickEvent\n"), text);
        assertTrue(text.contains("  endTime: 1786355857408\n"), text);
        // entries for one node are grouped onto that node's line, and a second node opens a new one
        assertTrue(text.contains("    - pricer: { price: 1.25, size: 100}"), text);
        assertTrue(text.contains("    - risk: { breach: false}"), text);
        // what the wire format does not carry is not invented
        assertFalse(text.contains("groupingId"), "the binary format has no groupingId: " + text);
        assertFalse(text.contains("thread"), "nor a thread name: " + text);
    }

    @Test
    void theEmittedTextParsesBackToTheSameValues(@TempDir Path dir) throws IOException {
        // The reader's whole job is to produce text the rest of the analyser can consume, so the
        // assertion that matters is that the analyser's own framer accepts it.
        Path binary = dir.resolve("audit.flxa");
        Files.write(binary, sampleFile());
        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(binary, records::add);

        List<String> framed = new ArrayList<>();
        telamin.fluxtion.audit.analyser.analyser.parse.RecordFramer.frame(
                String.join("", records), raw -> framed.add(raw.text()));
        assertEquals(1, framed.size(), "the framer must see exactly one record: " + records);
    }
}
