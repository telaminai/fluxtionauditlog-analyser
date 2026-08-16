package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Telling framework plumbing from the authored graph (M22.1), and the subgraph mechanism both hiding and
 * focusing are built on.
 *
 * <p>Asserted against the compiler-emitted demo graph, because the thing that makes this hard is what the
 * compiler actually writes: some nodes carry a package-qualified class, some a bare simple name, and
 * event nodes often carry none at all.
 */
class ScaffoldingTest {

    private static ProcessorTopology demo() throws IOException {
        try (InputStream in = ScaffoldingTest.class.getResourceAsStream("/topology/demo-quote-processor.graphml")) {
            assertNotNull(in);
            return GraphMlParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theAuthoredGraphIsWhatTheUserWrote() throws IOException {
        Set<String> authored = Scaffolding.authoredNodes(demo());
        assertEquals(
                Set.of("priceListener", "spreadCalculator", "orderTracker", "quotePublisher",
                        "MarketDataEvent", "OrderUpdateEvent", "QuoteControl"),
                authored,
                "the four nodes, two events and one exported service the builder declares");
        assertTrue(authored.contains("QuoteControl"),
                "an exported service is a way INTO the user's graph — plumbing is what the framework "
                + "adds, not what the author chose to expose");
    }

    @Test
    void tenOfSeventeenNodesArePlumbing() throws IOException {
        ProcessorTopology t = demo();
        assertEquals(17, t.nodeCount());
        assertEquals(10, Scaffolding.count(t), "which is why this feature exists");
    }

    @Test
    void bothLabelShapesAreRecognised() {
        // package-qualified, as CapabilitiesProcessor emits
        assertTrue(Scaffolding.isScaffolding(new ProcessorTopology.Node(
                "clock", "", "com.telamin.fluxtion.runtime.time.Clock", ProcessorTopology.Kind.EVENT_HANDLER)));
        // bare simple name, as the demo processor emits
        assertTrue(Scaffolding.isScaffolding(new ProcessorTopology.Node(
                "context", "", "MutableEventProcessorContext", ProcessorTopology.Kind.NODE)));
        // no class at all, matched by id
        assertTrue(Scaffolding.isScaffolding(new ProcessorTopology.Node(
                "EventLogControlEvent", "", null, ProcessorTopology.Kind.EVENT)));
    }

    @Test
    void anUnknownNodeIsShownNotHidden() {
        // wrongly showing plumbing is a far smaller harm than wrongly hiding what someone is looking for
        assertFalse(Scaffolding.isScaffolding(new ProcessorTopology.Node(
                "myThing_1", "", null, ProcessorTopology.Kind.NODE)));
        assertFalse(Scaffolding.isScaffolding(new ProcessorTopology.Node(
                "pricer", "", "com.acme.trading.Pricer", ProcessorTopology.Kind.NODE)));
        assertFalse(Scaffolding.isScaffolding(null));
    }

    // ---- subgraph ---------------------------------------------------------------------------------

    @Test
    void hidingScaffoldingLeavesTheAuthoredDataflowIntact() throws IOException {
        ProcessorTopology t = demo();
        ProcessorTopology authored = t.subgraph(Scaffolding.authoredNodes(t));

        assertEquals(7, authored.nodeCount());
        // the pipeline the builder declares survives end to end
        assertEquals(Set.of("priceListener"), authored.childrenOf("MarketDataEvent"));
        assertEquals(Set.of("spreadCalculator"), authored.childrenOf("priceListener"));
        assertEquals(Set.of("quotePublisher"), authored.childrenOf("spreadCalculator"));
        assertEquals(Set.of("spreadCalculator", "orderTracker", "QuoteControl"), authored.parentsOf("quotePublisher"),
                "the exported service is an inbound edge too: calling it enters at quotePublisher");
    }

    @Test
    void subgraphDropsEdgesThatWouldAssertADependencyThatIsNotThere() {
        // a→b→c; keeping only a and c must NOT invent a→c
        ProcessorTopology t = build();
        ProcessorTopology cut = t.subgraph(List.of("a", "c"));
        assertEquals(2, cut.nodeCount());
        assertEquals(0, cut.edgeCount(), "the route ran through b, which is gone");
        assertTrue(cut.childrenOf("a").isEmpty());
    }

    @Test
    void subgraphKeepsDocumentOrderAndIgnoresUnknownIds() {
        ProcessorTopology cut = build().subgraph(List.of("c", "a", "ghost"));
        assertEquals(List.of("a", "c"), List.copyOf(cut.ids()), "original order, not the argument's");
        assertEquals(2, cut.nodeCount());
    }

    @Test
    void aNullSelectionIsTheWholeGraph() {
        ProcessorTopology t = build();
        assertEquals(t.nodeCount(), t.subgraph(null).nodeCount());
    }

    private static ProcessorTopology build() {
        var nodes = new java.util.LinkedHashMap<String, ProcessorTopology.Node>();
        for (String id : List.of("a", "b", "c")) {
            nodes.put(id, new ProcessorTopology.Node(id, "", "com.acme." + id, ProcessorTopology.Kind.NODE));
        }
        return new ProcessorTopology(nodes, List.of(
                new ProcessorTopology.Edge("1", "a", "b"),
                new ProcessorTopology.Edge("2", "b", "c")));
    }
}
