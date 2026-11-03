package telamin.fluxtion.audit.analyser.analyser.session;

import com.telamin.fluxtion.runtime.audit.AuditStreamHeader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamHeader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence-set key (spec-evidence-set-key.md §4.5, acceptance 3–5, 8, 9) on the REAL generated session processor: the
 * {@code evidenceSet} node pairs the log's stream headers with the open graph's fingerprint, and the snapshot publishes
 * MATCH / MISMATCH / UNVERIFIED with both fingerprints. Facts in, published state out — as {@code LogEvidenceTest}.
 */
class EvidenceSetTest {

    static final String SHAPE_A = "de849dc6785ee1f13da506afebd8db510ae090af3f6bbcb8ef1d2bdfad4e2c8e";
    static final String SHAPE_B = "ef90a3a6d0ddd03608768ac65105260410841401f6d6b69516d12a28ac64f92b";

    /** A header as the runtime writes it, read by the analyser's own parser. */
    static StreamHeader header(String fingerprint, String runId, int firstRecord) {
        Map<String, String> host = new LinkedHashMap<>();
        if (runId != null) {
            host.put("serverId", "pricing-1");
            host.put("runId", runId);
            host.put("group", "pricing");
            host.put("name", "quoter");
            host.put("generation", "1");
        }
        String text = new AuditStreamHeader("com.acme.TradeProcessor", fingerprint, "1.0.84", "1.1.4",
                "2026-11-03T09:15:02.118Z", host).toString();
        return StreamHeader.parse(text, firstRecord);
    }

    static SessionEvents.GraphOpened graph(String fingerprint) {
        return new SessionEvents.GraphOpened("/graphs/quote.graphml", "OPENED", Set.of("a"), List.of("EventLogManager"), null, fingerprint);
    }

    private static SessionDriver opened(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/quote.yaml", "DECLARED", Set.of("a"), 3, 3, "TRACE");
        return d;
    }

    private static EvidenceSetState evidence(SessionDriver d) {
        return d.snapshot().evidenceSet();
    }

