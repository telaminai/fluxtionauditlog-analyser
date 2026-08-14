package telamin.fluxtion.audit.analyser.analyser.graph;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeriesExtractorTest {

    private static HeapLogStore store() {
        return new HeapLogStore(Samples.sample());
    }

    @Test
    void extractsNumericPointsForAKey() {
        HeapLogStore store = store();
        Series s = SeriesExtractor.extract(store, new FilterState(),
                new GraphKey("hedgeToOrdersNode", "hedgeQuantity"));
        assertEquals(3, s.size(), "hedgeToOrdersNode.hedgeQuantity logged in the 3 scheduled cycles");
        for (int i = 0; i < s.size(); i++) assertEquals(0.0, s.y(i), 1e-9);
        assertTrue(s.minX() <= s.maxX());
    }

    @Test
    void omitsNanValuesEntirely() {
        HeapLogStore store = store();
        // hedgeQuantity on this node is NaN in every record it appears → no data points
        Series s = SeriesExtractor.extract(store, new FilterState(),
                new GraphKey("contraPositionToHedgeQuantityCalculator", "hedgeQuantity"));
        assertEquals(0, s.size(), "NaN is not a data point (and not a parse failure) — omitted");
        // but the same node's numeric 'rate' still yields points
        Series rate = SeriesExtractor.extract(store, new FilterState(),
                new GraphKey("contraPositionToHedgeQuantityCalculator", "rate"));
        assertTrue(rate.size() >= 1);
        for (int i = 0; i < rate.size(); i++) assertTrue(Double.isFinite(rate.y(i)));
    }

    @Test
    void extractsFloatingRate() {
        HeapLogStore store = store();
        Series s = SeriesExtractor.extract(store, new FilterState(),
                new GraphKey("contraPositionToHedgeQuantityCalculator", "rate"));
        assertEquals(2, s.size(), "rate logged in the 2 onMultilevelMarketData cycles");
        assertTrue(s.maxFiniteY() > 19.0 && s.maxFiniteY() < 21.0);
    }

    @Test
    void graphsBooleanValuesAsZeroOne() {
        HeapLogStore store = store();
        Series s = SeriesExtractor.extract(store, new FilterState(),
                new GraphKey("hedgePositionMonitor", "hedgePositionBreach"));
        assertTrue(s.size() >= 2, "hedgePositionBreach: false logged in the orderVenueConnected cycles");
        for (int i = 0; i < s.size(); i++) assertEquals(-1.0, s.y(i), 1e-9, "false -> -1.0 (symmetric)");

        List<GraphKey> keys = SeriesExtractor.discover(store, new FilterState(), 1000);
        assertTrue(keys.contains(new GraphKey("hedgePositionMonitor", "hedgePositionBreach")),
                "boolean keys are discoverable");
    }

    @Test
    void acrossAllTimeIgnoresTheTimeWindowSoDraggingNeverReExtracts() {
        HeapLogStore store = store();
        GraphKey k = new GraphKey("hedgeToOrdersNode", "hedgeQuantity");
        Series all = SeriesExtractor.extract(store, new FilterState(), k);
        assertEquals(3, all.size());

        FilterState windowed = new FilterState();
        windowed.setTimeRange(all.maxX() + 1, all.maxX() + 1000);   // window excludes every point
        assertEquals(0, SeriesExtractor.extract(store, windowed, k, false).size(),
                "with the time filter applied, no points fall in the window");
        assertEquals(3, SeriesExtractor.extract(store, windowed, k, true).size(),
                "acrossAllTime ignores the window — the chart extracts once and windows via the view");
    }

    @Test
    void discoverFindsNumericKeys() {
        HeapLogStore store = store();
        List<GraphKey> keys = SeriesExtractor.discover(store, new FilterState(), 1000);
        assertTrue(keys.contains(new GraphKey("hedgeToOrdersNode", "hedgeQuantity")));
        assertTrue(keys.contains(new GraphKey("contraPositionToHedgeQuantityCalculator", "rate")));
        assertFalse(keys.contains(new GraphKey("hedgeConnectionMonitor", "status")), "non-numeric keys excluded");
    }
}
