package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M26.1 — the analyser computes, the agent concludes: stats + threshold crossings over a key or
 * formula in one call, with record anchors so the follow-up read is targeted, never estimated.
 */
class SeriesScanTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    @Test
    void statsOverAKey() {
        Map<String, Object> r = SeriesScan.scan(store, Map.of("expr", "bidMakerOrder.price"));
        assertTrue((Long) r.get("points") > 0, "the sample logs bid prices");
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) r.get("stats");
        double min = (Double) stats.get("min");
        double max = (Double) stats.get("max");
        assertTrue(min <= max);
        assertNotNull(stats.get("minAt"));
        assertNotNull(stats.get("maxAt"));
        assertEquals("STRICT", r.get("resolve"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void crossingsCarryRecordAnchors() {
        Map<String, Object> all = SeriesScan.scan(store, Map.of("expr", "bidMakerOrder.price"));
        double min = (Double) ((Map<String, Object>) all.get("stats")).get("min");

        // a threshold below the minimum: EVERY first sample is an entry — exactly one edge event
        Map<String, Object> r = SeriesScan.scan(store, Map.of(
                "expr", "bidMakerOrder.price",
                "crossings", Map.of("above", min - 1)));
        Map<String, Object> crossings = (Map<String, Object>) r.get("crossings");
        List<Map<String, Object>> events = (List<Map<String, Object>>) crossings.get("aboveEvents");
        assertEquals(1, events.size(), "value never leaves the region after entering it once");
        Map<String, Object> e = events.get(0);
        assertTrue((Integer) e.get("recordIndex") >= 0);
        assertTrue((Long) e.get("byteOffset") >= 0);
        assertNotNull(e.get("logTime"));
        assertEquals(false, crossings.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void formulaWithStrictSemantics_andTheCapIsExplicit() {
        Map<String, Object> r = SeriesScan.scan(store, Map.of(
                "expr", "askMakerOrder.price - bidMakerOrder.price",
                "crossings", Map.of("above", -1_000_000.0),
                "limit", 1));
        Map<String, Object> crossings = (Map<String, Object>) r.get("crossings");
        List<Map<String, Object>> events = (List<Map<String, Object>>) crossings.get("aboveEvents");
        assertTrue((Double) events.get(0).get("value") > 0, "the sample's spread is positive");
        assertEquals(1, events.size());
        // only one entry-event exists here, so nothing was cut — the flag must say so honestly
        assertEquals(false, crossings.get("truncated"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bucketsSummarisePerMinute() {
        Map<String, Object> r = SeriesScan.scan(store, Map.of(
                "expr", "bidMakerOrder.price", "buckets", "minute"));
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) r.get("buckets");
        assertFalse(buckets.isEmpty());
        assertTrue(buckets.get(0).get("key").toString().endsWith("Z"));
        assertTrue((Long) buckets.get(0).get("count") > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void locfCarriesValuesAcrossRecordsThatStrictWouldSkip() {
        // ask and bid are logged by different nodes and do not co-occur in every record — LOCF's
        // whole purpose. Same carry rule as graphing, so the point counts must relate the same way.
        Map<String, Object> params = Map.of("expr", "askMakerOrder.price - bidMakerOrder.price");
        long strict = (Long) SeriesScan.scan(store, params).get("points");
        Map<String, Object> locf = SeriesScan.scan(store, Map.of(
                "expr", "askMakerOrder.price - bidMakerOrder.price", "resolve", "LOCF"));
        assertEquals("LOCF", locf.get("resolve"));
        long carried = (Long) locf.get("points");
        assertTrue(carried >= strict, "carrying last-known values can only ADD points: LOCF="
                + carried + " STRICT=" + strict);
        assertTrue(carried > 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void belowCrossingsAreEdgeEventsWithAnchorsToo() {
        Map<String, Object> all = SeriesScan.scan(store, Map.of("expr", "bidMakerOrder.price"));
        double max = (Double) ((Map<String, Object>) all.get("stats")).get("max");

        // a threshold above the maximum: the FIRST sample enters the below-region — exactly one event
        Map<String, Object> r = SeriesScan.scan(store, Map.of(
                "expr", "bidMakerOrder.price",
                "crossings", Map.of("below", max + 1)));
        Map<String, Object> crossings = (Map<String, Object>) r.get("crossings");
        List<Map<String, Object>> events = (List<Map<String, Object>>) crossings.get("belowEvents");
        assertEquals(1, events.size(), "the value never leaves the region after entering it once");
        Map<String, Object> e = events.get(0);
        assertTrue((Integer) e.get("recordIndex") >= 0);
        assertTrue((Long) e.get("byteOffset") >= 0);
        assertNotNull(e.get("logTime"));
        assertNull(crossings.get("aboveEvents"), "no 'above' was asked for — none is echoed");
    }

    @Test
    void textFilterIsRefusedLoudly() {
        var e = assertThrows(IllegalArgumentException.class, () -> SeriesScan.scan(store, Map.of(
                "expr", "bidMakerOrder.price", "filter", Map.of("text", "x"))));
        assertTrue(e.getMessage().contains("not supported"));
    }

    @Test
    void missingExprIsAClearError() {
        var e = assertThrows(IllegalArgumentException.class, () -> SeriesScan.scan(store, Map.of()));
        assertTrue(e.getMessage().contains("'expr'"));
    }
}
