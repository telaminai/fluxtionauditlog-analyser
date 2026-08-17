package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M26.4 echo hardening: {@code rightAxis} (or a note's {@code series}) naming a series that is not on
 * the graph used to be a silent no-op, discoverable only by looking at the plot (spec-agent-efficiency
 * V4). It still applies — the series may be added next call — but the echo now says so.
 */
class GraphEchoWarningsTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private ActionExecutor executor(GraphTabs tabs) {
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        return new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f) -> { });
    }

    @SuppressWarnings("unchecked")
    private static List<String> warnings(telamin.fluxtion.audit.analyser.analyser.llm.ActionResult r) {
        assertTrue(r.ok(), () -> "graph action failed: " + r);
        return (List<String>) r.payload().get("warnings");
    }

    @Test
    void rightAxisNamingAnAbsentSeriesIsWarnedAboutNotSilent() {
        var r = executor(new GraphTabs()).render("graph", Map.of(
                "newTab", true,
                "series", List.of("bidMakerOrder.price"),
                "rightAxis", List.of("ghost.series")));
        List<String> w = warnings(r);
        assertNotNull(w, "the silent no-op must be named");
        assertTrue(w.get(0).contains("ghost.series"));
    }

    @Test
    void rightAxisNamingASeriesAddedInTheSameCallIsFine() {
        var r = executor(new GraphTabs()).render("graph", Map.of(
                "newTab", true,
                "series", List.of("bidMakerOrder.price", "askMakerOrder.price"),
                "rightAxis", List.of("askMakerOrder.price")));
        assertNull(warnings(r), "the series IS on the graph after this call — nothing to warn about");
    }

    @Test
    void rightAxisNamingASeriesAlreadyOnTheGraphIsFine() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        assertTrue(ex.render("graph", Map.of(
                "newTab", true, "name", "g", "series", List.of("bidMakerOrder.price"))).ok());
        var r = ex.render("graph", Map.of("name", "g", "rightAxis", List.of("bidMakerOrder.price")));
        assertNull(warnings(r), "a series added by an EARLIER call counts as on the graph");
    }

    @Test
    void noteNamingAnAbsentSeriesWarns() {
        var r = executor(new GraphTabs()).render("graph", Map.of(
                "newTab", true,
                "series", List.of("bidMakerOrder.price"),
                "notes", List.of(Map.of("text", "spike here", "at", 1L, "series", "nope.value"))));
        List<String> w = warnings(r);
        assertNotNull(w);
        assertTrue(w.get(0).contains("nope.value"));
    }
}
