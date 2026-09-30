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

    public ProjectReopenOffer(OpenBundle openBundle, EffectQueue effects) {
        this.openBundle = openBundle;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        if (openBundle.provenance().fromBundle()) {
            auditLog.info("offer", "skipped").info("reason", "fromBundle");
            return false;
        }
        effects.request(new SessionEffects.OfferProjectReopenEffect(event.opId(), event.profilePath()));
        auditLog.info("offer", "projectReopen").info("profile", event.profilePath());
        return true;
    }
}
