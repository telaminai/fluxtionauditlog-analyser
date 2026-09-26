package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M44.4a (spec §13, D-S13.2/.3): the facts that replaced the observation funnel. Each test names the mutation that
 * must turn it red.
 */
class SessionFactsTest {

    private static final String A = "/projects/alpha.properties";
    private static final String B = "/projects/beta.properties";

    @Test
    @DisplayName("P3: a reader-supplied graph has no file and IS a graph — an inferred one refuses coverage")
    void aGraphWithNoFileIsStillOpen() {
        // witness: OpenGraph.isOpen() back to `graphPath != null`
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
        d.submit(SessionFixtures.graph(null, "READER_INFERRED", Set.of("a"), List.of("EventLogManager")));

        assertTrue(d.processor().openGraph.isOpen(), "the processor used to believe no graph was on screen");
        var claim = d.processor().coverageClaim.assessment();
        assertEquals(CoveragePolicy.Claim.REFUSED, claim.claim());
        assertTrue(claim.reason().contains("inferred from what ran"), claim.reason());
    }

    @Test
    @DisplayName("P4: a fact posted during an operation runs after it, and is recorded — never dropped")
    void aFactPostedMidOperationIsQueued() {
        // witness: SessionDriver.post returns without queueing while dispatching
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A).withProfile(B);
        SessionDriver[] holder = new SessionDriver[1];
        SessionDriver d = new SessionDriver(effect -> {
            SessionEvents.Result r = adapter.perform(effect);
            // what MainFrame.closeLog does inside a CloseLogEffect: report the close as a fact
            if (effect instanceof SessionEffects.CloseLogEffect) {
                holder[0].post(new SessionEvents.LogCleared(holder[0].processor().openLog.generation()));
            }
            return r;
        });
        holder[0] = d;
        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), A, TransitionKind.STARTUP_ACTIVATION, "test"));
        SessionFixtures.openLog(d, adapter, "/logs/run.yaml");
        long before = d.auditSink().matching("LogCleared").size();

        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), B, TransitionKind.EXPLICIT_SWITCH, "test"));

        assertTrue(adapter.logClosed);
        assertFalse(d.processor().openLog.isOpen());
        var cleared = d.auditSink().matching("LogCleared");
        assertTrue(cleared.size() > before, "the posted fact ran after the operation");
        assertTrue(cleared.stream().anyMatch(r -> r.contains("noOp")),
                "and was recorded as a no-op, because the LogClosed result had already closed the log: " + cleared);
    }

    @Test
    @DisplayName("P5: a fact about an earlier log is refused as staleFact and changes nothing")
    void aStaleFactIsRefused() {
        // witness: OpenLog.current() without the generation comparison
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        SessionFixtures.openLog(d, adapter, "/one.yaml", "DECLARED", Set.of("a"), 10, 10, "TRACE");
        long first = d.processor().openLog.generation();
        SessionFixtures.openLog(d, adapter, "/two.yaml", "DECLARED", Set.of("b"), 20, 20, "TRACE");

        d.submit(new SessionEvents.LogAppended(first, Set.of("a"), 10, 99, "TRACE"));
        assertEquals(20, d.processor().openLog.total(), "an append read from /one.yaml must not re-scope /two.yaml");
        d.submit(new SessionEvents.LogCleared(first));
        assertTrue(d.processor().openLog.isOpen(), "and a close of /one.yaml must not close /two.yaml");
        assertEquals(2, d.auditSink().matching("staleFact").size());
    }

    @Test
    @DisplayName("post outside an operation runs at once (a mid-cycle SUBMIT stays a violation: SessionReplayTest.singleInFlightIsEnforced)")
    void postOutsideAnOperationRunsAtOnce() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        d.post(SessionFixtures.graph("/g.graphml"));
        assertTrue(d.processor().openGraph.isOpen(), "no operation was running, so the fact ran immediately");
    }
}
