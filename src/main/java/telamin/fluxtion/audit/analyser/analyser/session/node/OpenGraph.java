package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Whether a topology graph is open, which, and where it came from
 * ({@code OPENED} / {@code DECLARED} / {@code INFERRED}).
 *
 * <p>Same two-input shape as {@link OpenLog}, for the same reason: the close is a result that proves
 * something happened, the open is an observation only until the slice that moves graph opening.
 */
public class OpenGraph implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private String graphPath;
    private String source;

    public OpenGraph(OperationGate gate) {
        this.gate = gate;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onGraphClosed(SessionEvents.GraphClosed event) {
        if (!gate.accepted()) {
            return false;
        }
        graphPath = null;
        source = null;
        auditLog.info("openGraph", "none").info("via", "GraphClosed");
        return true;
    }

    @OnEventHandler
    public boolean onGraphObserved(SessionEvents.GraphObserved event) {
        graphPath = event.open() ? event.graphPath() : null;
        source = event.open() ? event.source() : null;
        auditLog.info("openGraph", event.open() ? event.graphPath() : "none").info("via", "observation");
        return true;
    }

    public boolean isOpen() {
        return graphPath != null;
    }

    public String graphPath() {
        return graphPath;
    }

    public String source() {
        return source;
    }
}
