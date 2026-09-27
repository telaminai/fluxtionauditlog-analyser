package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #51 review: the socket's delete does not match the UI's rule, and this is why that is allowed.
 *
 * <p>While definitions are ambiguous the UI disables Delete for <b>every</b> chart (owner,
 * 2026-09-24). The verb refuses only the names actually withheld, so an assistant can delete
 * something a person cannot — which sounds like the socket routing around a safety rule.
 *
 * <p>It is not, and the reason is worth pinning rather than asserting. The refusal exists to stop an
 * ambiguous NAME resolving to the wrong saved definition. A refusal also clears the tab strip, so
 * the only charts that can exist afterwards are ones created since — and those have no saved
 * definition, so a delete cannot reach the wrong one. The UI is blunt because it cannot say that to
 * a person per chart.
 *
 * <p>The claim that actually needs a test is the last one: <b>the profile is not touched.</b>
 */
class ChartDeleteUnderDefinitionRefusalTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private static GraphSpec chart(String name) {
        return new GraphSpec(name, List.of("bidMakerOrder.price"), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "step", true);
    }

    /** A GraphTabs wired to a mutable definition list, so a stray write to the profile is visible. */
    private record Rig(GraphTabs tabs, ActionExecutor verb, List<GraphSpec> saved) { }

    private Rig rig() {
        GraphTabs tabs = new GraphTabs();
        List<GraphSpec> saved = new ArrayList<>(List.of(chart("kept-A"), chart("kept-B")));
        tabs.setSavedDefinitions(() -> saved);
        tabs.setDeleteListener(n -> saved.removeIf(g -> g.name().equals(n)));
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        return new Rig(tabs, new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(),
                (r, n, f, k) -> { }), saved);
    }

    @Test
    @DisplayName("A withheld chart cannot be deleted over the socket, and says why")
    void aWithheldChartIsRefused() {
        Rig rig = rig();
        rig.tabs().refuseDefinitions("two saved charts are called 'kept-A'");

        var r = rig.verb().render("graph", Map.of("name", "kept-A", "delete", true));

        assertFalse(r.ok(), "deleting an ambiguous name could remove the wrong definition");
        assertEquals("two saved charts are called 'kept-A'", r.error(),
                "the refusal's OWN words reach the caller — a generic 'refused' would leave an assistant "
                        + "unable to tell an ambiguous profile from a missing chart");
        assertEquals(List.of("kept-A", "kept-B"), rig.saved().stream().map(GraphSpec::name).toList());
    }

    @Test
    @DisplayName("A chart created AFTER the refusal can be deleted, and the profile is untouched")
    void aChartWithNoSavedDefinitionMayGo() {
        Rig rig = rig();
        rig.tabs().refuseDefinitions("two saved charts are called 'kept-A'");
        rig.verb().render("graph", Map.of("newTab", true, "name", "scratch",
                "series", List.of("bidMakerOrder.price")));

        var r = rig.verb().render("graph", Map.of("name", "scratch", "delete", true));

        assertTrue(r.ok(), () -> "a chart with no saved definition cannot be the wrong one: " + r.error());
        assertEquals(List.of("kept-A", "kept-B"), rig.saved().stream().map(GraphSpec::name).toList(),
                "THE claim: a delete under refusal must not write to the profile. If this list has "
                        + "changed, the socket is doing what the UI's blanket disable exists to prevent.");
    }

    @Test
    @DisplayName("A refusal clears the strip, which is why only new charts are reachable")
    void theRefusalClearsTheStrip() {
        Rig rig = rig();
        rig.verb().render("graph", Map.of("newTab", true, "name", "before",
                "series", List.of("bidMakerOrder.price")));
        assertTrue(rig.tabs().graphNames().contains("before"));

        rig.tabs().refuseDefinitions("ambiguous");

        assertEquals(List.of(), rig.tabs().graphNames(),
                "the premise of the argument above: nothing survives a refusal, so nothing with a saved "
                        + "definition is reachable by a later delete");
    }
}
