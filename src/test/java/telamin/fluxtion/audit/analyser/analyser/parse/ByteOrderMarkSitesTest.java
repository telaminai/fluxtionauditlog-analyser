package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 round 4 additions, on top of the F1 consolidation: the byte-order-mark rule has ONE text home, {@link AuditText}, and every site that
 * decides something from the head of a file, record or line goes through it.
 *
 * <p>Round 4 found a sixth site the earlier fixes missed: {@code RecordParser} used {@code strip()},
 * which keeps U+FEFF, so a BOM before a record's {@code #} header dropped that record's thread, level
 * and logger — and changed {@code auditLevelFinest}, a coverage verdict. The tests here drive the
 * header path through both stores, at the file head and mid-file, and pin the structural rule so a
 * seventh site cannot reappear silently.
 */
class ByteOrderMarkSitesTest {

    private static final String BOM = "﻿";

    /** Two records, each opened by the documented header comment, DEBUG then INFO. */
    private static final String HEADERED =
            "#10:57:37.431 [worker-1] DEBUG maker\n"
                    + "eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n"
                    + "---\n"
                    + "#10:57:37.432 [worker-2] INFO maker\n"
                    + "eventLogRecord:\n  logTime: 1001\n  event: Tick\n  nodeLogs:\n    - a: { v: 2}\n"
                    + "---\n";

    private static MappedLogStore mapped(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        return new MappedLogStore(p);
    }

