package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Mechanism, not policy: does the result that just arrived answer the request that is in flight?
 *
 * <p>It sits <b>upstream of every state node and of the decision</b>, so a stale result is refused
 * before anything believes it. That ordering is the reason this is a node of its own rather than a
 * check inside {@code SessionBoundary}: the decision node needs to read the state nodes, so it must be
 * downstream of them, so it cannot also be the thing that guards them.
 *
 * <p><b>It deliberately does not track "busy".</b> Single-in-flight is a property of the synchronous
 * driver and is enforced there ({@code SessionDriver}), where re-entrancy is actually detectable. What
 * the graph does is <b>record the ids</b>, so that the property is checkable from the audit log alone.
 * If the driver ever goes asynchronous, the record does not quietly start lying — it starts showing
 * {@code staleResult}.
 *
 * <p>Consequence worth stating: a request whose decision turns out to be a no-op leaves
 * {@code expectedOpId} pointing at an operation that will never produce a result. Nothing arrives to be
 * matched against it, and the next request overwrites it — and that is why this node can stay ignorant of
 * what policy decided. <b>Since M44.3 the driver IS asynchronous at one boundary</b> (opening a log), so one
 * more fact is kept here: what is outstanding ({@link #inFlightWhat()}). A newer request of any kind —
 * another open, or a project transition — SUPERSEDES the outstanding open: its id is replaced, so its late
 * result is refused, and its description is retired at once (1.13.1 finish-first review B2: a project switch
 * used to leave "opening …" reported forever, because only an ACCEPTED log result cleared it and none could
 * arrive). An accepted log result still clears it; a refused one never touches it.
 *
 * <p><b>Since M44.3b a CLOSE supersedes too — of the same kind.</b> A close that covers the log is a newer
 * deliberate request about the log, so it takes the id of an outstanding open; a close of the graph alone,
 * or any close with nothing outstanding, changes nothing here. See {@link #onCloseRequested}.
 */
public class OperationGate implements EventLogSource {

    /**
     * Node-local state, and deliberately <b>not final</b>. Fluxtion's generator must reconstruct every
     * mapped field of a node in generated source; a final field has to come from a constructor, which
     * is what produces the constructor-match failure six measured agents hit. Mutable node-local state
     * is not a mapped field, so it does not participate. (M44 registered this as a prediction.)
     */
    private EventLogger auditLog = NullEventLogger.INSTANCE;

    /** The id of the most recent request. Results are matched against it. */
    private long expectedOpId = -1;

    /** Whether the event currently being dispatched is one downstream nodes may act on. */
    private boolean accepted;
    /** M44.3 D-A4: what the operation in flight is for, or null — so a hung load is reportable. */
    private String inFlightWhat;

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onOpenProjectRequested(SessionEvents.OpenProjectRequested event) {
        expectedOpId = event.opId();
        accepted = true;
        inFlightWhat = null;                    // review B2: a project transition supersedes a pending open
        auditLog.info("fact", "request").info("opId", event.opId()).info("kind", event.kind().name());
        return true;
    }

    @OnEventHandler
    public boolean onOpenLogRequested(SessionEvents.OpenLogRequested event) {
        // D-A3: a newer request supersedes — the older one's result now arrives stale and is refused
        expectedOpId = event.opId();
        accepted = true;
        inFlightWhat = "opening " + event.location();
        auditLog.info("fact", "request").info("opId", event.opId()).info("what", "openLog");
        return true;
    }

    /**
     * M44.3b: a close supersedes a pending open <b>of the same kind</b>. Only a log open can be pending
     * (it is the one asynchronous boundary), so only a close that covers the log takes the id: the
     * outstanding open's late result then arrives stale and is refused by {@link #check}, exactly as
     * after a newer open or a project transition.
     *
     * <p>A close of the graph alone, and any close with nothing outstanding, change NOTHING here — they
     * are recorded, and that is all. Taking the id regardless would be harmless today and wrong in
     * principle: it would let an unrelated close invalidate whatever asynchronous operation is added
     * next, which is the "new meaning for every close" the finish-first review warned against.
     */
    @OnEventHandler
    public boolean onCloseRequested(SessionEvents.CloseRequested event) {
        accepted = true;
        String superseded = event.target().coversLog() ? inFlightWhat : null;
        auditLog.info("fact", "request").info("opId", event.opId()).info("what", "close")
                .info("target", event.target().name());
        if (superseded != null) {
            expectedOpId = event.opId();
            inFlightWhat = null;
            auditLog.info("superseded", superseded);
        }
        return true;
    }

    @OnEventHandler
    public boolean onPending(SessionEvents.Pending event) {
        boolean ok = check(event.opId(), "Pending");
        if (accepted) auditLog.info("pending", event.what());
        return ok;
    }
    @OnEventHandler
    public boolean onLogOpened(SessionEvents.LogOpened event) {
        boolean ok = check(event.opId(), "LogOpened");
        if (accepted) inFlightWhat = null;
        return ok;
    }
    @OnEventHandler
    public boolean onLogOpenFailed(SessionEvents.LogOpenFailed event) {
        boolean ok = check(event.opId(), "LogOpenFailed");
        if (accepted) inFlightWhat = null;
        return ok;
    }
    @OnEventHandler
    public boolean onProfileLoaded(SessionEvents.ProfileLoaded event) {
        return check(event.opId(), "ProfileLoaded");
    }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        return check(event.opId(), "ProfileApplied");
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored event) {
        return check(event.opId(), "SettingsRestored");
    }

    @OnEventHandler
    public boolean onLogClosed(SessionEvents.LogClosed event) {
        return check(event.opId(), "LogClosed");
    }

    @OnEventHandler
    public boolean onGraphClosed(SessionEvents.GraphClosed event) {
        return check(event.opId(), "GraphClosed");
    }

    @OnEventHandler
    public boolean onStatusShown(SessionEvents.StatusShown event) {
        return check(event.opId(), "StatusShown");
    }

    @OnEventHandler
    public boolean onEffectFailed(SessionEvents.EffectFailed event) {
        return check(event.opId(), "EffectFailed");
    }

    /**
     * Facts carry no id because nobody requested them (M44.4a). They are always accepted HERE — a fact about a
     * log is gated by the log generation it names, in {@link OpenLog}, where the generation lives — and the record
     * says which fact it was, so that "accepted" never has to be read as "answered a request".
     */
    @OnEventHandler
    public boolean onGraphOpened(SessionEvents.GraphOpened event) {
        return fact("GraphOpened");
    }

    @OnEventHandler
    public boolean onGraphCleared(SessionEvents.GraphCleared event) {
        return fact("GraphCleared");
    }

    @OnEventHandler
    public boolean onLogCleared(SessionEvents.LogCleared event) {
        return fact("LogCleared");
    }

    @OnEventHandler
    public boolean onLogAppended(SessionEvents.LogAppended event) {
        return fact("LogAppended");
    }

    private boolean fact(String what) {
        accepted = true;
        auditLog.info("fact", "fact").info("what", what);
        return true;
    }

    private boolean check(long opId, String what) {
        accepted = opId == expectedOpId;
        if (accepted) {
            auditLog.info("fact", "result").info("what", what).info("opId", opId);
        } else {
            auditLog.warn("staleResult", what).warn("opId", opId).warn("expected", expectedOpId);
        }
        // Propagate either way: downstream nodes read accepted() and a refused result must still be
        // visible in the record. Stopping the branch here would hide the refusal.
        return true;
    }

    /** Whether the event being dispatched may be acted on. Read by every downstream node. */
    public boolean accepted() {
        return accepted;
    }

    /** The operation results are currently being matched against; {@code -1} before the first request. */
    public long expectedOpId() {
        return expectedOpId;
    }
    /** The operation started and not yet completed, e.g. {@code "opening /path"}, or null. */
    public String inFlightWhat() {
        return inFlightWhat;
    }
}
