package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderValidator;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M44.5 — the log's own derived state is owned by the session processor, on the real generated processor. The
 * {@code logEvidence} node decides WHEN the evidence is stale and asks for a scan; the adapter performs it and reports
 * the result as a fact; the snapshot publishes it. These tests drive facts in and read the published state out.
 */
class LogEvidenceTest {

    private static final ProducerDiagnostics EMPTY_LOG =
            ProducerDiagnostics.of(new LogIndex(), i -> null, List.of(), List.of(), false);

    private static TimeOrderReport oneViolation() {
        String rec = "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n---\n";
        return TimeOrderValidator.validate(new HeapLogStore("---\n" + rec.formatted(2000) + rec.formatted(1000)).index());
    }

    private static List<SessionEffects.ScanLogEvidenceEffect> scans(FakeSessionAdapter a) {
        return a.performed.stream().filter(SessionEffects.ScanLogEvidenceEffect.class::isInstance)
                .map(SessionEffects.ScanLogEvidenceEffect.class::cast).toList();
    }

    private static SessionDriver opened(FakeSessionAdapter a, String path) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, path, "DECLARED", Set.of("a"), 1, 1, "TRACE");
        return d;
    }

    @Test
    @DisplayName("An open asks for exactly one scan, for the new generation, and publishes nothing until it lands")
    void anOpenRequestsOneScan() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        assertEquals(1, scans(a).size(), "one scan requested: " + a.performed);
        assertEquals(d.snapshot().logGeneration(), scans(a).get(0).generation(), "for the generation now open");
        assertNull(d.snapshot().producerFindings(), "nothing is claimed before the scan lands");
        assertNull(d.snapshot().timeOrder());
        assertFalse(d.snapshot().pending(), "a scheduled scan is not an operation in flight");
    }

    @Test
    @DisplayName("The scan's results are published in the snapshot")
    void resultsArePublished() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        TimeOrderReport order = oneViolation();
        d.post(new SessionEvents.ProducerFindingsObserved(g, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(g, order));
        assertEquals(EMPTY_LOG, d.snapshot().producerFindings());
        assertEquals(order, d.snapshot().timeOrder());
        assertFalse(d.snapshot().timeOrder().isClean(), "control: the report carries a violation");
    }

    @Test
    @DisplayName("A result for another generation is refused, and the snapshot is unchanged")
    void aStaleResultIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long stale = d.snapshot().logGeneration();
        SessionFixtures.openLog(d, a, "/g.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");   // a newer log
        d.post(new SessionEvents.ProducerFindingsObserved(stale, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(stale, oneViolation()));
        assertNull(d.snapshot().producerFindings(), "a scan of the replaced log is never shown over the new one");
        assertNull(d.snapshot().timeOrder());
    }

    @Test
    @DisplayName("A Follow poll asks for a rescan only when the content signature moved")
    void theContentSignatureDecidesARescan() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        int afterOpen = scans(a).size();
        d.post(new SessionEvents.LogContentObserved(g, 1, 100, 0, false));
        assertEquals(afterOpen + 1, scans(a).size(), "the first signature asks");
        d.post(new SessionEvents.LogContentObserved(g, 1, 100, 0, false));
        assertEquals(afterOpen + 1, scans(a).size(), "an unchanged signature asks nothing (the repeated-failure skip)");
        d.post(new SessionEvents.LogContentObserved(g, 1, 100, 0, true));
        assertEquals(afterOpen + 2, scans(a).size(), "a failed read moved it");
        d.post(new SessionEvents.LogContentObserved(g, 1, 140, 40, true));
        assertEquals(afterOpen + 3, scans(a).size(), "a growing pending frame moved it");
        d.post(new SessionEvents.LogContentObserved(g, 2, 180, 0, false));
        assertEquals(afterOpen + 4, scans(a).size(), "an appended record moved it (W1)");
        d.post(new SessionEvents.LogContentObserved(g - 1, 9, 999, 0, false));
        assertEquals(afterOpen + 4, scans(a).size(), "a signature for another generation is refused");
    }

    @Test
    @DisplayName("A close clears the findings, the time order and Follow; a reopen asks again")
    void aCloseClearsAndAReopenAsksAgain() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.ProducerFindingsObserved(g, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(g, oneViolation()));
        d.post(new SessionEvents.FollowToggled(g, true));
        d.post(new SessionEvents.LogCleared(g));
        assertNull(d.snapshot().producerFindings(), "a closed log states no findings");
        assertNull(d.snapshot().timeOrder());
        assertFalse(d.snapshot().following());

        int before = scans(a).size();
        SessionFixtures.openLog(d, a, "/f.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
        assertEquals(before + 1, scans(a).size(), "a reopen asks for a scan of its own");
        assertEquals(d.snapshot().logGeneration(), scans(a).get(scans(a).size() - 1).generation());
    }

    @Test
    @DisplayName("Provenance and who supplied it are the session's, and published")
    void provenanceAndItsSourceArePublished() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        a.pendingOpens = true;
        long op = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(op, "/f.yaml", null, null, false));
        a.pendingOpens = false;
        d.submit(new SessionEvents.LogOpened(op, "/f.yaml", "prod-EU-7", Set.of("a"), 1, 1, "TRACE",
                "environment 'prod' (the log is under its logDir)"));
        assertEquals("prod-EU-7", d.snapshot().provenance());
        assertEquals("environment 'prod' (the log is under its logDir)", d.snapshot().provenanceSource(),
                "a project environment's match is the session's copy, not a frame field");
    }

    @Test
    @DisplayName("Follow on and off is the session's state; a toggle for another log is refused")
    void followIsSessionState() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        assertFalse(d.snapshot().following(), "a new log is not followed");
        d.post(new SessionEvents.FollowToggled(g, true));
        assertTrue(d.snapshot().following());
        d.post(new SessionEvents.FollowToggled(g - 1, false));
        assertTrue(d.snapshot().following(), "a stale toggle changes nothing");
        d.post(new SessionEvents.FollowToggled(g, false));
        assertFalse(d.snapshot().following());
    }
}
