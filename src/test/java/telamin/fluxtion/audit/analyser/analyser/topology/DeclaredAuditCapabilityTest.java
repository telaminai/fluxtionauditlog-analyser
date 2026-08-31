package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M45.3 — the coverage denominator gets better when the graph declares audit capability.
 *
 * <p>{@code NodeLogging} answers "can this node log" by reading its source, and — correctly —
 * refuses to exclude a node unless it is certain, which means an honest {@code UNKNOWN} whenever the
 * source is missing. <b>Missing source is the normal case for a log someone else produced</b>, so the
 * heuristic fails closed exactly where a user most needs the answer.
 *
 * <p>{@code fluxtion.auditCapable} is the compiler's own answer to the same question and needs no
 * source at all. This proves it reaches the denominator, and that the fallback still works for the
 * graphs — most of them, permanently — that carry no vocabulary.
 */
class DeclaredAuditCapabilityTest {

    private static final Path PARALLEL = Path.of(
            "src/test/resources/topology/vocabulary/session-processor-parallel.graphml");
    private static final Path LEGACY = Path.of(
            "src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.graphml");

    /** No source resolver at all — the position an analyser is in with a stranger's log. */
    private static final Function<String, Optional<String>> NO_SOURCE = fqn -> Optional.empty();

    private static Set<String> authored(ProcessorTopology t) {
        return new LinkedHashSet<>(t.ids());
    }

    @Test
    @DisplayName("a node the GRAPH says cannot log is excluded, with no source available")
    void declaredIncapabilityReachesTheDenominator() {
        ProcessorTopology t = GraphMlParser.parse(PARALLEL);
        // effectQueue is a push target with no logger — it implements nothing. Its absence from a log
        // was previously indistinguishable from "did not run", and unprovable without its source.
        assertEquals("false", t.node("effectQueue").fact("fluxtion.auditCapable"));

        CoverageScope.Scope scope = CoverageScope.of(t, authored(t), NO_SOURCE);

        assertFalse(scope.loggable().contains("effectQueue"),
                "a node that cannot log must not sit in the denominator as if it stayed silent");
        assertEquals(CoverageScope.Reason.SILENT_BY_CONSTRUCTION, scope.reasons().get("effectQueue"));
    }

    @Test
    @DisplayName("a node the graph says CAN log stays counted")
    void declaredCapabilityKeepsANodeCounted() {
        ProcessorTopology t = GraphMlParser.parse(PARALLEL);
        assertEquals("true", t.node("sessionBoundary").fact("fluxtion.auditCapable"));
        CoverageScope.Scope scope = CoverageScope.of(t, authored(t), NO_SOURCE);
        assertTrue(scope.loggable().contains("sessionBoundary"));
    }

    @Test
    @DisplayName("without the vocabulary nothing changes — the heuristic is demoted, not retired")
    void legacyGraphFallsBackAndStaysHonest() {
        ProcessorTopology t = GraphMlParser.parse(LEGACY);
        assertFalse(t.vocabulary().present());
        CoverageScope.Scope scope = CoverageScope.of(t, authored(t), NO_SOURCE);
        // With no vocabulary and no source there is no evidence, so nothing may be excluded for
        // silence. Assuming it would flatter the score, which is the error that cannot be spotted
        // from the output.
        assertTrue(scope.loggable().contains("effectQueue"),
                "no evidence must mean counted, not excluded");
        assertFalse(scope.reasons().containsKey("effectQueue"));
    }

    @Test
    @DisplayName("an AGGREGATED graph is not believed here either — one refusal, applied everywhere")
    void aggregatedDoesNotReachTheDenominator() {
        ProcessorTopology t = GraphMlParser.parse(
                Path.of("src/test/resources/topology/vocabulary/session-processor-aggregated.graphml"));
        assertFalse(t.vocabulary().trusted());
        CoverageScope.Scope scope = CoverageScope.of(t, authored(t), NO_SOURCE);
        assertTrue(scope.loggable().contains("effectQueue"),
                "D-V1 refuses aggregated facts, and the refusal has to hold at every consumer, "
                        + "not only where it was written down");
    }

    @Test
    @DisplayName("the answer says how it was reached — a declared fact and a guess must not look alike")
    void theBasisIsCarried() {
        ProcessorTopology parallel = GraphMlParser.parse(PARALLEL);
        NodeLogging.Answer declared = NodeLogging.of(
                parallel.node("effectQueue"), parallel.vocabulary(), NO_SOURCE);
        assertEquals(NodeLogging.Basis.DECLARED, declared.basis());
        assertTrue(declared.because().contains("fluxtion.auditCapable"), declared.because());

        ProcessorTopology legacy = GraphMlParser.parse(LEGACY);
        NodeLogging.Answer inferred = NodeLogging.of(
                legacy.node("effectQueue"), legacy.vocabulary(), NO_SOURCE);
        assertEquals(NodeLogging.Basis.INFERRED, inferred.basis());
        assertEquals(NodeLogging.Capability.UNKNOWN, inferred.capability(),
                "no vocabulary and no source is not evidence of anything");
    }
}
