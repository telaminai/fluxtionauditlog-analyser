package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Polish H4 — the M24 finding: focusing scope=routes on a node everything feeds selected 198 of 309
 * nodes, because every route into a sink IS the graph. A terminal-ish node now gets a hop-bounded
 * default, stated, with the unbounded answer one untick away. Pinned on the pure function and on the
 * context-confined version, with a synthetic fan-in graph large enough to trip the rule.
 */
class TopologyFocusRoutesBoundTest {

    /** {@code depth} layers of {@code width} nodes, every node feeding every node of the next layer, all into {@code sink}. */
    private static ProcessorTopology fanIn(int depth, int width) {
        StringBuilder nodes = new StringBuilder(), edges = new StringBuilder();
        int e = 0;
        for (int d = 0; d < depth; d++) {
            for (int w = 0; w < width; w++) {
                nodes.append(node("n" + d + "_" + w));
                if (d + 1 < depth) {
                    for (int w2 = 0; w2 < width; w2++) {
                        edges.append(edge("e" + e++, "n" + d + "_" + w, "n" + (d + 1) + "_" + w2));
                    }
                } else {
                    edges.append(edge("e" + e++, "n" + d + "_" + w, "sink"));
                }
            }
        }
        nodes.append(node("sink"));
        return GraphMlParser.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:jGraph=\"http://www.jgraph.com/\">"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>"
                + "<graph edgedefault=\"directed\">" + nodes + edges + "</graph></graphml>");
    }

    private static String node(String id) {
        return "<node id=\"" + id + "\"><data key=\"vertex_label\"><jGraph:ShapeNode>"
                + "<jGraph:label text=\"id:" + id + "&#10;class:com.acme." + id + "\"/>"
                + "<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>";
    }

    private static String edge(String id, String s, String t) {
        return "<edge id=\"" + id + "\" source=\"" + s + "\" target=\"" + t + "\"/>";
    }

    @Test
    void routesFromASinkOnALargeGraphAreBounded_andSayByHowMuch() {
        ProcessorTopology t = fanIn(8, 8);            // 65 nodes; every one of them is upstream of the sink
        var all = TopologyFocus.expand(t, List.of("sink"), TopologyFocus.Scope.ROUTES);
        assertEquals(65, all.size(), "unbounded, every route into the sink is the whole graph — the M24 shape");

        var r = TopologyFocus.routes(t, List.of("sink"), true);
        assertTrue(r.bounded(), "more than half of a 40+ node graph: bounded");
        assertEquals(TopologyFocus.ROUTE_HOP_BOUND, r.hops());
        assertEquals(65, r.unboundedSize(), "and the number the user would have got is carried, to be shown");
        assertEquals(1 + 3 * 8, r.ids().size(), "the sink plus three full layers upstream — not the whole graph");
        assertTrue(r.ids().contains("n7_0") && r.ids().contains("n5_3") && !r.ids().contains("n4_0"),
                "exactly three hops: layers 7, 6, 5 are in; layer 4 is not");
    }

    @Test
    void theUnboundedAnswerStaysOneUntickAway() {
        ProcessorTopology t = fanIn(8, 8);
        var r = TopologyFocus.routes(t, List.of("sink"), false);
        assertFalse(r.bounded());
        assertEquals(65, r.ids().size());
        assertEquals(0, r.hops());
    }

    @Test
    void midGraphRoutesAreNotBounded_focusWorksThereAlready() {
        ProcessorTopology t = fanIn(8, 8);
        // a node in layer 1: its routes are layer 0 (8) up and layers 2..7 + sink down — wide, but that is
        // what "all routes" of a mid-graph node means, and it was never the complaint
        var r = TopologyFocus.routes(t, List.of("n1_0"), true);
        int unbounded = TopologyFocus.expand(t, List.of("n1_0"), TopologyFocus.Scope.ROUTES).size();
        assertEquals(r.bounded(), TopologyFocus.degenerate(unbounded, t.nodeCount()),
                "bounded exactly when the rule says, never by a special case for 'mid-graph'");
    }

    @Test
    void smallGraphsAreNeverBounded_theyAreReadableAnyway() {
        ProcessorTopology t = fanIn(3, 4);              // 13 nodes
        var r = TopologyFocus.routes(t, List.of("sink"), true);
        assertFalse(r.bounded(), "below " + TopologyFocus.BOUND_MIN_GRAPH + " nodes the whole graph fits on a screen");
        assertEquals(13, r.ids().size());
    }

    @Test
    void theContextConfinedVersionAppliesTheSameRuleToTheWorld() {
        ProcessorTopology t = fanIn(8, 8);
        FocusStack stack = new FocusStack(t);
        var full = stack.routesInWorld(List.of("sink"), true);
        assertTrue(full.bounded(), "at the full graph the world is the graph: same answer as the pure function");
        assertEquals(1 + 3 * 8, full.ids().size());

        // focus down to a small world: two layers + sink (17 nodes) — inside it, routes are NOT degenerate
        Set<String> world = new java.util.LinkedHashSet<>();
        for (int w = 0; w < 8; w++) { world.add("n6_" + w); world.add("n7_" + w); }
        world.add("sink");
        assertTrue(stack.push(world, "two layers"));
        var inside = stack.routesInWorld(List.of("sink"), true);
        assertFalse(inside.bounded(), "a 17-node world is below the size at which bounding helps");
        assertEquals(17, inside.ids().size(), "and every in-world route is there");
    }
}
