package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.topology.TopologyFocus.Scope;

/**
 * The selection-scope model behind the topology's click cycle (M22.2).
 *
 * <p>Asserted on the real demo graph where it matters, because the interesting cases are the ones the
 * graph's actual shape produces: a node with two parents, an exported service that enters the graph from
 * nowhere, and scaffolding sitting adjacent to authored nodes.
 */
class TopologyFocusTest {

    private static ProcessorTopology demo() throws IOException {
        try (InputStream in = TopologyFocusTest.class.getResourceAsStream("/topology/demo-quote-processor.graphml")) {
            assertNotNull(in);
            return GraphMlParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** a → b → c, plus d → c: c has two parents, and a has a route to c that does not pass through d. */
    private static ProcessorTopology diamond() {
        var nodes = new LinkedHashMap<String, ProcessorTopology.Node>();
        for (String id : List.of("a", "b", "c", "d", "e")) {
            nodes.put(id, new ProcessorTopology.Node(id, "", "com.acme." + id, ProcessorTopology.Kind.NODE));
        }
        return new ProcessorTopology(nodes, List.of(
                new ProcessorTopology.Edge("1", "a", "b"),
                new ProcessorTopology.Edge("2", "b", "c"),
                new ProcessorTopology.Edge("3", "d", "c"),
                new ProcessorTopology.Edge("4", "c", "e")));
    }

    // ---- the cycle --------------------------------------------------------------------------------

    @Test
    void theScopeCycleReturnsToWhereItStarted() {
        Scope s = Scope.NODE;
        assertEquals(Scope.NEIGHBOURS, s = s.next());
        assertEquals(Scope.ROUTES, s = s.next());
        assertEquals(Scope.ALL, s = s.next());
        assertEquals(Scope.NODE, s.next(), "clicking on gets you back to one node, not stuck at 'all'");
    }

    @Test
    void everyScopeIsLabelledForTheToolbar() {
        for (Scope s : Scope.values()) {
            assertNotNull(s.label());
            assertFalse(s.label().isBlank(), s + " needs wording — an unlabelled width is a guess");
        }
    }

    // ---- expansion --------------------------------------------------------------------------------

    @Test
    void nodeScopeIsJustTheNode() {
        assertEquals(Set.of("c"), TopologyFocus.expand(diamond(), List.of("c"), Scope.NODE));
    }

    @Test
    void neighboursIsOneHopBothWays() {
        assertEquals(Set.of("c", "b", "d", "e"),
                TopologyFocus.expand(diamond(), List.of("c"), Scope.NEIGHBOURS),
                "both parents and the child — one hop, not one direction");
    }

    @Test
    void routesReachesEveryAncestorAndEveryDescendant() {
        // a is two hops up through b, so NEIGHBOURS misses it and ROUTES must not
        assertEquals(Set.of("c", "b", "a", "d", "e"),
                TopologyFocus.expand(diamond(), List.of("c"), Scope.ROUTES));
        assertFalse(TopologyFocus.expand(diamond(), List.of("c"), Scope.NEIGHBOURS).contains("a"));
    }

    @Test
    void routesDoesNotDragInASiblingBranchThatMerelySharesAnAncestor() {
        assertEquals(Set.of("a", "b", "c", "e"),
                TopologyFocus.expand(diamond(), List.of("a"), Scope.ROUTES),
                "d feeds c but is not downstream of a — 'blast radius' is not 'anything connected'");
    }

    @Test
    void allIsTheWholeGraphWhateverIsSelected() {
        assertEquals(5, TopologyFocus.expand(diamond(), List.of("a"), Scope.ALL).size());
        assertEquals(5, TopologyFocus.expand(diamond(), List.of(), Scope.ALL).size(),
                "'whole graph' does not depend on a selection");
    }

    @Test
    void aMultiNodeSelectionIsTheUnionOfItsScopes() {
        assertEquals(Set.of("a", "b", "d", "c"),
                TopologyFocus.expand(diamond(), List.of("a", "d"), Scope.NEIGHBOURS));
    }

    @Test
    void anEmptyOrUnknownSelectionScopesToNothingRatherThanEverything() {
        // a caller filtering by this must not accidentally show the whole graph when it meant one node
        assertTrue(TopologyFocus.expand(diamond(), List.of(), Scope.NODE).isEmpty());
        assertTrue(TopologyFocus.expand(diamond(), List.of("ghost"), Scope.ROUTES).isEmpty());
        assertTrue(TopologyFocus.expand(null, List.of("a"), Scope.NODE).isEmpty());
        assertTrue(TopologyFocus.expand(ProcessorTopology.empty(), List.of("a"), Scope.ALL).isEmpty());
    }

    @Test
    void aCycleInTheGraphDoesNotHang() {
        // a processor graph is acyclic, but a hand-edited or partial graphml need not be
        var nodes = new LinkedHashMap<String, ProcessorTopology.Node>();
        for (String id : List.of("x", "y")) {
            nodes.put(id, new ProcessorTopology.Node(id, "", null, ProcessorTopology.Kind.NODE));
        }
        ProcessorTopology looped = new ProcessorTopology(nodes, List.of(
                new ProcessorTopology.Edge("1", "x", "y"),
                new ProcessorTopology.Edge("2", "y", "x")));
        assertEquals(Set.of("x", "y"), TopologyFocus.expand(looped, List.of("x"), Scope.ROUTES));
    }

    // ---- the two filters together -----------------------------------------------------------------

    @Test
    void hidingScaffoldingLeavesTheAuthoredGraph() throws IOException {
        ProcessorTopology t = demo();
        assertEquals(10, TopologyFocus.visible(t, false, null).size());
        assertEquals(t.nodeCount(), TopologyFocus.visible(t, true, null).size());
    }

    @Test
    void focusAndScaffoldingIntersectRatherThanOverride() throws IOException {
        // eventLogger is scaffolding AND a neighbour of nothing authored; the point is that a scaffolding
        // node inside the focus scope stays hidden while scaffolding is off
        ProcessorTopology t = demo();
        Set<String> scoped = TopologyFocus.expand(t, List.of("quotePublisher"), Scope.ALL);
        assertTrue(scoped.contains("eventLogger"), "ALL really does mean all");

        Set<String> shown = TopologyFocus.visible(t, false, scoped);
        assertFalse(shown.contains("eventLogger"), "the scaffolding filter still applies inside a focus");
        assertTrue(shown.contains("quotePublisher"));
    }

    @Test
    void focusingOnAnExportedServiceShowsWhatItCallsInto() throws IOException {
        ProcessorTopology t = demo();
        Set<String> scoped = TopologyFocus.expand(t, List.of("QuoteControl"), Scope.NEIGHBOURS);
        assertTrue(scoped.contains("quotePublisher"),
                "an operator asking 'what does this control touch' gets the node it enters at");
    }

    @Test
    void aFocusedSubgraphNeverInventsAnEdge() throws IOException {
        // NODE scope on a middle node: its edges lead to nodes that are gone, so none may survive
        ProcessorTopology t = demo();
        ProcessorTopology view = t.subgraph(
                TopologyFocus.visible(t, true, TopologyFocus.expand(t, List.of("spreadCalculator"), Scope.NODE)));
        assertEquals(1, view.nodeCount());
        assertEquals(0, view.edgeCount());
    }
}
