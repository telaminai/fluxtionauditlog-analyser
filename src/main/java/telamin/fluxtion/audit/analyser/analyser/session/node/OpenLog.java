package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Whether a log is open, and which.
 *
 * <p>Two inputs, and they are different kinds of fact. {@link SessionEvents.LogClosed} is a
 * <b>result</b>: it answers a {@code CloseLogEffect} this processor asked for, and it is what proves
 * the close happened rather than merely being requested. {@link SessionEvents.LogObserved} is an
 * <b>observation</b>: slice 1 does not own log opening yet, so the adapter reports it, and that input
 * is deleted by the slice that moves the open path.
 */
public class OpenLog implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private String logPath;
    private String provenance;

    public OpenLog(OperationGate gate) {
        this.gate = gate;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onLogClosed(SessionEvents.LogClosed event) {
        if (!gate.accepted()) {
            return false;
        }
        logPath = null;
        provenance = null;
        auditLog.info("openLog", "none").info("via", "LogClosed");
        return true;
    }

    @OnEventHandler
    public boolean onLogObserved(SessionEvents.LogObserved event) {
        logPath = event.open() ? event.logPath() : null;
        provenance = event.open() ? event.provenance() : null;
        auditLog.info("openLog", event.open() ? event.logPath() : "none").info("via", "observation");
        return true;
    }

    public boolean isOpen() {
        return logPath != null;
    }

    public String logPath() {
        return logPath;
    }

    public String provenance() {
        return provenance;
    }
}
