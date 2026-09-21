package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.llm.ReadService;
import telamin.fluxtion.audit.analyser.analyser.summary.SummaryBuilder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-E4 — the stream-end marker never appears in any surface that counts, quotes or plots records.
 *
 * <p><b>Why this class exists when the marker never enters the index.</b> It is clean "by construction",
 * and construction is exactly what a later change alters. The tracker's filter is three lines in one
 * place; deleting it compiles, and every assertion here is written so that deleting it FAILS. That is the
 * difference between a property and a coincidence.
 *
 * <p>Each test states the number it expects rather than comparing two runs, so a regression reads as
 * "the marker turned up in `read`" rather than "two numbers differ".
 */
class StreamEndMarkerNeverLeaksTest {

    /**
     * Two real records, then a marker. Every count below is therefore 2, never 3.
     *
     * <p>The marker keeps a {@code logTime} later than either record, which §1a says a writer SHOULD not
     * write. That is deliberate here: it is the one field whose leak is measurable downstream, so a
     * marker that cannot reach the timeline has to be proved against the worst-behaved legal marker
     * rather than the recommended one.
     */
    private static final String LOG = """
            ---
            eventLogRecord:
              logTime: 1000
              event: Tick
              nodeLogs:
                - book: { mid: 17.1}
            ---
            eventLogRecord:
              logTime: 1001
              event: Tick
              nodeLogs:
                - book: { mid: 17.3}
            ---
            eventLogRecord:
              logTime: 9999
              streamEnd: normal
              streamEndRecords: 2
            ---
            """;

    private static HeapLogStore store() {
        return new HeapLogStore(LOG);
    }

    @Test
    void theStoreHoldsTwoRecordsAndKnowsTheFileIsComplete() {
        HeapLogStore s = store();
        assertEquals(2, s.size(), "the marker is not a record");
        assertEquals(StreamEnd.State.COMPLETE, s.streamEnd().state());
    }

    // ---- read -------------------------------------------------------------------------------

    @Test
    void readNeverReturnsTheMarkerAndItsTotalCountsOnlyRecords() {
        HeapLogStore s = store();
        Map<String, Object> out = ReadService.read(s.index().snapshot(), Map.of("recordIndex", 0, "count", 25),
                s::rawText, s::record);
        assertEquals(2, out.get("total"), "`total` is what an agent trusts as the record count");
        @SuppressWarnings("unchecked")
        List<Object> records = (List<Object>) out.get("records");
        assertEquals(2, records.size());
        assertFalse(records.toString().contains("streamEnd"),
                "the marker's text must never be quoted back to a caller");
    }

    // ---- series -----------------------------------------------------------------------------

    /**
     * The marker contributes no point, and the record that merely MENTIONS it contributes one.
     *
     * <p><b>Why the obvious assertion is not enough, twice over.</b> "A plain marker produces no series
     * point" passes whether or not the filter exists, because a marker has no node logs and a value
     * series can only plot keys it finds — mutating the filter proved it, that assertion stayed green
     * while six others went red. An earlier attempt fixed this by giving the marker the charted key, but
     * recognition has since been tightened: a record carrying node logs is a RECORD, so that case can no
     * longer exist. What is load-bearing now is the other direction. A lookalike record must still plot,
     * because the version of recognition that would have dropped it from the series also dropped it from
     * the index — the series was simply where the loss became visible as a missing point.
     */
    @Test
    void theMarkerPlotsNothingAndALookalikeRecordStillPlots() {
        HeapLogStore plain = store();
        Series clean = SeriesExtractor.extract(plain, new FilterState(), new GraphKey("book", "mid"));
        assertEquals(2, clean.size(), "two records, two points; the marker is neither");
        assertTrue(clean.maxX() <= 1001, "nor may the marker's logTime stretch the axis");

        // Built by concatenation, not by replacing into LOG. The first version of this test used a text
        // block as the search argument, whose incidental indentation is stripped while LOG's is not, so
        // the replace matched nothing and the test asserted twice over the unmodified log. Mutating the
        // allow-list away is what showed it: this test stayed green while three others went red.
        String withLookalike = "---\n"
                + "eventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - book: { mid: 17.1}\n"
                + "---\n"
                + "eventLogRecord:\n  logTime: 1001\n  event: Shutdown\n"
                + "  eventToString: |\n    Shutdown{\n    streamEnd: normal\n    }\n"
                + "  nodeLogs:\n    - book: { mid: 17.3}\n"
                + "---\n"
                + "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 2\n"
                + "---\n";
        assertTrue(withLookalike.contains("event: Shutdown"), "the lookalike must actually be in the log");
        HeapLogStore s = new HeapLogStore(withLookalike);
        assertEquals(2, s.size(), "the lookalike is a record: a toString must not delete its own point");
        Series series = SeriesExtractor.extract(s, new FilterState(), new GraphKey("book", "mid"));
        assertEquals(2, series.size(), "both points are plotted");
        assertEquals(StreamEnd.State.COMPLETE, s.streamEnd().state(), "and the real marker still counts");
    }

    // ---- summaries, which is what the report tables reduce over -------------------------------

    @Test
    void aSummaryCountsTwoRecordsAndKnowsNothingOfTheMarker() {
        HeapLogStore s = store();
        List<telamin.fluxtion.audit.analyser.analyser.summary.SummaryRow> rows =
                SummaryBuilder.build(s.index(), new FilterState());
        long total = rows.stream().mapToLong(
                telamin.fluxtion.audit.analyser.analyser.summary.SummaryRow::count).sum();
        assertEquals(2, total, "a report's counts come from here; the marker must not be among them");
        assertFalse(rows.toString().contains("streamEnd"));
    }

    // ---- the timeline, which coverage and every filter depend on ------------------------------

    @Test
    void theTimeRangeIgnoresTheMarkersOwnLogTime() {
        HeapLogStore s = store();
        assertEquals(Long.valueOf(1000), s.minLogTime());
        assertEquals(Long.valueOf(1001), s.maxLogTime(),
                "a marker written long after the last record must not widen the timeline");
    }

    /**
     * Coverage subtracts what was logged from what was declared, so a marker reaching the index would
     * add a record that logged nothing and quietly change the denominator's input. It cannot reach it,
     * and this pins the reason: every record the index holds carries real node logs.
     */
    @Test
    void everyIndexedRecordHasNodeLogsSoCoverageSeesNoEmptyPhantom() {
        HeapLogStore s = store();
        for (int i = 0; i < s.size(); i++) {
            assertFalse(s.record(i).nodeLogs().isEmpty(),
                    "row " + i + " has no node logs — a marker has leaked into the index");
        }
    }

    /** The raw text of every indexed row is a real record, so nothing downstream can re-parse a marker. */
    @Test
    void noIndexedRowsRawTextCarriesTheMarkerKey() {
        HeapLogStore s = store();
        for (int i = 0; i < s.size(); i++) {
            assertFalse(s.rawText(i).contains("streamEnd"), "row " + i + " is the marker");
        }
    }
}
