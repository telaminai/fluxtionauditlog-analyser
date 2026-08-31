package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * <b>M35.2, moved out of a Swing callback:</b> a log arrives and finds a graph already open — does
 * that graph survive?
 *
 * <p>The rule, and the reason it is not symmetrical with M35.3:
 *
 * <ul>
 *   <li><b>A log arrives</b> and finds a graph: that graph is <i>residue</i> from the previous
 *       investigation. Nobody asked for it here, so it is judged, and closed if it does not fit. An
 *       offer was considered and loses twice — a modal on every open is friction for a person, and on
 *       the agent path there is nobody to answer it, so it would be silently defaulted; and
 *       defaulting to "keep" is precisely the defect.</li>
 *   <li><b>A graph arrives</b> against an open log (M35.3): that graph is <i>intent</i> — someone
 *       named this processor — so a mismatch is announced and the graph is KEPT. Announce, never
 *       forbid, where there is an intention to respect.</li>
 * </ul>
 *
 * <p>This node owns the first. The verdict comes from {@link Pairing}, which states the FACT and
 * never the action, precisely so one comparison can serve two verbs.
 *
 * <p><b>Why this was worth moving.</b> It lived in {@code MainFrame.repairLoadedGraph}, reachable
 * only by running the application, and it decides whether someone loses a graph they were reading.
 * The defect it prevents is silent: open a second log and the FIRST log's topology stays on screen,
 * and coverage, "did not run" shading and step-through then describe a graph with nothing to do with
 * the records.
 */
public class LogArrival implements EventLogSource {

    private final OperationGate gate;
    private final Pairing pairing;
    private final OpenGraph openGraph;

    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;

    public LogArrival(OperationGate gate, Pairing pairing, OpenGraph openGraph, EffectQueue effects) {
        this.gate = gate;
        this.pairing = pairing;
        this.openGraph = openGraph;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onLogObserved(SessionEvents.LogObserved event) {
        if (!event.open()) {
            return false;                       // a log closing decides nothing about the graph
        }
        if (!openGraph.isOpen()) {
            auditLog.info("decision", "noGraph").info("reason", "nothingToJudge");
            return true;
        }
        if (!pairing.canSay()) {
            // NULL SAFETY, not a rule — and saying so because a mutation test proved it.
            //
            // Deleting this branch does not change any outcome, because it is unreachable: LogArrival
            // only fires on an OPEN log, so Pairing always has both artefacts by the time this runs.
            // The case it looks like it handles — a log that records no node output — is handled
            // somewhere better: GraphPairing.of returns applies=true for an empty log, with the reason
            // "this log records no node output, so it cannot say whether the graph applies". A silent
            // log cannot convict a graph, and that rule belongs with the comparison rather than here.
            //
            // It stays as a guard against Pairing and OpenGraph ever disagreeing, which would
            // otherwise be a NullPointerException inside a decision.
            auditLog.warn("decision", "keep")
                    .warn("closingGraph", false)
                    .warn("reason", "noVerdictAvailable");
            return true;
        }
        if (!pairing.doesNotApply()) {
            auditLog.info("decision", "keep")
                    .info("closingGraph", false)
                    .info("reason", "graphFitsThisLog")
                    .info("matched", pairing.verdict().matched())
                    .info("logged", pairing.verdict().logged());
            effects.request(new SessionEffects.ShowStatusEffect(0,
                    "graph kept — " + note(pairing)));
            return true;
        }
        auditLog.info("decision", "closeGraph")
                .info("closingGraph", true)
                .info("reason", "graphDoesNotDescribeThisLog")
                .info("matched", pairing.verdict().matched())
                .info("logged", pairing.verdict().logged());
        effects.request(new SessionEffects.CloseGraphEffect(0));
        effects.request(new SessionEffects.ShowWarningEffect(0,
                "graph closed — " + note(pairing)
                        + ". Reopen it deliberately if you meant to compare them."));
        return true;
    }

    /**
     * The numbers, and the honesty about where they came from. A pairing drawn from a sample must not
     * be stated as a whole-log claim — the same correction the Swing version carries.
     */
    private static String note(Pairing p) {
        String base = p.verdict().reason();
        return p.sampledOnly()
                ? base + " (from the first " + p.sampled() + " of " + p.total() + " records)"
                : base;
    }
}
