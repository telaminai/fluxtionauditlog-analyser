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
        assertTrue(d.snapshot().evidencePending(), "but it is outstanding, and the snapshot says so");
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
        assertFalse(d.snapshot().evidencePending(), "the scan has reported");
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
        settle(d, g);                                       // the open's own scan reports
        int afterOpen = scans(a).size();
        d.post(new SessionEvents.LogContentObserved(g, 1, 0, "UNKNOWN", 0, null));
        assertEquals(afterOpen + 1, scans(a).size(), "the first signature asks");
        settle(d, g);
        d.post(new SessionEvents.LogContentObserved(g, 1, 0, "UNKNOWN", 0, null));
        assertEquals(afterOpen + 1, scans(a).size(), "an unchanged signature asks nothing");
        d.post(new SessionEvents.LogContentObserved(g, 1, 0, "UNKNOWN", 1, "disk said no"));
        assertEquals(afterOpen + 2, scans(a).size(), "a failed read with new damage moved it");
        settle(d, g);
        d.post(new SessionEvents.LogContentObserved(g, 1, 0, "UNKNOWN", 1, "disk said no"));
        assertEquals(afterOpen + 2, scans(a).size(), "the same failure again asks nothing (the repeated-failure skip)");
        assertEquals("disk said no", d.snapshot().followReadFailure(), "why the read failed is published");
        d.post(new SessionEvents.LogContentObserved(g, 1, 40, "UNKNOWN", 1, null));
        assertEquals(afterOpen + 3, scans(a).size(), "a growing pending frame moved it");
        assertNull(d.snapshot().followReadFailure(), "a poll that read clears the failure");
        settle(d, g);
        d.post(new SessionEvents.LogContentObserved(g, 1, 40, "COMPLETE", 1, null));
        assertEquals(afterOpen + 4, scans(a).size(), "a marker with no record moved it");
        settle(d, g);
        d.post(new SessionEvents.LogContentObserved(g, 2, 0, "COMPLETE", 1, null));
        assertEquals(afterOpen + 5, scans(a).size(), "an appended record moved it (W1)");
        settle(d, g);
        d.post(new SessionEvents.LogContentObserved(g - 1, 9, 0, "UNKNOWN", 0, null));
        assertEquals(afterOpen + 5, scans(a).size(), "a signature for another generation is refused");
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
    @DisplayName("An identical rescan keeps the findings it holds, and publishes nothing")
    void anIdenticalRescanChangesNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.ProducerFindingsObserved(g, EMPTY_LOG));
        var held = d.snapshot();
        d.post(new SessionEvents.ProducerFindingsObserved(g,
                ProducerDiagnostics.of(new LogIndex(), i -> null, List.of(), List.of(), false)));
        assertSame(held, d.snapshot(), "equal findings are not a change");
        assertSame(EMPTY_LOG, d.snapshot().producerFindings(), "the object held is kept");
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

    private static void reopen(SessionDriver d, FakeSessionAdapter a, String path, boolean followable) {
        a.pendingOpens = true;
        long op = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(op, path, null, null, false));
        a.pendingOpens = false;
        d.submit(new SessionEvents.LogOpened(op, path, null, Set.of("a"), 1, 1, "TRACE", null, followable));
    }

    @Test
    @DisplayName("Follow continues through a reload of a followable log, and stops at one that cannot be followed")
    void followSurvivesAReloadOnlyWhereItCanRun() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        reopen(d, a, "/f.yaml", true);
        assertFalse(d.snapshot().following(), "opening a log never turns Follow on");
        d.post(new SessionEvents.FollowToggled(d.snapshot().logGeneration(), true));
        reopen(d, a, "/f.yaml", true);                       // a rotation, or the live re-read
        assertTrue(d.snapshot().following(), "a reload does not silently stop Follow");
        reopen(d, a, "/set.zip", false);
        assertFalse(d.snapshot().following(), "a log that cannot be followed is not reported as followed");
        reopen(d, a, "/f.yaml", true);
        assertFalse(d.snapshot().following(), "and Follow does not come back on by itself");
    }

    @Test
    @DisplayName("A content change leaves the evidence pending until the rescan reports, even when it finds the same")
    void aRescanThatFindsTheSameStillSettles() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        TimeOrderReport clean = TimeOrderReport.clean();
        d.post(new SessionEvents.ProducerFindingsObserved(g, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(g, clean));
        d.post(new SessionEvents.LogContentObserved(g, 2, 0, "UNKNOWN", 0, null));
        assertTrue(d.snapshot().evidencePending(), "the held findings describe the earlier content");
        d.post(new SessionEvents.ProducerFindingsObserved(g, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(g, clean));
        assertFalse(d.snapshot().evidencePending(), "an identical result still settles it — a surface may render");
    }

    /** The adapter's side of a scan: both results, for the generation asked about. */
    private static void settle(SessionDriver d, long g) {
        d.post(new SessionEvents.ProducerFindingsObserved(g, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(g, TimeOrderReport.clean()));
    }

    @Test
    @DisplayName("An append marks the evidence outstanding in the SAME cycle as the count moves, and asks for one scan")
    void anAppendIsOutstandingEvidenceAtOnce() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long g = d.snapshot().logGeneration();
        settle(d, g);
        int before = scans(a).size();
        d.post(new SessionEvents.LogAppended(g, Set.of("a"), 1, 2, "TRACE"));
        assertEquals(2, d.snapshot().total(), "the count moved");
        assertTrue(d.snapshot().evidencePending(), "and the same snapshot says the evidence is being re-derived");
        d.post(new SessionEvents.LogContentObserved(g, 2, 0, "UNKNOWN", 0, null));
        assertEquals(before + 1, scans(a).size(), "the poll's content report coalesces into the outstanding scan");
    }

    /**
     * Review F1 (PR #43): coalescing is sound only WITHIN a generation. A scan the adapter legitimately dropped — its
     * load threw after LogOpened, before the store was installed — never reports, so its outstanding flag must not
     * outlive its generation. Before the fix the next generation's request coalesced into the dead one: no scan was
     * ever asked for again, and the evidence stayed pending for every later open and append.
     */
    @Test
    @DisplayName("A scan that never reports does not swallow the next generation's scan")
    void aDroppedScanDoesNotSwallowTheNextGenerationsScan() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "/f.yaml");
        long first = d.snapshot().logGeneration();
        assertEquals(List.of(first), scans(a).stream().map(SessionEffects.ScanLogEvidenceEffect::generation).toList(),
                "control: gen 1 asked for its scan");
        // gen 1's results never arrive: the adapter dropped the scan

        SessionFixtures.openLog(d, a, "/g.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
        long second = d.snapshot().logGeneration();
        assertNotEquals(first, second);
        assertEquals(List.of(first, second),
                scans(a).stream().map(SessionEffects.ScanLogEvidenceEffect::generation).toList(),
                "the new generation asks for a scan of its own");

        d.post(new SessionEvents.ProducerFindingsObserved(second, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(second, oneViolation()));
        assertEquals(EMPTY_LOG, d.snapshot().producerFindings(), "gen 2's results publish");
        assertFalse(d.snapshot().timeOrder().isClean());
        assertFalse(d.snapshot().evidencePending(), "and settle it — the surfaces may render");

        d.post(new SessionEvents.LogAppended(second, Set.of("a"), 1, 2, "TRACE"));
        assertEquals(3, scans(a).size(), "and a later append still asks");
    }
}
