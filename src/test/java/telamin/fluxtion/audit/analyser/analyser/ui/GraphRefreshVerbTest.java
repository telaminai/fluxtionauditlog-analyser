package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M65 D-F4 — the {@code graph} verb is idempotent: a re-send that changes nothing re-extracts nothing, for
 * every key ({@code series} always was; {@code markers}/{@code bands} used to re-extract on any presence —
 * the accidental cure the M65 investigation found). {@code refresh: true} is the one word for "do it anyway",
 * and the echo says {@code "scheduled"}, never {@code true}: the walk lands after the call returns.
 */
class GraphRefreshVerbTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private ActionExecutor executor(GraphTabs tabs) {
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        return new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f) -> { });
    }

    private static final List<Map<String, Object>> MARKERS =
            List.of(Map.of("label", "buys", "when", "bidMakerOrder.price", "glyph", "triangleUp"));

    @Test
    void anIdenticalReSendReExtractsNothing_andRefreshForcesIt() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);

        var first = ex.render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"), "markers", MARKERS));
        assertTrue(first.ok(), first::toString);
        assertEquals("scheduled", first.payload().get("refreshed"), "a new graph extracts");

        var same = ex.render("graph", Map.of("name", "g",
                "series", List.of("bidMakerOrder.price"), "markers", MARKERS));
        assertTrue(same.ok(), same::toString);
        assertEquals(Boolean.FALSE, same.payload().get("refreshed"),
                "same series, same markers: nothing to re-extract (markers used to force one)");

        var sameBands = ex.render("graph", Map.of("name", "g", "bands", List.of()));
        assertEquals(Boolean.FALSE, sameBands.payload().get("refreshed"), "an empty set replacing an empty set is unchanged");

        var changed = ex.render("graph", Map.of("name", "g",
                "markers", List.of(Map.of("label", "sells", "when", "askMakerOrder.price"))));
        assertEquals("scheduled", changed.payload().get("refreshed"), "a changed set re-extracts");

        var forced = ex.render("graph", Map.of("name", "g", "refresh", true));
        assertEquals("scheduled", forced.payload().get("refreshed"), "refresh re-extracts with nothing else changed");
    }
}
