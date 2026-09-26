package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Re-review N2 (2026-09-26): a report's series section draws EXACTLY its stored call — the parameters that produce the
 * data ({@code ReportSpec.SectionSpec.call}). The adapter read only {@code expr}/{@code key}, forced LOCF and used the
 * view filter, so a report drew 2 points where {@code series {resolve:"STRICT"}} found 0, and all 3 where the call's
 * filter selected 1. Each case compares the production adapter, {@link ReportSeriesPicture}, with the series verb's
 * {@link SeriesScan} on the same call.
 */
class ReportSeriesCallTest {

    private static String rec(long t, String node, String key, double v) {
        return "eventLogRecord:\n  event: Tick\n  logTime: " + t + "\n  nodeLogs:\n    - " + node + ": {" + key + ": " + v + "}\n---\n";
    }

    /** nodeA.x and nodeB.y never in the same record — the reviewer's STRICT counterexample. */
    private static final HeapLogStore ALTERNATING = new HeapLogStore(
            rec(1000, "nodeA", "x", 1) + rec(2000, "nodeB", "y", 2) + rec(3000, "nodeA", "x", 4));
    /** rootNode.v at 1000, 2000 and 3000 — the reviewer's filter counterexample. */
    private static final HeapLogStore THREE = new HeapLogStore(
            rec(1000, "rootNode", "v", 1) + rec(2000, "rootNode", "v", 3) + rec(3000, "rootNode", "v", 2));

    private static ReportSeriesPicture.Result drawn(HeapLogStore store, Map<String, ?> call) throws Exception {
        var out = new AtomicReference<ReportSeriesPicture.Result>();
        SwingUtilities.invokeAndWait(() -> out.set(ReportSeriesPicture.of(store, call, 1200, 600)));
        return out.get();
    }

    private static long verbPoints(HeapLogStore store, Map<String, Object> call) {
        return ((Number) SeriesScan.scan(store, call).get("points")).longValue();
    }

    private static int points(ReportSeriesPicture.Result r) {
        assertNull(r.problem(), "precondition: drawn, not refused — " + r.problem());
        return Integer.parseInt(r.caption().replaceAll(".* · (\\d+) points? · .*", "$1"));
    }

    @Test
    @DisplayName("N2: a STRICT call draws what the series verb counts — none, where no record holds both refs")
    void aStrictCallIsNotCarried() throws Exception {
        // witness: the adapter forcing LOCF (review-n2-report-forces-locf)
        Map<String, Object> call = Map.of("expr", "nodeA.x + nodeB.y", "resolve", "STRICT");
        assertEquals(0, verbPoints(ALTERNATING, new LinkedHashMap<>(call)), "precondition: the verb finds none");
        var r = drawn(ALTERNATING, call);
        assertEquals(0, points(r), "N2: the report draws the call's STRICT resolution, not LOCF: " + r.caption());
        assertTrue(r.caption().contains("STRICT"), "and says which resolution it drew: " + r.caption());
    }

    @Test
    @DisplayName("N2: with no resolve named, the report uses the verb's default (STRICT), not LOCF")
    void theDefaultResolutionIsTheVerbs() throws Exception {
        Map<String, Object> call = Map.of("expr", "nodeA.x + nodeB.y");
        assertEquals(verbPoints(ALTERNATING, new LinkedHashMap<>(call)), points(drawn(ALTERNATING, call)),
                "N2: one default, the verb's");
    }

    @Test
    @DisplayName("N2: LOCF, when the call asks for it, is honoured — and still agrees with the verb")
    void anLocfCallIsCarried() throws Exception {
        Map<String, Object> call = Map.of("expr", "nodeA.x + nodeB.y", "resolve", "LOCF");
        assertEquals(verbPoints(ALTERNATING, new LinkedHashMap<>(call)), points(drawn(ALTERNATING, call)));
        assertEquals(2, points(drawn(ALTERNATING, call)));
    }

    @Test
    @DisplayName("N2: the call's filter is the scope — one point at 2000, not the whole log")
    void theCallFilterIsHonoured() throws Exception {
        // witness: the adapter dropping the call's filter (review-n2-report-drops-call-filter)
        Map<String, Object> call = Map.of("expr", "rootNode.v", "filter", Map.of("from", 2000L, "to", 2000L));
        assertEquals(1, verbPoints(THREE, new LinkedHashMap<>(call)), "precondition: the verb finds one");
        var r = drawn(THREE, call);
        assertEquals(1, points(r), "N2: the report draws the call's filter, not the whole log or the view's: " + r.caption());
        assertTrue(r.caption().contains("2000"), "and states the scope it drew: " + r.caption());
    }

