package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * When a project comes into force with nothing to look at, ask whether to open something (O3).
 *
 * <p>A profile carries settings and never session state, so opening a project restores the source and
 * the processors and leaves the log and the topology closed. That is the recorded decision, and it is
 * the right one — reopening half a workspace unasked was the surprise it avoided — but in use it left
 * a project that looked half-loaded with nothing on screen to say what belonged to it. The answer is
 * an offer, and this node decides when one is warranted.
 *
 * <p><b>Why it is a node.</b> The condition is entirely session state: a profile is genuinely applied,
 * and the session did not come from an evidence bundle. Both facts live here already — {@link OpenBundle}
 * settles the second on the same event — and neither is visible to a surface without guessing from the
 * order effects happen to run in. Sibling of {@link BundleAnchor}, for the same reason and after the
 * same lesson.
 *
 * <p>A bundle is deliberately never asked: it arrives with its own log and graph, and they open as part
 * of opening it. Being offered a choice of someone else's files at that moment would be nonsense.
 */
public class ProjectReopenOffer implements EventLogSource {

    private final OpenBundle openBundle;
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private long operation = -1;
    private SessionEvents.ProjectAudience audience;
    private String appliedProfile;
    private boolean awaitingPresentation;
    private boolean gateOwned;
    private boolean anotherOperationSeen;

    public ProjectReopenOffer(OpenBundle openBundle, EffectQueue effects) {
        this.openBundle = openBundle;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onOpenProjectRequested(SessionEvents.OpenProjectRequested event) {
        anotherOperationSeen = true;
        operation = event.opId();
        audience = event.audience();
        appliedProfile = null;
        awaitingPresentation = false;
        gateOwned = true;
        return false;
    }

    @OnEventHandler
    public boolean onProjectReopenRequested(SessionEvents.ProjectReopenRequested event) {
        if (anotherOperationSeen) return skipped("startup offer was superseded");
        anotherOperationSeen = true;
        operation = event.opId();
        audience = event.audience();
        appliedProfile = event.profilePath();
        awaitingPresentation = false;
        gateOwned = false;
        return prepareOffer();
    }

    @OnEventHandler
    public boolean onOpenLogRequested(SessionEvents.OpenLogRequested event) { anotherOperationSeen = true; operation = -1; return false; }

    @OnEventHandler
    public boolean onGraphOpened(SessionEvents.GraphOpened event) { anotherOperationSeen = true; operation = -1; return false; }

    @OnEventHandler
    public boolean onWalkPlayRequested(SessionEvents.WalkPlayRequested event) { anotherOperationSeen = true; operation = -1; return false; }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        if (event.opId() != operation || !openBundle.acceptsOperation(operation)) return false;
        appliedProfile = event.profilePath();
        return prepareOffer();
    }

    private boolean prepareOffer() {
        if (openBundle.provenance().fromBundle() || audience == null
                || audience.origin() != SessionEvents.OperationOrigin.PERSON || !audience.offersAllowed()) {
            return skipped("bundle or operation does not permit an offer");
        }
        effects.request(new SessionEffects.OfferProjectReopenEffect(operation, appliedProfile));
        auditLog.info("offer", "preparing").info("profile", appliedProfile);
        return true;
    }

    @OnEventHandler
    public boolean onProjectReopenReady(SessionEvents.ProjectReopenReady event) {
        if (event.opId() != operation || !operationCurrent()
                || !java.util.Objects.equals(appliedProfile, event.profilePath())
                || event.occupied() || event.candidates().isEmpty()) return skipped("superseded, occupied or empty");
        awaitingPresentation = true;
        effects.request(new SessionEffects.ShowProjectReopenEffect(operation, appliedProfile, event.label(), event.candidates()));
        return true;
    }

    /** A deferred modal cannot inherit a later operation's permission. */
    public boolean mayPresent(long opId, String profile) {
        return awaitingPresentation && opId == operation && operationCurrent()
                && java.util.Objects.equals(appliedProfile, profile);
    }

    private boolean operationCurrent() { return operation >= 0 && (!gateOwned || openBundle.acceptsOperation(operation)); }

    @OnEventHandler
    public boolean onProjectReopenPresented(SessionEvents.ProjectReopenPresented event) {
        if (event.opId() == operation) awaitingPresentation = false;
        auditLog.info("offer", event.shown() ? "offerProjectReopen" : "offerProjectReopenSkipped");
        return true;
    }

    private boolean skipped(String reason) {
        auditLog.info("offer", "offerProjectReopenSkipped").info("reason", reason);
        return true;
    }
}
