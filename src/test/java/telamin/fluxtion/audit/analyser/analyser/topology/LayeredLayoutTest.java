package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The layered layout (M21.2). Assertions are on <b>invariants</b>, not on coordinates: exact pixel
 * positions are an implementation detail that would make the tests brittle, whereas "every edge points
 * downward", "nothing overlaps" and "the same graph lays out the same way" are the properties the view
 * actually depends on.
 */
class LayeredLayoutTest {

    /** Build a topology from {@code "a->b"} specs; nodes are created in first-seen order. */
    private static ProcessorTopology topo(String... edgeSpecs) {
        Map<String, ProcessorTopology.Node> nodes = new LinkedHashMap<>();
        List<ProcessorTopology.Edge> edges = new ArrayList<>();
        for (String spec : edgeSpecs) {
            String[] parts = spec.split("->");
            for (String id : parts) {
                nodes.computeIfAbsent(id.trim(), n ->
                        new ProcessorTopology.Node(n, "id:" + n, "com.acme." + n, ProcessorTopology.Kind.NODE));
            }
            if (parts.length == 2) {
                edges.add(new ProcessorTopology.Edge(spec, parts[0].trim(), parts[1].trim()));
            }
        }
        return new ProcessorTopology(nodes, edges);
    }

    private static int layerOf(TopologyLayout l, String id) {
        return l.box(id).layer();
    }

    // ---- layering ---------------------------------------------------------------------------------

