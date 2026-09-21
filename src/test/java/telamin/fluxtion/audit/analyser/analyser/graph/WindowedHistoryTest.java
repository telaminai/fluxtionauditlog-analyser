package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** TA-4 constructed regression cases. The session's original A8 inputs were not preserved. */
class WindowedHistoryTest {
    private static HeapLogStore log(int... values) {
        StringBuilder raw = new StringBuilder("---\n");
        for (int i = 0; i < values.length; i++) raw.append("eventLogRecord:\n  logTime: ")
                .append((i + 1) * 1000).append("\n  event: Tick\n  nodeLogs:\n    - node: {value: ")
                .append(values[i]).append("}\n---\n");
        return new HeapLogStore(raw.toString());
    }

    @Test void deltaAtWindowStartAgreesWithWholeLogForBothResolutionModes() {
        var store = log(10, 10, 15, 15);
        for (var policy : SeriesExtractor.Resolve.values()) {
            var window = new FilterState(); window.setTimeRange(3000L, 4000L);
            var plotted = SeriesExtractor.extractExpr(store, window, Expr.parse("delta(node.value)"), "change", false, policy);
            var whole = SeriesExtractor.extractExpr(store, new FilterState(), Expr.parse("delta(node.value)"), "change", false, policy);
            assertEquals(2, plotted.size());
            assertEquals(3000, plotted.x(0));
            assertEquals(5, plotted.y(0));
            assertEquals(whole.y(1), plotted.y(0));
            var scan = SeriesScan.scan(store, Map.of("expr", "delta(node.value)", "resolve", policy.name(),
                    "filter", Map.of("from", 3000, "to", 4000), "crossings", Map.of("above", 0)));
            assertEquals(2L, scan.get("points"));
            assertEquals(5.0, ((Map<?,?>)scan.get("stats")).get("first"));
            var events = (List<?>)((Map<?,?>)scan.get("crossings")).get("aboveEvents");
            assertEquals(1, events.size());
            assertEquals(2, ((Map<?,?>)events.getFirst()).get("recordIndex"));
        }
    }

    @Test void windowDoesNotInventAnEntryAlreadyPresentBeforeItsLowerBound() {
        var scan = SeriesScan.scan(log(0, 5, 10, 15), Map.of("expr", "delta(node.value)",
                "filter", Map.of("from", 3000, "to", 4000), "crossings", Map.of("above", 0)));
        assertEquals(2L, scan.get("points"));
        assertEquals(List.of(), ((Map<?,?>)scan.get("crossings")).get("aboveEvents"));
    }

    @Test void durationAndSampleWindowsUseEarlierHistoryButReturnOnlyRequestedTimes() {
        var store = log(10, 10, 16, 20);
        var filter = new FilterState(); filter.setTimeRange(3000L, 3000L);
        for (String expr : List.of("mean(node.value, 3)", "mean(node.value, \"5s\")")) {
            var series = SeriesExtractor.extractExpr(store, filter, Expr.parse(expr), expr, false, SeriesExtractor.Resolve.STRICT);
            assertEquals(1, series.size()); assertEquals(12.0, series.y(0));
            var scan = SeriesScan.scan(store, Map.of("expr", expr, "filter", Map.of("from", 3000, "to", 3000)));
            assertEquals(1L, scan.get("points"));
            assertEquals(12.0, ((Map<?,?>)scan.get("stats")).get("first"));
            assertTrue(scan.containsKey("history"));
        }
        // A non-time filter still removes observations from history; it cannot leak them into the result.
        filter.setText("16");
        assertEquals(0, SeriesExtractor.extractExpr(store, filter, Expr.parse("delta(node.value)"),
                "filtered", false, SeriesExtractor.Resolve.STRICT).size());
    }
}
