package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stream-end contract — {@code spec-audit-stream-end.md} D-E1 to D-E3.
 *
 * <p>The state that matters most is {@link StreamEnd.State#UNKNOWN}. Every file the analyser has ever
 * read carries no marker, and reading that silence as "complete" is the failure this whole contract
 * exists to prevent (D-T8). Several tests below exist only to pin that it stays unknown — including the
 * one that was got wrong first time: a file whose last record has no closing separator is ORDINARY, and
 * saying otherwise reported every real export as damaged.
 */
class StreamEndTest {

    private static final String REC =
            "eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - book: { mid: 1.0}\n";
    private static final String MARKER = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: %d\n";

    private static String file(String... records) {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append("---\n").append(r);
        return sb.append("---\n").toString();
    }

    // ---- what a marker is, and what it is not --------------------------------------------------

    @Test
    void anOrdinaryRecordCarriesNoMarker() {
        assertTrue(StreamEndMarker.of(REC).isEmpty());
    }

    @Test
    void aMarkerIsReadWithItsReasonAndCount() {
        var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 25\n")
                .orElseThrow();
        assertEquals("normal", m.reason());
        assertEquals(25, m.records());
    }

    @Test
    void aMarkerMayCarryItsOwnLogTimeBecauseFilesAlreadyWrittenThatWayMustStillRead() {
        var m = StreamEndMarker.of("eventLogRecord:\n  logTime: 9\n  streamEnd: normal\n  streamEndRecords: 3\n")
                .orElseThrow();
        assertEquals(3, m.records());
    }

    @Test
    void aQuotedReasonAndATrailingCommentAreBothHandled() {
        var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: \"stopping\"  # shutting down\n  streamEndRecords: 3\n")
                .orElseThrow();
        assertEquals("stopping", m.reason());
        assertEquals(3, m.records());
    }

    /**
     * The defect this allow-list exists for. {@link RecordParser} ignores indentation, so a line inside a
     * multiline value is indistinguishable from a top-level key by shape alone. Searching for the key
     * therefore let a producer's own {@code toString} delete its record from the index — silently, and
     * without malice. Every case below must be a RECORD, not a marker.
     */
    @Test
    void aRecordIsNeverAMarkerBecauseOfItsOwnContent() {
        assertTrue(StreamEndMarker.of("eventLogRecord:\n  event: Order\n  eventToString: |\n"
                + "    Order{\n    streamEnd: normal\n    }\n").isEmpty(), "a multiline toString");
        assertTrue(StreamEndMarker.of("eventLogRecord:\n  event: Tick\n"
                + "  nodeLogs:\n    - book: { note: \"streamEnd: normal\"}\n").isEmpty(), "a node-log value");
        assertTrue(StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 2\n"
                + "  nodeLogs:\n    - book: { mid: 1.0}\n").isEmpty(),
                "a record carrying node logs is evidence, whatever else it says");
        assertTrue(StreamEndMarker.of("eventLogRecord:\n  event: Tick\n  streamEnd: normal\n").isEmpty(),
                "a record that also names an event is a record");
    }

    @Test
    void aMarkerIsRecognisedThroughItsHeaderCommentAndBlankLines() {
        assertTrue(StreamEndMarker.of("#00:00:01.000 [t] INFO L\n\neventLogRecord:\n"
                + "  streamEnd: stopping\n  streamEndRecords: 7\n").isPresent());
    }

    // ---- the counts, including the ones that used to print nonsense ----------------------------

    @Test
    void aMarkerWithNoReadableCountIsUnverifiedRatherThanMissingRecords() {
        var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: banana\n")
                .orElseThrow();
        assertEquals(-1, m.records());
        StreamEnd e = StreamEnd.declared(m.records(), 4);
        assertEquals(StreamEnd.State.UNVERIFIED, e.state(),
                "this reported MISSING_RECORDS and printed 'holds -1 records ... -5 are missing'");
        assertFalse(e.isKnownComplete(), "an unbacked claim is still not evidence");
    }

    @Test
    void anOverflowingCountIsNoCountAtAll() {
        var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n"
                + "  streamEndRecords: 99999999999999999999\n").orElseThrow();
        assertEquals(-1, m.records(), "Long.parseLong overflows; -1 means no readable count");
        assertEquals(StreamEnd.State.UNVERIFIED, StreamEnd.declared(m.records(), 26).state());
    }

    @Test
    void aMarkerWhoseCountMatchesIsComplete() {
        StreamEnd e = StreamEnd.declared(25, 25);
        assertEquals(StreamEnd.State.COMPLETE, e.state());
        assertTrue(e.isKnownComplete());
        assertNull(e.diagnostic("x.yaml"), "a complete file has nothing to report");
    }

    @Test
    void aMarkerClaimingMoreRecordsThanWereReadNamesBothNumbersAndAPositiveGap() {
        StreamEnd e = StreamEnd.declared(25, 20);
        assertEquals(StreamEnd.State.MISSING_RECORDS, e.state());
        String d = e.diagnostic("x.yaml");
        assertTrue(d.contains("25") && d.contains("20") && d.contains("5 are missing"), d);
        assertFalse(d.contains("-"), "a gap is never negative: " + d);
    }

    @Test
    void aMarkerClaimingFewerRecordsThanPrecedeItIsItsOwnState() {
        StreamEnd e = StreamEnd.declared(20, 25);
        assertEquals(StreamEnd.State.MORE_THAN_DECLARED, e.state(),
                "this reported MISSING_RECORDS and printed '-5 are missing'");
        assertTrue(e.diagnostic("x.yaml").contains("5 more"), e.diagnostic("x.yaml"));
    }

    @Test
    void noMarkerIsUnknownAndNeverComplete() {
        StreamEnd e = StreamEnd.unknown(9);
        assertEquals(StreamEnd.State.UNKNOWN, e.state());
        assertFalse(e.isKnownComplete(), "silence is not a completeness claim");
        assertNull(e.diagnostic("x.yaml"), "every existing file is here; a warning on each would be noise");
    }

    // ---- framing: a missing trailing separator is not a defect ----------------------------------

    @Test
    void aFileEndingWithoutASeparatorIsWholeAndOrdinary() {
        // the shape Mongoose's export writes: "\n---\n" BETWEEN records, nothing after the last
        String export = REC.strip() + "\n---\n" + REC.strip() + "\n---\n" + REC.strip();
        var store = new HeapLogStore(export);
        assertEquals(3, store.size(), "every record is read");
        assertEquals(StreamEnd.State.UNKNOWN, store.streamEnd().state(),
                "this reported STOPPED_MID_WRITE, which made every real export look damaged");
        assertTrue(store.sourceDiagnostics().isEmpty(), () -> store.sourceDiagnostics().toString());
    }

    @Test
    void followModeWithholdsAnUnclosedTailBecauseTheRestIsStillComing() {
        List<RawRecord> out = new ArrayList<>();
        String growing = "---\n" + REC + "---\n" + REC.substring(0, 20);
        RecordFramer.frame(growing, out::add, true);
        assertEquals(1, out.size(), "a record still being written isn't indexed until it is complete");

        out.clear();
        RecordFramer.frame(growing, out::add, false);
        assertEquals(2, out.size(), "an ordinary load reads it: the file may simply end there");
    }

    // ---- the states end to end, through the store ----------------------------------------------

    @Test
    void anOrdinaryFileWithNoMarkerLoadsUnknownAndKeepsEveryRecord() {
        var store = new HeapLogStore(file(REC, REC, REC));
        assertEquals(3, store.size());
        assertEquals(StreamEnd.State.UNKNOWN, store.streamEnd().state());
        assertTrue(store.sourceDiagnostics().isEmpty(), "silence is the ordinary case, not a warning");
    }

    @Test
    void aMatchingMarkerIsCompleteAndIsNotItselfARecord() {
        var store = new HeapLogStore(file(REC, REC, String.format(MARKER, 2)));
        assertEquals(2, store.size(), "the marker must not be indexed (D-E4)");
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state());
        assertTrue(store.sourceDiagnostics().isEmpty());
    }

    @Test
    void aMarkerClaimingMoreThanTheFileHoldsReportsTheGap() {
        var store = new HeapLogStore(file(REC, REC, String.format(MARKER, 9)));
        assertEquals(2, store.size());
        assertEquals(StreamEnd.State.MISSING_RECORDS, store.streamEnd().state());
        assertEquals(1, store.sourceDiagnostics().size());
        assertTrue(store.sourceDiagnostics().get(0).contains("9"));
    }

    @Test
    void aRecordThatMerelyMentionsTheKeyIsCountedAndShown() {
        String lookalike = "eventLogRecord:\n  logTime: 1001\n  event: Order\n"
                + "  eventToString: |\n    Order{\n    streamEnd: normal\n    }\n"
                + "  nodeLogs:\n    - book: { mid: 2.0}\n";
        var store = new HeapLogStore(file(REC, lookalike, String.format(MARKER, 2)));
        assertEquals(2, store.size(), "the lookalike is evidence and was silently dropped before");
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state());
        assertEquals("Order", store.record(1).event());
    }

    // ---- segments: a marker counts the records since the previous one ---------------------------

    @Test
    void twoWholeRunsAppendedIntoOneFileAreComplete() {
        // Mongoose's Chronicle export is cumulative across boots, so this shape is real. A whole-file
        // count would read the second marker as claiming 2 in a 4-record file and report 2 missing.
        String run = file(REC, REC, String.format(MARKER, 2));
        var store = new HeapLogStore(run + run);
        assertEquals(4, store.size());
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state(),
                "each marker covers the records since the previous one");
    }

    @Test
    void aRunFollowedByAnUnfinishedOneIsUnknown() {
        String run = file(REC, REC, String.format(MARKER, 2));
        var store = new HeapLogStore(run + file(REC));
        assertEquals(3, store.size());
        assertEquals(StreamEnd.State.UNKNOWN, store.streamEnd().state(),
                "a declared end that is not the end says nothing about what followed it");
    }

    @Test
    void oneBadSegmentAmongGoodOnesIsTheVerdict() {
        String good = file(REC, REC, String.format(MARKER, 2));
        String short_ = file(REC, String.format(MARKER, 5));
        var store = new HeapLogStore(good + short_);
        assertEquals(StreamEnd.State.MISSING_RECORDS, store.streamEnd().state());
        assertEquals(5, store.streamEnd().declaredRecords(), "the numbers are the offending segment's");
        assertEquals(1, store.streamEnd().emittedRecords());
    }

    /**
     * Re-review finding 3. The numbers belong to the RUN, and saying them as though they were the file's
     * tells a reader the log holds fewer records than it does.
     */
    @Test
    void aMultiRunDiagnosticNamesTheRunRatherThanSpeakingForTheFile() {
        String bad = file(REC, REC, String.format(MARKER, 3));     // declares 3 over 2
        String good = file(REC, REC, String.format(MARKER, 2));
        var store = new HeapLogStore(bad + good);
        assertEquals(4, store.size());
        String d = store.sourceDiagnostics().get(0);
        assertTrue(d.contains("run 1"), () -> "the run must be named: " + d);
        assertTrue(d.contains("records 0 to 1"), () -> "and the records it covers: " + d);
        assertTrue(d.contains("of 4 in the file"),
                () -> "this said 'holds 3 records and 2 were read' about a 4-record file: " + d);

        var seg = store.streamEnd().segment();
        assertNotNull(seg);
        assertEquals(1, seg.ordinal());
        assertEquals(4, seg.fileRecords());
    }

    /**
     * Re-review finding 3. A marker immediately after another closes a run holding nothing, and
     * {@code first + emitted - 1} then puts the last record one before the first: "records 25 to 24".
     */
    @Test
    void aRunWithNoRecordsIsNamedAsEmptyRatherThanGivenABackwardsRange() {
        String body = file(REC, REC, String.format(MARKER, 2));
        var store = new HeapLogStore(body + "---\n" + String.format(MARKER, 3));
        assertEquals(2, store.size());
        assertEquals(StreamEnd.State.MISSING_RECORDS, store.streamEnd().state());
        String d = store.sourceDiagnostics().get(0);
        assertTrue(d.contains("holds no records at all"), () -> d);
        assertFalse(d.matches("(?s).*records 2 to 1.*"), () -> "a backwards range: " + d);
        assertTrue(store.streamEnd().segment().isEmpty());
    }

    @Test
    void aSingleRunFileNamesNoRunBecauseTheFileIsTheRun() {
        var store = new HeapLogStore(file(REC, REC, String.format(MARKER, 9)));
        assertNull(store.streamEnd().segment(), "'run 1 of' is noise when there is only one run");
        String d = store.sourceDiagnostics().get(0);
        assertFalse(d.contains("run 1"), d);
        assertTrue(d.startsWith("this log declares 9"), d);
    }

    // ---- a byte-order mark -----------------------------------------------------------------------

    /**
     * Re-review finding 4. {@code String.strip()} treats U+FEFF as a character rather than whitespace, so
     * a BOM'd file's FIRST record failed the `eventLogRecord:` opener check and a leading marker was
     * indexed as an ordinary record — the file then reported one record more than it declared.
     */
    @Test
    void aByteOrderMarkDoesNotTurnALeadingMarkerIntoARecord() {
        String bom = "﻿";
        var store = new HeapLogStore("---\n" + bom + "eventLogRecord:\n  streamEnd: normal\n"
                + "  streamEndRecords: 0\n" + file(REC, REC, String.format(MARKER, 2)));
        assertEquals(2, store.size(), "the BOM'd marker was counted as a third record");
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state(),
                "and the file then reported more records than it declared");
    }

    // ---- the record type's own invariant (re-review M8) --------------------------------------------

    /**
     * The negative-count clamp is an equivalent mutant against {@link StreamEnd#declared}, which already
     * treats any negative as unverified. It is kept for the invariant on this accessor, so the invariant
     * is what gets asserted — an unguarded line that happens to be harmless is still unguarded.
     */
    @Test
    void anyUnusableCountReadsBackAsExactlyMinusOne() {
        for (String v : new String[]{"banana", "-5", "-1", "99999999999999999999", "", "  "}) {
            var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: " + v + "\n");
            assertTrue(m.isPresent(), "an unusable count does not stop it being a marker: " + v);
            assertEquals(-1, m.get().records(), "records() must normalise '" + v + "' to -1");
        }
    }

    // ---- all three stores must agree about the same bytes ---------------------------------------

    /**
     * The divergence review found: the heap framer could see an unterminated tail and the other two could
     * not, so the same file reported differently depending only on whether it opened small or large.
     * The signal is gone; this pins that no new one creeps back in.
     */
    @Test
    void theHeapAndMappedStoresAgreeAboutEveryShape(@TempDir Path dir) throws IOException {
        record Shape(String name, String text) {}
        List<Shape> shapes = List.of(
                new Shape("closed, no marker", file(REC, REC)),
                new Shape("export layout", REC.strip() + "\n---\n" + REC.strip()),
                new Shape("marker matching", file(REC, REC, String.format(MARKER, 2))),
                new Shape("marker short", file(REC, REC, String.format(MARKER, 9))),
                new Shape("marker over", file(REC, REC, String.format(MARKER, 1))),
                new Shape("marker uncounted", file(REC, "eventLogRecord:\n  streamEnd: normal\n")),
                new Shape("two runs", file(REC, String.format(MARKER, 1)) + file(REC, String.format(MARKER, 1))),
                new Shape("run then tail", file(REC, String.format(MARKER, 1)) + file(REC)));
        for (Shape s : shapes) {
            Path p = dir.resolve(s.name().replace(' ', '-').replace(',', '_') + ".yaml");
            Files.writeString(p, s.text(), StandardCharsets.UTF_8);
            try (LogStore heap = HeapLogStore.fromFile(p); LogStore mapped = new MappedLogStore(p)) {
                assertEquals(heap.size(), mapped.size(), () -> "record count differs for " + s.name());
                assertEquals(heap.streamEnd().state(), mapped.streamEnd().state(),
                        () -> "state differs for " + s.name());
                assertEquals(heap.sourceDiagnostics(), mapped.sourceDiagnostics(),
                        () -> "diagnostics differ for " + s.name());
            }
        }
    }

    // ---- follow mode ----------------------------------------------------------------------------

    /**
     * Acceptance 8. Review found both halves broken: {@code appendFrom} framed without a tracker, so an
     * appended marker was indexed AS A RECORD and the state never moved off its load-time value.
     */
    @Test
    void followSeesTheMarkerArriveAndSwitchesTheFileToComplete(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("growing.yaml");
        Files.writeString(p, file(REC, REC), StandardCharsets.UTF_8);
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(2, store.size());
        assertEquals(StreamEnd.State.UNKNOWN, store.streamEnd().state());

        Files.writeString(p, file(REC, REC, String.format(MARKER, 2)), StandardCharsets.UTF_8);
        int added = store.appendFrom(p);

        assertEquals(0, added, "the marker is not a record, so nothing was added to the index");
        assertEquals(2, store.size(), "the marker leaked into the index as a third row");
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state(),
                "the state stayed UNKNOWN for ever, whatever arrived");
        for (int i = 0; i < store.size(); i++) {
            assertFalse(store.rawText(i).contains("streamEnd"), "row " + i + " is the marker");
        }
    }

    @Test
    void followKeepsIndexingOrdinaryRecordsAfterAMarkerHasBeenSeen(@TempDir Path dir) throws IOException {
        Path p = dir.resolve("restarted.yaml");
        Files.writeString(p, file(REC, String.format(MARKER, 1)), StandardCharsets.UTF_8);
        HeapLogStore store = HeapLogStore.fromFile(p);
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state());

        Files.writeString(p, file(REC, String.format(MARKER, 1)) + file(REC), StandardCharsets.UTF_8);
        assertEquals(1, store.appendFrom(p), "the next run's first record is an ordinary record");
        assertEquals(2, store.size());
        assertEquals(StreamEnd.State.UNKNOWN, store.streamEnd().state(),
                "a file that carried on past its declared end is no longer a complete file");
    }

    // ---- the default for anything that cannot tell -----------------------------------------------

    @Test
    void theDefaultForAnyStoreThatCannotTellIsUnknown() {
        // a reader plugin over someone else's container: silence, not a claim
        LogStore cannotTell = new LogStore() {
            public int size() { return 4; }
            public LogIndex index() { return null; }
            public LogRecord record(int row) { return null; }
            public String rawText(int row) { return ""; }
            public Long minLogTime() { return null; }
            public Long maxLogTime() { return null; }
        };
        assertEquals(StreamEnd.State.UNKNOWN, cannotTell.streamEnd().state());
        assertEquals(4, cannotTell.streamEnd().emittedRecords());
    }

    /**
     * The recommended shape: a marker with no logTime at all. §2 keeps an untimed record but leaves it
     * off the timeline, which is why omitting it makes the marker invisible to an older reader's time
     * range — measured against released 1.16.0. The new reader must accept it just the same.
     */
    @Test
    void anUntimedMarkerIsStillAMarker() {
        var store = new HeapLogStore(file(REC, REC,
                "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 2\n"));
        assertEquals(2, store.size());
        assertEquals(StreamEnd.State.COMPLETE, store.streamEnd().state());
    }
}
