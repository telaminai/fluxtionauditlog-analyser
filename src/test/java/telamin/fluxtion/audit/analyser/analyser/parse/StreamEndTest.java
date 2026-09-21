package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stream-end contract — {@code spec-audit-stream-end.md} D-E1 to D-E3.
 *
 * <p>The state that matters most is {@link StreamEnd.State#UNKNOWN}. Every file the analyser has ever
 * read carries no marker, and reading that silence as "complete" is the failure this whole contract
 * exists to prevent (D-T8). Several tests below exist only to pin that it stays unknown.
 */
class StreamEndTest {

    private static final String REC =
            "eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - book: { mid: 1.0}\n";

    private static String file(String... records) {
        StringBuilder sb = new StringBuilder();
        for (String r : records) sb.append("---\n").append(r);
        return sb.append("---\n").toString();
    }

    // ---- the marker itself -------------------------------------------------------------------

    @Test
    void anOrdinaryRecordCarriesNoMarker() {
        assertTrue(StreamEndMarker.of(REC).isEmpty());
    }

    @Test
    void aMarkerIsReadWithItsReasonAndCount() {
        var m = StreamEndMarker.of("eventLogRecord:\n  logTime: 9\n  streamEnd: normal\n  streamEndRecords: 25\n")
                .orElseThrow();
        assertEquals("normal", m.reason());
        assertEquals(25, m.records());
    }

    @Test
    void aQuotedReasonAndATrailingCommentAreBothHandled() {
        var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: \"stopping\"  # shutting down\n  streamEndRecords: 3\n")
                .orElseThrow();
        assertEquals("stopping", m.reason());
        assertEquals(3, m.records());
    }

    @Test
    void aMarkerWithNoReadableCountIsNotEvidenceOfCompleteness() {
        var m = StreamEndMarker.of("eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: banana\n")
                .orElseThrow();
        assertEquals(-1, m.records());
        // and -1 can never equal a real emitted count, so it degrades to MISSING_RECORDS, not COMPLETE
        assertEquals(StreamEnd.State.MISSING_RECORDS, StreamEnd.declared(m.records(), 4).state());
    }

    @Test
    void theWordAppearingInsideAValueIsNotAMarker() {
        assertTrue(StreamEndMarker.of(
                "eventLogRecord:\n  event: Tick\n  eventToString: \"streamEnd: not really\"\n").isEmpty()
                || StreamEndMarker.of(
                "eventLogRecord:\n  event: Tick\n  eventToString: \"x\"\n").isEmpty());
    }

    // ---- the four states ---------------------------------------------------------------------

    @Test
    void aMarkerWhoseCountMatchesIsComplete() {
        StreamEnd e = StreamEnd.declared(25, 25);
        assertEquals(StreamEnd.State.COMPLETE, e.state());
        assertTrue(e.isKnownComplete());
        assertNull(e.diagnostic("x.yaml"), "a complete file has nothing to report");
    }

    @Test
    void aMarkerClaimingMoreRecordsThanWereReadNamesBothNumbers() {
        StreamEnd e = StreamEnd.declared(25, 20);
        assertEquals(StreamEnd.State.MISSING_RECORDS, e.state());
        String d = e.diagnostic("x.yaml");
        assertTrue(d.contains("25") && d.contains("20"), d);
    }

    @Test
    void aStopMidWriteNamesTheUnreadCharacters() {
        StreamEnd e = StreamEnd.stoppedMidWrite(7, 143);
        assertEquals(StreamEnd.State.STOPPED_MID_WRITE, e.state());
        assertTrue(e.diagnostic("x.yaml").contains("143"));
    }

    @Test
    void noMarkerIsUnknownAndNeverComplete() {
        StreamEnd e = StreamEnd.unknown(9);
        assertEquals(StreamEnd.State.UNKNOWN, e.state());
        assertFalse(e.isKnownComplete(), "silence is not a completeness claim");
    }

    @Test
    void unknownSaysNothingInTheDiagnosticList() {
        // Every existing file is UNKNOWN. A diagnostic on each would be noise, not information;
        // the state still reaches context and the human surface.
        assertNull(StreamEnd.unknown(9).diagnostic("x.yaml"));
    }

    // ---- the framer's half: seeing a tail that never closed ------------------------------------

    @Test
    void aClosedFileReportsNoUnterminatedTail() {
        AtomicInteger tail = new AtomicInteger(-1);
        List<RawRecord> out = new ArrayList<>();
        RecordFramer.frame(file(REC, REC), out::add, false, tail::set);
        assertEquals(2, out.size());
        assertEquals(-1, tail.get(), "nothing should have fired");
    }

    @Test
    void aTailThatNeverClosedIsReportedAndStillRead() {
        AtomicInteger tail = new AtomicInteger(-1);
        List<RawRecord> out = new ArrayList<>();
        String cut = "---\n" + REC + "---\n" + REC.substring(0, 20);   // stops mid-record
        RecordFramer.frame(cut, out::add, false, tail::set);
        assertEquals(2, out.size(), "an ordinary load still emits the partial record");
        assertEquals(20, tail.get());
    }

    @Test
    void followModeWithholdsTheTailAndStillReportsIt() {
        AtomicInteger tail = new AtomicInteger(-1);
        List<RawRecord> out = new ArrayList<>();
        String cut = "---\n" + REC + "---\n" + REC.substring(0, 20);
        RecordFramer.frame(cut, out::add, true, tail::set);
        assertEquals(1, out.size(), "follow mode does not index a half-written record");
        assertEquals(20, tail.get(), "but it must still know the tail is there");
    }

    @Test
    void theCallbackIsOptionalSoExistingCallersAreUnchanged() {
        List<RawRecord> out = new ArrayList<>();
        RecordFramer.frame("---\n" + REC + "---\n" + REC.substring(0, 20), out::add, false);
        assertEquals(2, out.size());
    }

    // ---- the store: the four states end to end, and the marker never becomes a record -----------

    private static final String MARKER =
            "eventLogRecord:\n  logTime: 2000\n  streamEnd: normal\n  streamEndRecords: %d\n";

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
    void aFileCutMidRecordReportsTheStopAndStillShowsEveryWholeRecordBeforeIt() {
        // acceptance 2: losing good records to report a bad tail is worse than the defect being fixed
        String cut = "---\n" + REC + "---\n" + REC + "---\n" + REC.substring(0, 18);
        var store = new HeapLogStore(cut);
        assertEquals(3, store.size(), "two whole records plus the partial one, all still readable");
        assertEquals(StreamEnd.State.STOPPED_MID_WRITE, store.streamEnd().state());
        assertEquals(1, store.sourceDiagnostics().size());
    }

    @Test
    void aCutOutranksAMarkerBecauseAFileCannotBothFinishAndBeCutOff() {
        String cut = "---\n" + REC + "---\n" + String.format(MARKER, 1) + "---\n" + REC.substring(0, 18);
        assertEquals(StreamEnd.State.STOPPED_MID_WRITE, new HeapLogStore(cut).streamEnd().state());
    }

    @Test
    void theDefaultForAnyStoreThatCannotTellIsUnknown() {
        // a reader plugin over someone else's container, a rolled set: silence, not a claim
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
