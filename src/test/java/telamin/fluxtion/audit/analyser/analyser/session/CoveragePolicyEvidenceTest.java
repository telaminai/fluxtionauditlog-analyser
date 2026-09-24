package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.1 acceptance 2, the downstream half. {@link GraphPairing#applies()} is a retention policy; before this
 * slice, two retained-but-unproven pairings reached {@link CoveragePolicy.Claim#FULL}, whose sentence says the
 * graph describes the log. Reproduced in review round 3: a log with no node output, TRACE, an installed
 * auditor — FULL. A change to the pairing's reason string would not have caught it; the gate is here.
 */
class CoveragePolicyEvidenceTest {

    private static CoveragePolicy.Assessment decide(GraphPairing pairing, int sampled, int total) {
        return CoveragePolicy.decide(true, true, "OPENED", CoveragePolicy.AuditInstalled.YES,
                pairing, sampled, total, "TRACE");
    }

    @Test
    @DisplayName("no node output never reaches FULL, and never says the graph describes the log")
    void noNodeOutputIsNotAFit() {
        CoveragePolicy.Assessment a = decide(GraphPairing.of(Set.of("a", "b"), Set.of()), 10, 10);
        assertEquals(CoveragePolicy.Claim.QUALIFIED, a.claim(), a.reason());
        assertTrue(a.reason().contains("could not establish that this graph describes this log"), a.reason());
        assertFalse(a.reason().contains("is declared, declares every"), "not FULL's sentence: " + a.reason());
    }

    @Test
    @DisplayName("a graph retained on a partial match is QUALIFIED, not FULL")
    void aRetainedPartialMatchIsQualified() {
        GraphPairing partial = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "x"));
        assertTrue(partial.applies());
        CoveragePolicy.Assessment a = decide(partial, 10, 10);
        assertEquals(CoveragePolicy.Claim.QUALIFIED, a.claim(), a.reason());
        assertTrue(a.reason().contains("Kept is not the same as fits"), a.reason());
    }

    @Test
    @DisplayName("a complete, whole-log, TRACE pairing is still FULL — the gate refuses nothing it should allow")
    void aCompletePairingIsStillFull() {
        CoveragePolicy.Assessment a = decide(GraphPairing.of(Set.of("a", "b"), Set.of("a", "b")), 10, 10);
        assertEquals(CoveragePolicy.Claim.FULL, a.claim(), a.reason());
        assertTrue(a.reason().contains("declares every node id this log writes"), a.reason());
        assertFalse(a.reason().contains("describes this log"), "states what was compared: " + a.reason());
    }

    @Test
    @DisplayName("a pairing that carries its own sampled scope is qualified by it, stated correctly")
    void aPairingsOwnScopeQualifies() {
        GraphPairing sampled = GraphPairing.of(Set.of("a"), Set.of("a")).withScope(500, 501);
        CoveragePolicy.Assessment a = decide(sampled, 0, 0);
        assertEquals(CoveragePolicy.Claim.QUALIFIED, a.claim(), a.reason());
        assertTrue(a.reason().contains("first 500 of 501 records"), a.reason());
        assertFalse(a.reason().contains("first 0 of 0"), a.reason());
    }
}
