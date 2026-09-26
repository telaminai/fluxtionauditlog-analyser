package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.Expr;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesScan;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-E8's "a chart does not contradict its series", the general half the review said was unverified (set 13). A chart
 * and the {@code series} verb reach their points by two separate paths — {@link SeriesExtractor} for the chart and
 * a report's series section, {@link SeriesScan} for the verb — so a picture could say one thing and the verb another.
 * Over a real fixture, across keys, formulas, both resolve policies and time windows, the two must agree on the same
 * inputs: the same log revision, expression, filter and window, exactly as D-E8 defines "the same inputs".
 */
class ChartSeriesAgreementTest {

    private static final HeapLogStore STORE = load();

    private static HeapLogStore load() {
        try {
            return HeapLogStore.fromFile(Path.of("src/test/resources/topology/demo-quote-series.yaml"));
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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
    void theChartAndTheSeriesVerbCountTheSamePoints(String expr, String resolve, Long from, Long to) {
        // witness: SeriesScan counting a point it did not produce (registered control review-set13-chart-series-agree)
        var filter = new FilterState();
        filter.setTimeRange(from, to);
        var chart = SeriesExtractor.extractExpr(STORE, filter, Expr.parse(expr), expr, false,
                SeriesExtractor.Resolve.valueOf(resolve));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("expr", expr);
        params.put("resolve", resolve);
        Map<String, Object> window = new LinkedHashMap<>();
        if (from != null) window.put("from", from);
        if (to != null) window.put("to", to);
        if (!window.isEmpty()) params.put("filter", window);
        var verb = SeriesScan.scan(STORE, params);
        assertTrue(chart.size() > 0, "the case must have data, or agreement is vacuous: " + expr);
        assertEquals(((Number) verb.get("points")).longValue(), chart.size(),
                "D-E8: the chart and the series verb disagree for " + expr + " under " + resolve + ": " + verb);
    }
}
