package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M32.2 — markers ride the same record walk as every series: key-triple and condition sources,
 * series-pinned y, payload as display cargo with a recordIndex signpost, the M2 dangling rule, and
 * D-M3's density aggregation as plain data.
 */
class MarkerExtractorTest {

    /** Fills at 2000 (buy 17.2, ORD-1) and 4000 (buy 17.4, ORD-2); mid quotes at every second. */
    private static final HeapLogStore STORE = new HeapLogStore("""
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 1000
              event: Quote
              nodeLogs:
                - book: { mid: 17.1}
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 2000
              event: Fill
              nodeLogs:
                - book: { mid: 17.2}
                - fills: { fillPrice: 17.2, clOrdId: ORD-1}
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 3000
              event: Quote
              nodeLogs:
                - book: { mid: 17.3}
            ---
            #t [t] INFO L
            eventLogRecord:
              logTime: 4000
              event: Fill
              nodeLogs:
                - book: { mid: 17.4}
                - fills: { fillPrice: 17.4, clOrdId: ORD-2}
            ---
            """);

    private static MarkerSeries extract(GraphSpec.MarkerSpec spec) {
        return MarkerExtractor.extract(STORE, new FilterState(), spec, label -> null);
    }

    @Test
    void keyTripleSource_theMotivatingChart() {
        // "plot buys on the price graph with the client order id"
        MarkerSeries m = extract(new GraphSpec.MarkerSpec(
                "buys", "triangleUp", "fills.fillPrice", "fills.fillPrice", "fills.clOrdId"));
        assertEquals(2, m.points().size());
        var p = m.points().get(0);
        assertEquals(2000L, p.time());
        assertEquals(17.2, p.y(), 1e-9);
        assertEquals("ORD-1", p.payload(), "the order id rides as display cargo");
        assertEquals(1, p.recordIndex(), "the marker is a signpost to the record (D-M2)");
        assertNull(m.note());
    }

    @Test
    void conditionSourceFiresWhereTruthy() {
        MarkerSeries m = extract(new GraphSpec.MarkerSpec(
                "high mid", "diamond", "book.mid > 17.25", "book.mid", null));
        assertEquals(2, m.points().size(), "17.3 and 17.4 breach");
        assertEquals(3000L, m.points().get(0).time());
    }

    @Test
    void seriesPinnedY_ridesThePinnedSeriesValue() {
        Series mid = new Series("mid price");
        mid.add(1000, 17.1);
        mid.add(3000, 17.3);
        MarkerSeries m = MarkerExtractor.extract(STORE, new FilterState(),
                new GraphSpec.MarkerSpec("fills", "circle", "fills.fillPrice", "series:mid price",
                        "fills.clOrdId"),
                label -> "mid price".equals(label) ? mid : null);
        assertEquals(2, m.points().size());
        assertEquals(17.1, m.points().get(0).y(), 1e-9, "at t=2000 the pinned series' last value is 17.1");
        assertEquals(17.3, m.points().get(1).y(), 1e-9);
        assertEquals("mid price", m.riddenSeries(),
                "a marker that rides a series must ride its SCALE too — the chart resolves the axis (D12)");
    }

    @Test
    void onlySeriesPinnedMarkersDeclareARiddenSeries() {
        // a key/expr y has no declared axis — it maps to the LEFT scale, and says so by carrying no pin
        MarkerSeries keyed = extract(new GraphSpec.MarkerSpec(
                "buys", "triangleUp", "fills.fillPrice", "fills.fillPrice", null));
        assertNull(keyed.riddenSeries());
        MarkerSeries rug = extract(new GraphSpec.MarkerSpec(
                "fills", "x", "fills.fillPrice", "axis", null));
        assertNull(rug.riddenSeries());
    }

    @Test
    void aDanglingSeriesPinDegradesLoudly() {
        MarkerSeries m = extract(new GraphSpec.MarkerSpec(
                "fills", "circle", "fills.fillPrice", "series:gone", null));
        assertTrue(m.points().isEmpty());
        assertTrue(m.note().contains("'gone'"), "the note names the missing label (M2): " + m.note());
        assertTrue(m.note().contains("not on this graph"), m.note());
    }

    @Test
    void axisLaneMarkersCarryNoY() {
        MarkerSeries m = extract(new GraphSpec.MarkerSpec(
                "fills", "x", "fills.fillPrice", "axis", "fills.clOrdId"));
        assertEquals(2, m.points().size());
        assertTrue(Double.isNaN(m.points().get(0).y()), "the rug lane needs no y (D-M5)");
    }

    @Test
    void densityAggregationIsDataNotPaint() {
        List<MarkerSeries.MarkerPoint> pts = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) pts.add(new MarkerSeries.MarkerPoint(500, 1.0, "p" + i, i));
        pts.add(new MarkerSeries.MarkerPoint(900, 2.0, "solo", 99));
        var agg = MarkerSeries.aggregate(pts, 0, 1000, 100, 3);
        assertEquals(2, agg.size());
        var dense = agg.get(0);
        assertEquals(30, dense.count(), "the presence of hidden markers is always visible (D-M3)");
        assertEquals(3, dense.first().size(), "hover lists the first N payloads");
        assertEquals(1, agg.get(1).count());
    }

    // ---- the built-in Flags rug (M32.6, D-M5's second half) --------------------------------------

    @Test
    void theFlagsRugTicksEveryFlaggedRecordOnTheAxisLane() {
        java.util.Map<Integer, String> flags = new java.util.HashMap<>();
        flags.put(1, "fill looks late");
        flags.put(3, null);                                   // flagged without a note is still a tick
        MarkerSeries rug = MarkerExtractor.flagRug(STORE.index(), new FilterState(), flags);
        assertEquals(MarkerExtractor.FLAG_RUG_LABEL, rug.label());
        assertEquals(2, rug.points().size());
        var p = rug.points().get(0);
        assertTrue(Double.isNaN(p.y()), "the rug lives on the axis lane");
        assertEquals("fill looks late", p.payload(), "the finding note rides as display cargo");
        assertEquals(1, p.recordIndex(), "click a tick, open its record");
        assertTrue(rug.note().contains("unflag to remove"), "the tooltip says how a tick leaves");
    }

    @Test
    void theRugHonoursTheFilterAndSkipsWhatItCannotPlace() {
        java.util.Map<Integer, String> flags = new java.util.HashMap<>();
        flags.put(0, null);
        flags.put(99, "out of range");
        FilterState f = new FilterState();
        f.setDimensions(java.util.Set.of("nothing matches"));
        assertNull(MarkerExtractor.flagRug(STORE.index(), f, flags),
                "every tick filtered or unplaceable → no rug, not an empty one");
        assertNull(MarkerExtractor.flagRug(STORE.index(), new FilterState(), java.util.Map.of()),
                "no flags → no rug: a built-in has no declared intent to degrade loudly about");
    }

    @Test
    void anUnparseableWhenIsANoteNotAnException() {
        MarkerSeries m = extract(new GraphSpec.MarkerSpec("bad", "circle", "1 < 2 < 3", "axis", null));
        assertTrue(m.points().isEmpty());
        assertTrue(m.note().contains("does not parse"), m.note());
    }
}
