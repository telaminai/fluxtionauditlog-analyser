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
 * <p><b>M68.4: residue is the graph that was open when the log was REQUESTED.</b> {@code open {log, graphml}} requests
 * the log — the load goes pending — and then opens the graph, mid-load. This node used to judge whatever graph was
 * open when the log landed, so it closed the graph the same request had just opened, after that request had replied
 * {@code ok}: a request that silently lost half of itself (D-E3). A graph somebody OPENED after the log was requested
 * was opened for this log — the second bullet's intent, not the first's residue — so it is kept and the mismatch
 * announced. The distinction is whether a graph was OPENED since the request — counted, not compared, because a
 * request that re-opens the graph already on screen asked for it too; a graph a log's reader
 * supplied is nobody's intent and is judged as before.
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
    /** How many graph opens had happened when the log in flight was requested; node-local, so not final (FLX-1009). */
    private long graphOpeningsAtRequest = -1;

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

    /**
     * M44.3 / M44.3a: the judgement fires on {@link SessionEvents.LogOpened} — a real arrival this
     * processor asked for — and never on a refresh observation. The 1.13.1 review (R2-F3) found the old
     * observation-driven version closing the graph that REPLACED the one judged: a menu refresh observed
     * an unchanged log before the new graph, and the close effect cleared whatever was current. The
     * effect now also names the graph it judged, so an adapter holding a different one closes nothing.
     */
    /** M68.4: remember which graph was open when the log was asked for — that one, and only that one, is residue. */
    @OnEventHandler
    public boolean onOpenLogRequested(SessionEvents.OpenLogRequested event) {
        graphOpeningsAtRequest = openGraph.openings();
        return false;                           // nothing downstream changes until the log lands
    }

    @OnEventHandler
    public boolean onLogOpened(SessionEvents.LogOpened event) {
        if (!gate.accepted()) {
            return false;                       // a superseded load: nothing to judge
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
            effects.request(new SessionEffects.ShowStatusEffect(event.opId(),
                    "graph kept — " + note(pairing)));
            return true;
        }
        if (openGraph.openings() != graphOpeningsAtRequest && "OPENED".equals(openGraph.source())) {
            auditLog.info("decision", "keep")
                    .info("closingGraph", false)
                    .info("reason", "graphOpenedForThisLog")
                    .info("matched", pairing.verdict().matched())
                    .info("logged", pairing.verdict().logged());
            effects.request(new SessionEffects.ShowWarningEffect(event.opId(),
                    "graph kept — it was opened for this log, but " + note(pairing)));
            return true;
        }
        auditLog.info("decision", "closeGraph")
                .info("closingGraph", true)
                .info("reason", "graphDoesNotDescribeThisLog")
                .info("matched", pairing.verdict().matched())
                .info("logged", pairing.verdict().logged());
        effects.request(new SessionEffects.CloseGraphEffect(event.opId(), openGraph.graphPath()));
        effects.request(new SessionEffects.ShowWarningEffect(event.opId(),
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