    @Test
    @DisplayName("Fingerprints equal: graph shape matches, both fingerprints published")
    void equalFingerprintsMatch() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.StreamHeadersObserved(g, List.of(header(SHAPE_A, "run-1", 0)), 3));
        assertNull(evidence(d).verdict(), "no graph: no pair to judge");
        d.post(graph(SHAPE_A));
        EvidenceSetState e = evidence(d);
        assertEquals(EvidenceSetState.Verdict.MATCH, e.verdict());
        assertEquals("graph shape matches", e.line());
        assertEquals(SHAPE_A, e.logFingerprint());
        assertEquals(SHAPE_A, e.graphFingerprint());
        assertEquals("MATCH", e.toMap().get("evidenceSet"));
        assertEquals(SHAPE_A, e.toMap().get("logSourceFingerprint"));
        assertEquals(SHAPE_A, e.toMap().get("graphSourceFingerprint"));
        assertFalse(e.reason().toLowerCase(java.util.Locale.ROOT).contains("same build"), "D4: " + e.reason());
    }

    @Test
    @DisplayName("Acceptance 4: a graph of another shape opened by hand is a persistent MISMATCH, never a refusal")
    void aForcedPairingWithAnotherShapeIsAMismatch() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.StreamHeadersObserved(g, List.of(header(SHAPE_A, "run-1", 0)), 3));
        d.post(graph(SHAPE_B));
        EvidenceSetState e = evidence(d);
        assertEquals(EvidenceSetState.Verdict.MISMATCH, e.verdict());
        assertEquals("graph shape does not match", e.line());
        assertEquals(SHAPE_A, e.logFingerprint());
        assertEquals(SHAPE_B, e.graphFingerprint());
        assertTrue(d.snapshot().graphOpen(), "D2: the graph stays open — a warning, never a refusal");
        // and it persists: an unrelated Follow observation does not clear it
        d.post(new SessionEvents.LogContentObserved(g, 3, 0, "unknown", 0, null, 1));
        assertEquals(EvidenceSetState.Verdict.MISMATCH, evidence(d).verdict());
    }

    @Test
    @DisplayName("Acceptance 5: a log with no stream header is 'graph not verified'")
    void aLogWithNoHeaderIsUnverified() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.StreamHeadersObserved(d.snapshot().logGeneration(), List.of(), 3));
        d.post(graph(SHAPE_A));
        EvidenceSetState e = evidence(d);
        assertEquals(EvidenceSetState.Verdict.UNVERIFIED, e.verdict());
        assertEquals("graph not verified", e.line());
        assertTrue(e.reason().contains("no stream header"), e.reason());
        assertTrue(e.segments().isEmpty(), "nothing states a run");
    }

    @Test
    @DisplayName("Acceptance 9: a null fingerprint (FLX-1010 refuses the unstable key; an interpreted processor) is UNVERIFIED, never MISMATCH")
    void aNullFingerprintIsUnverifiedNeverAMismatch() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.StreamHeadersObserved(d.snapshot().logGeneration(), List.of(header(null, "run-1", 0)), 3));
        d.post(graph(SHAPE_A));
        assertEquals(EvidenceSetState.Verdict.UNVERIFIED, evidence(d).verdict());
        assertTrue(evidence(d).reason().contains("states no graph fingerprint"), evidence(d).reason());
        // the graph's side missing is the same answer
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver d2 = opened(b);
        d2.post(new SessionEvents.StreamHeadersObserved(d2.snapshot().logGeneration(), List.of(header(SHAPE_A, "r", 0)), 3));
        d2.post(graph(null));
        assertEquals(EvidenceSetState.Verdict.UNVERIFIED, evidence(d2).verdict());
    }

    @Test
    @DisplayName("Malformed headers, and records before the first header, are UNVERIFIED")
    void malformedAndPreHeaderRecordsAreUnverified() {
        StreamHeader bad = StreamHeader.parse("auditStreamHeader:\n    schema: 2\n", 0);
        EvidenceSetState malformed = EvidenceSetState.decide(true, 1, List.of(bad), 3, true, 1, SHAPE_A);
        assertEquals(EvidenceSetState.Verdict.UNVERIFIED, malformed.verdict());
        EvidenceSetState late = EvidenceSetState.decide(true, 1, List.of(header(SHAPE_A, "r", 2)), 5, true, 1, SHAPE_A);
        assertEquals(EvidenceSetState.Verdict.UNVERIFIED, late.verdict());
        assertTrue(late.reason().contains("records 1–2 come before the first stream header"), late.reason());
        // a proven difference outranks missing evidence
        EvidenceSetState mixed = EvidenceSetState.decide(true, 1,
                List.of(header(SHAPE_A, "r", 0), header(null, "r", 1), header(SHAPE_B, "s", 2)), 3, true, 1, SHAPE_A);
        assertEquals(EvidenceSetState.Verdict.MISMATCH, mixed.verdict());
        assertEquals(java.util.Arrays.asList(SHAPE_A, SHAPE_B, null), mixed.logFingerprints());
        assertEquals(java.util.Arrays.asList(SHAPE_A, SHAPE_B, null), mixed.toMap().get("logSourceFingerprints"));
    }

    @Test
    @DisplayName("Acceptance 8: a header-only log (an idle processor) still pairs by its shape, with an empty run")
    void aHeaderOnlyLogPairsByShape() {
        EvidenceSetState e = EvidenceSetState.decide(true, 1, List.of(header(SHAPE_A, "run-1", 0)), 0, true, 1, SHAPE_A);
        assertEquals(EvidenceSetState.Verdict.MATCH, e.verdict());
        assertEquals(1, e.segments().size());
        assertEquals(0, e.segments().get(0).records());
        assertEquals("no records", e.segments().get(0).span());
    }

    @Test
    @DisplayName("A different log forgets the previous log's headers; a fact naming another generation is refused")
    void staleAndReplacedHeadersNeverJudgeTheNewLog() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long first = d.snapshot().logGeneration();
        d.post(new SessionEvents.StreamHeadersObserved(first, List.of(header(SHAPE_A, "run-1", 0)), 3));
        d.post(graph(SHAPE_A));
        assertEquals(EvidenceSetState.Verdict.MATCH, evidence(d).verdict());
        SessionFixtures.openLog(d, a, "/logs/other.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
        long second = d.snapshot().logGeneration();
        assertNotEquals(first, second);
        assertNull(evidence(d).verdict(), "the new log's headers are not read yet: nothing is claimed");
        d.post(new SessionEvents.StreamHeadersObserved(first, List.of(header(SHAPE_A, "run-1", 0)), 3));
        assertNull(evidence(d).verdict(), "a stale fact is refused");
        d.post(new SessionEvents.StreamHeadersObserved(second, List.of(header(SHAPE_B, "run-2", 0)), 1));
        assertTrue(d.snapshot().graphOpen(), "the graph stays across a log open");
        assertEquals(EvidenceSetState.Verdict.MISMATCH, evidence(d).verdict(), "judged on the new log's headers");
        assertEquals(SHAPE_B, evidence(d).logFingerprint());
    }

    @Test
    @DisplayName("Follow: a moved header count is content — the evidence scan is asked for again")
    void aHeaderAppendedUnderFollowAsksForAScan() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.StreamHeadersObserved(g, List.of(header(SHAPE_A, "run-1", 0)), 3));
        d.post(new SessionEvents.ProducerFindingsObserved(g, null));
        d.post(new SessionEvents.TimeOrderObserved(g, null));
        d.post(new SessionEvents.LogContentObserved(g, 3, 0, "unknown", 0, null, 1));
        long scans = a.performed.stream().filter(SessionEffects.ScanLogEvidenceEffect.class::isInstance).count();
        d.post(new SessionEvents.TimeOrderObserved(g, null));            // the scan in flight lands
        d.post(new SessionEvents.LogContentObserved(g, 3, 0, "unknown", 0, null, 2));
        long after = a.performed.stream().filter(SessionEffects.ScanLogEvidenceEffect.class::isInstance).count();
        assertEquals(scans + 1, after, "the second header, with no record after it, moved the content: " + a.performed);
    }

    @Test
    @DisplayName("O3: a graph-shape MISMATCH makes a FULL coverage claim QUALIFIED; UNVERIFIED and MATCH leave it")
    void aMismatchQualifiesTheCoverageClaim() {
        for (String[] c : new String[][]{{SHAPE_B, "QUALIFIED"}, {SHAPE_A, "FULL"}, {null, "FULL"}}) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = opened(a);
            long g = d.snapshot().logGeneration();
            d.post(new SessionEvents.StreamHeadersObserved(g, List.of(header(SHAPE_A, "run-1", 0)), 3));
            d.post(graph(c[0]));
            var claim = d.snapshot().claim();
            assertEquals(c[1], claim.claim().name(), "graph " + c[0] + ": " + claim.reason());
            if (c[1].equals("QUALIFIED")) {
                assertTrue(claim.reason().startsWith("the graph shape does not match the log: the fingerprints differ"),
                        claim.reason());
                assertFalse(claim.reason().toLowerCase(java.util.Locale.ROOT).contains("same build"));
                assertFalse(claim.reason().toLowerCase(java.util.Locale.ROOT).contains("different build"));
            }
        }
    }
}
