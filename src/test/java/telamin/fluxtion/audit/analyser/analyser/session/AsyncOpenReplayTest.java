package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.generated.SessionProcessor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M44.3 — the open is an operation the processor asks for, answered later. Replayed synchronously on one
 * thread: the fake adapter answers {@code Pending} only when told to, so nothing here is timing-dependent.
 */
class AsyncOpenReplayTest {

    private static final String AUDITOR = "EventLogManager";

    private static SessionEvents.GraphObserved graph(String path, Set<String> declared) {
        return new SessionEvents.GraphObserved(true, path, "OPENED", declared, List.of(AUDITOR, "PriceListener"));
    }

    private static SessionEvents.OpenLogRequested open(SessionDriver d, String location) {
        return new SessionEvents.OpenLogRequested(d.nextOpId(), location, null, "DECLARED", false);
    }

    private static SessionEvents.LogOpened landed(long opId, String location, Set<String> ids) {
        return new SessionEvents.LogOpened(opId, location, "DECLARED", ids, ids.size(), ids.size(), null);
    }

    @Test
    @DisplayName("D-A2: a Pending answer leaves state untouched, settles in one round, and is on the record")
    void pendingLeavesStateUntouched() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        adapter.pendingOpens = true;
        SessionDriver d = new SessionDriver(adapter);
        SessionProcessor p = d.processor();
        d.submit(graph("/g.graphml", Set.of("priceListener")));

        SessionEvents.OpenLogRequested req = open(d, "/a.yaml");
        d.submit(req);

