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

    /** Two real records, then a marker. Every count below is therefore 2, never 3. */
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
     * A marker that ALSO carries node logs must still not plot.
     *
     * <p>The obvious version of this test — a plain marker produces no series point — passes whether or
     * not the filter exists, because a marker has no node logs and a value series can only plot keys it
     * finds. Mutating the filter proved it: that assertion stayed green while six others went red. So it
     * guarded nothing and is replaced by this, where the marker carries the very key being plotted. A
     * sloppy or hostile writer producing such a record is exactly the case where "clean by construction"
     * stops being true, and it is the only series assertion here that fails when the filter is removed.
     */
    @Test
    void aMarkerCarryingTheChartedKeyStillNeverPlots() {
        String hostile = LOG.replace("""
                  logTime: 9999
                  streamEnd: normal""", """
                  logTime: 9999
                  streamEnd: normal
                  nodeLogs:
                    - book: { mid: 999.0}""");
        HeapLogStore s = new HeapLogStore(hostile);
        assertEquals(2, s.size(), "a marker is not a record however much it carries");
        Series series = SeriesExtractor.extract(s, new FilterState(), new GraphKey("book", "mid"));
        assertEquals(2, series.size(), "the marker's value must not become a point");
        for (int i = 0; i < series.size(); i++) {
            assertTrue(series.y(i) < 100.0, "999.0 came from the marker and must not be plotted");
        }
        assertTrue(series.maxX() <= 1001, "nor may its logTime stretch the axis");
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
