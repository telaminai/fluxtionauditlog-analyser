package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.StatusLineState;

/**
 * INTEG-R10 — <b>which line does the status bar state?</b> One owner for the decision, so the bar has one writer.
 *
 * <p>The log line and the graph line used to be set by two call sites: the snapshot renderer, once the log's evidence
 * scan landed, and {@code judgeOpenedGraph}, when a graph opened. After {@code open {log, graphml}} the last one to run
 * stayed on screen, and which one ran last was timing. The mutation control {@code ups1-graph-line-keeps-warnings}
 * survived because of it: when the log line won, dropping the graph line's warnings was never seen.
 *
 * <p>The decision reads facts the session already holds — the open log and its generation, whether its evidence has
 * landed, whether a graph is open with a verdict, Follow — and is the same whichever of the log and the graph arrived
 * first. Downstream of {@link Pairing} and {@link LogEvidence}, so it runs after both in the cycle that moved either.
 */
public class StatusLine implements EventLogSource {

    private final OpenLog openLog;
    private final OpenGraph openGraph;
    private final Pairing pairing;
    private final LogEvidence logEvidence;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private StatusLineState state = StatusLineState.NONE;

    public StatusLine(OpenLog openLog, OpenGraph openGraph, Pairing pairing, LogEvidence logEvidence) {
        this.openLog = openLog;
        this.openGraph = openGraph;
        this.pairing = pairing;
        this.logEvidence = logEvidence;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnTrigger
    public boolean decide() {
        boolean evidenceLanded = logEvidence.findings() != null && logEvidence.timeOrder() != null
                && !logEvidence.scanPending();
        StatusLineState next = StatusLineState.decide(openLog.isOpen(), openLog.generation(), evidenceLanded,
                openGraph.isOpen() && pairing.canSay(), openLog.following());
        if (next.equals(state)) return false;
        state = next;
        auditLog.info("statusLine", state.subject().name()).info("generation", state.logGeneration());
        return true;
    }

    /** The line in force, as decided. */
    public StatusLineState state() {
        return state;
    }
}
