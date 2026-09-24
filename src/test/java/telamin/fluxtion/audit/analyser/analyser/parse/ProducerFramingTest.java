package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.3 (spec-evidence-integrity D-E9, acceptance 10), on the CONSTRUCTED fixtures in {@code src/test/resources/framing}
 * and the one real collapsed log the repository holds. See that folder's README.
 */
class ProducerFramingTest {

    private static final Path FIXTURES = Path.of("src/test/resources/framing");

    private static ProducerDiagnostics diagnose(LogStore s) {
        return ProducerDiagnostics.of(s.index(), s::rawText, s.sourceDiagnostics(), s.completenessDiagnostics(),
                s.completenessIsNote(), s.pendingFrameText());
    }

    @Test
    @DisplayName("THE REPRODUCED FALSE VERDICT: a legal one-record file with the key in quoted values is not 'two records'")
    void aLegalOneRecordFileIsNotAccused() throws Exception {
        // witness: ProducerDiagnostics back to counting the key anywhere in the text
        var s = HeapLogStore.fromFile(FIXTURES.resolve("quoted-key-one-record.yaml"));
        assertEquals(1, s.size());
        var d = diagnose(s);
        assertFalse(d.findings().stream().anyMatch(f -> f.kind() == ProducerDiagnostics.Kind.UNSEPARATED), d.messages().toString());
    }

    @Test
    @DisplayName("the client-shaped collapse is SUSPECTED, naming the span and every candidate line")
    void theCollapseIsSuspectedWithItsEvidence() throws Exception {
        var s = HeapLogStore.fromFile(FIXTURES.resolve("client-shape-collapsed.yaml"));
        assertEquals(1, s.size(), "the precondition: nine records read as one");
        var d = diagnose(s);
        var f = d.findings().get(0);
        assertEquals(ProducerDiagnostics.Kind.UNSEPARATED, f.kind());
        assertTrue(f.message().startsWith("Suspected"), f.message());
        assertTrue(f.message().contains("9 records run together"), f.message());
        assertTrue(f.message().contains("Inspected lines 1–"), "it names the span it read: " + f.message());
        assertTrue(f.message().contains("line(s) ["), "and the candidate lines: " + f.message());
    }

    @Test
    @DisplayName("the one REAL collapsed log in the repository is suspected too")
    void theRealStarterSampleIsSuspected() throws Exception {
        var s = HeapLogStore.fromFile(Path.of("docs/handoff/evidence/sg1-release-2026-09-21/sample-run.txt"));
        var d = diagnose(s);
        assertEquals(ProducerDiagnostics.Kind.UNSEPARATED, d.findings().get(0).kind(), d.messages().toString());
    }

    @Test
    @DisplayName("an apostrophe cannot hide a collapse behind it")
    void anApostropheDoesNotHideACollapse() throws Exception {
        var d = diagnose(HeapLogStore.fromFile(FIXTURES.resolve("apostrophe-then-collapse.yaml")));
        assertEquals(ProducerDiagnostics.Kind.UNSEPARATED, d.findings().get(0).kind(), d.messages().toString());
    }

    @Test
    @DisplayName("both readers that index a file say the same thing about it")
    void heapAndMappedAgree() throws Exception {
        Path f = FIXTURES.resolve("client-shape-collapsed.yaml");
        var heap = diagnose(HeapLogStore.fromFile(f));
        try (var mapped = new MappedLogStore(f)) {
            assertEquals(heap.messages(), diagnose(mapped).messages());
        }
    }

    @Test
    @DisplayName("an ordinary unterminated export tail stays a record on a static open, and is not suspected")
    void anUnterminatedTailIsARecord() throws Exception {
        var s = HeapLogStore.fromFile(FIXTURES.resolve("unterminated-tail.yaml"));
        assertEquals(2, s.size());
        assertTrue(diagnose(s).isClean(), diagnose(s).messages().toString());
    }

    @Test
    @DisplayName("an item longer than the check reads is NOT ASSESSED — a note, never a clean bill, never a warning")
    void beyondTheBoundIsNotAssessed() {
        String big = "eventLogRecord:\n" + "    pad: x\n".repeat(120_000);
        LogIndex idx = new LogIndex();
        idx.add(telamin.fluxtion.audit.analyser.analyser.model.LogRecord.builder().event("Big").nodeLogsCount(1).build());
        var d = ProducerDiagnostics.of(idx, row -> big);
        assertEquals(ProducerDiagnostics.Kind.FRAMING_NOT_ASSESSED, d.findings().get(0).kind(), d.messages().toString());
        assertFalse(d.isWarning(), "a limit is stated in the tooltip and context, not raised on the bar");
    }

    @Test
    @DisplayName("under Follow a separator arriving in pieces stays pending, and the pending frame is observable, not a record")
    void aSeparatorInPiecesStaysPending(@TempDir Path dir) throws Exception {
        String rec = "eventLogRecord: \n    logTime: 1\n    event: Tick\n    nodeLogs: \n        - n: { v: 1}\n";
        Path f = Files.writeString(dir.resolve("live.yaml"), "---\n" + rec + "---\n" + rec);
        HeapLogStore s = HeapLogStore.fromFile(f).forFollow();
        s.appendFrom(f);
        int before = s.size();
        Files.writeString(f, "--", StandardOpenOption.APPEND);
        s.appendFrom(f);
        assertEquals(before, s.size(), "'--' is not a separator");
        assertNotNull(s.pendingFrameText(), "the frame being written is observable");
        Files.writeString(f, "-\n", StandardOpenOption.APPEND);
        s.appendFrom(f);
        assertEquals(before + 1, s.size(), "the separator arrived whole: now it is a record");
    }

    @Test
    @DisplayName("a live log with no separators is suspected from its pending frame, before any record exists")
    void aLiveCollapseIsSuspectedFromThePendingFrame(@TempDir Path dir) throws Exception {
        // witness: ProducerDiagnostics ignoring the pending frame
        String rec = "eventLogRecord: \n    logTime: 1\n    event: Tick\n    nodeLogs: \n        - n: { v: 1}\n";
        Path f = Files.writeString(dir.resolve("live.yaml"), "---\n" + rec + rec + rec);
        HeapLogStore s = HeapLogStore.fromFile(f).forFollow();
        s.appendFrom(f);
        var d = diagnose(s);
        assertTrue(d.findings().stream().anyMatch(x -> x.kind() == ProducerDiagnostics.Kind.UNSEPARATED
                && x.message().contains("still being written")), d.messages() + " pending=" + s.pendingFrameText());
    }
}
