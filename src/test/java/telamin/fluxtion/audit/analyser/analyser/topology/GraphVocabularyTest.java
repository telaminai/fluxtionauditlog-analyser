package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M45.2 — read the vocabulary, change no behaviour.
 *
 * <p>All three fixtures are <b>compiler output</b>, not hand-written: this repo's own
 * {@code SessionProcessor} graph, emitted by {@code fluxtion-builder} at three settings. A
 * hand-edited GraphML drifts from what the emitter actually produces and still parses perfectly,
 * which is the failure the analyser exists to catch in other people's files.
 */
class GraphVocabularyTest {

    private static final Path DIR = Path.of("src/test/resources/topology/vocabulary");
    /** Committed pre-vocabulary output — the shape most files in the world will always have. */
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

    private static ProcessorTopology parallel() {
        return GraphMlParser.parse(DIR.resolve("session-processor-parallel.graphml"));
    }

    // ------------------------------------------------------------------ mode and trust

    @Test
    @DisplayName("a pre-vocabulary graph reads as NONE and says so without complaining")
    void legacyGraphHasNoVocabulary() {
        GraphVocabulary v = GraphMlParser.parse(LEGACY).vocabulary();
        assertEquals(GraphVocabulary.Mode.NONE, v.mode());
        assertFalse(v.present());
        assertFalse(v.trusted());
        assertTrue(v.whyNot().contains("no fluxtion.* metadata"), v.whyNot());
    }

    @Test
    @DisplayName("a PARALLEL graph is trusted, and declares a version this build reads")
    void parallelIsTrusted() {
        GraphVocabulary v = parallel().vocabulary();
        assertEquals(GraphVocabulary.Mode.PARALLEL, v.mode());
        assertEquals("1.0", v.metaVersion());
        assertEquals(1, v.major());
        assertTrue(v.trusted());
        assertNull(v.whyNot());
        assertTrue(v.declaredNodeCount() > 0, "the file declares its own node count");
    }

    @Test
    @DisplayName("an AGGREGATED graph is REFUSED — its merged facts cannot be matched back")
    void aggregatedIsRefused() {
        GraphVocabulary v = GraphMlParser.parse(
                DIR.resolve("session-processor-aggregated.graphml")).vocabulary();
        assertEquals(GraphVocabulary.Mode.AGGREGATED, v.mode());
        assertTrue(v.present(), "the keys ARE there — this is a decision, not a parse failure");
        assertFalse(v.trusted(), "half-trusted EDGE facts are worse than none");
        assertTrue(v.whyNot().contains("PARALLEL"),
                "and the refusal must say how to get a usable file: " + v.whyNot());
    }

    @Test
    @DisplayName("but an AGGREGATED file's NODE facts ARE exact — the refusal is fact-scoped")
    void aggregatedNodeFactsAreStillExact() {
        ProcessorTopology aggregated = GraphMlParser.parse(
                DIR.resolve("session-processor-aggregated.graphml"));
        ProcessorTopology parallel = parallel();

        assertTrue(aggregated.vocabulary().trustedForNodeFacts(),
                "aggregation merges EDGE facts onto one edge per pair; it does not touch nodes");

        // Measured, not assumed: every node fact is identical in both shapes. Refusing them because
        // they shared a document with merged edge facts cost the whole audit-capability win for no
        // reason, which is why the rule is fact-scoped rather than file-scoped.
        for (ProcessorTopology.Node p : parallel.nodes()) {
            ProcessorTopology.Node a = aggregated.node(p.id());
            assertNotNull(a, p.id());
            assertEquals(p.facts(), a.facts(), "node facts differ for " + p.id());
        }
    }

    @Test
    @DisplayName("and an AGGREGATED edge is refused only where it actually merged")
    void aggregatedEdgeTrustIsPerEdge() {
        ProcessorTopology t = GraphMlParser.parse(DIR.resolve("session-processor-aggregated.graphml"));
        GraphVocabulary v = t.vocabulary();

        ProcessorTopology.Edge merged = t.edges().stream()
                .filter(e -> {
                    String c = e.fact("fluxtion.relationshipCount");
                    return c != null && !c.trim().equals("1");
                })
                .findFirst().orElse(null);
        assertNotNull(merged, "this fixture has a pair carrying two references");
        assertTrue(merged.fact("fluxtion.referenceField").contains(","),
                "and the merge is visible: two field names in one value");
        assertFalse(v.trustedForEdgeFacts(merged.facts()),
                "a merged edge cannot attribute its propagates to either reference");

        ProcessorTopology.Edge single = t.edges().stream()
                .filter(e -> !e.facts().isEmpty() && e != merged)
                .filter(e -> {
                    String c = e.fact("fluxtion.relationshipCount");
                    return c == null || c.trim().equals("1");
                })
                .findFirst().orElse(null);
        assertNotNull(single, "most edges carry exactly one relationship");
        assertTrue(v.trustedForEdgeFacts(single.facts()),
                "nothing was collapsed onto this edge, so its facts are exact");
    }

    @Test
    @DisplayName("an unknown MAJOR degrades to absent rather than failing the open")
    void aFutureVersionIsNotAnError() {
        String text = java.nio.file.Path.of("x").toString();   // placeholder, replaced below
        String parallelText = read(DIR.resolve("session-processor-parallel.graphml"))
                .replace("<data key=\"fluxtion.metaVersion\">1.0</data>",
                        "<data key=\"fluxtion.metaVersion\">9.3</data>");
        ProcessorTopology topology = GraphMlParser.parse(parallelText);
        assertFalse(topology.nodes().isEmpty(), "a file from the future is still a file to open");
        GraphVocabulary v = topology.vocabulary();
        assertEquals(9, v.major());
        assertFalse(v.trusted());
        assertTrue(v.whyNot().contains("9.3"), v.whyNot());
        assertNotNull(text);
    }

