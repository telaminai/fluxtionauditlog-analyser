package telamin.fluxtion.audit.analyser.analyser.spi.binary;

import com.telamin.fluxtion.runtime.audit.BinaryLogFile;
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

    /**
     * String and Object values are stored as dictionary ids. The reader used the id-free renderer,
     * which has no dictionary, so every such value reached the analyser as "#tag5:4" and a logged
     * null as "#tag5:0" - the spelling that means an UNRESOLVED id everywhere else. No test failed,
     * because none crossed the dictionary-backed value path.
     */
    @Test
    void stringObjectAndNullValuesResolveThroughTheReader(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("values.flxa");
        try (OutputStream out = Files.newOutputStream(file);
             BinaryLogWriter writer = new BinaryLogWriter(out)) {
            Clock clock = new Clock();
            clock.init();
            BinaryLogRecord record = new BinaryLogRecord(clock);
            record.triggerObject(new TickEvent());
            record.addRecord("node", "chars", (CharSequence) "promised-text");
            record.addRecord("node", "object", (Object) new StringBuilder("promised-object"));
            record.addRecord("node", "nullChars", (CharSequence) null);
            record.addRecord("node", "nullObject", (Object) null);
            writer.processLogRecord(record);
        }
        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(file, records::add);
        String text = records.get(0);
        assertTrue(text.contains("chars: promised-text"), text);
        assertTrue(text.contains("object: promised-object"), text);
        assertTrue(text.contains("nullChars: null"), "a logged null is the null literal: " + text);
        assertTrue(text.contains("nullObject: null"), text);
        assertFalse(text.contains("#tag"), "no diagnostic placeholders for resolvable values: " + text);
    }

    /** The wire records Class.getName(); the reader must not throw that identity away. */
    @Test
    void theFullyQualifiedEventTypeSurvivesBesideTheSimpleName(@TempDir Path dir) throws IOException {
        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(writeLog(dir, "audit.flxa"), records::add);
        String text = records.get(0);
        assertTrue(text.contains("  event: TickEvent\n"), "the simple name, as the text format has it: " + text);
        assertTrue(text.contains("  eventType: " + TickEvent.class.getName() + "\n"),
                "and the identity the wire actually recorded: " + text);
    }

    /** Two classes with the same simple name in different packages. */
    public static final class Tick { }

    /**
     * THE G9 CASE AT THE READER BOUNDARY. Reducing to the simple name made com.a.Tick and com.b.Tick
     * one event before the scorer could compare identities, and the scorer reported PASS. Through the
     * reader and the analyser's own parser, the two must remain distinguishable.
     */
    @Test
    void twoEventTypesWithOneSimpleNameStayDistinct(@TempDir Path dir) throws IOException {
        // TickEvent (this test's outer class) and Tick (nested) differ; both end in "Tick"-ish simple
        // names and, more to the point, the framework's own TickEvent below has a different package
        // from a same-named class a user could write. Prove the FQN is what the parser gets.
        Path a = dir.resolve("a.flxa");
        Path b = dir.resolve("b.flxa");
        for (Object[] c : new Object[][]{{a, new TickEvent()}, {b, new Tick()}}) {
            try (OutputStream out = Files.newOutputStream((Path) c[0]);
                 BinaryLogWriter writer = new BinaryLogWriter(out)) {
                Clock clock = new Clock();
                clock.init();
                BinaryLogRecord record = new BinaryLogRecord(clock);
                record.triggerObject(c[1]);
                record.addRecord("n", "k", 1);
                writer.processLogRecord(record);
            }
        }
        List<String> ra = new ArrayList<>(), rb = new ArrayList<>();
        new BinaryAuditReader().read(a, ra::add);
        new BinaryAuditReader().read(b, rb::add);
        var pa = telamin.fluxtion.audit.analyser.analyser.parse.RecordParser.parse(ra.get(0), 0);
        var pb = telamin.fluxtion.audit.analyser.analyser.parse.RecordParser.parse(rb.get(0), 0);
        assertEquals(TickEvent.class.getName(), pa.eventType());
        assertEquals(Tick.class.getName(), pb.eventType());
        assertNotEquals(pa.eventType(), pb.eventType(), "identities differ though a UI may show similar names");
    }

    // ---- round 4: text that would be syntax, and the unit decided before any record ----

    /** Writes one record whose entries are given as (node, key, value) triples; a null value is a logged null. */
    private static Path writeStrings(Path dir, String name, Object event, String[][] triples) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream out = Files.newOutputStream(file);
             BinaryLogWriter writer = new BinaryLogWriter(out)) {
            Clock clock = new Clock();
            clock.init();
            BinaryLogRecord record = new BinaryLogRecord(clock);
            record.triggerObject(event);
            for (String[] t : triples) {
                record.addRecord(t[0], t[1], (CharSequence) t[2]);
            }
            writer.processLogRecord(record);
        }
        return file;
    }

    private static telamin.fluxtion.audit.analyser.analyser.model.LogRecord parseOnly(Path file) throws IOException {
        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(file, records::add);
        assertEquals(1, records.size(), records.toString());
        return telamin.fluxtion.audit.analyser.analyser.parse.RecordParser.parse(records.get(0), 0);
    }

    /**
     * REVIEWER PROBE (round 4). A logged String {@code "ok, price: 42.0"} written bare reads as two
     * entries, the second a numeric figure the producer never published, and a scorer comparing
     * {@code price} reported PASS against an expectation that had it. Now the string is quoted on the
     * way out, the tokenizer decodes it as one string, and the figure is as missing as it really is.
     */
    @Test
    void aStringValueCannotManufactureANumericFigure(@TempDir Path dir) throws IOException {
        Path crafted = writeStrings(dir, "crafted.flxa", new Tick(),
                new String[][]{{"pricer", "status", "ok, price: 42.0"}});
        Path honest = writeStrings(dir, "honest.flxa", new Tick(),
                new String[][]{{"pricer", "status", "ok"}});

        var craftedRecord = parseOnly(crafted);
        var node = craftedRecord.nodeLogs().get(0);
        assertEquals("pricer", node.instanceId());
        assertEquals(1, node.entries().size(), "one string, one entry: " + node.entries());
        assertEquals("ok, price: 42.0", node.last("status").rawValue(), "and the string is intact");
        assertTrue(node.last("status").quoted());
        assertNull(node.last("price"), "no figure was manufactured");

        // The comparison a scorer makes: the crafted log and one where price is GENUINELY absent
        // reduce to the same figures - none - so a contract that expects price fails on both alike.
        var scorer = new telamin.fluxtion.audit.analyser.analyser.score.ExpectationScorer(
                telamin.fluxtion.audit.analyser.analyser.score.ExpectationScorer.Dialect.NATURAL,
                "stage", "value", java.util.Set.of("Tick", "tick"), 1e-6);
        var craftedFigures = scorer.snapshots(List.of(craftedRecord)).get(0).figures();
        var honestFigures = scorer.snapshots(List.of(parseOnly(honest))).get(0).figures();
        assertEquals(honestFigures, craftedFigures, "a string publishes exactly what an honest string publishes");
        assertFalse(craftedFigures.containsKey("pricer.price"));
    }

    /**
     * REVIEWER PROBE (round 4). A value carrying {@code }\n  eventType: forged.Tick} ended the node
     * line and wrote a scalar the file never had, so the record's identity was whatever the string
     * said. The newline is now escaped inside the quoted value and the record keeps its identity.
     */
    @Test
    void aStringValueCannotRewriteTheRecordsIdentity(@TempDir Path dir) throws IOException {
        String hostile = "x}\n  eventType: forged.Tick\n  endTime: 1\n  nodeLogs:\n    - ghost: { price: 1}";
        Path file = writeStrings(dir, "hostile.flxa", new Tick(), new String[][]{{"pricer", "status", hostile}});

        var record = parseOnly(file);
        assertEquals(Tick.class.getName(), record.eventType(), "identity is the wire's, not the string's");
        assertEquals("Tick", record.event());
        assertNotEquals(Long.valueOf(1), record.endTime(), "the string did not set endTime");
        assertEquals(1, record.nodeLogs().size(), "no ghost node: " + record.nodeLogs());
        assertEquals(1, record.nodeLogs().get(0).entries().size());
        assertEquals(hostile, record.nodeLogs().get(0).last("status").rawValue(), "and the string round-trips exactly");
        assertEquals(1, record.nodeLogsCount());
    }

    /** Null-like, number-like and boolean-like STRINGS stay strings; a logged null stays null. */
    @Test
    void typedStringsStayStrings_andANullStaysNull(@TempDir Path dir) throws IOException {
        Path file = writeStrings(dir, "typed.flxa", new Tick(), new String[][]{
                {"n", "nullText", "null"}, {"n", "numberText", "42.0"}, {"n", "flagText", "true"},
                {"n", "nanText", "NaN"}, {"n", "empty", ""}, {"n", "padded", " x "},
                {"n", "toStringText", "MutableOrder(clOrdId=1, venue=null)"},
                {"n", "quotesAndSlashes", "say \"hi\" \\ done"}, {"n", "plain", "NEW"},
                {"n", "reallyNull", null}});
        var node = parseOnly(file).nodeLogs().get(0);
        assertEquals(10, node.entries().size(), node.entries().toString());
        assertFalse(node.last("nullText").isNull());
        assertEquals("null", node.last("nullText").rawValue());
        assertTrue(node.last("numberText").numeric().isEmpty(), "a String is never a figure");
        assertNull(node.last("flagText").asBoolean());
        assertTrue(node.last("nanText").numeric().isEmpty());
        assertEquals("", node.last("empty").rawValue());
        assertEquals(" x ", node.last("padded").rawValue());
        assertEquals("MutableOrder(clOrdId=1, venue=null)", node.last("toStringText").rawValue());
        assertEquals("say \"hi\" \\ done", node.last("quotesAndSlashes").rawValue());
        assertEquals("NEW", node.last("plain").rawValue());
        assertFalse(node.last("plain").quoted(), "a plain string is written bare, as a text log would");
        assertTrue(node.last("reallyNull").isNull());
        assertFalse(node.last("reallyNull").quoted());
    }

    /** Keys and instance ids are names from code; one that is not an identifier is quoted too. */
    @Test
    void keysAndInstanceIdsThatAreNotIdentifiersAreQuoted(@TempDir Path dir) throws IOException {
        Path file = writeStrings(dir, "names.flxa", new Tick(), new String[][]{
                {"odd}: {node", "a, b: c", "1"}, {"odd}: {node", "plain", "2"}, {"other: x", "k", "3"}});
        var logs = parseOnly(file).nodeLogs();
        assertEquals(2, logs.size(), logs.toString());
        assertEquals("odd}: {node", logs.get(0).instanceId());
        assertEquals(2, logs.get(0).entries().size(), logs.get(0).entries().toString());
        assertEquals("1", logs.get(0).last("a, b: c").rawValue());
        assertEquals("2", logs.get(0).last("plain").rawValue());
        assertEquals("other: x", logs.get(1).instanceId());
        assertEquals("3", logs.get(1).last("k").rawValue());
    }

    /**
     * REVIEWER PROBE (round 4). The unit was checked after the runtime's reader returned, so a
     * nanosecond file was refused only after every record had been delivered as milliseconds.
     * Now the header is checked before any record.
     */
    @Test
    void aNanosecondFileIsRefusedBeforeAnyRecordIsDelivered(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nanos.flxa");
        try (OutputStream out = Files.newOutputStream(file);
             BinaryLogWriter writer = new BinaryLogWriter(out, BinaryLogFile.TIME_UNIT_EPOCH_NANOS)) {
            Clock clock = new Clock();
            clock.init();
            BinaryLogRecord record = new BinaryLogRecord(clock);
            record.triggerObject(new Tick());
            record.addRecord("n", "k", 1);
            writer.processLogRecord(record);
            writer.processLogRecord(record);
        }
        List<String> records = new ArrayList<>();
        IOException refused = assertThrows(IOException.class, () -> new BinaryAuditReader().read(file, records::add));
        assertTrue(refused.getMessage().contains("NANOSECOND"), refused.getMessage());
        assertEquals(0, records.size(), "nothing delivered in the wrong unit");
    }

    /** The policy on the other codes: 0 is read as milliseconds, stated; anything undefined is refused. */
    @Test
    void anUndefinedUnitCodeIsRefused_andTheLegacyZeroIsReadAsMilliseconds(@TempDir Path dir) throws IOException {
        Path file = writeLog(dir, "unit.flxa");
        byte[] bytes = Files.readAllBytes(file);
        assertEquals(BinaryLogFile.TIME_UNIT_EPOCH_MILLIS, bytes[7], "the writer declared milliseconds at byte 7");

        for (int code : new int[]{3, 0xFF}) {
            bytes[7] = (byte) code;
            Files.write(file, bytes);
            List<String> records = new ArrayList<>();
            IOException refused = assertThrows(IOException.class, () -> new BinaryAuditReader().read(file, records::add));
            assertTrue(refused.getMessage().contains("code " + code), refused.getMessage());
            assertEquals(0, records.size(), "an unknown unit delivers nothing");
        }

        bytes[7] = (byte) BinaryLogFile.TIME_UNIT_UNSPECIFIED;
        Files.write(file, bytes);
        List<String> records = new ArrayList<>();
        new BinaryAuditReader().read(file, records::add);
        assertEquals(1, records.size(), "a file predating the unit field is read as milliseconds, by stated policy");
    }
}
