package telamin.fluxtion.audit.analyser.analyser.session;

import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import telamin.fluxtion.audit.analyser.analyser.session.generated.SessionProcessor;

import java.util.List;

/**
 * The only thing that talks to both sides: it feeds facts into the processor and performs the effects
 * the processor asks for — <b>always in that order, and never at the same time</b>.
 *
 * <h2>The cycle (M44 D-S0.4)</h2>
 * <pre>
 *   onEvent(fact)                    one dispatch
 *   batch = effectQueue.drain()      immutable snapshot, taken AFTER dispatch returns
 *   for each effect in batch:
 *       result = adapter.perform(effect)     OUTSIDE Fluxtion dispatch
 *       cycle(result)                        the result re-enters at the top
 * </pre>
 *
 * <h2>Three prohibitions, and why they are here rather than in a comment on each node</h2>
 * <ul>
 *   <li><b>No node calls an adapter.</b> A node that touched Swing or the filesystem would put an
 *       irreversible act inside a dispatch that has not finished deciding.</li>
 *   <li><b>No node calls {@code onEvent}.</b> Re-entrancy would become part of the model by accident,
 *       and the audit record would interleave two cycles with no way to tell them apart.</li>
 *   <li><b>No effect is performed before {@code onEvent} returns.</b> Which is why the queue is drained
 *       rather than pushed.</li>
 * </ul>
 *
 * <h2>Single-in-flight, enforced rather than assumed</h2>
 * {@link #submit} refuses to be re-entered. That is what makes an {@code opId} mean something: with one
 * operation at a time, a result carrying any other id is provably stale, and {@code OperationGate} says
 * so in the record. The guard is the enforcement; the ids are what make the enforcement checkable from
 * the audit log alone, which is what would survive this driver being replaced by an asynchronous one.
 *
 * <h2>Audit wiring — every line of it measured, and one of them corrects an earlier guess</h2>
 * Fluxtion's no-arg {@code EventLogManager()} — the constructor the generated processor uses — defaults
 * its sink to {@code System.out::println}. The first version of this class attached the sink <em>after</em>
 * {@code init()}, on the reasoning that "before the first request" was early enough. It is not:
 * {@code init()} itself audits a lifecycle event, so <b>the analyser printed audit records to stdout
 * before the sink existed</b>.
 *
 * <p>The repair was then over-corrected. Six wirings were run against this processor and counted:
 *
 * <pre>
 *   setAuditLogProcessor(sink); init();                    3 records, stdout CLEAN   <-- documented
 *   eventLogger.setLogSink(sink); logLevel(l); init();      2 records, stdout clean
 *   init(); setAuditLogProcessor(sink);                     2 records, LEAKS RECORDS
 *   setAuditLogProcessor(sink); setAuditLogLevel(l); init(); 4 records, PRINTS CONFIG
 *   init(); setAuditLogLevel(l);                            4 records, PRINTS CONFIG
 *   init(); eventLogger.logLevel(NONE);                     SILENT NO-OP
 * </pre>
 *
 * <p>So: <b>the documented {@code setAuditLogProcessor}-then-{@code init()} route works and catches one
 * record more</b> than reaching for the auditor directly, which an earlier version of this class did on
 * the mistaken belief that the {@code DataFlow} route was itself the problem. It is not — the ordering
 * was. The level is the one place we still bypass it, because {@code setAuditLogLevel} prints
 * {@code "updating event log config:"} to stdout on every call, which a desktop application may not do,
 * while {@code eventLogger.logLevel(...)} is silent.
 *
 * <p>And the level must precede {@code init()} whichever route you take: {@code nodeRegistered} stamps
 * each node's logger with the level <em>as the node is registered</em>, so a later {@code logLevel(...)}
 * changes the field and none of the loggers already built from it — measured above as a silent no-op.
 *
 * <p>Underneath all of it, one fact worth carrying: {@code setAuditLogProcessor} and
 * {@code setAuditLogLevel} are <b>not setters</b>. Each is {@code onEvent(new EventLogControlEvent(…))} —
 * a dispatch — which is why their ordering against {@code init()} matters at all, and why neither could
 * ever be called from inside a node.
 */
public final class SessionDriver {

    /**
     * Performs one effect and says what happened. Implemented by {@code MainFrame} / {@code
     * ProjectSession} — the translate-and-perform layer.
     *
     * <p><b>It may not decide whether to perform.</b> If an implementation ever asks "should I really
     * close this?", the decision has leaked out of the graph and the audit log stops describing the
     * application.
     */
    @FunctionalInterface
    public interface Adapter {

        /**
         * @return the fact describing what happened — never {@code null}. Throwing is also allowed and
         * is converted to {@link SessionEvents.EffectFailed}; returning nothing is not, because an
         * unanswered effect is a hole in the record rather than a quiet success.
         */
        SessionEvents.Result perform(SessionEffects effect) throws Exception;
    }