    @Test
    void everyEdgePointsStrictlyDownward() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b", "b->c", "a->c", "c->d"));
        for (TopologyLayout.EdgePath e : l.edges()) {
            if (e.reversed()) continue;
            assertTrue(layerOf(l, e.source()) < layerOf(l, e.target()),
                    e.source() + " must sit above " + e.target() + " — lower means later in dispatch");
        }
    }

    @Test
    void longestPathPutsANodeBelowEverythingThatFeedsIt() {
        // a→b→c and a→c: c must be below b, not beside it, or the picture lies about dispatch order
        TopologyLayout l = LayeredLayout.layout(topo("a->b", "b->c", "a->c"));
        assertEquals(0, layerOf(l, "a"));
        assertEquals(1, layerOf(l, "b"));
        assertEquals(2, layerOf(l, "c"));
    }

    @Test
    void rootsShareTheTopLayer() {
        TopologyLayout l = LayeredLayout.layout(topo("a->c", "b->c"));
        assertEquals(0, layerOf(l, "a"));
        assertEquals(0, layerOf(l, "b"));
        assertEquals(1, layerOf(l, "c"));
        assertEquals(2, l.layerCount());
    }

    @Test
    void disconnectedComponentsAreBothPlaced() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b", "c->d"));
        assertEquals(4, l.boxes().size());
        assertEquals(0, layerOf(l, "a"));
        assertEquals(0, layerOf(l, "c"));
    }

    @Test
    void anIsolatedNodeStillGetsABox() {
        ProcessorTopology t = topo("a->b");
        Map<String, ProcessorTopology.Node> nodes = new LinkedHashMap<>();
        t.nodes().forEach(n -> nodes.put(n.id(), n));
        nodes.put("lonely", new ProcessorTopology.Node("lonely", "", "com.acme.Lonely", ProcessorTopology.Kind.NODE));
        TopologyLayout l = LayeredLayout.layout(new ProcessorTopology(nodes, t.edges()));
        assertNotNull(l.box("lonely"));
    }

    // ---- geometry ---------------------------------------------------------------------------------

    @Test
    void nodesOnALayerNeverOverlap() {
        TopologyLayout l = LayeredLayout.layout(topo("r->a", "r->b", "r->c", "r->d", "r->e"));
        List<TopologyLayout.NodeBox> layer = l.layer(1);
        assertEquals(5, layer.size());
        for (int i = 0; i + 1 < layer.size(); i++) {
            assertTrue(layer.get(i).maxX() <= layer.get(i + 1).x() + 1e-9,
                    "boxes must not overlap: " + layer.get(i).id() + " / " + layer.get(i + 1).id());
        }
    }

    @Test
    void layersAreVerticallySeparated() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b"));
        assertTrue(l.box("b").y() > l.box("a").maxY(), "layers must not touch");
    }

    @Test
    void layoutStartsAtTheOriginAndBoundsCoverEveryBox() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b", "a->c", "c->d"));
        double minX = l.boxes().stream().mapToDouble(TopologyLayout.NodeBox::x).min().orElseThrow();
        double minY = l.boxes().stream().mapToDouble(TopologyLayout.NodeBox::y).min().orElseThrow();
        assertEquals(0, minX, 1e-9, "pan/zoom belongs to the view, so layout is origin-anchored");
        assertEquals(0, minY, 1e-9);
        for (TopologyLayout.NodeBox b : l.boxes()) {
            assertTrue(b.maxX() <= l.width() + 1e-9);
            assertTrue(b.maxY() <= l.height() + 1e-9);
        }
    }

    @Test
    void orientationSwapsTheAxes() {
        ProcessorTopology t = topo("a->b");
        TopologyLayout down = LayeredLayout.layout(t);
        TopologyLayout right = LayeredLayout.layout(t,
                LayeredLayout.Config.defaults().withOrientation(LayeredLayout.Orientation.LEFT_RIGHT));
        assertTrue(down.box("b").y() > down.box("a").y(), "top-down: later is lower");
        assertTrue(right.box("b").x() > right.box("a").x(), "left-right: later is further right");
        assertEquals(down.box("a").y(), right.box("a").x(), 1e-9);
    }

    // ---- edges ------------------------------------------------------------------------------------

    @Test
    void aLongEdgeIsRoutedThroughTheLayersItSpans() {
        // a→d skips two layers; without bend points it would cut straight across b and c
        TopologyLayout l = LayeredLayout.layout(topo("a->b", "b->c", "c->d", "a->d"));
        TopologyLayout.EdgePath spanning = l.edges().stream()
                .filter(e -> e.source().equals("a") && e.target().equals("d"))
                .findFirst().orElseThrow();
        assertTrue(spanning.isRouted(), "expected bend points, got " + spanning.points().size());
        assertEquals(4, spanning.points().size(), "one point per layer crossed");
    }

    @Test
    void aShortEdgeIsAStraightLine() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b"));
        assertEquals(2, l.edges().get(0).points().size());
        assertFalse(l.edges().get(0).isRouted());
    }

    @Test
    void edgePathsRunFromSourceToTarget() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b"));
        TopologyLayout.EdgePath e = l.edges().get(0);
        assertEquals(l.box("a").centerY(), e.start().y(), 1e-9);
        assertEquals(l.box("b").centerY(), e.end().y(), 1e-9);
    }

    // ---- robustness -------------------------------------------------------------------------------

    @Test
    void aCycleIsBrokenRatherThanHangingTheLayout() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b", "b->c", "c->a"));
        assertEquals(3, l.boxes().size());
        assertTrue(l.edges().stream().anyMatch(TopologyLayout.EdgePath::reversed),
                "one edge must be marked reversed so the view can show it was a back edge");
    }

    @Test
    void aReversedEdgeStillReportsItsOriginalDirection() {
        TopologyLayout l = LayeredLayout.layout(topo("a->b", "b->a"));
        for (TopologyLayout.EdgePath e : l.edges()) {
            assertTrue(l.box(e.source()) != null && l.box(e.target()) != null);
            assertNotEquals(e.source(), e.target());
        }
        assertTrue(l.edges().stream().anyMatch(e -> e.source().equals("b") && e.target().equals("a")),
                "the back edge keeps its own direction in the output");
    }

    @Test
    void aSelfLoopDoesNotAffectLayering() {
        TopologyLayout l = LayeredLayout.layout(topo("a->a", "a->b"));
        assertEquals(0, layerOf(l, "a"));
        assertEquals(1, layerOf(l, "b"));
    }

    @Test
    void anEmptyOrNullTopologyYieldsAnEmptyLayout() {
        assertTrue(LayeredLayout.layout(ProcessorTopology.empty()).isEmpty());
        assertTrue(LayeredLayout.layout(null).isEmpty());
        assertEquals(0, LayeredLayout.layout(ProcessorTopology.empty()).width());
    }

    @Test
    void aSingleNodeSitsAtTheOrigin() {
        TopologyLayout l = LayeredLayout.layout(topo("solo"));
        assertEquals(1, l.boxes().size());
        assertEquals(0, l.box("solo").x(), 1e-9);
        assertEquals(0, l.box("solo").y(), 1e-9);
    }

    // ---- determinism and scale --------------------------------------------------------------------

    @Test
    void theSameGraphAlwaysLaysOutIdentically() {
        // a graph that reshuffles between openings is unreadable, and the tests depend on this too
        ProcessorTopology t = topo("a->b", "a->c", "b->d", "c->d", "d->e", "a->e");
        TopologyLayout first = LayeredLayout.layout(t);
        TopologyLayout second = LayeredLayout.layout(t);
        for (TopologyLayout.NodeBox b : first.boxes()) {
            TopologyLayout.NodeBox other = second.box(b.id());
            assertEquals(b.x(), other.x(), 1e-9, b.id());
            assertEquals(b.y(), other.y(), 1e-9, b.id());
            assertEquals(b.indexInLayer(), other.indexInLayer(), b.id());
        }
    }

    @Test
    void crossingReductionBeatsTheNaiveOrdering() {
        // deliberately adversarial: sources listed in the opposite order to their targets
        TopologyLayout l = LayeredLayout.layout(topo(
                "s1->t3", "s2->t2", "s3->t1", "s1->t2", "s3->t2"));
        assertEquals(0, countCrossings(l, 0), "this graph is plainly untangleable; layout should find it");
    }

    /** Count crossings between {@code layer} and the one below, from the final geometry. */
    private static int countCrossings(TopologyLayout l, int layer) {
        List<double[]> spans = new ArrayList<>();
        for (TopologyLayout.EdgePath e : l.edges()) {
            TopologyLayout.NodeBox from = l.box(e.source());
            TopologyLayout.NodeBox to = l.box(e.target());
            if (from == null || to == null) continue;
            if (from.layer() == layer && to.layer() == layer + 1) {
                spans.add(new double[]{from.centerX(), to.centerX()});
            }
        }
        int crossings = 0;
        for (int i = 0; i < spans.size(); i++) {
            for (int j = i + 1; j < spans.size(); j++) {
                double[] a = spans.get(i);
                double[] b = spans.get(j);
                if ((a[0] - b[0]) * (a[1] - b[1]) < 0) crossings++;
            }
        }
        return crossings;
    }

    @Test
    void aLayerFullOfUnanchoredVerticesStillSorts() {
        // regression: ordering used a comparator returning 0 for vertices with no neighbour to sort by,
        // which is intransitive — TimSort throws "Comparison method violates its general contract" once
        // a layer is big enough to hit its merge path. Only a real graph was large enough to expose it.
        Map<String, ProcessorTopology.Node> nodes = new LinkedHashMap<>();
        List<ProcessorTopology.Edge> edges = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String root = "r" + i;
            nodes.put(root, new ProcessorTopology.Node(root, "", "com.acme.R" + i, ProcessorTopology.Kind.NODE));
        }
        for (int i = 0; i < 20; i++) {          // only a third of the roots feed anything…
            String target = "t" + i;
            nodes.put(target, new ProcessorTopology.Node(target, "", "com.acme.T" + i, ProcessorTopology.Kind.NODE));
            edges.add(new ProcessorTopology.Edge("e" + i, "r" + i, target));
        }
        // …so the upward sweep sorts 60 vertices of which 40 have no child to anchor them
        TopologyLayout l = assertDoesNotThrow(() -> LayeredLayout.layout(new ProcessorTopology(nodes, edges)));
        assertEquals(80, l.boxes().size());
    }

    @Test
    void bendPointsDoNotConsumeAWholeNodesWidth() {
        // a graph with many long edges is otherwise spread to absurdity by rows that are mostly bends
        ProcessorTopology withoutSpan = topo("a->b", "b->c", "c->d", "x->b");
        ProcessorTopology withSpan = topo("a->b", "b->c", "c->d", "x->b", "a->d");
        double narrow = LayeredLayout.layout(withoutSpan).width();
        double wide = LayeredLayout.layout(withSpan).width();
        assertTrue(wide - narrow < LayeredLayout.Config.defaults().nodeWidth(),
                "routing one long edge added " + (wide - narrow) + "px — a bend is not a box");
    }

    @Test
    void handlesAThreeHundredNodeGraphQuickly() {
        // the largest emitted graph to hand is 300 nodes — that is the acceptance scale
        Map<String, ProcessorTopology.Node> nodes = new LinkedHashMap<>();
        List<ProcessorTopology.Edge> edges = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            String id = "n" + i;
            nodes.put(id, new ProcessorTopology.Node(id, "", "com.acme.N" + i, ProcessorTopology.Kind.NODE));
            if (i >= 3) {
                edges.add(new ProcessorTopology.Edge("e" + i + "a", "n" + (i - 3), id));
                edges.add(new ProcessorTopology.Edge("e" + i + "b", "n" + (i - 1), id));
            }
        }
        long start = System.nanoTime();
        TopologyLayout l = LayeredLayout.layout(new ProcessorTopology(nodes, edges));
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertEquals(300, l.boxes().size());
        assertTrue(millis < 5_000, "layout took " + millis + "ms — it is computed once and cached, but "
                + "this is the size that must stay comfortable");
        for (TopologyLayout.EdgePath e : l.edges()) {
            if (!e.reversed()) assertTrue(layerOf(l, e.source()) < layerOf(l, e.target()));
        }
    }
}
