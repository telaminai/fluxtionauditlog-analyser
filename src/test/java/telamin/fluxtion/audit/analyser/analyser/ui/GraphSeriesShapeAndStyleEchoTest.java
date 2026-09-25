package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.llm.SessionFacts;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two things a model driving the {@code graph} verb could not get right, found in virgin-LLM runs on 1.19.2.
 *
 * <p><b>A {@code series} entry that is not a string.</b> Sonnet and Haiku each sent {@code exprs}' shape,
 * {@code series: [{expr, label}]}. The object was stringified to {@code {expr=a.b, label=x}}, which has a dot,
 * so it was added as a key that could never fire, persisted, and echoed {@code ok}. One model then told the user
 * the keys "resolved as valid but found no finite plottable values". It is now refused, and nothing changes.
 *
 * <p><b>The chart's style.</b> 1.19.2 persists it, but no echo reported it, so a model that set {@code points}
 * could not confirm it. The graph echo and {@code context.savedGraphs} now carry it, and {@code savedGraphs}
 * shows series as {@code instanceId.key} rather than the persisted U+0001 form.
 */
class GraphSeriesShapeAndStyleEchoTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private ActionExecutor executor(GraphTabs tabs) {
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        return new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
    }

    @Test
    void anObjectInSeriesIsRefusedAndNothingIsCreated() {
        GraphTabs tabs = new GraphTabs();
        var r = executor(tabs).render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of(Map.of("expr", "bidMakerOrder.price", "label", "bid")), "style", "points"));
        assertFalse(r.ok(), "an object is not an instanceId.key: " + r);
        assertTrue(r.toString().contains("use exprs: [{expr, label}]"), "the refusal names the right shape: " + r);
        assertTrue(tabs.specs().stream().noneMatch(s -> "g".equals(s.name())), "nothing was created");
    }

    @Test
    void aSeriesThatIsNotAListIsRefused() {
        GraphTabs tabs = new GraphTabs();
        var r = executor(tabs).render("graph", Map.of("newTab", true, "name", "g", "series", "bidMakerOrder.price"));
        assertFalse(r.ok(), "a bare string used to be dropped silently and echoed ok: " + r);
        assertTrue(tabs.specs().stream().noneMatch(s -> "g".equals(s.name())), "nothing was created");
    }

    @Test
    void aNullSeriesEntryIsRefused() {
        GraphTabs tabs = new GraphTabs();
        Map<String, Object> params = new java.util.HashMap<>(Map.of("newTab", true, "name", "g"));
        params.put("series", java.util.Arrays.asList("bidMakerOrder.price", null));
        var r = executor(tabs).render("graph", params);
        assertFalse(r.ok(), "a null entry used to be dropped silently and echoed ok: " + r);
        assertTrue(r.toString().contains("got null"), "the refusal says what it got: " + r);
        assertTrue(tabs.specs().stream().noneMatch(s -> "g".equals(s.name())), "nothing was created");
    }

    @Test
    void theEchoReportsTheStyleTheChartHas() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        var set = ex.render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"), "style", "points"));
        assertTrue(set.ok(), set::toString);
        assertEquals("points", set.payload().get("style"));
        var untouched = ex.render("graph", Map.of("name", "g"));
        assertEquals("points", untouched.payload().get("style"), "a call that sends no style still reports it");
        var fresh = ex.render("graph", Map.of("newTab", true, "name", "h", "series", List.of("bidMakerOrder.price")));
        assertEquals("step", fresh.payload().get("style"), "the default is reported, not omitted");
    }

    @Test
    void savedGraphsShowTheKeyAsSentAndTheStyle() {
        GraphTabs tabs = new GraphTabs();
        assertTrue(executor(tabs).render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"), "style", "line")).ok());
        var facts = SessionFacts.savedGraphs(tabs.specs(), Set.of("g"), true);
        var g = facts.stream().filter(m -> "g".equals(m.get("name"))).findFirst().orElseThrow();
        assertEquals(List.of("bidMakerOrder.price"), g.get("series"), "no U+0001 in what a caller reads");
        assertEquals("line", g.get("style"));
    }
}
