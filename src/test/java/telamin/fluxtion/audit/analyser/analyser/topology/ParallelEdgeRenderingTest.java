package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M45.5 — a PARALLEL graph must render as one arrow per pair and still carry every relationship.
 *
 * <p>The defect this prevents was found by reading this repo's own code rather than assumed:
 * {@code ProcessorTopology.of} keeps edges in a list with no deduplication, and {@code LayeredLayout}
 * and {@code TopologyCanvas} drew straight from {@code edges()}. A parallel file therefore drew a
 * pair's arrow once per relationship, on top of itself — a heavier line, not more information — and
 * the layered layout counted the pair twice as an ordering constraint.
 *
 * <p><b>This is the slice upstream's default-flip is gated on.</b> Their condition is one consumer
 * that *understands* PARALLEL, and reading it was never the same as understanding it.
 */
class ParallelEdgeRenderingTest {

    private static final Path PARALLEL = Path.of(
            "src/test/resources/topology/vocabulary/session-processor-parallel.graphml");
    /**
     * A genuinely PRE-VOCABULARY graph — the shape most files in the world will always have.
     *
     * <p>This used to point at the analyser's own committed processor GraphML, which worked only while
     * the compiler's emission default was {@code OFF}. Builder 1.0.66 flipped that default to
     * {@code PARALLEL}, so the committed processor now CARRIES the vocabulary and could no longer stand
     * for a file without it — seven tests failed at once, correctly, because the fixture role had
     * quietly become vacant rather than because anything regressed.
     *
     * <p>This file is the analyser's own graph as emitted immediately BEFORE that flip, so its
     * provenance is exact: it is what this repository actually shipped when the default was OFF.
     */
    private static final Path LEGACY = Path.of(
            "src/test/resources/topology/vocabulary/session-processor-legacy-no-vocabulary.graphml");

    @Test
    @DisplayName("layout draws one line per pair, while edges() stays faithful to the file")
    void layoutDeduplicatesButTheModelDoesNot() {
        ProcessorTopology t = GraphMlParser.parse(PARALLEL);

        assertTrue(t.edgeCount() > t.layoutEdges().size(),
                "this graph has a doubled pair, so the two counts must differ");
        assertEquals(GraphMlParser.parse(LEGACY).edgeCount(), t.layoutEdges().size(),
                "and the drawn count must match what the same graph drew before the vocabulary");

        Set<String> pairs = t.layoutEdges().stream()
                .map(e -> e.source() + "->" + e.target()).collect(Collectors.toSet());
        assertEquals(t.layoutEdges().size(), pairs.size(), "no pair may appear twice in the layout");
    }

    @Test
    @DisplayName("a legacy graph is untouched — the dedup cannot be noticed by a file without it")
    void legacyIsUnaffected() {
        ProcessorTopology t = GraphMlParser.parse(LEGACY);
        assertEquals(t.edges().size(), t.layoutEdges().size());
        assertEquals(t.edges(), t.layoutEdges());
    }

    @Test
    @DisplayName("both relationships on the doubled pair survive, and stay distinguishable")
    void relationshipsAreRelocatedNotLost() {
        ProcessorTopology t = GraphMlParser.parse(PARALLEL);
        List<ProcessorTopology.Edge> both = t.relationshipsFor("callbackDispatcher", "context");
        assertEquals(2, both.size(), "the drawing collapses them; the model must not");

        Set<String> keys = both.stream()
                .map(ProcessorTopology.Edge::relationshipKey).collect(Collectors.toSet());
        assertEquals(2, keys.size(), "and they must not share an identity: " + keys);
    }

    @Test
    @DisplayName("relationshipCount counts relationships; edgeCount is left meaning what it meant")
    void theTwoCountsAreKeptApart() {
        ProcessorTopology parallel = GraphMlParser.parse(PARALLEL);
        ProcessorTopology legacy = GraphMlParser.parse(LEGACY);
        assertEquals(legacy.edgeCount(), legacy.relationshipCount(),
                "for a legacy graph the two questions have one answer");
        assertTrue(parallel.relationshipCount() >= parallel.layoutEdges().size());
    }

    @Test
    @DisplayName("the layout still produces a path for every drawn pair, and no more")
    void theLayoutItselfDrawsOnePathPerPair() {
        ProcessorTopology t = GraphMlParser.parse(PARALLEL);
        TopologyLayout layout = LayeredLayout.layout(t);
        assertNotNull(layout);
        // Every drawn pair appears once. Before the dedup this was one path per RELATIONSHIP, so the
        // doubled pair produced two identical polylines painted over each other.
        Map<String, Long> perPair = layout.edges().stream()
                .map(p -> p.source() + "->" + p.target())
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        List<String> doubled = perPair.entrySet().stream()
                .filter(e -> e.getValue() > 1).map(Map.Entry::getKey).collect(Collectors.toList());
        assertTrue(doubled.isEmpty(), "a pair drawn more than once: " + doubled);
    }

    @Test
    @DisplayName("dispatch rank is available as a column, pinned against the order we know")
    void dispatchRankIsUsable() {
        ProcessorTopology t = GraphMlParser.parse(PARALLEL);
        List<String> byRank = t.nodes().stream()
                .filter(n -> n.topologicalRank() >= 0)
                .sorted(java.util.Comparator.comparingInt(ProcessorTopology.Node::topologicalRank))
                .map(ProcessorTopology.Node::id).collect(Collectors.toList());
        assertTrue(byRank.size() > 5, "most nodes carry a rank: " + byRank.size());
        assertTrue(byRank.indexOf("operationGate") < byRank.indexOf("sessionBoundary"),
                "the gate guards the decision: " + byRank);
        assertTrue(byRank.indexOf("sessionBoundary") < byRank.indexOf("effectQueue"),
                "and the decision precedes the queue it pushes to — this ordering was REVERSED in "
                        + "fluxtion-builder before dbcbe17, and looked authoritative: " + byRank);
    }
}
