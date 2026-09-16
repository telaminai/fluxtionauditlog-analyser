package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * M44.3 — opening a log is a decision this processor makes, not an observation it is told about.
 *
 * <p>Today the decision is always "open it": the value is that the open is now an OPERATION with an
 * {@code opId}. The adapter answers {@link SessionEvents.Pending} at once (the load runs off the event
 * thread) and {@link SessionEvents.LogOpened} when it lands; {@link OperationGate} refuses a result whose
 * id was superseded by a later request (D-A3), and {@link LogArrival} judges an open graph on the real
 * arrival rather than on a menu refresh (M44.3a). A load that never lands leaves the gate saying what is
 * outstanding (D-A4) instead of an idle-looking screen.
 */
public class LogOpening implements EventLogSource {
    private final OperationGate gate;
    @PushReference
    private final EffectQueue effects;
    private EventLogger auditLog = NullEventLogger.INSTANCE;

    public LogOpening(OperationGate gate, EffectQueue effects) {
        this.gate = gate;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onOpenLogRequested(SessionEvents.OpenLogRequested event) {
        auditLog.info("decision", "openLog")
                .info("location", event.location())
                .info("fromSocket", event.fromSocket())
                .info("opId", event.opId());
        effects.request(new SessionEffects.OpenLogEffect(event.opId(), event.location(), event.format(),
                event.provenance(), event.fromSocket()));
        return true;
    }

    @OnEventHandler
    public boolean onLogOpenFailed(SessionEvents.LogOpenFailed event) {
        if (!gate.accepted()) {
            return false;
        }
        // The previously open log, if any, is still the open one; the adapter shows the failure to
        // whoever asked. Recorded here so the operation ends explicitly in the record (D-A5).
        auditLog.info("decision", "openFailed").info("reason", event.reason()).info("opId", event.opId());
        return true;
    }
}
