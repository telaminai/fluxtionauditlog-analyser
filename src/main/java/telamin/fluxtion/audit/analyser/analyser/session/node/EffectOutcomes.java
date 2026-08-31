package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * <b>The node that makes the audit log evidence rather than narration.</b>
 *
 * <p>{@code SessionBoundary} records {@code decision=close, closingLog=true}. That is what it decided.
 * On its own it is an intention, and an intention read as an outcome is exactly the defect this
 * milestone exists to remove — a log saying "closing" being taken as proof the log closed.
 *
 * <p>So every completed effect lands here and is written under one pinned shape:
 *
 * <pre>
 *   EffectOutcome  effect=closeLog  success=true   opId=7
 *   EffectOutcome  effect=loadProfile  success=false  opId=8  reason=no such file
 * </pre>
 *
 * <p>One place to look for <i>did it actually happen</i>, one vocabulary, and a failure that cannot be
 * silent: an effect that neither succeeds nor fails simply has no row, and a missing row is visible in
 * a way a missing log line is not.
 *
 * <p>It records outcomes whether or not the gate accepted them — a refused stale result is still
 * something that happened, and hiding it would defeat the purpose. The {@code stale} key says which.
 */
public class EffectOutcomes implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;

    public EffectOutcomes(OperationGate gate) {
        this.gate = gate;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onProfileLoaded(SessionEvents.ProfileLoaded event) {
        return record("loadProfile", event.ok(), event.opId(), event.reason());
    }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        return record("applyProfile", true, event.opId(), null);
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored event) {
        return record("restoreSettings", true, event.opId(), null);
    }

    @OnEventHandler
    public boolean onLogClosed(SessionEvents.LogClosed event) {
        return record("closeLog", true, event.opId(), null);
    }

    @OnEventHandler
    public boolean onGraphClosed(SessionEvents.GraphClosed event) {
        return record("closeGraph", true, event.opId(), null);
    }

    @OnEventHandler
    public boolean onStatusShown(SessionEvents.StatusShown event) {
        return record(event.kind(), true, event.opId(), null);
    }

    @OnEventHandler
    public boolean onEffectFailed(SessionEvents.EffectFailed event) {
        return record(event.effect(), false, event.opId(), event.reason());
    }

    private boolean record(String effect, boolean success, long opId, String reason) {
        auditLog.info("EffectOutcome", effect)
                .info("effect", effect)
                .info("success", success)
                .info("opId", opId)
                .info("stale", !gate.accepted());
        if (reason != null && !reason.isBlank()) {
            auditLog.info("reason", reason);
        }
        return true;
    }
}
