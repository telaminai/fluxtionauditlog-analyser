package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.util.List;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MA-0 — an empty log is a finding — and MA-6 — a document with no {@code eventLogRecord:} key.
 *
 * <p><b>Why MA-0 exists.</b> Every empty shape returned from {@link ProducerDiagnostics#of} before any
 * check ran, so an empty log raised NOTHING, marked or unmarked. A marker only changed the label from
 * {@code unknown} to {@code complete}. What made an empty file look healthy was the missing warning,
 * not the verdict.
 *
 * <p><b>It is a finding beside an unchanged state</b>, never a seventh stream-end state: the six are a
 * published contract that three store paths and {@code context} agree on.
 *
 * <p><b>MA-6 is separate on purpose.</b> MA-0 keys on zero records only. A document missing the record
 * key is a different fault — it is COUNTED as a record, so a marker over it declares a count including
 * it and the file reads {@code complete} while the document's header and keys are gone.
 */
class EmptyLogAndRecordKeyDiagnosticsTest {

    private static LogRecord record(String event, int nodeLogs) {
        LogRecord.Builder b = LogRecord.builder();
        b.event(event);
        b.logTime(1_767_258_000_000L);
        b.nodeLogsCount(nodeLogs);
        return b.build();
    }

    private static LogIndex indexOf(LogRecord... records) {
        LogIndex idx = new LogIndex();
        for (LogRecord r : records) idx.add(r);
        return idx;
    }

    private static IntFunction<String> texts(String... raw) {
        return i -> i >= 0 && i < raw.length ? raw[i] : null;
    }

    private static ProducerDiagnostics diagnose(LogIndex idx, IntFunction<String> raw) {
        return ProducerDiagnostics.of(idx, raw, List.of(), List.of(), false);
    }

    // ---------------------------------------------------------------- MA-0

    /**
     * MA-0.1 — an index with no records raises the finding. Every empty shape on disk — zero bytes,
     * whitespace only, an empty export, a marker declaring zero, two empty marked segments, a rolled
     * set of empty members — reaches this method identically, as an index of size zero.
     */
    @Test
    void anIndexWithNoRecordsRaisesTheEmptyLogFinding() {
        ProducerDiagnostics d = diagnose(new LogIndex(), texts());
        assertTrue(d.firstWarning().isPresent(),
                "an empty log must raise a finding — this is what was silent");
        assertEquals(ProducerDiagnostics.Kind.EMPTY_LOG, d.firstWarning().orElseThrow().kind());
    }

    /**
     * A NULL index is not an empty log, and MA-0 must not fire on it.
     *
     * <p>Callers that only want the reader's own diagnostics echoed pass {@code null} — the binary
     * conformance suite does exactly that while having parsed a whole record. Firing here would put an
     * "empty file" warning on a file with records in it. D-MA0b keys on {@code size() == 0}, and this
     * is why it says only that.
     */
    @Test
    void aNullIndexIsNotAnEmptyLog() {
        ProducerDiagnostics d = diagnose(null, texts());
        assertTrue(d.isClean(), "no index supplied is not a claim about the file: " + d.messages());
    }

    /**
     * MA-0.1, the other half — the finding is a WARNING, not a {@code COMPLETENESS_NOTE}, and the
     * stream-end state is untouched by it. This method never returns a state; that it cannot is the
     * point of D-MA0a, so the assertion is that the only thing produced is a finding.
     */
    @Test
    void theEmptyLogSignalIsAWarningAndNotAState() {
        ProducerDiagnostics d = diagnose(new LogIndex(), texts());
        assertTrue(d.firstWarning().isPresent(),
                "MA-0.1: it must raise a warning on the status bar (asserted via firstWarning, MA-0.4)");
        assertEquals(1, d.findings().size(), "one finding, and nothing that could become a state");
        assertNotEquals(ProducerDiagnostics.Kind.COMPLETENESS_NOTE, d.findings().get(0).kind(),
                "a COMPLETENESS_NOTE is explicitly NOT raised as a warning, which would hide this");
    }

    /**
     * MA-0.6 — a file whose zero records come from source damage says BOTH, damage first.
     */
    @Test
    void sourceDamageIsStatedBeforeTheEmptyLogFinding() {
        ProducerDiagnostics d = ProducerDiagnostics.of(
                new LogIndex(), texts(), List.of("the tail of the file was cut"), List.of(), false);

        assertEquals(2, d.findings().size(), "both are worth saying");
        assertEquals(ProducerDiagnostics.Kind.SOURCE_DAMAGE, d.findings().get(0).kind(),
                "MA-0.6: damage first — it explains why the file is empty");
        assertEquals(ProducerDiagnostics.Kind.EMPTY_LOG, d.findings().get(1).kind());
    }

    /**
     * MA-0.4 — why the acceptance names {@code firstWarning()}. For a marked rolled set the first
     * finding is a {@code COMPLETENESS_NOTE}, which {@code isWarning()} reads and reports false. A test
     * asserting through {@code isWarning()} would pass for the wrong reason.
     */
    @Test
    void firstWarningSeesPastACompletenessNoteAndIsWarningDoesNot() {
        ProducerDiagnostics d = ProducerDiagnostics.of(
                new LogIndex(), texts(), List.of(), List.of("a rolled set is never reported complete"), true);

        assertFalse(d.isWarning(),
                "isWarning() reads only get(0), which is the note — this is the trap MA-0.4 names");
        assertTrue(d.firstWarning().isPresent(), "firstWarning() sees past it");
        assertEquals(ProducerDiagnostics.Kind.EMPTY_LOG, d.firstWarning().orElseThrow().kind(),
                "and finds the empty-log finding behind the note");
    }

    /** MA-0.3 — records WITHOUT node entries are NO_NODE_LOGS, not EMPTY_LOG. Two cases, not one. */
    @Test
    void recordsWithoutNodeEntriesAreNotAnEmptyLog() {
        LogIndex idx = indexOf(record("Tick", 0), record("Tick", 0), record("Tick", 0),
                record("Tick", 0), record("Tick", 0));
        ProducerDiagnostics d = diagnose(idx,
                texts("eventLogRecord:\n  event: Tick\n", "eventLogRecord:\n  event: Tick\n",
                        "eventLogRecord:\n  event: Tick\n", "eventLogRecord:\n  event: Tick\n",
                        "eventLogRecord:\n  event: Tick\n"));

        assertEquals(ProducerDiagnostics.Kind.NO_NODE_LOGS, d.firstWarning().orElseThrow().kind(),
                "five empty records are NO_NODE_LOGS — MA-0 is zero records only (D-MA0b)");
        assertTrue(d.findings().stream().noneMatch(f -> f.kind() == ProducerDiagnostics.Kind.EMPTY_LOG),
                "and must NOT also claim the file is empty");
    }

    /** MA-0.2, the wording half of D-MA0d — the finding is about the FILE, never the run. */
    @Test
    void theWordingIsAboutTheFileNotTheRun() {
        String message = diagnose(new LogIndex(), texts()).firstWarning().orElseThrow().message();
        assertTrue(message.contains("No records in this file"),
                "must say FILE: a buffered writer holds records in memory while the file is empty");
        assertTrue(message.contains("not that the run produced nothing"),
                "and must say so explicitly, because the file cannot support that claim: " + message);
    }

    /** A healthy log stays clean — the guard against a finding that fires on everything. */
    @Test
    void aHealthyLogRaisesNothing() {
        LogIndex idx = indexOf(record("Tick", 2), record("Tick", 3));
        ProducerDiagnostics d = diagnose(idx,
                texts("eventLogRecord:\n  nodeLogs:\n    - a: { v: 1}\n",
                        "eventLogRecord:\n  nodeLogs:\n    - a: { v: 2}\n"));
        assertTrue(d.isClean(), "a healthy log must raise nothing: " + d.messages());
    }

    // ---------------------------------------------------------------- MA-6

    /**
     * MA-6.1 — a document with no record key is named. This is AFMT-3's output: a per-node level of
     * NONE corrupts the next record into a run-together line with no header and no keys, and the reader
     * counts it as a record.
     */
    @Test
    void aDocumentWithNoRecordKeyIsNamed() {
        LogIndex idx = indexOf(record("Tick", 1), record("?", 0), record("Tick", 1));
        ProducerDiagnostics d = diagnose(idx,
                texts("eventLogRecord:\n  nodeLogs:\n    - a: { v: 1}\n",
                        "mainonPriceEventPriceEvent{symbol=AAPL, price=195.3}195.31200",
                        "eventLogRecord:\n  nodeLogs:\n    - a: { v: 2}\n"));

        assertTrue(d.findings().stream().anyMatch(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY),
                "MA-6.1: the corrupt document must be named, or a marker over it vouches for it");
        String message = d.findings().stream()
                .filter(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY)
                .findFirst().orElseThrow().message();
        assertTrue(message.contains("Record 2"), "it names WHICH record: " + message);
        assertTrue(message.contains("per-node audit level of NONE"),
                "and names the known producer-side cause: " + message);
    }

    /** More than one affected document says so once, with a count, rather than repeating. */
    @Test
    void severalAffectedDocumentsAreReportedOnce() {
        LogIndex idx = indexOf(record("?", 0), record("Tick", 1), record("?", 0));
        ProducerDiagnostics d = diagnose(idx,
                texts("mainonA", "eventLogRecord:\n  nodeLogs:\n    - a: { v: 1}\n", "mainonB"));

        long named = d.findings().stream()
                .filter(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY).count();
        assertEquals(1, named, "one finding, not one per row");
        assertTrue(d.findings().stream()
                        .filter(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY)
                        .findFirst().orElseThrow().message().contains("2 records"),
                "and it says how many");
    }

    /** MA-6 is independent: it fires even when another finding already explains the file. */
    @Test
    void itFiresAlongsideAnotherFinding() {
        LogIndex idx = indexOf(record("?", 0));
        ProducerDiagnostics d = diagnose(idx, texts("mainonRiskCheck"));
        assertTrue(d.findings().stream().anyMatch(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY),
                "a producer fault is worth naming whatever else the file shows: " + d.messages());
    }

    /**
     * MA-6 and V1 — the check is on FRAMING, not on a substring.
     *
     * <p>Review found the hole: a headerless document whose CONTENT merely contains the record key read
     * as a well-formed record, so a marker over it declared a count including it and the file read
     * complete with no finding. A payload changing the verdict is exactly what V1 forbids.
     */
    @Test
    void aHeaderlessDocumentThatMentionsTheKeyInItsContentIsStillNamed() {
        LogIndex idx = indexOf(record("?", 0));
        ProducerDiagnostics d = diagnose(idx,
                texts("mainonRiskCheckPriceEvent{note=see eventLogRecord: below}195.3"));

        assertTrue(d.findings().stream().anyMatch(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY),
                "V1: a payload mentioning the key must not make a corrupt document read as a record");
    }

    /** And a normal record with a leading comment is NOT named — §1 allows comments before a record. */
    @Test
    void aRecordWithALeadingCommentIsNotNamed() {
        LogIndex idx = indexOf(record("Tick", 1));
        ProducerDiagnostics d = diagnose(idx,
                texts("#00:00:00.000 [main] INFO\neventLogRecord:\n  nodeLogs:\n    - a: { v: 1}\n"));

        assertTrue(d.findings().stream().noneMatch(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY),
                "a comment before the record is legal and must not be reported: " + d.messages());
    }

    // ---------------------------------------------------------------- BOM

    private static final String BOM = "\uFEFF";

    /**
     * REGRESSION, caught by review. MA-6 first shipped using {@code trim()}, which keeps U+FEFF, so a
     * healthy UTF-8 file with a byte-order mark was flagged as having no record key on record 1. The
     * substring check it replaced had passed it.
     *
     * <p>It now uses {@code StreamEndMarker.strip} — the one that already handled this — rather than a
     * second notion of whitespace. A duplicated framing rule drifting from the original is a failure
     * this project has made often enough to name.
     */
    @Test
    void aHealthyFileWithAByteOrderMarkIsNotFlagged() {
        LogIndex idx = indexOf(record("Tick", 1));
        ProducerDiagnostics d = diagnose(idx,
                texts(BOM + "eventLogRecord:\n  nodeLogs:\n    - a: { v: 1}\n"));

        assertTrue(d.findings().stream().noneMatch(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY),
                "a BOM is not a missing record key: " + d.messages());
    }

    /** And the check still works THROUGH a BOM: a headerless document is still named. */
    @Test
    void aHeaderlessDocumentBehindAByteOrderMarkIsStillNamed() {
        LogIndex idx = indexOf(record("?", 0));
        ProducerDiagnostics d = diagnose(idx, texts(BOM + "mainonRiskCheck195.3"));

        assertTrue(d.findings().stream().anyMatch(f -> f.kind() == ProducerDiagnostics.Kind.NO_RECORD_KEY),
                "the BOM must not become a way to hide a corrupt document: " + d.messages());
    }
}
