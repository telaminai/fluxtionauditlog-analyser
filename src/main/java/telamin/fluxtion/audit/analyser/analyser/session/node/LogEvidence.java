package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * M44.5 — what the open log says about ITSELF: its producer findings (empty, damaged, run together, a document with no
 * record key…) and its time order. One owner, so every surface reads the same value from the published snapshot.
 *
 * <p><b>Why a node, and why it decides WHEN.</b> Before M44.5 both values were frame fields, computed at several call
 * sites and pushed to the status bar, the tooltip, {@code context}, the {@code report} reply, the PDF and the Reports
 * tab by hand-placed refresh calls. Every defect found in that area on 2026-09-26 was one of those hand-placed steps
 * (PR #40's H1–H3, X4 and X5), and two more were witnessed on 2026-09-27: a time-order violation appended under
 * Follow was never reported, and the Follow status line dropped provenance and the time-order warning. The owner's
 * rule is one way of handling dispatch and orchestration, so this node decides when the evidence is stale and asks
 * for a scan; the adapter only performs the scan and reports the result.
 *
 * <p><b>When it asks.</b> A new log generation clears what it held and asks. A Follow poll reports the log's content
 * signature ({@link SessionEvents.LogContentObserved}); a changed signature asks again, an unchanged one does not.
 * That replaces the frame's refresh gate, including its skip of a repeated identical read failure.
 *
 * <p><b>What it refuses.</b> A result that names another generation, so a scan of a log that has since been replaced
 * can never be shown over the new one.
 */
public class LogEvidence implements EventLogSource {

    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private long boundGeneration = -1;
    private String signature;
    private ProducerDiagnostics findings;
    private TimeOrderReport timeOrder;

    public LogEvidence(OpenLog openLog, EffectQueue effects) {
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** A different log, or none: what was held described the previous one. */
    @OnTrigger
    public boolean onOpenLogChanged() {
        if (!openLog.isOpen()) {
            boolean had = findings != null || timeOrder != null;
            findings = null;
            timeOrder = null;
            signature = null;
            boundGeneration = -1;
            if (had) auditLog.info("logEvidence", "cleared").info("reason", "no log open");
            return had;
        }
        long generation = openLog.generation();
        if (generation == boundGeneration) {
            return false;                                  // the same log: its content signature decides a rescan
        }
        findings = null;
        timeOrder = null;
        signature = null;
        boundGeneration = generation;
        requestScan(generation, "opened");
        return true;
    }

    /** A Follow poll's content signature. A rescan is asked for only when it moved. */
    @OnEventHandler
    public boolean onLogContentObserved(SessionEvents.LogContentObserved event) {
        if (!current(event.generation(), "LogContentObserved")) return false;
        String next = event.total() + "/" + event.bytes() + "/" + event.pendingChars() + "/" + event.readFailed();
        if (next.equals(signature)) {
            return false;                                  // nothing moved: the evidence held still describes the log
        }
        signature = next;
        requestScan(event.generation(), "content moved");
        return false;                                      // the request is the effect; nothing published changed yet
    }

    @OnEventHandler
    public boolean onProducerFindingsObserved(SessionEvents.ProducerFindingsObserved event) {
        if (!current(event.generation(), "ProducerFindingsObserved")) return false;
        boolean moved = !java.util.Objects.equals(findings, event.findings());
        findings = event.findings();
        if (moved) auditLog.info("producerFindings", findings == null ? 0 : findings.findings().size());
        return moved;
    }

    @OnEventHandler
    public boolean onTimeOrderObserved(SessionEvents.TimeOrderObserved event) {
        if (!current(event.generation(), "TimeOrderObserved")) return false;
        boolean moved = !java.util.Objects.equals(timeOrder, event.report());
        timeOrder = event.report();
        if (moved) auditLog.info("timeOrderViolations", timeOrder == null ? 0 : timeOrder.violations().size());
        return moved;
    }

    /** The adapter's acknowledgement that a scan is scheduled — recorded, and it changes nothing. */
    @OnEventHandler
    public boolean onScanScheduled(SessionEvents.ScanScheduled event) {
        auditLog.info("scanScheduled", event.generation());
        return false;
    }

    private void requestScan(long generation, String why) {
        auditLog.info("decision", "scanLogEvidence").info("generation", generation).info("why", why);
        effects.request(new SessionEffects.ScanLogEvidenceEffect(0L, generation));
    }

    private boolean current(long named, String what) {
        if (!openLog.isOpen()) {
            auditLog.info("noOp", what).info("reason", "no log open");
            return false;
        }
        if (named != openLog.generation()) {
            auditLog.warn("staleFact", what).warn("generation", named).warn("current", openLog.generation());
            return false;
        }
        return true;
    }

    /** The open log's producer findings, or null before the first scan of this generation lands. */
    public ProducerDiagnostics findings() {
        return findings;
    }

    /** The open log's time-order report, or null before the first scan of this generation lands. */
    public TimeOrderReport timeOrder() {
        return timeOrder;
    }
}
