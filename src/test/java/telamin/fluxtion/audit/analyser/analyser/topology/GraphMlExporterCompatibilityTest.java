package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The before/after check on the compiler's GraphML exporter — kept as a test rather than run once.
 *
 * <p>Upstream ran our parser against its own before/after output at `dd36bc5` and found node facts,
 * kinds, class names and adjacency identical. Then the exporter was <b>rewritten as a projection of
 * the model</b> rather than a second discovery pass, which is a far larger change than that check
 * covered — and a one-off check does not cover the next rewrite either.
 *
 * <p>So both sides are committed here as fixtures and the comparison runs on every build:
 *
 * <ul>
 *   <li><b>before</b> — the committed {@code SessionProcessor.graphml}, emitted by builder 1.0.64,
 *       from before the vocabulary existed;
 *   <li><b>after</b> — the same graph emitted by a post-rewrite builder with the vocabulary
 *       <b>OFF</b>, which is the default and therefore what an author gets today.
 * </ul>
 *
 * <p>What must hold is the contract upstream states for {@code OFF}: <i>emit no {@code fluxtion.*}
 * vocabulary and no extra edges — the document this emitter produced before the vocabulary existed,
 * with the one unconditional difference that {@code edgedefault} is corrected to directed.</i>
 * Everything this analyser reads must be unchanged.
 */
class GraphMlExporterCompatibilityTest {

    private static final Path BEFORE = Path.of(
            "src/test/resources/topology/vocabulary/session-processor-legacy-no-vocabulary.graphml");
    private static final Path AFTER = Path.of(
            "src/test/resources/topology/vocabulary/session-processor-off-new-builder.graphml");

    private static ProcessorTopology before() {
        return GraphMlParser.parse(BEFORE);
    }

    private static ProcessorTopology after() {
        return GraphMlParser.parse(AFTER);
    }

    @Test
    @DisplayName("OFF emits no vocabulary at all — the escape hatch really is an escape hatch")
    void offCarriesNoVocabulary() {
        assertFalse(after().vocabulary().present(),
                "OFF must mean 'the file I had before', not 'a differently shaped new file'");
    }

    @Test
    @DisplayName("the same nodes, with the same ids, kinds and class names")
    void nodesAreUnchanged() {
        ProcessorTopology a = before();
        ProcessorTopology b = after();
        assertEquals(a.nodeCount(), b.nodeCount());
        assertEquals(a.ids(), b.ids());
        for (String id : a.ids()) {
            ProcessorTopology.Node x = a.node(id);
            ProcessorTopology.Node y = b.node(id);
            assertEquals(x.kind(), y.kind(), id + " changed kind");
            assertEquals(x.className(), y.className(), id + " changed class");
        }
    }

    @Test
    @DisplayName("the same adjacency, in both directions — what every focus and scope walks")
    void adjacencyIsUnchanged() {
        ProcessorTopology a = before();
        ProcessorTopology b = after();
        for (String id : a.ids()) {
            assertEquals(setOf(a.childrenOf(id)), setOf(b.childrenOf(id)), "children of " + id);
            assertEquals(setOf(a.parentsOf(id)), setOf(b.parentsOf(id)), "parents of " + id);
        }
    }

    @Test
    @DisplayName("no extra edges at OFF — parallel expansion is opt-in, as the contract says")
    void edgeCountIsUnchanged() {
        // This is the assertion that would catch the parallel shape leaking into the default. At
        // PARALLEL this repo's graph goes from 43 edges to 44, so the check has real range.
        assertEquals(before().edgeCount(), after().edgeCount());
    }

    @Test
    @DisplayName("the audit verdict is unchanged — a surface an analyser user actually reads")
    void auditReadinessIsUnchanged() {
        assertEquals(AuditReadiness.of(before()).verdict(), AuditReadiness.of(after()).verdict());
    }

    @Test
    @DisplayName("and PARALLEL is where the shape legitimately differs — so the check above has range")
    void parallelDiffersAsDesigned() {
        ProcessorTopology parallel = GraphMlParser.parse(
                Path.of("src/test/resources/topology/vocabulary/session-processor-parallel.graphml"));
        assertTrue(parallel.edgeCount() > before().edgeCount(),
                "one pair in this graph carries two relationships");
        assertEquals(before().ids(), parallel.ids(), "but no node appears or disappears");
        List<String> pairs = parallel.edges().stream()
                .map(e -> e.source() + "->" + e.target()).distinct().collect(Collectors.toList());
        assertEquals(before().edgeCount(), pairs.size(),
                "and the distinct PAIRS still match the legacy edge count");
    }

    private static Set<String> setOf(java.util.Collection<String> in) {
        return in == null ? Set.of() : Set.copyOf(in);
    }
}
