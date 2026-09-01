package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnBatchEnd;
import com.telamin.fluxtion.runtime.annotations.builder.Inject;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.context.DataFlowContext;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Where decisions ask for effects, and where those effects are performed — at the framework's own
 * transaction boundary rather than in a loop the driver hand-rolled.
 *
 * <h2>What this used to be, and why it changed</h2>
 * The first version was a passive list. The driver called {@code onEvent}, then {@code drain()}, then
 * performed each effect itself, entirely outside Fluxtion. The stated reason was ordering: an effect
 * must not happen before the audit record of deciding it exists.
 *
 * <p><b>That reason was right and the mechanism was invented.</b> Fluxtion already has a transaction
 * boundary — {@link com.telamin.fluxtion.runtime.lifecycle.BatchHandler#batchEnd()}, bound by
 * {@link OnBatchEnd}, documented as <i>"a transaction of events have been received and complete… process
 * a set of events before publishing/exposing state changes outside of the Static Event Processor"</i>.
 * That is this, exactly, and it was reimplemented in the driver because nobody had read it.
 *
 * <h2>The ordering, read from the emitted method rather than assumed</h2>
 * <pre>
 *   onEvent(fact)              the decision cycle; ends in afterEvent() — THE RECORD PUBLISHES
 *   batchEnd()
 *     auditEvent(BatchEnd)
 *     processing = true
 *     effectQueue.performRequestedEffects()   &lt;-- HERE: adapter called, results re-dispatched
 *     afterEvent()                             the BatchEnd record publishes
 *     callbackDispatcher.dispatchQueuedCallbacks()  &lt;-- the results dispatch, each fully audited
 *     processing = false
 * </pre>
 *
 * <p>So <b>decided → recorded → acted</b> still holds, and it no longer depends on where the driver
 * chose to put a loop. It holds because the decision's own {@code afterEvent()} ran when {@code onEvent}
 * returned, before {@code batchEnd()} was entered at all.
 *
 * <p>The re-dispatch uses {@code processAsNewEventCycle}, which reaches {@code onEvent}, sees
 * {@code processing == true}, and queues the result to the back of the callback stack. Back, not front:
 * several results from one batch must arrive in the order they were performed. Each is then dispatched
 * through {@code onEventInternal} and ends in its own {@code afterEvent()}, so <b>every result gets a
 * complete record of its own</b> rather than being folded into the batch's.
 *
 * <h2>Why a throw may not escape this method</h2>
 * The generated {@code batchEnd()} sets {@code processing = true} with <b>no try/finally</b>. An
 * exception leaving here would leave the flag set, and every subsequent event would be queued as
 * re-entrant and never dispatched — a permanently wedged processor, silently. So everything is caught:
 * an effect failure becomes {@link SessionEvents.EffectFailed} as before, and a genuine protocol
 * violation is <em>stashed</em> for {@link SessionDriver} to rethrow once {@code batchEnd()} has
 * returned and the flag is clear. The violation still reaches the caller; it just does not take the
 * processor with it.
 *
 * <h2>Still not an {@code EventLogSource}</h2>
 * This node deliberately implements nothing and holds no logger, so {@code fluxtion.auditCapable} stays
 * {@code false} for it. Its absence from a log means "cannot log", not "did not run" — the distinction
 * M45.3 exists to make, and the analyser's own graph is the fixture proving it. What happened is
 * recorded by {@code EffectOutcomes} when the results arrive, which is the node whose job that is.
 */
public class EffectQueue {

    /** Node-local and not final: see the comment in {@link OperationGate}. */
    private List<SessionEffects> pending = new ArrayList<>();

    /**
     * Injected by the framework so this node can put results back on the queue. Non-final with a
     * setter, which is how a non-final field is wired — see the authoring notes on field mapping.
     */
    @Inject
    private DataFlowContext dataFlowContext;

    /**
     * The translate-and-perform layer, arriving as a SERVICE rather than a constructor argument.
     *
     * <p>It cannot be a constructor argument: the generated processor constructs its nodes, and the
     * adapter is a property of the running application, not of the graph. As a service it is also the
     * seam the replay tests use — {@code FakeSessionAdapter} is registered exactly as {@code MainFrame}
     * is, so the tests exercise the real wiring rather than a mock of it.
     */
    private SessionDriver.Adapter adapter;

    /** Stashed rather than thrown; see the class comment. Cleared when the driver takes it. */
    private RuntimeException fatal;

    @ServiceRegistered
    public void adapterRegistered(SessionDriver.Adapter adapter) {
        this.adapter = adapter;
    }

    /** Called by a decision node during dispatch. Appending to a list is not an effect. */
    public void request(SessionEffects effect) {
        pending.add(effect);
    }

    /**
     * The drain. Performs everything the dispatch just asked for, and feeds each answer back in.
     *
     * <p>The batch is snapshotted and the queue emptied first, so an effect requested by a result —
     * which happens: a close can cascade — lands in a fresh batch for the next {@code batchEnd()}
     * rather than extending this one while it is being iterated.
     */
    @OnBatchEnd
    public void performRequestedEffects() {
        if (pending.isEmpty() || fatal != null) {
            return;
        }
        List<SessionEffects> batch = new ArrayList<>(pending);
        pending.clear();
        for (SessionEffects effect : batch) {
            SessionEvents.Result result = perform(effect);
            if (result == null) {
                return;             // fatal stashed; abandon the rest of the batch
            }
            dataFlowContext.getEventDispatcher().processAsNewEventCycle(result);
        }
    }

    /** @return the result, or {@code null} having stashed a fatal that the driver must rethrow. */
    private SessionEvents.Result perform(SessionEffects effect) {
        if (adapter == null) {
            fatal = new SessionDriver.ProtocolViolation(
                    "no adapter registered: SessionDriver must registerService(Adapter) before any "
                            + "effect is requested. " + SessionDriver.name(effect) + " could not be "
                            + "performed and no result can be honestly reported.");
            return null;
        }
        try {
            SessionEvents.Result result = adapter.perform(effect);
            if (result == null) {
                return new SessionEvents.EffectFailed(effect.opId(), SessionDriver.name(effect),
                        "adapter returned no result — an effect must be answered");
            }
            return result;
        } catch (SessionDriver.ProtocolViolation e) {
            fatal = e;
            return null;
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new SessionEvents.EffectFailed(effect.opId(), SessionDriver.name(effect), reason);
        }
    }

    /** Taken by the driver after {@code batchEnd()} returns, when {@code processing} is clear again. */
    public RuntimeException takeFatal() {
        RuntimeException e = fatal;
        fatal = null;
        return e;
    }

    /** For assertions, and for the driver's "keep calling batchEnd until nothing more is asked" loop. */
    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public DataFlowContext getDataFlowContext() {
        return dataFlowContext;
    }

    public void setDataFlowContext(DataFlowContext dataFlowContext) {
        this.dataFlowContext = dataFlowContext;
    }
}
