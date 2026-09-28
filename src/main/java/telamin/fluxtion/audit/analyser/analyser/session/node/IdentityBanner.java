package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.view.IdentityBannerView;

import java.util.Map;

/**
 * View-model spike, second element — the file-identity banner as a node.
 *
 * <p>Chosen as the second element because of what it is NOT: it needs no new fact (the verdict is already
 * {@link OpenLog}'s) and it changes only when the file behind the log does. The status line needed a fact and
 * changes on every appending poll. Measuring both separates a per-element cost from a per-change one — see
 * {@code PREDICTIONS-2.md}.
 *
 * <p>There is also no consistency gate here, deliberately. The status line had two, because it states a count and
 * findings that can describe different revisions. A verdict is one value from one fact; it cannot be half-landed,
 * so the only question is whether it CHANGED. A node that invented a gate it did not need would make the
 * abstraction look more expensive than it is.
 */
public class IdentityBanner implements EventLogSource {

    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private IdentityBannerView emitted;

    public IdentityBanner(OpenLog openLog, EffectQueue effects) {
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** The answer to a render: which backends drew it. Recorded; it changes nothing. */
    @OnEventHandler(propagate = false, filterString = "identityBanner")
    public boolean onViewRendered(SessionEvents.ViewRendered event) {
        auditLog.info("rendered", event.element()).info("backends", String.join(",", event.backends()));
        return false;
    }

    /** The open log moved: state the banner again if the verdict or its reason changed. */
    @OnTrigger
    public boolean onStateChanged() {
        IdentityBannerView view;
        if (!openLog.isOpen()) {
            // Review of #58: the three backends are the banner's ONLY writers, so a close must be stated too — or
            // a warning stays over an empty screen. Nothing told yet means nothing to take down.
            if (emitted == null) return false;
            view = IdentityBannerView.of(emitted.generation(), null, null);
        } else {
            view = IdentityBannerView.of(openLog.generation(), openLog.identity(), openLog.identityReason());
        }
        if (view.equals(emitted)) return false;
        Map<String, Object> changed = view.changedFrom(emitted);
        emitted = view;
        EventLogger entry = auditLog.info("render", "identityBanner");
        changed.forEach((k, v) -> entry.info(k, String.valueOf(v)));
        effects.request(new SessionEffects.RenderIdentityBannerEffect(0L, view));
        return true;
    }

    /** The last view emitted, or null before the first — what every backend was last told. */
    public IdentityBannerView view() {
        return emitted;
    }
}
