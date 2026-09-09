package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import com.telamin.fluxtion.runtime.audit.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.BinaryLogWriter;
import com.telamin.fluxtion.runtime.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.spi.ReaderRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M52.5: a binary log opens IN THE ANALYSER, not only at a command line.
 *
 * <p><b>The fixtures are written by the real encoder.</b> An earlier version of this test hand-encoded
 * the format from the specification, to check a second decoder the analyser used to carry. That proved
 * less than it appeared to: the decoder and the test encoder were written from one reading of one
 * document, so a misreading would have appeared in both and passed. Driving the shipped writer means
 * the bytes under test are the bytes a real processor produces.
 */
class BinaryAuditReaderTest {

    /** Writes a log with the real writer, and returns its path. */
    private static Path writeLog(Path dir, String name) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file);
             BinaryLogWriter writer = new BinaryLogWriter(out)) {
            BinaryLogRecord record = new BinaryLogRecord(new Clock());
            // The PRODUCTION path: BinaryEventLogger resolves each name to an id once and logs by id.
            // The String-keyed overloads inherited from LogRecord write into a byte buffer that
            // length() does not describe, so a record built with those is empty by the time it reaches
            // a writer - which is what this test found when it used them.
            int pricer = record.internName("pricer");
            int risk = record.internName("risk");
            int price = record.internName("price");
            int size = record.internName("size");
            int breach = record.internName("breach");
            record.triggerObject(new TickEvent());
            record.addRecord(pricer, price, 1.25d);
            record.addRecord(pricer, size, 100);
            record.addRecord(risk, breach, false);
            writer.processLogRecord(record);
        }
        return file;
    }

    public static final class TickEvent {
    }

    @Test
    void theRegistryOffersTheBinaryReaderAndItClaimsOnlyItsOwnFiles(@TempDir Path dir)
            throws IOException {
        Path binary = writeLog(dir, "audit.flxa");
        Path text = dir.resolve("audit.yaml");
        Files.writeString(text, "---\neventLogRecord:\n  eventTime: 1\n", StandardCharsets.UTF_8);

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
        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(writeLog(dir, "audit.flxa"), records::add);

        assertEquals(1, records.size(), "one record in, one record out: " + records);
        String text = records.get(0);
        assertTrue(text.startsWith("---\neventLogRecord:\n"), text);
        assertTrue(text.contains("  event: TickEvent\n"), text);
        // entries for one node group onto that node's line; a second node opens a new one
        assertTrue(text.contains("    - pricer: { price: 1.25, size: 100}"), text);
        assertTrue(text.contains("    - risk: { breach: false}"), text);
        // what the wire format does not carry is not invented
        assertFalse(text.contains("groupingId"), "the binary format has no groupingId: " + text);
        assertFalse(text.contains("thread"), "nor a thread name: " + text);
    }

    @Test
    void doublesSurviveTheRoundTripExactly(@TempDir Path dir) throws IOException {
        double awkward = 0.1 + 0.2;                       // not representable; must not be reformatted
        Path file = dir.resolve("double.flxa");
        try (OutputStream out = Files.newOutputStream(file);
             BinaryLogWriter writer = new BinaryLogWriter(out)) {
            BinaryLogRecord record = new BinaryLogRecord(new Clock());
            int pricer = record.internName("pricer");
            int price = record.internName("price");
            record.triggerObject(new TickEvent());
            record.addRecord(pricer, price, awkward);
            writer.processLogRecord(record);
        }

        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(file, records::add);
        assertTrue(records.get(0).contains("price: " + awkward),
                "the exact double must survive: " + records.get(0));
    }

    @Test
    void aTruncatedTrailingRecordStillOpensWithEverythingBeforeIt(@TempDir Path dir)
            throws IOException {
        // A half-written trailing record is the expected end state of a crashed process, and a crash is
        // the usual reason to open an audit log at all.
        Path whole = writeLog(dir, "whole.flxa");
        byte[] bytes = Files.readAllBytes(whole);
        Path cut = dir.resolve("cut.flxa");
        Files.write(cut, java.util.Arrays.copyOf(bytes, bytes.length - 9));

        List<String> records = new ArrayList<>();
        assertDoesNotThrow(() -> new BinaryAuditReader().read(cut, records::add),
                "a truncated log must open, not throw");
    }

    @Test
    void theEmittedTextParsesBackThroughTheAnalysersOwnFramer(@TempDir Path dir) throws IOException {
        // The reader's whole job is to produce text the rest of the analyser consumes, so the assertion
        // that matters is that the analyser's own framer accepts it.
        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(writeLog(dir, "audit.flxa"), records::add);

        List<String> framed = new ArrayList<>();
        telamin.fluxtion.audit.analyser.analyser.parse.RecordFramer.frame(
                String.join("", records), raw -> framed.add(raw.text()));
        assertEquals(1, framed.size(), "the framer must see exactly one record: " + records);
    }
}
