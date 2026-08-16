package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finding where a cycle entered the graph. Both doors matter: an event, and an <b>exported service
 * callback</b>, which is how the sample log's records actually arrive — {@code ExportFunctionAuditEvent}
 * carrying a method signature rather than an event class.
 */
class EntryPointResolverTest {

    private static ProcessorTopology topology() {
        Map<String, ProcessorTopology.Node> nodes = new LinkedHashMap<>();
        List<ProcessorTopology.Edge> edges = new ArrayList<>();
        record N(String id, String cls, ProcessorTopology.Kind kind) { }
        List<N> spec = List.of(
                new N("priceEvent", "com.acme.api.PriceEvent", ProcessorTopology.Kind.EVENT),
                new N("handler_1", "com.acme.node.PriceHandler", ProcessorTopology.Kind.EVENT_HANDLER),
                new N("hedgeMonitor", "com.acme.node.VenueHedgeMonitorCalculator", ProcessorTopology.Kind.EXPORT_SERVICE),
                new N("calc_2", "com.acme.node.Calc", ProcessorTopology.Kind.NODE),
                new N("orphan_3", "com.acme.node.Orphan", ProcessorTopology.Kind.NODE));
        for (N n : spec) nodes.put(n.id(), new ProcessorTopology.Node(n.id(), "", n.cls(), n.kind()));
        edges.add(new ProcessorTopology.Edge("1", "priceEvent", "handler_1"));
        edges.add(new ProcessorTopology.Edge("2", "handler_1", "calc_2"));
        edges.add(new ProcessorTopology.Edge("3", "hedgeMonitor", "calc_2"));
        return new ProcessorTopology(nodes, edges);
    }

    @Test
    void anExportedServiceCallbackResolvesToTheDeclaringNode() {
        // this is the shape the sample log actually uses
        Set<String> entries = EntryPointResolver.resolve(topology(), "ExportFunctionAuditEvent",
                "public boolean com.acme.node.VenueHedgeMonitorCalculator.orderVenueConnected"
                + "(com.acme.api.OrderVenueConnectedEvent)");
        assertEquals(Set.of("hedgeMonitor"), entries);
    }

    @Test
    void anEventResolvesToItsEventNode() {
        assertEquals(Set.of("priceEvent"), EntryPointResolver.resolve(topology(), "PriceEvent", null));
        assertEquals(Set.of("priceEvent"),
                EntryPointResolver.resolve(topology(), "com.acme.api.PriceEvent", null));
    }

    @Test
    void anUnknownEventResolvesToNothingRatherThanGuessing() {
        // empty is a real answer: inventing an entry would put a whole subtree on the predicted path
        assertTrue(EntryPointResolver.resolve(topology(), "SomeOtherEvent", null).isEmpty());
        assertTrue(EntryPointResolver.resolve(topology(), null, null).isEmpty());
        assertTrue(EntryPointResolver.resolve(ProcessorTopology.empty(), "PriceEvent", null).isEmpty());
    }

    @Test
    void aCallbackOnAnUnknownClassFallsBackToNothing() {
        assertTrue(EntryPointResolver.resolve(topology(), "ExportFunctionAuditEvent",
                "public void com.other.Thing.method(java.lang.String)").isEmpty());
    }

    @Test
    void signatureParsingHandlesTheShapesTheLogProduces() {
        assertEquals("com.acme.node.Foo",
                EntryPointResolver.declaringClassOf("public boolean com.acme.node.Foo.bar(com.acme.E)"));
        assertEquals("com.acme.node.Foo",
                EntryPointResolver.declaringClassOf("com.acme.node.Foo.bar()"));
        assertNull(EntryPointResolver.declaringClassOf("bar()"), "no package — not a signature we know");
        assertNull(EntryPointResolver.declaringClassOf(""));
        assertNull(EntryPointResolver.declaringClassOf(null));
    }

    // ---- the point of all this: the predicted path ------------------------------------------------

    @Test
    void aBranchThatLoggedNothingIsUnknownNotExcluded() {
        ProcessorTopology t = topology();
        // hedgeMonitor is the entry; nothing logged at all on its branch
        Map<String, ProcessorTopology.Execution> without = t.classifyCycle(List.of());
        assertEquals(ProcessorTopology.Execution.OFF_PATH, without.get("calc_2"),
                "with no entry point there is nothing to reason from");

        Map<String, ProcessorTopology.Execution> with = t.classifyCycle(List.of(), List.of("hedgeMonitor"));
        assertEquals(ProcessorTopology.Execution.MAY_HAVE_RUN, with.get("hedgeMonitor"));
        assertEquals(ProcessorTopology.Execution.MAY_HAVE_RUN, with.get("calc_2"),
                "dispatch could have reached it — unknown, not 'the event never came near it'");
        assertEquals(ProcessorTopology.Execution.OFF_PATH, with.get("orphan_3"),
                "genuinely unreachable from the entry");
    }

    @Test
    void evidenceStillOutranksThePredictedPath() {
        Map<String, ProcessorTopology.Execution> state =
                topology().classifyCycle(List.of("calc_2"), List.of("hedgeMonitor"));
        assertEquals(ProcessorTopology.Execution.LOGGED, state.get("calc_2"));
        assertEquals(ProcessorTopology.Execution.MAY_HAVE_RUN, state.get("hedgeMonitor"),
                "calc_2 has two parents, so neither is forced");
    }

    @Test
    void aKnownEntryPointRulesOutWhatItCannotReach() {
        // handler_1 also feeds calc_2, but this cycle came in through hedgeMonitor — so however calc_2
        // is wired, the price branch did not run. Connectivity to a logged node must not resurrect it.
        Map<String, ProcessorTopology.Execution> state =
                topology().classifyCycle(List.of("calc_2"), List.of("hedgeMonitor"));
        assertEquals(ProcessorTopology.Execution.OFF_PATH, state.get("handler_1"));
        assertEquals(ProcessorTopology.Execution.OFF_PATH, state.get("priceEvent"));
    }

    @Test
    void anEntryPointContradictedByTheLogIsDistrusted() {
        // resolution missed, or the graphml is from another build: a logged node outside the predicted
        // path means the entry cannot be believed
        Map<String, ProcessorTopology.Execution> state =
                topology().classifyCycle(List.of("handler_1"), List.of("hedgeMonitor"));
        assertEquals(ProcessorTopology.Execution.LOGGED, state.get("handler_1"));
        assertEquals(ProcessorTopology.Execution.MAY_HAVE_RUN, state.get("priceEvent"),
                "with the entry distrusted we no longer know this was event dispatch at all, so nothing "
                + "upstream is forced — connectivity only");
    }
}