    @Test
    @DisplayName("N2: a key-based section still draws, under the call's scope, and says the view filter does not apply")
    void aKeySectionStillDraws() throws Exception {
        var r = drawn(THREE, Map.of("key", "rootNode.v"));
        assertEquals(3, points(r));
        assertTrue(r.caption().contains("view filter does not apply"), r.caption());
    }

    @Test
    @DisplayName("F1: a literal key keeps its value, including expression punctuation, spaces and backticks")
    void aLiteralKeyNeverBecomesAFormula() throws Exception {
        for (String key : List.of("v+1", "v-1", "v with space", "v`literal")) {
            var store = new HeapLogStore("eventLogRecord:\n  event: Tick\n  logTime: 1000\n  nodeLogs:\n"
                    + "    - rootNode: {v: 100, " + key + ": 7}\n---\n");
            String label = "rootNode." + key;
            var actual = drawn(store, Map.of("key", label));
            assertNull(actual.problem(), "literal key must remain drawable: " + label);
            var expected = new AtomicReference<java.awt.image.BufferedImage>();
            SwingUtilities.invokeAndWait(() -> {
                // Independent expected values, not the adapter's parser or extraction path.
                var series = new telamin.fluxtion.audit.analyser.analyser.graph.Series(label);
                series.add(1000, 7);
                var chart = new ChartPanel();
                chart.setSeries(List.of(series));
                expected.set(chart.toImage(1200, 600));
            });
            assertArrayEquals(expected.get().getRGB(0, 0, 1200, 600, null, 0, 1200),
                    actual.image().getRGB(0, 0, 1200, 600, null, 0, 1200),
                    "F1: literal key " + label + " must draw value 7, not evaluate a formula");
        }
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"crossings", "buckets", "limit", "an unknown key",
            "both key and expr", "an unknown resolve", "a text filter"})
    @DisplayName("N2: semantics a drawn series cannot carry are NOT RENDERED with the reason — never substituted")
    void unsupportedSemanticsAreRefused(String what) throws Exception {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("expr", "rootNode.v");
        switch (what) {
            case "crossings" -> call.put("crossings", Map.of("above", 2));
            case "buckets" -> call.put("buckets", "1s");
            case "limit" -> call.put("limit", 5);
            case "an unknown key" -> call.put("resolv", "LOCF");
            case "both key and expr" -> call.put("key", "rootNode.v");
            case "an unknown resolve" -> call.put("resolve", "LAST");
            default -> call.put("filter", Map.of("text", "x"));
        }
        var r = drawn(THREE, call);
        assertNull(r.image(), what + ": nothing drawn");
        assertNotNull(r.problem(), what + ": and the reason is stated");
    }

    @ParameterizedTest(name = "{0} · {1} · [{2}, {3}]")
    @CsvSource({
            "quotePublisher.spread, LOCF, , ",
            "quotePublisher.spread, STRICT, , ",
            "priceListener.mid, STRICT, 1767258010000, 1767258100000",
            "quotePublisher.liveOrders * 2, LOCF, , ",
            "priceListener.mid - quotePublisher.spread, STRICT, , ",
            "priceListener.mid - quotePublisher.spread, LOCF, 1767258004000, 1767258050000",
    })
    @DisplayName("N2: over the committed fixture, the report adapter and the series verb count the same points")
    void theAdapterAgreesWithTheVerb(String expr, String resolve, Long from, Long to) throws Exception {
        var store = HeapLogStore.fromFile(Path.of("src/test/resources/topology/demo-quote-series.yaml"));
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("expr", expr);
        call.put("resolve", resolve);
        Map<String, Object> window = new LinkedHashMap<>();
        if (from != null) window.put("from", from);
        if (to != null) window.put("to", to);
        if (!window.isEmpty()) call.put("filter", window);
        long verb = verbPoints(store, new LinkedHashMap<>(call));
        assertTrue(verb > 0, "the case must have data, or agreement is vacuous");
        assertEquals(verb, points(drawn(store, call)), "N2: the report adapter disagrees with the series verb for " + expr);
    }
}
