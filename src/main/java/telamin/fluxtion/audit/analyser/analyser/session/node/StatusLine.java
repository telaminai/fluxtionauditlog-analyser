package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.view.StatusLineView;

import java.util.Map;

/**
 * View-model spike (2026-09-27) — the log's status line as a node: a PROXY for the drawing element, in the graph.
 *
 * <p>The frame used to decide two things about this line on every snapshot: whether the evidence it would state was
 * consistent (a scan outstanding, or a store that had grown past the count the session knew), and whether the line had
 * changed. Both were gates in {@code MainFrame.renderLogEvidence}, and the first W2 defect lived in a second copy of the
 * composition. Here the session decides both, and the audit log records what the line was TOLD, field by field:
 * <ul>
 *   <li><b>Consistent</b>: no scan outstanding, and the store's shape describes the record count the session holds.
 *   Until then the previous view stands — a surface never states the new count beside the previous findings.</li>
 *   <li><b>Changed</b>: a view equal to the last one emitted is not emitted. An idle Follow poll therefore renders
 *   nothing and writes nothing.</li>
 * </ul>
 * A changed, consistent view is requested as {@link SessionEffects.RenderStatusLineEffect}. The adapter hands it to
 * every registered backend; backends compose, they do not decide.
 */
public class StatusLine implements EventLogSource {

    private final OpenLog openLog;
    private final LogEvidence logEvidence;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private SessionEvents.LogShapeObserved shape;
    private StatusLineView emitted;

    public StatusLine(OpenLog openLog, LogEvidence logEvidence, EffectQueue effects) {
        this.openLog = openLog;
        this.logEvidence = logEvidence;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** The store's shape, from the scan. Held; the scan's settling fact is what renders it. */
    @OnEventHandler(propagate = false)
    public boolean onLogShapeObserved(SessionEvents.LogShapeObserved event) {
        if (!openLog.isOpen() || event.generation() != openLog.generation()) {
            auditLog.info("staleShape", event.generation());
            return false;
        }
        shape = event;
        return false;
    }

    /** The answer to a render: which backends drew it. Recorded; it changes nothing. */
    @OnEventHandler(propagate = false)
    public boolean onViewRendered(SessionEvents.ViewRendered event) {
        auditLog.info("rendered", event.element()).info("backends", String.join(",", event.backends()));
        return false;
    }

    /** The open log or its evidence moved: state the line again, if what it states is consistent and new. */
    @OnTrigger
    public boolean onStateChanged() {
        if (!openLog.isOpen()) {
            shape = null;
            emitted = null;              // the next log's first view is always emitted
            return false;
        }
        long generation = openLog.generation();
        if (shape != null && shape.generation() != generation) shape = null;
        ProducerDiagnostics findings = logEvidence.findings();
        if (findings == null || logEvidence.timeOrder() == null || logEvidence.scanPending()) {
            return false;                // the evidence describes an earlier revision: the previous view stands
        }
        if (shape == null || shape.records() != openLog.total()) {
            return false;                // the store's shape and the session's count are different revisions
        }
        StatusLineView view = new StatusLineView(generation, openLog.following(), openLog.logPath(),
                openLog.provenance(), openLog.total(), shape.firstLogTime(), shape.lastLogTime(),
                shape.knownComplete(), logEvidence.timeOrder().violations().size(),
                findings.firstWarning().map(f -> f.kind().name()).orElse(null),
                shape.pendingRecords(), shape.eofIncluded(), logEvidence.readFailure(),
                "REOPENED".equals(openLog.identity()) ? openLog.identityReason() : null);
        if (view.equals(emitted)) return false;
        Map<String, Object> changed = view.changedFrom(emitted);
        emitted = view;
        EventLogger entry = auditLog.info("render", "statusLine");
        changed.forEach((k, v) -> entry.info(k, String.valueOf(v)));
        effects.request(new SessionEffects.RenderStatusLineEffect(0L, view));
        return true;
    }

    /** The last view emitted, or null before the first — what every backend was last told. */
    public StatusLineView view() {
        return emitted;
    }
}
