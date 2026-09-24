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
    public boolean onLogOpened(SessionEvents.LogOpened event) {
        if (!gate.accepted()) {
            return false;                       // a superseded load landed late: refused, state untouched
        }
        logPath = event.logPath();
        provenance = event.provenance();
        loggedNodeIds = event.loggedNodeIds();
        sampled = event.sampled();
        total = event.total();
        mostVerboseLevel = event.mostVerboseLevel();
        // round 4, Q9: the arrival records its sample, so what the arrival judged can be checked after the fact
        auditLog.info("openLog", event.logPath()).info("via", "LogOpened").info("sampled", sampled).info("total", total);
        return true;
    }
    /** M35-era observation: still the route for closes and menu refreshes; never judged (M44.3a). */
    @OnEventHandler
    public boolean onLogObserved(SessionEvents.LogObserved event) {
        String wasPath = logPath;
        java.util.Set<String> wasIds = loggedNodeIds;
        int wasTotal = total;
        int wasSampled = sampled;
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
        // M68.1 round 3, N1: a grown log is something that moved. Its sample and its total are the pairing's scope,
        // so a Follow append must re-derive the pairing's scope ("first 500 of 601") rather than go on stating the
        // old total. Only the pairing and the coverage claim depend on this node, and both are pure recomputes.
        return !java.util.Objects.equals(wasPath, logPath) || !wasIds.equals(loggedNodeIds)
                || wasTotal != total || wasSampled != sampled;
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
