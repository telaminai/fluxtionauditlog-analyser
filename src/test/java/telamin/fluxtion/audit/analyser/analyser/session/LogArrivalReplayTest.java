package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.generated.SessionProcessor;
import telamin.fluxtion.audit.analyser.analyser.session.node.AuditInstallation;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M44.2 — the M35.2 rule, and the review's F3, as replays against the processor.
 *
 * <p>Until now this rule lived in {@code MainFrame.repairLoadedGraph} and could only be checked by
 * running the application. What it decides is whether someone loses a graph they were reading, and the
 * defect it prevents is silent: open a second log and the first log's topology stays on screen, while
 * coverage, shading and step-through all describe a graph with nothing to do with the records.
 */
class LogArrivalReplayTest {

    private static final String AUDITOR = "EventLogManager";

    /** A graph carrying the given node ids, with audit installed. */
    private static SessionEvents.GraphObserved graph(Set<String> declared) {
        return new SessionEvents.GraphObserved(true, "/g.graphml", "OPENED", declared,
                List.of(AUDITOR, "PriceListener", "QuotePublisher"));
    }

    private static SessionEvents.LogObserved log(Set<String> logged) {
        return new SessionEvents.LogObserved(true, "/l.yaml", "DECLARED", logged, logged.size(), logged.size());
    }

    private static FakeSessionAdapter drive(SessionDriver[] out, Object... facts) {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver driver = new SessionDriver(adapter);
        out[0] = driver;
        for (Object f : facts) {
            driver.submit(f);
        }
        return adapter;
    }

    @Test
    @DisplayName("a log arrives and the open graph describes it — the graph is KEPT")
    void aFittingGraphSurvivesTheArrival() {
        SessionDriver[] d = new SessionDriver[1];
        FakeSessionAdapter adapter = drive(d,
                graph(Set.of("priceListener", "spreadCalculator", "quotePublisher")),
                log(Set.of("priceListener", "quotePublisher")));

        assertEquals(0, adapter.countOf(SessionEffects.CloseGraphEffect.class),
                "the graph describes this log; closing it would cost the reader their topology");
        assertFalse(adapter.graphClosed);
        assertFalse(d[0].auditSink().matching("graphFitsThisLog").isEmpty());
    }

    @Test
    @DisplayName("a log arrives and the open graph is RESIDUE — it is judged and closed")
    void aStaleGraphIsClosedOnArrival() {
        SessionDriver[] d = new SessionDriver[1];
        FakeSessionAdapter adapter = drive(d,
                graph(Set.of("supermarketTill", "shelfStock", "checkoutQueue")),
                log(Set.of("priceListener", "quotePublisher", "orderTracker")));

        assertEquals(1, adapter.countOf(SessionEffects.CloseGraphEffect.class));
        assertTrue(adapter.graphClosed, "and it must ACTUALLY close, not merely be asked to");
        assertNotNull(adapter.lastWarning);
        assertTrue(adapter.lastWarning.contains("Reopen it deliberately"), adapter.lastWarning);
        assertFalse(d[0].auditSink().matching("graphDoesNotDescribeThisLog").isEmpty());
    }

    @Test
    @DisplayName("a log with nothing logged cannot convict a graph — silence is not evidence")
    void anEmptyLogDoesNotCostYouTheGraph() {
        SessionDriver[] d = new SessionDriver[1];
        FakeSessionAdapter adapter = drive(d,
                graph(Set.of("priceListener", "quotePublisher")),
                log(Set.of()));

        assertEquals(0, adapter.countOf(SessionEffects.CloseGraphEffect.class),
                "a log that logged nothing says nothing about the graph, and closing on no evidence "
                        + "would cost someone a graph for a log's silence");
        assertFalse(adapter.graphClosed);

        // Asserting the DECISION line, not any line mentioning the idea. The first version of this
        // test matched "cannotSay" anywhere in the sink — and passed on a line Pairing writes when it
        // sees the graph, BEFORE any log exists. A mutation to LogArrival left it green, which is how
        // the hollow assertion was found. The protection actually lives in GraphPairing.of, which
        // returns applies=true for an empty log because a silent log cannot convict a graph.
        assertFalse(d[0].auditSink().matching("graphFitsThisLog").isEmpty(),
                "the decision must record that it KEPT the graph, and why");
        assertTrue(d[0].processor().pairing.canSay(), "an open log and an open graph can be compared");
        assertFalse(d[0].processor().pairing.doesNotApply(),
                "and a log with no node output does not count against the graph");
    }

