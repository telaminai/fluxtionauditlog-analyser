package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UPS-1, the review of 9474c687, finding 3, through the generated session processor (rule 9): the count of sampled
 * records whose node logs were withheld arrives as part of the arrival fact, and the published pairing and coverage
 * claim say no node output was READ — never that none was recorded. DEMO values only.
 */
class WithheldPairingTest {

    private static final Set<String> DECLARED = Set.of("alarmMonitor", "alarmPublisher");

    @Test
    @DisplayName("an all-withheld log: the published pairing and claim say read, and carry the count")
    void anAllWithheldLogIsNotSilence() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        d.submit(SessionFixtures.graph("/DEMO-CycleAlarmProcessor.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        long op = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(op, "/all-broken.yaml", null, "DECLARED", true));
        d.submit(new SessionEvents.LogOpened(op, "/all-broken.yaml", "DECLARED", Set.of(), 2, 2, "INFO",
                "declared by the opener", false, 2));
        var pairing = d.snapshot().pairing();
        assertNotNull(pairing, "a graph and a log are open, so there is a verdict");
        assertEquals(2, pairing.nodeLogsWithheld(), "the arrival fact's count reached the verdict");
        assertFalse(pairing.reason().contains("recorded"), pairing.reason());
        assertTrue(pairing.reason().contains("no node output was read"), pairing.reason());
        var claim = d.snapshot().claim();
        assertNotNull(claim);
        assertFalse(claim.reason().contains("no node output was recorded"), claim.reason());
    }

    @Test
    @DisplayName("a Follow append that changes only the withheld count moves the pairing")
    void anAppendThatWithholdsMovesThePairing() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        d.submit(SessionFixtures.graph("/DEMO-CycleAlarmProcessor.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        long op = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(op, "/l.yaml", null, "DECLARED", true));
        d.submit(new SessionEvents.LogOpened(op, "/l.yaml", "DECLARED", Set.of(), 1, 1, "INFO",
                "declared by the opener", true, 0));
        assertEquals(0, d.snapshot().pairing().nodeLogsWithheld());
        d.submit(new SessionEvents.LogAppended(d.snapshot().logGeneration(), Set.of(), 1, 1, "INFO", 1));
        assertEquals(1, d.snapshot().pairing().nodeLogsWithheld(), "the append's count is the session's");
        assertTrue(d.snapshot().pairing().reason().contains("withheld"), d.snapshot().pairing().reason());
    }
}
