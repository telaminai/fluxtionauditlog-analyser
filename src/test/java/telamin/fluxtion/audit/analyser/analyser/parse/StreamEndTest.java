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

    /** The whole point of the digit rule: an exotic digit must not buy a completeness claim. */
    @Test
    void aFullwidthDigitDoesNotMakeAFileComplete() {
        var store = new HeapLogStore(file(REC, REC, REC,
                "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: \uFF13\n"));
        assertEquals(3, store.size());
        assertEquals(StreamEnd.State.UNVERIFIED, store.streamEnd().state(),
                "this read as 3 and reported COMPLETE - a claim a stricter reader would not make");
        assertFalse(store.streamEnd().isKnownComplete());
    }

    /**
     * §1a's whitespace rule — space, tab, CR, LF, and nothing else. Round six found it pinned NOWHERE,
     * and the README claiming otherwise; the only reason a non-breaking space behaved was that Java's
     * {@code strip()} happens not to treat it as whitespace. Three different notions of whitespace were
     * in use, and five of the resulting disagreements claimed completeness a conforming reader would not.
     */
    @Test
    void onlyAsciiWhitespaceSurroundsAKeyOrAValue() {
        for (String ws : new String[]{"\u00a0", "\f", "\u000b", "\u2003", "\u3000"}) {
            assertTrue(StreamEndMarker.of("eventLogRecord:\n" + ws
                            + "streamEnd: normal\n  streamEndRecords: 1\n").isEmpty(),
                    "U+" + Integer.toHexString(ws.charAt(0)) + " before a key is content, not whitespace");
            var counted = StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n"
                    + "  streamEndRecords: 3" + ws + "\n");
            assertTrue(counted.isPresent());
            assertEquals(-1, counted.get().records(),
                    "U+" + Integer.toHexString(ws.charAt(0)) + " in a count is not a digit and not space");
        }
        for (String ws : new String[]{" ", "\t"}) {
            assertTrue(StreamEndMarker.of("eventLogRecord:\n" + ws
                    + "streamEnd: normal\n  streamEndRecords: 1\n").isPresent(), "ASCII space is space");
        }
    }

    /** §1a: the value is stripped AFTER extraction, quoted or not, so a quoted count may be padded. */
    @Test
    void aQuotedCountIsStrippedLikeAnyOther() {
        for (String v : new String[]{"\" 3\"", "\"3 \"", "\"\t3\"", "' 3'"}) {
            var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: "
                    + v + "\n").orElseThrow();
            assertEquals(3, m.records(), "the published text strips before applying the digit rule: " + v);
        }
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
        assertTrue(d.contains("25 records") && d.contains("20 were read")
                && d.contains("5 records are missing"), d);
        assertFalse(d.contains("-"), "a gap is never negative: " + d);
    }

    @Test
    void aMarkerClaimingFewerRecordsThanPrecedeItIsItsOwnState() {
        StreamEnd e = StreamEnd.declared(20, 25);
        assertEquals(StreamEnd.State.MORE_THAN_DECLARED, e.state(),
                "this reported MISSING_RECORDS and printed '-5 are missing'");
        assertTrue(e.diagnostic("x.yaml").contains("5 records more"), e.diagnostic("x.yaml"));
    }

    /**
     * Singular and plural, on the surface a person reads. Round five found the round-three wording fix
     * untested: making the helper always add "s" left the whole suite green.
     */
    @Test
    void oneOfAnythingReadsAsOneNotOnes() {
        String one = StreamEnd.declared(2, 1).diagnostic("x.yaml");
        assertTrue(one.contains("2 records") && one.contains("1 was read"), one);
        assertTrue(one.contains("1 record is missing"), () -> "not '1 records are missing': " + one);
        String many = StreamEnd.declared(5, 2).diagnostic("x.yaml");
        assertTrue(many.contains("5 records") && many.contains("2 were read"), many);
        assertTrue(many.contains("3 records are missing"), many);
        String over = StreamEnd.declared(2, 3).diagnostic("x.yaml");
        assertTrue(over.contains("1 record more"), () -> "not '1 records more': " + over);
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
        assertEquals(1, store.completenessDiagnostics().size());
        assertTrue(store.completenessDiagnostics().get(0).contains("9"));
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
        String d = store.completenessDiagnostics().get(0);
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
        // both markers terminated: §1a says an unterminated final item is not a marker at all
        String body = file(REC, REC, String.format(MARKER, 2));
        var store = new HeapLogStore(body + String.format(MARKER, 3) + "---\n");
        assertEquals(2, store.size());
        assertEquals(StreamEnd.State.MISSING_RECORDS, store.streamEnd().state());
        String d = store.completenessDiagnostics().get(0);
        assertTrue(d.contains("holds no records at all"), () -> d);
        assertFalse(d.matches("(?s).*records 2 to 1.*"), () -> "a backwards range: " + d);
        assertTrue(store.streamEnd().segment().isEmpty());
    }

    /**
     * Round six S-5: the "and N other runs" clause was dropped by a mutation and nothing failed. Every
     * failing run reaches `context`, but a person reading the sentence must be told there are more.
     */
    @Test
    void theSentenceSaysHowManyOtherRunsAlsoFailed() {
        String bad1 = file(REC, REC, REC, String.format(MARKER, 5));
        String bad2 = file(REC, REC, String.format(MARKER, 9));
        var store = new HeapLogStore(bad1 + bad2);
        assertEquals(2, store.streamEnd().runs().size());
        String d = store.completenessDiagnostics().get(0);
        assertTrue(d.contains("1 other run"), () -> "a reader must know there are more: " + d);
        assertTrue(d.contains("`context` lists them"), () -> d);
    }

    @Test
    void aSingleRunFileNamesNoRunBecauseTheFileIsTheRun() {
        var store = new HeapLogStore(file(REC, REC, String.format(MARKER, 9)));
        assertNull(store.streamEnd().segment(), "'run 1 of' is noise when there is only one run");
        String d = store.completenessDiagnostics().get(0);
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
        // §1a: ASCII digits with an optional sign, and nothing else, AFTER the value is stripped — so
        // `streamEndRecords:   3` is the count 3 and agrees with a Python reader's int(" 3"). The Unicode
        // entries are the ones
        // that mattered: Long.parseLong accepts any Unicode decimal digit, so a fullwidth "3" made a
        // three-record file report COMPLETE where a reader following the prose said unverified. That is
        // the unsafe direction, and it was found by implementing §1a from its own text.
        for (String v : new String[]{"banana", "-5", "-1", "99999999999999999999", "", "  ",
                "\uFF13", "\u0663", "3_0"}) {
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