    private final SessionProcessor processor = new SessionProcessor();
    private final SessionAuditSink sink;
    private final Adapter adapter;

    private boolean dispatching;
    private long nextOpId = 1;

    public SessionDriver(Adapter adapter) {
        this(adapter, new SessionAuditSink());
    }

    public SessionDriver(Adapter adapter, SessionAuditSink sink) {
        if (adapter == null || sink == null) {
            throw new IllegalArgumentException("adapter and sink are required");
        }
        this.adapter = adapter;
        this.sink = sink;
        // Both BEFORE init(), and each by the route measured to be silent — see the class comment.
        // The sink goes through the documented DataFlow call; the level does NOT, because the
        // documented one prints to stdout and this one does not.
        processor.setAuditLogProcessor(sink);
        processor.eventLogger.logLevel(EventLogControlEvent.LogLevel.INFO);
        processor.init();
    }

    /** Ids the caller stamps on a request; the results answering it must carry the same one. */
    public long nextOpId() {
        return nextOpId++;
    }

    /**
     * Run one operation to completion: dispatch the fact, perform whatever it asks for, feed the
     * results back, and keep going until nothing more is asked.
     *
     * @throws IllegalStateException if called while a cycle is already running — the single-in-flight
     *                               rule. An adapter that calls back into {@code submit} is trying to
     *                               start a second operation inside the first, and the audit record
     *                               could not distinguish the two afterwards.
     */
    public void submit(Object fact) {
        if (dispatching) {
            throw new ProtocolViolation(
                    "SessionDriver is single-in-flight: submit(" + fact.getClass().getSimpleName()
                            + ") was called while a cycle was still running. An adapter must return a "
                            + "result rather than starting another operation.");
        }
        dispatching = true;
        try {
            cycle(fact);
        } finally {
            dispatching = false;
        }
    }

    private void cycle(Object fact) {
        processor.onEvent(fact);
        List<SessionEffects> batch = processor.effectQueue.drain();
        for (SessionEffects effect : batch) {
            cycle(perform(effect));
        }
    }

    /**
     * Breaking the driver's contract, as distinct from an effect failing.
     *
     * <p>This type exists because of a bug found by its own test. The single-in-flight guard threw
     * {@link IllegalStateException}; the effect-failure handler below catches {@code Exception}; so a
     * re-entrant adapter had its violation quietly converted into an ordinary {@code EffectFailed} and
     * the guard did nothing. <b>The error handling that makes effects safe was swallowing the check
     * that makes the record trustworthy.</b> A protocol violation is a programming error and must reach
     * the caller.
     */
    public static final class ProtocolViolation extends IllegalStateException {
        ProtocolViolation(String message) {
            super(message);
        }
    }

    private SessionEvents.Result perform(SessionEffects effect) {
        try {
            SessionEvents.Result result = adapter.perform(effect);
            if (result == null) {
                return new SessionEvents.EffectFailed(effect.opId(), name(effect),
                        "adapter returned no result — an effect must be answered");
            }
            return result;
        } catch (ProtocolViolation e) {
            throw e;
        } catch (Exception e) {
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new SessionEvents.EffectFailed(effect.opId(), name(effect), reason);
        }
    }

    /** The effect's name in the audit vocabulary — the same word {@code EffectOutcomes} records. */
    static String name(SessionEffects effect) {
        return switch (effect) {
            case SessionEffects.LoadProfileEffect ignored -> "loadProfile";
            case SessionEffects.CreateProfileEffect ignored -> "createProfile";
            case SessionEffects.ApplyProfileEffect ignored -> "applyProfile";
            case SessionEffects.RestoreSettingsEffect ignored -> "restoreSettings";
            case SessionEffects.CloseLogEffect ignored -> "closeLog";
            case SessionEffects.CloseGraphEffect ignored -> "closeGraph";
            case SessionEffects.ShowStatusEffect ignored -> "showStatus";
            case SessionEffects.ShowWarningEffect ignored -> "showWarning";
        };
    }

    /**
     * True while a cycle is running.
     *
     * <p>For adapters that also have a non-transition path into the same state — the analyser's File
     * menu closes a log directly, as well as a project switch closing one. Those paths tell the
     * processor by submitting an observation, and must not do so mid-cycle, where the processor is
     * already being told the same thing by a typed result. Asking is better than a comment saying
     * "do not call this from the adapter", which is the version that rots.
     */
    public boolean isDispatching() {
        return dispatching;
    }

    /** The processor's own audit record. Snapshot it before opening it; see {@link SessionAuditSink}. */
    public SessionAuditSink auditSink() {
        return sink;
    }

    /** Read-only access to the decided state, for surfaces that render it. */
    public SessionProcessor processor() {
        return processor;
    }
}
