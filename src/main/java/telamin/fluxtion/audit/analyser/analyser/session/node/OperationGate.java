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
 * {@code expectedOpId} pointing at an operation that will never produce a result. That is harmless —
 * nothing arrives to be matched against it, and the next request overwrites it — and it is why this
 * node can stay ignorant of what policy decided.
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

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onOpenProjectRequested(SessionEvents.OpenProjectRequested event) {
        expectedOpId = event.opId();
        accepted = true;
        auditLog.info("fact", "request").info("opId", event.opId()).info("kind", event.kind().name());
        return true;
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
     * Observations carry no id because nobody requested them. They are always accepted, and the record
     * says which they were so that "accepted" never has to be read as "answered a request".
     */
    @OnEventHandler
    public boolean onLogObserved(SessionEvents.LogObserved event) {
        accepted = true;
        auditLog.info("fact", "observation").info("what", "LogObserved").info("open", event.open());
        return true;
    }

    @OnEventHandler
    public boolean onGraphObserved(SessionEvents.GraphObserved event) {
        accepted = true;
        auditLog.info("fact", "observation").info("what", "GraphObserved").info("open", event.open());
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
}
