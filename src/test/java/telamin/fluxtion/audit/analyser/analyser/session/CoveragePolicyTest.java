package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coverage policy, tested with <b>no processor, no driver and no events</b> — which is the point
 * of it being a plain class.
 *
 * <p>{@code CoverageClaimTest} still drives the same rules through the real graph, because a policy
 * nobody wired up is a policy nobody applies. This is the other half: every branch reachable directly,
 * so the ones that are awkward to stage as an event sequence are still covered.
 */
class CoveragePolicyTest {

    private static final GraphPairing FITS = GraphPairing.of(
            Set.of("a", "b"), Set.of("a", "b"));
    private static final GraphPairing DOES_NOT_FIT = GraphPairing.of(
            Set.of("x", "y"), Set.of("a", "b", "c"));

    private static CoveragePolicy.Assessment decide(String provenance,
                                                    CoveragePolicy.AuditInstalled audit,
                                                    GraphPairing pairing, String level) {
        return CoveragePolicy.decide(true, true, provenance, audit, pairing, 10, 10, level);
    }

    @Test
    @DisplayName("the four refusals, in the order they are checked")
    void theRefusalsAreOrdered() {
        // Order matters and is asserted: an inferred graph is refused for BEING inferred, even when it
        // also has no auditor, because that is the reason a reader can act on.
        assertTrue(decide("READER_INFERRED", CoveragePolicy.AuditInstalled.NO, DOES_NOT_FIT, "INFO")
                .reason().contains("inferred from what ran"));
        assertTrue(decide("OPENED", CoveragePolicy.AuditInstalled.NO, DOES_NOT_FIT, "INFO")
                .reason().contains("without audit logging"));
        assertTrue(decide("OPENED", CoveragePolicy.AuditInstalled.YES, DOES_NOT_FIT, "INFO")
                .reason().contains("different system or build"));
    }

    @Test
    @DisplayName("UNKNOWN audit installation does not refuse — only a positive NO does")
    void unknownAuditIsNotARefusal() {
        CoveragePolicy.Assessment a =
                decide("OPENED", CoveragePolicy.AuditInstalled.UNKNOWN, FITS, "TRACE");
        assertEquals(CoveragePolicy.Claim.FULL, a.claim(),
                "not knowing whether audit is installed is not evidence that it is not — refusing on "
                        + "UNKNOWN would be the same error as excluding a node we cannot prove silent");
    }

    @Test
    @DisplayName("a null pairing does not refuse — cannot-say is not does-not-fit")
    void aMissingPairingIsNotAMismatch() {
        CoveragePolicy.Assessment a =
                decide("OPENED", CoveragePolicy.AuditInstalled.YES, null, "TRACE");
        assertEquals(CoveragePolicy.Claim.FULL, a.claim());
    }

    @Test
    @DisplayName("level is checked before sampling — the coarser caveat wins")
    void theLevelCaveatOutranksTheSampleCaveat() {
        CoveragePolicy.Assessment a = CoveragePolicy.decide(
                true, true, "OPENED", CoveragePolicy.AuditInstalled.YES, FITS, 500, 41_000, "INFO");
        assertEquals(CoveragePolicy.Claim.QUALIFIED, a.claim());
        assertTrue(a.reason().contains("not TRACE"),
                "both caveats apply; the one that changes what a MISSING node means is the one to "
                        + "lead with: " + a.reason());
    }

    @Test
    @DisplayName("an unknown level string is treated as not-TRACE, which is the safe direction")
    void anUnrecognisedLevelQualifies() {
        assertEquals(CoveragePolicy.Claim.QUALIFIED,
                decide("OPENED", CoveragePolicy.AuditInstalled.YES, FITS, "VERBOSE").claim());
    }

    @Test
    @DisplayName("no provenance at all is not treated as inferred")
    void absentProvenanceIsNotInferred() {
        assertEquals(CoveragePolicy.Claim.FULL,
                decide(null, CoveragePolicy.AuditInstalled.YES, FITS, "TRACE").claim());
    }

    @Test
    @DisplayName("every assessment carries a reason — a bare refusal is never acceptable")
    void everyAnswerExplainsItself() {
        for (CoveragePolicy.Assessment a : new CoveragePolicy.Assessment[]{
                CoveragePolicy.decide(false, true, "OPENED", CoveragePolicy.AuditInstalled.YES, FITS, 1, 1, "TRACE"),
                CoveragePolicy.decide(true, false, "OPENED", CoveragePolicy.AuditInstalled.YES, FITS, 1, 1, "TRACE"),
                decide("READER_INFERRED", CoveragePolicy.AuditInstalled.YES, FITS, "TRACE"),
                decide("OPENED", CoveragePolicy.AuditInstalled.NO, FITS, "TRACE"),
                decide("OPENED", CoveragePolicy.AuditInstalled.YES, DOES_NOT_FIT, "TRACE"),
                decide("OPENED", CoveragePolicy.AuditInstalled.YES, FITS, "INFO"),
                decide("OPENED", CoveragePolicy.AuditInstalled.YES, FITS, "TRACE")}) {
            assertTrue(a.reason() != null && a.reason().length() > 20,
                    "a surface has to be able to say why: " + a);
        }
    }
}