    private static List<String> threads(LogStore s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) out.add(s.record(i).thread());
        return out;
    }

    private static List<String> levels(LogStore s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.size(); i++) out.add(s.record(i).level());
        return out;
    }

    private static List<String> kinds(LogStore store) {
        return ProducerDiagnostics
                .of(store.index(), store::rawText, List.of(), List.of(), false)
                .findings().stream().map(f -> f.kind().name()).toList();
    }

    @Test
    void aBomBeforeTheFirstHeaderCommentKeepsThreadAndLevel_heapAndMapped(@TempDir Path dir) throws IOException {
        HeapLogStore clean = new HeapLogStore(HEADERED);
        assertEquals(List.of("worker-1", "worker-2"), threads(clean));
        assertEquals(List.of("DEBUG", "INFO"), levels(clean));

        HeapLogStore heap = new HeapLogStore(BOM + HEADERED);
        assertEquals(threads(clean), threads(heap), "F1: a BOM'd '#' header must still be read as a header (heap)");
        assertEquals(levels(clean), levels(heap), "F1: the finest level must not change behind a BOM (heap)");

        try (MappedLogStore m = mapped(dir, "bom-head.yaml", BOM + HEADERED)) {
            assertEquals(threads(clean), threads(m), "F1: a BOM'd '#' header must still be read as a header (mapped)");
            assertEquals(levels(clean), levels(m), "F1: the finest level must not change behind a BOM (mapped)");
        }
    }

    @Test
    void aMidFileBomFromConcatenatedRunsKeepsEveryHeader_heapAndMapped(@TempDir Path dir) throws IOException {
        String joined = BOM + HEADERED + BOM + HEADERED;   // `cat run1.yaml run2.yaml`, both BOM'd
        HeapLogStore heap = new HeapLogStore(joined);
        assertEquals(List.of("worker-1", "worker-2", "worker-1", "worker-2"), threads(heap),
                "F1: the record behind a mid-file BOM keeps its header (heap)");
        try (MappedLogStore m = mapped(dir, "bom-joined.yaml", joined)) {
            assertEquals(threads(heap), threads(m), "F1: heap and mapped agree on a mid-file BOM");
        }
    }

    @Test
    void aDoubleLeadingBomIsNotAMissingRecordKey(@TempDir Path dir) throws IOException {
        String doubled = BOM + BOM + HEADERED;              // `cat bom-only.yaml run.yaml`
        assertFalse(kinds(new HeapLogStore(doubled)).contains("NO_RECORD_KEY"),
                "F3: two leading BOMs are still only BOMs (heap)");
        try (MappedLogStore m = mapped(dir, "bom-double.yaml", doubled)) {
            assertFalse(kinds(m).contains("NO_RECORD_KEY"), "F3: two leading BOMs are still only BOMs (mapped)");
            assertEquals(List.of("worker-1", "worker-2"), threads(m));
        }
    }

    @Test
    void theFramersSkipEveryLeadingBom_beforeASeparatorAndInABomOnlyFile(@TempDir Path dir) throws IOException {
        String doubledSeparator = BOM + BOM + "---\n" + HEADERED;
        HeapLogStore heap = new HeapLogStore(doubledSeparator);
        assertEquals(2, heap.size(), "F3: a separator behind two BOMs still separates (string framer)");
        assertFalse(kinds(heap).contains("NO_RECORD_KEY"));
        try (MappedLogStore m = mapped(dir, "bom-double-sep.yaml", doubledSeparator)) {
            assertEquals(2, m.size(), "F3: a separator behind two BOMs still separates (byte framer)");
            assertFalse(kinds(m).contains("NO_RECORD_KEY"));
        }
        assertEquals(0, new HeapLogStore(BOM + BOM).size(), "F3: a file of only BOMs is empty (string framer)");
        try (MappedLogStore m = mapped(dir, "bom-double-only.yaml", BOM + BOM + "\n")) {
            assertEquals(0, m.size(), "F3: a file of only BOMs is empty (byte framer)");
        }
    }

    @Test
    void theHeaderParserItselfSkipsLeadingBoms() {
        RecordHeader h = HeaderParser.parse(BOM + BOM + "#10:57:37.431 [w] WARN x");
        assertEquals("w", h.thread(), "F1: HeaderParser must see a header behind BOMs");
        assertEquals("WARN", h.level());
    }

    @Test
    void followDoesNotFailOnAHalfWrittenCharacter(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("follow.yaml");
        Files.writeString(p, "---\n", StandardCharsets.UTF_8);
        HeapLogStore store = HeapLogStore.fromFile(p).forFollow();
        byte[] rest = ("#10:57:37.431 [wé] INFO x\neventLogRecord:\n  logTime: 1\n---\n")
                .getBytes(StandardCharsets.UTF_8);
        int split = new String(rest, StandardCharsets.ISO_8859_1).indexOf('Ã') + 1;  // inside the 2-byte é
        Files.write(p, java.util.Arrays.copyOfRange(rest, 0, split), StandardOpenOption.APPEND);
        assertDoesNotThrow(() -> store.appendFrom(p),
                "F2: a poll landing mid-character must wait for the rest, not fail the read");
        Files.write(p, java.util.Arrays.copyOfRange(rest, split, rest.length), StandardOpenOption.APPEND);
        assertEquals(1, store.appendFrom(p), "the completed record arrives on the next poll");
        assertEquals("wé", store.record(0).thread());
    }

    @Test
    void genuinelyMalformedBytesStillFailLoudly() {
        byte[] bad = {'a', (byte) 0xC3, 'b', '\n'};                 // a lead byte followed by ASCII, mid-file
        assertThrows(java.nio.charset.CharacterCodingException.class, () -> HeapLogStore.completeUtf8(bad));
    }

    @Test
    void oneControlEventPredicateForTheEmptyLogFamilyAndCoverage() {
        assertTrue(ProducerDiagnostics.isControlEvent("EventLogControlEvent"));
        assertTrue(ProducerDiagnostics.isControlEvent("com.telamin.fluxtion.runtime.audit.EventLogControlEvent"));
        assertFalse(ProducerDiagnostics.isControlEvent("FakeEventLogControlEventX"),
                "F4: a lookalike is not a control record for ONLY_CONTROL_EVENTS either");
        String lookalikes = "eventLogRecord:\n  logTime: 1\n  event: FakeEventLogControlEventX\n---\n";
        assertFalse(kinds(new HeapLogStore(lookalikes)).contains("ONLY_CONTROL_EVENTS"),
                "F4: ONLY_CONTROL_EVENTS uses the same exact predicate as MA-8");
    }

    /**
     * The structural half: no source file other than {@link AuditText} tests for a BOM itself.
     * A new head-of-line site must call it — or this fails and says where.
     */
    @Test
    void theBomRuleLivesInOneClass() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                String fn = f.getFileName().toString();
                // AuditText owns the text rule; ByteRecordFramer keeps the byte form and says so.
                if (fn.equals("AuditText.java") || fn.equals("ByteRecordFramer.java")) continue;
                int n = 0;
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    n++;
                    String t = line.strip();
                    if (t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")) continue;
                    if (t.contains("\\uFEFF") || t.contains("﻿") || t.contains("0xEF") || t.contains("0xBB")
                            || t.contains("0xBF")) {
                        offenders.add(root.relativize(f) + ":" + n);
                    }
                }
            }
        }
        assertEquals(List.of(), offenders, "BOM handling must go through AuditText (or the byte framer)");
    }
}
