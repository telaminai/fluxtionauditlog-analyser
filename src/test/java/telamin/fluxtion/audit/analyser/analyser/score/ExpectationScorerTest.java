package telamin.fluxtion.audit.analyser.analyser.score;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.parse.RecordParser;
import telamin.fluxtion.audit.analyser.analyser.score.ExpectationScorer.Result;
import telamin.fluxtion.audit.analyser.analyser.score.ExpectationScorer.Snapshot;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One test per guard, each reproducing a comparison defect that ACTUALLY OCCURRED in this project's
 * experiment scoring. Every one of the five erred in the direction of agreeing with its author, so
 * these are regression tests against a specific bias, not hypotheticals.
 */
class ExpectationScorerTest {

    private final ExpectationScorer scorer = new ExpectationScorer();

    /** Build records through the real parser, so the tests exercise the shipped reader path. */
    private List<LogRecord> parse(String... recordTexts) {
        List<LogRecord> out = new ArrayList<>();
        long off = 0;
        for (String t : recordTexts) out.add(RecordParser.parse(t, off++));
        return out;
    }

    private String record(String event, String node, String figure, String value) {
        return """
                eventLogRecord:
                  logTime: 1000
                  event: %s
                  nodeLogs:
                    - %s: { stage: %s, value: %s}
                """.formatted(event, node, figure, value);
    }

    /** A scored event that publishes nothing at all. */
    private String silentRecord(String event) {
        return """
                eventLogRecord:
                  logTime: 1000
                  event: %s
                  nodeLogs:
                """.formatted(event);
    }

    // ---------------------------------------------------------------- G1

    @Test
    void g1_unequalLengthsIsFatal_notARate() {
        // THE DEFECT: 12 expected events zipped against 0 actual printed "12/12 identical",
        // because zip() over an empty list yields nothing and zero mismatches read as agreement.
        List<Snapshot> expected = scorer.snapshots(parse(
                record("Tick", "mid", "marketdata.mid", "100.0"),
                record("Trade", "risk", "risk.exposure", "5.0")));
        List<Snapshot> actual = List.of();

        Result r = scorer.score(expected, actual);

        assertFalse(r.trustworthy(), "a length mismatch must never yield a score");
        assertFalse(r.pass());
        assertTrue(r.fatal().contains("event count differs"), r.fatal());
        assertEquals(0, r.matched(), "matched must not be inflated when untrustworthy");
        assertTrue(r.summary().startsWith("UNTRUSTWORTHY"), r.summary());
    }

    // ---------------------------------------------------------------- G2 / G3

    @Test
    void g2_silentEventIsKept_soLaterEventsStayAligned() {
        // THE DEFECT: dropping a value-less event shifted every later comparison by one and
        // reported the entire tail as wrong.
        List<Snapshot> snaps = scorer.snapshots(parse(
                record("Tick", "mid", "marketdata.mid", "100.0"),
                silentRecord("Config"),
                record("Trade", "risk", "risk.exposure", "5.0")));

        assertEquals(3, snaps.size(), "the silent Config event must still produce a snapshot");
        assertEquals("config", snaps.get(1).event());
    }

    @Test
    void g3_silentEventCarriesStateAtThatMoment_notTheFinalState() {
        // THE DEFECT: the fix for G2 used the file-FINAL state for silent events, back-dating the
        // end state onto every one of them and manufacturing disagreements.
        List<Snapshot> snaps = scorer.snapshots(parse(
                record("Tick", "mid", "marketdata.mid", "100.0"),
                silentRecord("Config"),
                record("Tick", "mid", "marketdata.mid", "103.5")));

        assertEquals(100.0, snaps.get(1).figures().get("marketdata.mid"), 1e-9,
                "the silent event must hold the value as at that point, not the end value");
        assertEquals(103.5, snaps.get(2).figures().get("marketdata.mid"), 1e-9);
    }

    // ---------------------------------------------------------------- G4

    @Test
    void g4_absentFigureIsDistinctFromDifferingFigure() {
        // THE DEFECT: get(key, 0) conflated "recorded a different value" with "recorded nothing".
        List<Snapshot> expected = scorer.snapshots(parse(
                record("Tick", "mid", "marketdata.mid", "100.0")));
        List<Snapshot> actual = scorer.snapshots(parse(
                record("Tick", "other", "some.other.figure", "100.0")));

        Result r = scorer.score(expected, actual);

        assertTrue(r.trustworthy());
        assertFalse(r.pass());
        assertEquals(1, r.differences().size());
        assertNull(r.differences().get(0).actual(), "an unpublished figure must report as absent");
        assertTrue(r.differences().get(0).toString().contains("NEVER PUBLISHED"),
                r.differences().get(0).toString());
    }