    // ------------------------------------------------------------------ the facts themselves

    @Test
    @DisplayName("node facts are read, and fluxtion.class wins over the label text")
    void nodeFactsAreRead() {
        ProcessorTopology t = parallel();
        ProcessorTopology.Node boundary = t.node("sessionBoundary");
        assertNotNull(boundary);
        assertEquals("true", boundary.fact("fluxtion.auditCapable"),
                "sessionBoundary implements EventLogSource");
        assertNotNull(boundary.fact("fluxtion.auditCapableVia"));
        assertEquals("telamin.fluxtion.audit.analyser.analyser.session.node.SessionBoundary",
                boundary.className());
        assertTrue(boundary.topologicalRank() > 0);
    }

    @Test
    @DisplayName("a node that cannot log says so — the fact the analyser could never establish")
    void aNodeThatCannotLogIsDistinguishable() {
        ProcessorTopology t = parallel();
        ProcessorTopology.Node queue = t.node("effectQueue");
        assertNotNull(queue);
        // EffectQueue deliberately implements nothing: it is a push target with no logger. Before the
        // vocabulary, its absence from a log was indistinguishable from "did not run".
        assertEquals("false", queue.fact("fluxtion.auditCapable"));
    }

    @Test
    @DisplayName("absent is not false — an unknown key reads as null, never as a default")
    void absentIsNotFalse() {
        ProcessorTopology.Node bare = new ProcessorTopology.Node(
                "n", "", "com.acme.N", ProcessorTopology.Kind.NODE);
        assertNull(bare.fact("fluxtion.auditCapable"));
        assertEquals(-1, bare.topologicalRank());
        assertNull(new ProcessorTopology.Edge("e", "a", "b").propagates());
    }

    @Test
    @DisplayName("the build fingerprint hashes the MODEL, so it does not move with the emission mode")
    void theFingerprintIsModelScopedNotFileScoped() {
        String parallelPrint = parallel().vocabulary().graphFacts().get("fluxtion.sourceFingerprint");
        String aggregatedPrint = GraphMlParser.parse(DIR.resolve("session-processor-aggregated.graphml"))
                .vocabulary().graphFacts().get("fluxtion.sourceFingerprint");

        assertNotNull(parallelPrint, "the emitter declares a fingerprint");
        assertEquals(parallelPrint, aggregatedPrint,
                "PARALLEL and AGGREGATED are two renderings of ONE model, so a fingerprint over the "
                        + "model must not move between them. A fingerprint that changed with the "
                        + "emission mode would report a difference that does not exist — and this "
                        + "repo measured exactly that class of problem when the emitter's byte order "
                        + "was unstable at builder 1.0.64.");
    }

    // ------------------------------------------------------------------ relationships

    @Test
    @DisplayName("one pair, two relationships — the case AGGREGATED cannot express")
    void parallelKeepsTwoRelationshipsOnOnePairApart() {
        ProcessorTopology t = parallel();
        List<ProcessorTopology.Edge> doubled = t.edges().stream()
                .filter(e -> e.source().equals("callbackDispatcher") && e.target().equals("context"))
                .collect(Collectors.toList());
        assertEquals(2, doubled.size(), "this graph has exactly one doubled pair");

        Set<String> keys = doubled.stream()
                .map(ProcessorTopology.Edge::relationshipKey).collect(Collectors.toSet());
        assertEquals(2, keys.size(), "and the two must not collapse to one identity: " + keys);

        // adjacency is a pair question and must NOT double-count
        assertEquals(1, t.childrenOf("callbackDispatcher").stream()
                .filter(id -> id.equals("context")).count());
        assertTrue(t.relationshipCount() > 0);
    }

    @Test
    @DisplayName("the push edge is declared as PUSH — vocabulary that was unreachable until dbcbe17")
    void thePushReferenceIsDeclared() {
        ProcessorTopology t = parallel();
        ProcessorTopology.Edge push = t.edges().stream()
                .filter(e -> e.source().equals("sessionBoundary") && e.target().equals("effectQueue"))
                .findFirst().orElse(null);
        assertNotNull(push, "M44 wires this with @PushReference");
        assertEquals("PUSH", push.refKind());
        assertEquals(Boolean.FALSE, push.propagates(), "a push edge does not propagate");
    }

    @Test
    @DisplayName("topologicalRank agrees with the generated dispatch order across the push edge")
    void rankIsPinnedAgainstDispatchOrder() {
        ProcessorTopology t = parallel();
        int boundary = t.node("sessionBoundary").topologicalRank();
        int queue = t.node("effectQueue").topologicalRank();
        // Pinned rather than trusted. Before fluxtion-builder dbcbe17 this key was an index into
        // object-sorted order, and it came out REVERSED across exactly this edge — effectQueue 2
        // against sessionBoundary 10 — while looking perfectly authoritative. A column of integers
        // is the worst place to be quietly wrong, so it is asserted against the dispatch we know.
        assertTrue(boundary < queue,
                "the decision is invoked before the queue it pushes to; got sessionBoundary="
                        + boundary + " effectQueue=" + queue);
        int gate = t.node("operationGate").topologicalRank();
        assertTrue(gate < boundary, "the gate guards the decision, so it dispatches first");
    }

    private static String read(Path p) {
        try {
            return java.nio.file.Files.readString(p);
        } catch (java.io.IOException e) {
            throw new AssertionError(p + " is a committed fixture and must be readable", e);
        }
    }
}
