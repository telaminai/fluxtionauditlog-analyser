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
 * the close happened rather than merely being requested. {@link SessionEvents.LogOpened} is the result of
 * the open the processor asked for. {@link SessionEvents.LogCleared} and {@link SessionEvents.LogAppended} are
 * <b>facts</b> (M44.4a): nobody requested them, so each names the log {@link #generation()} it describes.
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
    private long generation;

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
        generation++;
        // round 4, Q9: the arrival records its sample, so what the arrival judged can be checked after the fact
        auditLog.info("openLog", event.logPath()).info("via", "LogOpened").info("sampled", sampled).info("total", total)
                .info("generation", generation);
        return true;
    }

    /**
     * M44.4a: a close that happened outside a transition. Refused unless it names the log that is open — a close
     * posted during an operation arrives after it, by which time a {@code LogClosed} result may already have
     * closed this log (a no-op, recorded) or a newer log may be open (a stale fact, refused and recorded).
     */
    @OnEventHandler
    public boolean onLogCleared(SessionEvents.LogCleared event) {
        if (!current(event.generation(), "LogCleared")) return false;
        logPath = null;
        provenance = null;
        loggedNodeIds = java.util.Set.of();
        sampled = 0;
        total = 0;
        mostVerboseLevel = null;
        auditLog.info("openLog", "none").info("via", "LogCleared");
        return true;
    }

    /**
     * M44.4a: the open log grew. Dirty only when something moved — the boolean is Fluxtion's propagation control,
     * and the pairing and the coverage claim below are recomputed only when it is true. M68.1 round 3, N1: a grown
     * total IS something that moved, because the total is half of the pairing's scope.
     */
    @OnEventHandler
    public boolean onLogAppended(SessionEvents.LogAppended event) {
        if (!current(event.generation(), "LogAppended")) return false;
        boolean moved = total != event.total() || sampled != event.sampled()
                || !loggedNodeIds.equals(event.loggedNodeIds())
                || !java.util.Objects.equals(mostVerboseLevel, event.mostVerboseLevel());
        loggedNodeIds = event.loggedNodeIds();
        sampled = event.sampled();
        total = event.total();
        mostVerboseLevel = event.mostVerboseLevel();
        auditLog.info("openLog", "appended").info("sampled", sampled).info("total", total);
        return moved;
    }

    private boolean current(long named, String what) {
        if (named != generation) {
            auditLog.warn("staleFact", what).warn("generation", named).warn("current", generation);
            return false;
        }
        if (logPath == null) {
            auditLog.info("noOp", what).info("reason", "no log open");
            return false;
        }
        return true;
    }

    /**
     * Which open this is: incremented by every accepted {@code LogOpened}. A fact about a log names the generation
     * it was read from, so it can never be applied to a different log opened since — the same rule as
     * {@code staleResult}, applied to facts that no request id can correlate.
     */
    public long generation() {
        return generation;
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