    // ---------------------------------------------------------------- G5

    @Test
    void g5_emptyExpectationIsFatal_notAVacuousPass() {
        Result r = scorer.score(List.of(), List.of());
        assertFalse(r.trustworthy(), "two empty logs must not agree");
        assertFalse(r.pass());
        assertTrue(r.fatal().contains("no scored events"), r.fatal());
    }

    // ---------------------------------------------------------------- sensitivity

    @Test
    void identicalLogsPass() {
        var recs = parse(record("Tick", "mid", "marketdata.mid", "100.0"),
                         record("Trade", "risk", "risk.exposure", "5.0"));
        Result r = scorer.score(scorer.snapshots(recs), scorer.snapshots(recs));
        assertTrue(r.pass(), r.summary());
        assertEquals(2, r.matched());
    }

    @Test
    void oneWrongValueIsCaught_andPropagatesByCarryForward() {
        // A single wrong value at event 0 must fail event 0 AND every later event that carries it.
        List<Snapshot> expected = scorer.snapshots(parse(
                record("Tick", "mid", "marketdata.mid", "100.0"),
                record("Trade", "risk", "risk.exposure", "5.0")));
        List<Snapshot> actual = scorer.snapshots(parse(
                record("Tick", "mid", "marketdata.mid", "999.0"),
                record("Trade", "risk", "risk.exposure", "5.0")));

        Result r = scorer.score(expected, actual);
        assertTrue(r.trustworthy());
        assertFalse(r.pass());
        assertEquals(2, r.differences().size(),
                "carry-forward means the bad value is wrong at both events");
        assertEquals(0, r.matched());
    }

    @Test
    void lifecycleAndControlRecordsAreNotScored() {
        List<Snapshot> snaps = scorer.snapshots(parse(
                record("EventLogControlEvent", "x", "ignored", "1.0"),
                record("LifecycleEvent", "x", "ignored", "2.0"),
                record("Tick", "mid", "marketdata.mid", "100.0")));
        assertEquals(1, snaps.size(), "only business events are scored");
        assertEquals("tick", snaps.get(0).event());
    }

    @Test
    void innerClassEventNamesAreSimplified() {
        assertEquals("tick", ExpectationScorer.simpleEventName("com.vendor.contract.Events$Tick"));
        assertEquals("trade", ExpectationScorer.simpleEventName("Events$Trade"));
        assertEquals("tick", ExpectationScorer.simpleEventName("Tick"));
        assertNull(ExpectationScorer.simpleEventName("  "));
    }

    // ---------------------------------------------------------------- natural form

    @Test
    void naturalFluxtionFormIsRead_instanceIdQualifiesTheKey() {
        // Fluxtion's own shape: `- book: { mid: 17.1}`. No stage/value tagging convention.
        var recs = parse("""
                eventLogRecord:
                  logTime: 1000
                  event: Tick
                  nodeLogs:
                    - book: { mid: 17.1, depth: 4}
                """);
        var snaps = scorer.snapshots(recs);
        assertEquals(1, snaps.size());
        assertEquals(17.1, snaps.get(0).figures().get("book.mid"), 1e-9);
        assertEquals(4.0, snaps.get(0).figures().get("book.depth"), 1e-9);
    }

    @Test
    void naturalFormRoundTripsThroughTheShippedReader() throws Exception {
        // End to end through YamlAuditReader + RecordParser, on a committed conformance fixture.
        var path = java.nio.file.Path.of("src/test/resources/conformance/c05-untimed.yaml");
        var records = ScoreCommand.read(path);
        var snaps = scorer.snapshots(records);
        assertEquals(2, snaps.size(), "two Tick events; the LifecycleEvent is not scored");
        assertEquals(1.0, snaps.get(0).figures().get("book.mid"), 1e-9);
        assertEquals(3.0, snaps.get(1).figures().get("book.mid"), 1e-9);

        var self = scorer.score(snaps, snaps);
        assertTrue(self.pass(), self.summary());
    }
}
