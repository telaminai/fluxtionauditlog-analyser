package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.TransitionKind;

/**
 * <b>The rule M35 spent eleven slices getting right, in one readable place:</b> a project change is a
 * session boundary, so it closes the log and the graph — except when it is not, and the exceptions are
 * the part that lived in Swing callbacks.
 *
 * <p>A project owns the source roots, event processors, named graphs, focuses and reports. Swap it and
 * every one of those changes underneath the open log, so keeping the log would mean reading one
 * project's records through another project's settings, with focuses pointing at nodes from a graph
 * that is no longer the right one.
 *
 * <p><b>Two decisions, at two different moments, and separating them is the point.</b>
 *
 * <ol>
 *   <li>On the <b>request</b>: is this worth doing at all? Re-opening the already-active project is a
 *       no-op, and says so rather than silently re-applying everything.</li>
 *   <li>On the <b>result</b>: the profile actually loaded — so now, does this transition close what is
 *       open? A failed load closes nothing, which is the behaviour the old code already had and which
 *       deciding on the request would have destroyed.</li>
 * </ol>
 *
 * <p>What it never does is touch a log, a graph or a widget. It appends effect requests to
 * {@link EffectQueue} and the driver performs them after dispatch. The audit record separates
 * {@code decision} from the {@code EffectOutcome} the driver logs afterwards, so <i>"asked to close"</i>
 * can never be read as <i>"closed"</i>.
 */
public class SessionBoundary implements EventLogSource {

    private final OperationGate gate;
    private final ActiveProject activeProject;
    private final OpenLog openLog;
    private final OpenGraph openGraph;

    /**
     * Push, not pull. Without this the queue would be an ordinary dependency and the event wave would
     * visit it <em>before</em> the decision that fills it — which is backwards, and would draw the
     * GraphML backwards too.
     */
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;

    /** The kind of the request currently awaiting its {@code ProfileLoaded}. */
    private TransitionKind inFlightKind;
    private long inFlightOpId = -1;

    public SessionBoundary(OperationGate gate, ActiveProject activeProject, OpenLog openLog,
                           OpenGraph openGraph, EffectQueue effects) {
        this.gate = gate;
        this.activeProject = activeProject;
        this.openLog = openLog;
        this.openGraph = openGraph;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /**
     * Decision one — admit or no-op. Nothing has changed yet and nothing may change here: this only
     * asks for the profile to be read.
     */
    @OnEventHandler
    public boolean onOpenProjectRequested(SessionEvents.OpenProjectRequested event) {
        if (event.kind().mayNoOp() && activeProject.isAt(event.profilePath())) {
            inFlightKind = null;
            inFlightOpId = -1;
            auditLog.info("decision", "noOp")
                    .info("noOp", true)
                    .info("kind", event.kind().name())
                    .info("reason", "alreadyActive")
                    .info("opId", event.opId());
            // No effect. The record is the whole output — a no-op that says nothing is
            // indistinguishable from a request that was dropped.
            return true;
        }
        if (event.kind() == TransitionKind.CLOSE) {
            // Leaving a project loads nothing, so there is no result to wait for and the decision is
            // whole here. It is still a session boundary: the settings that are about to be replaced
            // by your own are the ones the open log was being read through.
            inFlightKind = null;
            inFlightOpId = -1;
            String note = close(event.opId(), TransitionKind.CLOSE, activeProject.name());
            effects.request(new SessionEffects.ShowStatusEffect(event.opId(), note));
            return true;
        }
        inFlightKind = event.kind();
        inFlightOpId = event.opId();
        effects.request(event.kind() == TransitionKind.CREATE
                ? new SessionEffects.CreateProfileEffect(event.opId(), event.profilePath())
                : new SessionEffects.LoadProfileEffect(event.opId(), event.profilePath()));
        auditLog.info("decision", event.kind() == TransitionKind.CREATE ? "create" : "load")
                .info("kind", event.kind().name())
                .info("source", event.source())
                .info("opId", event.opId());
        return true;
    }

    /**
     * Decision two — close or keep. The profile has been read; now, and only now, is it known whether
     * there is anything to transition to.
     */
    @OnEventHandler
    public boolean onProfileLoaded(SessionEvents.ProfileLoaded event) {
        if (!gate.accepted()) {
            // The gate has already recorded staleResult. Acting here would apply an outcome for an
            // operation that is no longer in flight.
            return false;
        }
        TransitionKind kind = inFlightKind;
        long opId = inFlightOpId;
        inFlightKind = null;
        inFlightOpId = -1;

        if (kind == null) {
            auditLog.warn("decision", "ignored").warn("reason", "noRequestInFlight").warn("opId", opId);
            return true;
        }

        if (!event.ok()) {
            auditLog.info("decision", "keep")
                    .info("closingLog", false)
                    .info("closingGraph", false)
                    .info("kind", kind.name())
                    .info("reason", "loadFailed")
                    .info("opId", opId);
            effects.request(new SessionEffects.ShowWarningEffect(opId, event.reason()));
            return true;
        }

        String note = close(opId, kind, event.name());
        // Apply BEFORE the status line: the note describes settings that are in force by the time it
        // is read. The old code had the same order for the same reason.
        effects.request(new SessionEffects.ApplyProfileEffect(opId, event.profilePath(), event.name()));
        effects.request(new SessionEffects.ShowStatusEffect(opId, note));
        return true;
    }

    /**
     * The rule itself, in one place — used by both moments it can be reached: after a load succeeded,
     * and immediately for {@link TransitionKind#CLOSE}, which has nothing to load.
     *
     * @return what to say about it; the caller decides where that sits relative to applying the profile
     */
    private String close(long opId, TransitionKind kind, String name) {
        boolean endsSession = kind.endsSession();
        boolean closingLog = endsSession && openLog.isOpen();
        boolean closingGraph = endsSession && openGraph.isOpen();

        if (closingLog) {
            effects.request(new SessionEffects.CloseLogEffect(opId));
        }
        if (closingGraph) {
            effects.request(new SessionEffects.CloseGraphEffect(opId));
        }
        if (kind == TransitionKind.CLOSE) {
            effects.request(new SessionEffects.RestoreSettingsEffect(opId));
        }
        auditLog.info("decision", endsSession ? "close" : "keep")
                .info("closingLog", closingLog)
                .info("closingGraph", closingGraph)
                .info("kind", kind.name())
                .info("reason", reason(kind, endsSession))
                .info("opId", opId);
        return note(kind, name, closingLog, closingGraph);
    }

    /**
     * Why, in the vocabulary of the rule rather than of the surface. {@code ADOPT_FOR_OPEN_LOG} gets
     * its own reason because it is the exception a reader is most likely to think is a bug.
     */
    private static String reason(TransitionKind kind, boolean endsSession) {
        if (endsSession) {
            return "projectChanged";
        }
        return switch (kind) {
            case ADOPT_FOR_OPEN_LOG -> "adoptedForOpenLog";
            case STARTUP_ACTIVATION -> "startup";
            default -> "doesNotEndSession";
        };
    }

    private static String note(TransitionKind kind, String name, boolean closingLog, boolean closingGraph) {
        String head = kind == TransitionKind.CLOSE
                ? "closed project " + (name == null ? "" : name) + " — back to your own settings"
                : "opened project " + name;
        if (!closingLog && !closingGraph) {
            return head;
        }
        String what = closingLog && closingGraph ? "log and graph" : closingLog ? "log" : "graph";
        return head + "  ·  closed the " + what + " — a project is a session boundary";
    }
}