        assertEquals(1, adapter.countOf(SessionEffects.OpenLogEffect.class), "the open was asked for");
        assertFalse(p.openLog.isOpen(), "nothing is open yet — the adapter only STARTED the work");
        assertTrue(p.openGraph.isOpen(), "and nothing was closed");
        assertEquals("opening /a.yaml", p.operationGate.inFlightWhat(), "D-A4: the outstanding operation is reportable");
        assertTrue(p.effectQueue.isEmpty(), "a Pending effect does not re-queue itself — the round loop cannot spin");
        assertFalse(d.auditSink().matching("pending").isEmpty(), "D-A5: asked → pending is visible in the record");
    }

    @Test
    @DisplayName("the landed result opens the log, and the arrival judges the open graph — closing the graph it JUDGED")
    void landedResultOpensAndJudges() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        adapter.pendingOpens = true;
        SessionDriver d = new SessionDriver(adapter);
        SessionProcessor p = d.processor();
        d.submit(graph("/stale.graphml", Set.of("supermarketTill", "shelfStock")));
        SessionEvents.OpenLogRequested req = open(d, "/a.yaml");
        d.submit(req);

        d.submit(landed(req.opId(), "/a.yaml", Set.of("priceListener", "quotePublisher")));

        assertTrue(p.openLog.isOpen());
        assertEquals("/a.yaml", p.openLog.logPath());
        assertNull(p.operationGate.inFlightWhat(), "the operation completed");
        assertEquals(1, adapter.countOf(SessionEffects.CloseGraphEffect.class));
        assertEquals("/stale.graphml", adapter.closedGraphPath, "M44.3a: the close names the graph the decision judged");
        assertTrue(adapter.graphClosed);
    }

    @Test
    @DisplayName("D-A3: a second request SUPERSEDES — the first load's late result is refused and changes nothing")
    void aSupersededLoadIsRefused() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        adapter.pendingOpens = true;
        SessionDriver d = new SessionDriver(adapter);
        SessionProcessor p = d.processor();
        SessionEvents.OpenLogRequested first = open(d, "/slow.yaml");
        SessionEvents.OpenLogRequested second = open(d, "/fast.yaml");
        d.submit(first);
        d.submit(second);

        d.submit(landed(second.opId(), "/fast.yaml", Set.of("priceListener")));
        assertEquals("/fast.yaml", p.openLog.logPath());

        d.submit(landed(first.opId(), "/slow.yaml", Set.of("supermarketTill")));   // the slow one lands late
        assertFalse(p.operationGate.accepted(), "refused: its opId was superseded");
        assertEquals("/fast.yaml", p.openLog.logPath(), "state untouched by the stale result");
        assertFalse(d.auditSink().matching("staleResult").isEmpty(), "D-A5: the superseded operation ends EXPLICITLY");
    }

    @Test
    @DisplayName("M44.3a: a refresh observation of an unchanged log judges NOTHING — only a real arrival does")
    void aRefreshObservationDoesNotJudge() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        adapter.openable.put("/a.yaml", Set.of("priceListener"));
        SessionDriver d = new SessionDriver(adapter);
        d.submit(graph("/a.graphml", Set.of("priceListener")));
        d.submit(open(d, "/a.yaml"));                              // lands synchronously: A/A fits
        assertEquals(0, adapter.countOf(SessionEffects.CloseGraphEffect.class));

        // the person opens a mismatching graph B (kept: intent), then the menu funnel re-observes the log
        d.submit(graph("/b.graphml", Set.of("supermarketTill")));
        d.submit(new SessionEvents.LogObserved(true, "/a.yaml", "DECLARED", Set.of("priceListener"), 1, 1));

        assertEquals(0, adapter.countOf(SessionEffects.CloseGraphEffect.class),
                "the pre-fix version closed B here: an observation of the unchanged log re-judged the new graph");
        assertTrue(d.processor().openGraph.isOpen());
    }

    @Test
    @DisplayName("D-A1: the driver is confined to the thread that built it — a wrong-thread submit is a loud violation")
    void wrongThreadIsAProtocolViolation() throws Exception {
        SessionDriver d = new SessionDriver(new FakeSessionAdapter());
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try { d.submit(open(d, "/a.yaml")); } catch (Throwable t) { caught.set(t); }
        }, "not-the-designated-thread");
        other.start();
        other.join();
        assertNotNull(caught.get(), "a submit from another thread must not silently run");
        assertInstanceOf(SessionDriver.ProtocolViolation.class, caught.get(), String.valueOf(caught.get()));
        assertTrue(caught.get().getMessage().contains("confined"), caught.get().getMessage());
    }

    @Test
    @DisplayName("a load that cannot start answers LogOpenFailed at once and the previous log stays open")
    void aFailedOpenLeavesThePreviousLog() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        adapter.openable.put("/a.yaml", Set.of("priceListener"));
        SessionDriver d = new SessionDriver(adapter);
        d.submit(open(d, "/a.yaml"));
        assertEquals("/a.yaml", d.processor().openLog.logPath());

        SessionEvents.OpenLogRequested req = open(d, "/missing.yaml");
        adapter.pendingOpens = true;
        d.submit(req);
        d.submit(new SessionEvents.LogOpenFailed(req.opId(), "/missing.yaml", "cannot read"));

        assertEquals("/a.yaml", d.processor().openLog.logPath(), "the previous log is still the open one");
        assertNull(d.processor().operationGate.inFlightWhat());
        assertFalse(d.auditSink().matching("openFailed").isEmpty());
    }

    @Test
    @DisplayName("review B2: a project transition supersedes a pending open — its description retires at once, its late result is refused, a later open is unaffected")
    void aProjectRequestRetiresThePendingOpen() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile("/p/.analyser/project.fluxtion-settings");
        adapter.pendingOpens = true;
        SessionDriver d = new SessionDriver(adapter);
        SessionProcessor p = d.processor();
        SessionEvents.OpenLogRequested slow = open(d, "/a.slow");
        d.submit(slow);
        assertEquals("opening /a.slow", p.operationGate.inFlightWhat());

        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/p/.analyser/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "test"));
        assertNull(p.operationGate.inFlightWhat(), "the switch superseded the open: nothing is outstanding any more");

        d.submit(landed(slow.opId(), "/a.slow", Set.of("nodeA")));            // the old load lands late
        assertFalse(p.operationGate.accepted(), "refused");
        assertFalse(p.openLog.isOpen(), "and it opened nothing");
        assertNull(p.operationGate.inFlightWhat(), "a refused result never touches the description");

        SessionEvents.OpenLogRequested next = open(d, "/b.yaml");             // a genuinely newer open
        d.submit(next);
        assertEquals("opening /b.yaml", p.operationGate.inFlightWhat());
        d.submit(landed(slow.opId(), "/a.slow", Set.of("nodeA")));            // the old one lands AGAIN (a retry)
        assertEquals("opening /b.yaml", p.operationGate.inFlightWhat(), "an old completion cannot clear a newer pending load");
        d.submit(landed(next.opId(), "/b.yaml", Set.of("nodeB")));
        assertNull(p.operationGate.inFlightWhat());
        assertEquals("/b.yaml", p.openLog.logPath());
    }
}
