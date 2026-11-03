package telamin.fluxtion.audit.analyser.analyser.parse;

import com.telamin.fluxtion.runtime.audit.AuditStreamHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;
import telamin.fluxtion.audit.analyser.analyser.spi.YamlAuditReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The audit stream header in a TEXT log (evidence-set key, tolerance first): recognised as stream metadata, never a
 * record, on every store path; parsed field for field as the runtime writes it; a malformed one stated, never read.
 *
 * <p><b>The writer is the runtime's own.</b> Header text in the agreement tests is produced by
 * {@link AuditStreamHeader#toString()} from the runtime on the classpath, not typed here, so a change to how the
 * runtime spells a header fails this test rather than passing two readings of the same misunderstanding (rule 6).
 * The conformance fixtures c35–c38 hold the two-path agreement; this holds the parser, the other stores and Follow.
 */
class StreamHeaderTest {

    @TempDir
    Path dir;

    private static String record(long logTime) {
        return "eventLogRecord:\n    logTime: " + logTime + "\n    event: Tick\n    nodeLogs:\n    - book: { mid: 1.5}\n";
    }

    private static Map<String, String> host(String runId) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("serverId", "pricing-1");
        h.put("runId", runId);
        h.put("group", "pricing");
        h.put("name", "quoter");
        h.put("generation", "1");
        return h;
    }

    private static String header(String runId) {
        return new AuditStreamHeader("com.acme.TradeProcessor", "5f3c0a9e", "1.0.84", "1.1.4",
                "2026-11-03T09:15:02.118Z", host(runId)).toString() + "\n";
    }

    private Path file(String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private static void assertAgrees(AuditStreamHeader written) {
        StreamHeader read = StreamHeader.parse(written.toString(), 0);
        assertTrue(read.valid(), () -> read.problem() + "\n" + written);
        assertEquals(1, read.schema());
        assertEquals(written.processor(), read.processor(), written::toString);
        assertEquals(written.sourceFingerprint(), read.sourceFingerprint(), written::toString);
        assertEquals(written.toolchainVersion(), read.toolchainVersion(), written::toString);
        assertEquals(written.runtimeVersion(), read.runtimeVersion(), written::toString);
        assertEquals(written.startedAt(), read.startedAt(), written::toString);
        assertEquals(written.host(), read.host(), written::toString);
        assertEquals(List.copyOf(written.host().keySet()), List.copyOf(read.host().keySet()), "order kept");
    }

    // ---- the parser, against the runtime's writer ---------------------------------------------------------

    @Test
    void everyHeaderTheRuntimeWritesReadsBackFieldForField() {
        assertAgrees(new AuditStreamHeader("com.acme.TradeProcessor", "5f3c0a9e", "1.0.84", "1.1.4",
                "2026-11-03T09:15:02.118Z", host("2c41")));
        assertAgrees(new AuditStreamHeader(null, null, null, null, "2026-11-03T09:15:02.118Z", null));
        Map<String, String> hostile = new LinkedHashMap<>();
        hostile.put("note", "a, b: {c} 'd'");
        hostile.put("yes", "true");
        hostile.put("empty", "");
        hostile.put("nothing", null);
        hostile.put("it's", "x#y");
        hostile.put("line", "one\ntwo");
        // values YAML would misread: the string "null", numbers, booleans, a space, a leading dash
        AuditStreamHeader odd = new AuditStreamHeader("null", "42", "on", "x y", "-1", hostile);
        StreamHeader read = StreamHeader.parse(odd.toString(), 0);
        assertTrue(read.valid(), read.problem());
        assertEquals("null", read.processor(), "the quoted string 'null' is a string");
        assertEquals("42", read.sourceFingerprint());
        assertEquals("on", read.toolchainVersion());
        assertEquals("x y", read.runtimeVersion());
        assertEquals("-1", read.startedAt());
        assertEquals("a, b: {c} 'd'", read.host().get("note"), "quotes, commas and colons inside a value split nothing");
        assertEquals("true", read.host().get("yes"));
        assertEquals("", read.host().get("empty"), "an empty value is empty, not absent");
        assertFalse(read.host().containsKey("nothing"), "the runtime drops a null host value (1.1.4)");
        assertEquals("x#y", read.host().get("it's"));
        assertEquals("one\uFFFDtwo", read.host().get("line"),
                "the writer replaces a control character with U+FFFD, so the header stays one document (1.1.4)");
    }

    @Test
    void anUnknownKeyIsTolerated_newKeysAreAdditiveWithinASchema() {
        String text = header("r") + "    buildId: 77\n";
        StreamHeader h = StreamHeader.parse(text, 3);
        assertTrue(h.valid(), h.problem());
        assertEquals(3, h.firstRecord());
    }

    @Test
    void aMalformedHeaderSaysWhatIsWrong_neverThrows() {
        String good = header("r");
        assertProblem(good.replace("schema: 1", "schema: 2"), "declares schema 2, and this analyser reads schema 1");
        assertProblem(good.replace("    schema: 1\n", ""), "has no 'schema'");
        assertProblem(good.replace("schema: 1", "schema: one"), "is not a number");
        assertProblem(good.substring(0, good.indexOf("    host:")), "missing host");
        assertProblem(good.replace("    startedAt", "startedAt"), "is not indented");
        assertProblem(good.replace("host: {", "host: ["), "host is not a {key: value} map");
        assertProblem(good.replace("host: {serverId: pricing-1", "host: {serverId: 'pricing-1"), "host is not a {key: value} map");
        assertProblem(good + "    processor: com.acme.Other\n", "'processor' appears twice");
        assertProblem(good.replace("    processor:", "    - processor:"), "is not a 'key: value' line");
        assertProblem("auditStreamHeader:\n", "has no 'schema'");
        assertProblem("auditStreamHeader:\n    schema: 1\n    host: pricing-1\n", "missing processor");
    }

    private static void assertProblem(String text, String expected) {
        assertTrue(StreamHeader.isHeader(text), text);
        StreamHeader h = StreamHeader.parse(text, 0);
        assertFalse(h.valid(), () -> "should be malformed:\n" + text);
        assertTrue(h.problem().contains(expected), () -> h.problem() + "\n---\n" + text);
        assertNotNull(h.diagnostic(0));
    }

    @Test
    void recognitionIsByTheFirstContentLine_soNoRecordCanBecomeAHeader() {
        assertTrue(StreamHeader.isHeader(header("r")));
        assertTrue(StreamHeader.isHeader("---\n" + header("r")), "a plugin's leading separator");
        assertTrue(StreamHeader.isHeader("#09:15:02.118 [main] INFO  com.acme.X\n" + header("r")), "a logger's comment");
        assertTrue(StreamHeader.isHeader("﻿" + header("r")), "a byte-order mark at the head of the file");
        assertFalse(StreamHeader.isHeader(record(1)));
        // a record whose payload carries a header-shaped line, even at column 0, is a record
        String forged = "eventLogRecord:\n    logTime: 1\n    eventToString: x\n" + header("forged") + "    nodeLogs:\n";
        assertFalse(StreamHeader.isHeader(forged));
        assertEquals(-1, StreamHeader.documentAfter(forged), "and is never split");
        assertFalse(StreamHeader.isHeader("auditStreamHeader: {schema: 1}\n"), "the key line is the key alone");
    }

    @Test
    void aMalformedHeaderSaysWhereItIs() {
        StreamHeader bad = StreamHeader.parse("auditStreamHeader:\n    schema: 9\n", 0);
        assertTrue(bad.diagnostic(0).startsWith("The stream header in this log is not well formed: it declares schema 9"),
                bad.diagnostic(0));
        assertTrue(bad.atRecord(4).diagnostic(10).startsWith("The stream header before record 5 "), bad.atRecord(4).diagnostic(10));
        assertTrue(bad.atRecord(10).diagnostic(10).startsWith("The stream header after the last record "));
        assertNull(StreamHeader.parse(header("r"), 0).diagnostic(5));
    }

    // ---- the stores --------------------------------------------------------------------------------------

    /** The same bytes through every store that frames text: heap, memory-mapped, the SPI over the YAML reader. */
    private List<LogStore> everyTextStore(String content) throws IOException {
        Path p = file("log-" + Integer.toHexString(content.hashCode()) + ".yaml", content);
        return List.of(HeapLogStore.fromFile(p), new MappedLogStore(p), SpiLogStore.open(new YamlAuditReader(), p));
    }

    @Test
    void everyStorePathTakesTheHeaderOutOfTheRecords() throws IOException {
        String log = "---\n" + header("run-1") + "---\n" + record(1000) + "---\n" + record(1001)
                + "---\n" + header("run-2") + "---\n" + record(1002) + "---\n";
        for (LogStore s : everyTextStore(log)) {
            String path = s.getClass().getSimpleName();
            assertEquals(3, s.size(), path);
            assertEquals(List.of(0, 2), s.streamHeaders().stream().map(StreamHeader::firstRecord).toList(), path);
            assertEquals("run-2", s.streamHeaders().get(1).host().get("runId"), path);
            assertTrue(s.sourceDiagnostics().isEmpty(), path + ": " + s.sourceDiagnostics());
            for (int i = 0; i < s.size(); i++) {
                assertTrue(s.rawText(i).contains("eventLogRecord:"), path + " row " + i + ": " + s.rawText(i));
                assertFalse(s.rawText(i).contains("auditStreamHeader"), path + " row " + i);
            }
            assertTrue(ProducerDiagnostics.of(s.index(), s::rawText, s.sourceDiagnostics()).isClean(), path);
            assertEquals(1000L, s.minLogTime(), path);
        }
    }

    @Test
    void aHeaderRunIntoTheNextRecordIsSplit_onEveryPath_withExactSpans() throws IOException {
        // no separator anywhere between the header and the record: the sink frames nothing
        String log = "﻿" + header("run-1") + record(1000) + "---\n" + record(1001);
        for (LogStore s : everyTextStore(log)) {
            String path = s.getClass().getSimpleName();
            assertEquals(2, s.size(), path);
            assertEquals(1, s.streamHeaders().size(), path);
            assertTrue(s.streamHeaders().get(0).valid(), path + ": " + s.streamHeaders().get(0).problem());
            assertTrue(s.rawText(0).startsWith("eventLogRecord:"), path + ": " + s.rawText(0));
            assertEquals(Long.valueOf(1000), s.record(0).logTime(), path);
            assertEquals(1.5, s.record(0).nodeLogs().get(0).last("mid").numeric().getAsDouble(), 0, path);
        }
        // the mapped store re-reads a row from the file by its byte span: the span must be the record's exactly
        Path p = file("spans.yaml", "﻿" + header("é-run") + record(1000));
        try (MappedLogStore mapped = new MappedLogStore(p)) {
            assertEquals(record(1000).strip(), mapped.rawText(0).strip());
            assertEquals("é-run", mapped.streamHeaders().get(0).host().get("runId"));
        }
    }

    @Test
    void aHeaderOnlyFileOpensAsAnEmptyLogOfThatProcessor() throws IOException {
        Path p = file("idle.log", header("run-1"));
        assertTrue(new YamlAuditReader().canOpen(p));
        for (LogStore s : everyTextStore(header("run-1"))) {
            assertEquals(0, s.size());
            assertEquals("com.acme.TradeProcessor", s.streamHeaders().get(0).processor());
            assertEquals(List.of(ProducerDiagnostics.Kind.EMPTY_LOG),
                    ProducerDiagnostics.of(s.index(), s::rawText, s.sourceDiagnostics()).findings().stream()
                            .map(ProducerDiagnostics.Finding::kind).toList());
        }
        // more than the sniff's 4 KB of comments before it is not a case a writer produces; the header itself is enough
        assertTrue(new YamlAuditReader().canOpen(file("header-after-blank.txt", "\n\n---\n" + header("x"))));
    }

    @Test
    void aMalformedHeaderIsADiagnosticOnEveryPath_neverARecord() throws IOException {
        String log = "---\nauditStreamHeader:\n    schema: 1\n    processor: com.acme.X\n---\n" + record(1000) + "---\n";
        for (LogStore s : everyTextStore(log)) {
            String path = s.getClass().getSimpleName();
            assertEquals(1, s.size(), path);
            assertFalse(s.streamHeaders().get(0).valid(), path);
            assertEquals(1, s.sourceDiagnostics().size(), path + ": " + s.sourceDiagnostics());
            var kinds = ProducerDiagnostics.of(s.index(), s::rawText, s.sourceDiagnostics()).findings().stream()
                    .map(ProducerDiagnostics.Finding::kind).toList();
            assertEquals(List.of(ProducerDiagnostics.Kind.SOURCE_DAMAGE), kinds, path);
        }
    }

    @Test
    void aPreHeaderLogIsUnchanged() throws IOException {
        String sample = Files.readString(Path.of("src/test/resources/sample.yml"));
        long records = sample.lines().filter(l -> l.strip().equals("eventLogRecord:")).count();
        for (LogStore s : everyTextStore(sample)) {
            assertEquals(records, s.size(), s.getClass().getSimpleName());
            assertTrue(s.streamHeaders().isEmpty());
            assertTrue(s.sourceDiagnostics().isEmpty());
        }
    }

    @Test
    void aMarkerDoesNotCountTheHeader() throws IOException {
        String log = "---\n" + header("r") + "---\n" + record(1) + "---\n" + record(2)
                + "---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 2\n---\n";
        for (LogStore s : everyTextStore(log)) {
            assertEquals(StreamEnd.State.COMPLETE, s.streamEnd().state(), s.getClass().getSimpleName());
        }
    }

    @Test
    void aRolledSetPlacesEachMembersHeaderInTheSetsNumbering() throws IOException {
        Path a = file("roll.1.log", header("day-1") + record(1000) + "---\n" + record(1001));
        Path b = file("roll.2.log", header("day-2") + record(2000));
        try (RolledLogStore set = RolledLogStore.open(List.of(a, b), 100)) {
            assertEquals(3, set.size());
            assertEquals(List.of(0, 2), set.streamHeaders().stream().map(StreamHeader::firstRecord).toList());
            assertEquals("day-2", set.streamHeaders().get(1).host().get("runId"));
            assertTrue(set.sourceDiagnostics().isEmpty());
        }
        Path bad = file("roll.3.log", "auditStreamHeader:\n    schema: 1\n---\n" + record(3000));
        try (RolledLogStore set = RolledLogStore.open(List.of(a, bad), 100)) {
            assertEquals(List.of("The stream header before record 3 is not well formed: it is missing processor, "
                            + "sourceFingerprint, toolchainVersion, runtimeVersion, startedAt, host — a header cut short, or "
                            + "one this writer did not finish. It is not read as a record, and it says nothing about which "
                            + "processor or graph wrote the records after it."),
                    set.sourceDiagnostics(), "named in the set's numbering");
        }
    }

    @Test
    void followTakesAnAppendedHeaderOutOfTheRecordsToo() throws IOException {
        Path p = file("live.log", "---\n" + header("run-1") + "---\n" + record(1) + "---\n");
        HeapLogStore store = HeapLogStore.fromFile(p).forFollow();
        assertEquals(1, store.size());
        Files.writeString(p, header("run-2") + "---\n" + record(2) + "---\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(1, store.appendFrom(p), "one record appended; the header is not one");
        assertEquals(2, store.size());
        assertEquals(List.of(0, 1), store.streamHeaders().stream().map(StreamHeader::firstRecord).toList());
        // a header still being written is pending, never half-read
        Files.writeString(p, "auditStreamHeader:\n    schema: 1\n    proc", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(0, store.appendFrom(p));
        assertEquals(2, store.streamHeaders().size());
        assertTrue(store.sourceDiagnostics().isEmpty(), store.sourceDiagnostics().toString());
    }
}
