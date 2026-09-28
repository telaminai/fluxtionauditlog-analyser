package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderValidator;
import telamin.fluxtion.audit.analyser.analyser.session.view.StatusLineView;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * View-model spike (2026-09-27): the status line as a node, on the REAL generated processor with a recording backend
 * and no display. What the line is told, and when, is asserted here — the part that used to need a frame test.
 */
class StatusLineViewTest {

    private static TimeOrderReport oneViolation() {
        String rec = "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n---\n";
        return TimeOrderValidator.validate(new HeapLogStore("---\n" + rec.formatted(2000) + rec.formatted(1000)).index());
    }

    /** Open a log the way the frame does, with the given provenance and record count. */
    private static SessionDriver opened(FakeSessionAdapter a, String provenance, int total) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/f.yaml", provenance, Set.of("a"), 1, total, "TRACE");
        return d;
    }

    /** The scan the node asked for lands, in the adapter's order: shape, findings, then time order (which settles it). */
    private static void scanLands(SessionDriver d, int records, long first, long last, TimeOrderReport order) {
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogShapeObserved(g, records, first, last, false, 0, 0));
        d.post(new SessionEvents.ProducerFindingsObserved(g, ProducerDiagnostics.clean()));
        d.post(new SessionEvents.TimeOrderObserved(g, order));
    }

    private static void append(SessionDriver d, int total) {
        d.post(new SessionEvents.LogAppended(d.snapshot().logGeneration(), Set.of("a"), 1, total, "TRACE"));
    }

    @Test
    @DisplayName("Nothing is drawn until the scan of THIS log lands; then exactly one view, stating what the session holds")
    void oneViewOnceTheScanLands() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        assertEquals(List.of(), a.statusLines, "the scan is outstanding: the line has nothing consistent to state");

        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());

        assertEquals(1, a.statusLines.size(), "one render: " + a.statusLines);
        StatusLineView v = a.statusLines.get(0);
        assertEquals(25, v.records());
        assertEquals("DEMO", v.provenance());
        assertEquals("/logs/f.yaml", v.location());
        assertEquals(1_000L, v.firstLogTime());
        assertEquals(0, v.timeOrderViolations());
        assertNull(v.producerWarning());
        assertFalse(v.following());
    }

    @Test
    @DisplayName("A shape for a different record count than the session holds is not drawn — two revisions never mix")
    void aShapeOfAnotherRevisionWaits() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        scanLands(d, 30, 1_000L, 5_000L, TimeOrderReport.clean());   // the store has grown past what the session knows
        assertEquals(List.of(), a.statusLines,
                "this is the frame's `next.total() != s.size()` gate, now decided in the processor");
    }

    /**
     * W2, headless. The Follow line once dropped the provenance and the order warning because it was assembled
     * a second time. Here Follow is a field of the one view, so the provenance cannot fall out of it by toggling.
     */
    @Test
    @DisplayName("W2: turning Follow on re-states the line with its provenance and its order warning intact")
    void followKeepsProvenanceAndOrderWarning() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        scanLands(d, 25, 1_000L, 5_000L, oneViolation());

        d.post(new SessionEvents.FollowToggled(d.snapshot().logGeneration(), true));

        assertEquals(2, a.statusLines.size(), "the toggle is a change to what the line states: " + a.statusLines);
        StatusLineView following = a.statusLines.get(1);
        assertTrue(following.following());
        assertEquals("DEMO", following.provenance(), "the W2 defect: the Follow line lost the provenance");
        assertEquals(1, following.timeOrderViolations(), "the W2 defect: the Follow line lost the order warning");
        assertEquals(java.util.Map.of("following", true), following.changedFrom(a.statusLines.get(0)),
                "and the audit records only what the toggle changed");
    }

    @Test
    @DisplayName("An append draws nothing while its scan is outstanding, then one view with the new count")
    void anAppendIsDrawnOnceSettled() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());

        append(d, 27);
        assertEquals(1, a.statusLines.size(), "27 records beside 25 records' findings would mix two revisions");

        scanLands(d, 27, 1_000L, 7_000L, TimeOrderReport.clean());
        assertEquals(2, a.statusLines.size());
        assertEquals(27, a.statusLines.get(1).records());
        assertEquals(java.util.Set.of("records", "lastLogTime"),
                a.statusLines.get(1).changedFrom(a.statusLines.get(0)).keySet(),
                "an append's render is described by the two fields it moved");
    }

    /**
     * A scan reports in three facts. After the shape and the findings, but before the time order that settles it, the
     * count and the findings are new and the time order is the previous revision's. The shape gate cannot see that —
     * the shape already matches the count — so this is the scan-pending gate's own witness.
     */
    @Test
    @DisplayName("A scan is drawn only once it has fully landed — never its findings beside the previous time order")
    void aHalfLandedScanIsNotDrawn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        scanLands(d, 25, 1_000L, 5_000L, oneViolation());
        append(d, 27);
        long g = d.snapshot().logGeneration();

        d.post(new SessionEvents.LogShapeObserved(g, 27, 1_000L, 7_000L, false, 0, 0));
        d.post(new SessionEvents.ProducerFindingsObserved(g, ProducerDiagnostics.of(
                new telamin.fluxtion.audit.analyser.analyser.index.LogIndex(), i -> null, List.of(), List.of(), false)));
        assertEquals(1, a.statusLines.size(), "27 records and the new findings beside the previous revision's "
                + "time-order violation would state a log that never existed: " + a.statusLines);

        d.post(new SessionEvents.TimeOrderObserved(g, TimeOrderReport.clean()));
        assertEquals(2, a.statusLines.size());
        assertEquals(0, a.statusLines.get(1).timeOrderViolations(), "drawn once, with all three facts of the one scan");
        assertNotNull(a.statusLines.get(1).producerWarning());
    }

    @Test
    @DisplayName("An idle Follow poll draws nothing: the same content, the same view")
    void anIdlePollDrawsNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogContentObserved(g, 25, 0, "UNKNOWN", 0, null));   // first poll: a rescan
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());
        int before = a.statusLines.size();

        for (int i = 0; i < 50; i++) d.post(new SessionEvents.LogContentObserved(g, 25, 0, "UNKNOWN", 0, null));

        assertEquals(before, a.statusLines.size(), "no render, so an explanation on the bar is never overwritten");
        assertEquals(1, before, "and the rescan of unchanged content drew nothing either");
    }

    @Test
    @DisplayName("A Follow read failure is stated without a rescan, and its recovery is stated too")
    void aReadFailureIsDrawn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.LogContentObserved(g, 25, 0, "UNKNOWN", 0, null));
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());

        d.post(new SessionEvents.LogContentObserved(g, 25, 0, "UNKNOWN", 0, "disk went away"));
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());
        assertEquals("disk went away", a.statusLines.get(a.statusLines.size() - 1).readFailure());
    }

    @Test
    @DisplayName("A new log's first view is drawn even when it states the same as the last log's")
    void aNewGenerationIsAlwaysDrawn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a, "DEMO", 25);
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, 25, "TRACE");
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());
        assertEquals(2, a.statusLines.size());
        assertNotEquals(a.statusLines.get(0).generation(), a.statusLines.get(1).generation());
    }

    @Test
    @DisplayName("The audit records each render and what it changed, and which backends drew it")
    void theAuditSaysWhatTheLineWasTold() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink();
        SessionDriver d = new SessionDriver(a, sink);
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, 25, "TRACE");
        scanLands(d, 25, 1_000L, 5_000L, TimeOrderReport.clean());
        d.post(new SessionEvents.FollowToggled(d.snapshot().logGeneration(), true));

        List<String> renders = sink.matching("render: statusLine");
        assertEquals(2, renders.size(), "one entry per render: " + renders);
        // the node's own entry — other nodes in the same record log keys of their own (coverageClaim has a provenance)
        String entry = renders.get(1).lines().filter(l -> l.contains("- statusLineView:")).findFirst().orElse("");
        assertTrue(entry.contains("following: true"), entry);
        assertFalse(entry.contains("provenance:"), "a field that did not change is not repeated: " + entry);
        // PR #55 second element found this: "backends: recorder" matched ANY view's answer, so a second element
        // rendering to the same recorder broke it. A render answer is only evidence about the element it names.
        assertEquals(2, sink.matching("rendered: statusLine").size(),
                "the answer names the element AND the backend that drew it");
    }
}