    @Test
    @DisplayName("a log arriving with no graph open decides nothing")
    void noGraphNoDecision() {
        SessionDriver[] d = new SessionDriver[1];
        FakeSessionAdapter adapter = drive(d, log(Set.of("priceListener")));
        assertEquals(0, adapter.countOf(SessionEffects.CloseGraphEffect.class));
        assertFalse(d[0].auditSink().matching("nothingToJudge").isEmpty());
    }

    @Test
    @DisplayName("a pairing drawn from a SAMPLE says so — it is not a whole-log claim")
    void aSampledVerdictSaysItIsSampled() {
        SessionDriver[] d = new SessionDriver[1];
        FakeSessionAdapter adapter = drive(d,
                graph(Set.of("supermarketTill")),
                new SessionEvents.LogObserved(true, "/l.yaml", "DECLARED",
                        Set.of("priceListener", "quotePublisher"), 200, 41_000));

        assertTrue(adapter.graphClosed);
        assertTrue(adapter.lastWarning.contains("first 200 of 41000"),
                "the numbers describe what was scanned, and the sentence must say so: "
                        + adapter.lastWarning);
    }

    // ---------------------------------------------------------------- review F3: three questions

    @Test
    @DisplayName("audit installation is answerable WITHOUT a log — which is the whole point of F3")
    void auditInstallationNeedsNoLog() {
        SessionDriver[] d = new SessionDriver[1];
        drive(d, graph(Set.of("priceListener")));
        SessionProcessor p = d[0].processor();

        assertEquals(AuditInstallation.Verdict.ENABLED, p.auditInstallation.verdict(),
                "the auditor is on the graph, and no log was needed to say so");
        assertFalse(p.pairing.canSay(), "while the pairing correctly cannot answer without a log");
    }

    @Test
    @DisplayName("a graph with no auditor is NOT_ENABLED — the earliest catchable form of the mistake")
    void aGraphWithoutAnAuditorIsAProblem() {
        SessionDriver[] d = new SessionDriver[1];
        drive(d, new SessionEvents.GraphObserved(true, "/g.graphml", "OPENED",
                Set.of("priceListener"), List.of("PriceListener", "QuotePublisher")));
        SessionProcessor p = d[0].processor();

        assertEquals(AuditInstallation.Verdict.NOT_ENABLED, p.auditInstallation.verdict());
        assertTrue(p.auditInstallation.isProblem(),
                "this processor will write nothing at all, however carefully its nodes narrate "
                        + "themselves — and it is knowable before the run");
    }

    @Test
    @DisplayName("with no graph, both refuse to answer rather than guessing from the log")
    void noGraphMeansNoVerdictFromEither() {
        SessionDriver[] d = new SessionDriver[1];
        drive(d, log(Set.of("priceListener", "quotePublisher")));
        SessionProcessor p = d[0].processor();

        assertEquals(AuditInstallation.Verdict.UNKNOWN, p.auditInstallation.verdict(),
                "audit installation is never inferred from a log's contents");
        assertFalse(p.pairing.canSay());
    }

    @Test
    @DisplayName("the two verdicts are independent — a graph can fit a log and still write nothing")
    void theQuestionsDoNotDetermineEachOther() {
        SessionDriver[] d = new SessionDriver[1];
        FakeSessionAdapter adapter = drive(d,
                new SessionEvents.GraphObserved(true, "/g.graphml", "OPENED",
                        Set.of("priceListener", "quotePublisher"),
                        List.of("PriceListener", "QuotePublisher")),   // no auditor
                log(Set.of("priceListener", "quotePublisher")));       // but a perfect pairing
        SessionProcessor p = d[0].processor();

        assertTrue(p.pairing.canSay());
        assertFalse(p.pairing.doesNotApply(), "the graph describes this log exactly");
        assertEquals(AuditInstallation.Verdict.NOT_ENABLED, p.auditInstallation.verdict(),
                "and it still cannot produce an audit log — merging these into one verdict would "
                        + "have made this state unsayable");
        assertFalse(adapter.graphClosed, "a fitting graph is kept regardless of the audit answer");
    }
}
