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

    /**
     * The evidence a derived node needs, held HERE rather than re-observed there.
     *
     * <p>M44.2b: the derived nodes used to carry one {@code @OnEventHandler} per event that could
     * change their answer — four on {@code CoverageClaim} alone, each calling the same recompute. They
     * now hold a reference to this node and use a single {@code @OnTrigger}, which the compiler renders
     * as an OR of its parents' dirty flags and invokes at most once per cycle. The state lives with
     * the state; the derivation lives with the derivation.
     */
    private java.util.Set<String> loggedNodeIds = java.util.Set.of();
    private int sampled;
    private int total;
    private String mostVerboseLevel;

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
        boolean wasOpen = logPath != null;
        logPath = null;
        provenance = null;
        loggedNodeIds = java.util.Set.of();
        sampled = 0;
        total = 0;
        mostVerboseLevel = null;
        auditLog.info("openLog", "none").info("via", "LogClosed");
        return wasOpen;
    }

    @OnEventHandler
    public boolean onLogObserved(SessionEvents.LogObserved event) {
        String wasPath = logPath;
        java.util.Set<String> wasIds = loggedNodeIds;
        logPath = event.open() ? event.logPath() : null;
        provenance = event.open() ? event.provenance() : null;
        loggedNodeIds = event.open() ? event.loggedNodeIds() : java.util.Set.of();
        sampled = event.open() ? event.sampled() : 0;
        total = event.open() ? event.total() : 0;
        mostVerboseLevel = event.open() ? event.mostVerboseLevel() : null;
        auditLog.info("openLog", event.open() ? event.logPath() : "none").info("via", "observation");
        // Dirty ONLY when something moved. The boolean is Fluxtion's propagation control, so returning
        // true unconditionally would re-derive every dependent on every observation — including the
        // ones the menu funnel fires when nothing has changed at all.
        return !java.util.Objects.equals(wasPath, logPath) || !wasIds.equals(loggedNodeIds);
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

    /** Distinct instanceIds seen in the sample — the raw evidence a pairing needs. */
    public java.util.Set<String> loggedNodeIds() {
        return loggedNodeIds;
    }

    public int sampled() {
        return sampled;
    }

    public int total() {
        return total;
    }

    /** A LOWER BOUND on the capture threshold, never the threshold itself. */
    public String mostVerboseLevel() {
        return mostVerboseLevel;
    }
}
