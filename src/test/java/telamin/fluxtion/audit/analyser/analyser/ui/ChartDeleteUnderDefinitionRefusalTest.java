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

    /**
     * A GraphTabs wired to a mutable definition list, and to a record of every name that reached the
     * profile's removal channel.
     *
     * <p>PR #51 second review: asserting only that the list survived is a liveness check, not a
     * witness — {@code definitions::remove} cannot fail for a name that was never in it, so the
     * assertion held whether or not the delete behaved. {@code removed} is what can actually go
     * wrong: the wrong name reaching the listener, or a saved one.
     */
    private record Rig(GraphTabs tabs, ActionExecutor verb, List<GraphSpec> saved, List<String> removed) { }

    private Rig rig() {
        GraphTabs tabs = new GraphTabs();
        List<GraphSpec> saved = new ArrayList<>(List.of(chart("kept-A"), chart("kept-B")));
        List<String> removed = new ArrayList<>();
        tabs.setSavedDefinitions(() -> saved);
        tabs.setDeleteListener(n -> { removed.add(n); saved.removeIf(g -> g.name().equals(n)); });
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        return new Rig(tabs, new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(),
                (r, n, f, k) -> { }), saved, removed);
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
        assertEquals(List.of("scratch"), rig.removed(),
                "the witness: EXACTLY the chart asked for reached the profile's removal channel. The "
                        + "survival of the saved list cannot show this on its own — removing a name that "
                        + "was never in it is a no-op, so that assertion holds however the delete behaves.");
        assertEquals(List.of("kept-A", "kept-B"), rig.saved().stream().map(GraphSpec::name).toList(),
                "and neither withheld definition went with it");
    }

    /**
     * Review of the review response: {@code close} replies "the DEFINITION is kept", and under a refusal that
     * is false. {@code syncOpenGraphsIntoConfig} returns early while definitions are refused, so a chart made
     * since the refusal exists only as a tab — closing it discards it, exactly as a delete would, while the
     * reply tells the caller the opposite. An assistant closing the chart that carries its finding, to tidy
     * up, would lose it believing it was kept. Refusing (and saying why) keeps "close is the safe half" true.
     */
    @Test
    @DisplayName("A close under refusal is refused: nothing is saved, so it would discard what it claims to keep")
    void aCloseUnderRefusalWouldDiscardSoItIsRefused() {
        Rig rig = rig();
        rig.tabs().refuseDefinitions("two saved charts are called 'kept-A'");
        rig.verb().render("graph", Map.of("newTab", true, "name", "finding",
                "series", List.of("bidMakerOrder.price")));
        rig.verb().render("graph", Map.of("newTab", true, "name", "probe",
                "series", List.of("bidMakerOrder.price")));

        var r = rig.verb().render("graph", Map.of("name", "finding", "close", true));

        assertFalse(r.ok(), "a close that cannot keep the definition must not report that it did");
        assertTrue(r.error().contains("two saved charts are called 'kept-A'"),
                () -> "the refusal's own words, so the caller knows why and what to repair: " + r.error());
        assertTrue(rig.tabs().graphNames().contains("finding"), "refused means nothing changed");
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
