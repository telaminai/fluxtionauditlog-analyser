package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.CoveragePolicy;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M44.2 — the third of the review's F3 questions: what may a surface actually ASSERT?
 *
 * <p>Pairing and audit installation are facts. This is a policy, and it is the only one of the three
 * that grants or withholds permission. Two of the four refusals below did not exist anywhere before —
 * coverage would have printed a number over a graph whose processor cannot log, and over a graph a
 * person deliberately opened against a log it does not describe.
 */
class CoverageClaimTest {

    private static final String AUDITOR = "EventLogManager";

    private static SessionDriver driver() {
        return new SessionDriver(new FakeSessionAdapter());
    }

    private static SessionEvents.GraphObserved graph(String source, Set<String> declared, boolean audited) {
        return new SessionEvents.GraphObserved(true, "/g.graphml", source, declared,
                audited ? List.of(AUDITOR, "PriceListener") : List.of("PriceListener"));
    }

    private static SessionEvents.LogObserved log(Set<String> logged, String level, int sampled, int total) {
        return new SessionEvents.LogObserved(true, "/l.yaml", "DECLARED", logged, sampled, total, level);
    }

    private static CoveragePolicy.Assessment assess(Object... facts) {
        SessionDriver d = driver();
        for (Object f : facts) {
            d.submit(f);
        }
        return d.processor().coverageClaim.assessment();
    }

    @Test
    @DisplayName("no graph, or no log, and coverage has nothing to compare")
    void bothArtefactsAreRequired() {
        assertEquals(CoveragePolicy.Claim.REFUSED,
                assess(log(Set.of("a"), "TRACE", 1, 1)).claim());
        assertEquals(CoveragePolicy.Claim.REFUSED,
                assess(graph("OPENED", Set.of("a"), true)).claim());
    }

    @Test
    @DisplayName("an INFERRED graph is refused — the subtraction is empty by construction")
    void anInferredGraphMakesCoverageATautology() {
        CoveragePolicy.Assessment a = assess(
                graph("READER_INFERRED", Set.of("priceListener"), true),
                log(Set.of("priceListener"), "TRACE", 1, 1));

        assertEquals(CoveragePolicy.Claim.REFUSED, a.claim());
        assertFalse(a.allowed());
        assertTrue(a.reason().contains("inferred from what ran"), a.reason());
        // This rule already existed, in ActionExecutor.doCoverage. What is new is that it is now
        // decided in one place with the other three, instead of being the only one anybody checked.
    }

    @Test
    @DisplayName("NEW — a graph whose processor cannot log at all is refused")
    void aProcessorThatWritesNothingCannotBeScored() {
        CoveragePolicy.Assessment a = assess(
                graph("OPENED", Set.of("priceListener"), false),
                log(Set.of("priceListener"), "TRACE", 1, 1));

        assertEquals(CoveragePolicy.Claim.REFUSED, a.claim());
        assertTrue(a.reason().contains("without audit logging"), a.reason());
        assertTrue(a.reason().contains("blame the nodes for the build"),
                "and it must say why the number would be misleading, not just refuse: " + a.reason());
    }

    @Test
    @DisplayName("NEW — a deliberately opened graph that does NOT describe this log is refused")
    void theM353ExceptionDoesNotLicenceScoring() {
        CoveragePolicy.Assessment a = assess(
                graph("OPENED", Set.of("supermarketTill", "shelfStock"), true),
                log(Set.of("priceListener", "quotePublisher", "orderTracker"), "TRACE", 3, 3));

        assertEquals(CoveragePolicy.Claim.REFUSED, a.claim());
        assertTrue(a.reason().contains("different system or build"), a.reason());
        // M35.3 keeps a graph a person opened against a mismatched log — announce, never forbid. That
        // is right, and it left a gap: coverage would score against it in silence. Keeping the graph
        // and refusing the NUMBER are not in tension; they are the same respect for intent.
        assertTrue(a.reason().contains("kept because"), a.reason());
    }

    @Test
    @DisplayName("a level below TRACE is QUALIFIED, not refused — the number is still computable")
    void aCoarseLevelQualifiesRatherThanRefuses() {
        CoveragePolicy.Assessment a = assess(
                graph("OPENED", Set.of("priceListener"), true),
                log(Set.of("priceListener"), "INFO", 1, 1));

        assertEquals(CoveragePolicy.Claim.QUALIFIED, a.claim());
        assertTrue(a.allowed(), "refusing a computable number is as much a failure as printing a "
                + "meaningless one");
        assertTrue(a.reason().contains("not TRACE"), a.reason());
        assertTrue(a.reason().contains("not proof a node never ran"), a.reason());
    }

    @Test
    @DisplayName("a sampled pairing qualifies too — it is not a whole-log claim")
    void aSampledPairingQualifies() {
        CoveragePolicy.Assessment a = assess(
                graph("OPENED", Set.of("priceListener", "quotePublisher"), true),
                log(Set.of("priceListener", "quotePublisher"), "TRACE", 500, 41_000));

        assertEquals(CoveragePolicy.Claim.QUALIFIED, a.claim());
        assertTrue(a.reason().contains("first 500 of 41000"), a.reason());
    }

    @Test
    @DisplayName("declared, fitting, TRACE — the one case where coverage means what it says")
    void everythingHoldingIsFull() {
        CoveragePolicy.Assessment a = assess(
                graph("OPENED", Set.of("priceListener", "quotePublisher"), true),
                log(Set.of("priceListener", "quotePublisher"), "TRACE", 2, 2));

        assertEquals(CoveragePolicy.Claim.FULL, a.claim());
        assertTrue(a.allowed());
    }

    @Test
    @DisplayName("the claim reassesses when the graph closes — permission is not sticky")
    void closingTheGraphWithdrawsTheClaim() {
        SessionDriver d = driver();
        d.submit(graph("OPENED", Set.of("priceListener"), true));
        d.submit(log(Set.of("priceListener"), "TRACE", 1, 1));
        assertEquals(CoveragePolicy.Claim.FULL, d.processor().coverageClaim.assessment().claim());

        d.submit(new SessionEvents.GraphObserved(false, null, null, Set.of(), List.of()));
        assertEquals(CoveragePolicy.Claim.REFUSED, d.processor().coverageClaim.assessment().claim(),
                "a claim granted against a graph must not outlive it");
    }

    @Test
    @DisplayName("the refusal is always in the record, with its reason")
    void theDecisionIsAudited() {
        SessionDriver d = driver();
        d.submit(graph("READER_INFERRED", Set.of("a"), true));
        d.submit(log(Set.of("a"), "TRACE", 1, 1));

        assertFalse(d.auditSink().matching("coverageClaim").isEmpty());
        assertFalse(d.auditSink().matching("REFUSED").isEmpty(),
                "a surface that refuses must leave evidence of refusing, not just decline");
    }
}
