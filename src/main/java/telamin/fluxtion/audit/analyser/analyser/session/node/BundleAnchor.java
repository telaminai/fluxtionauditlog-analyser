package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Where the code for an evidence bundle lives on THIS machine (#75).
 *
 * <p>A bundle carries no source: capture strips every root, because they are machine paths. Its graph
 * still names the classes, so the missing half is only WHERE, and the roots a person chooses while the
 * bundle is open are that answer. This node decides when that answer is worth keeping and when it should
 * be put back; the adapter only performs the writing and reports what it saw.
 *
 * <p><b>Why this is a node and not a few lines in the frame.</b> It began in the frame, hung off the
 * method the {@code source_root} verb calls — which the Settings dialog does not, so the feature worked
 * from a script and silently recorded nothing when a person used the very dialog the Project panel sends
 * them to. Moving it to the config funnel fixed that and introduced a worse fault: the funnel also runs
 * inside a transition's rendering half, and during a close the person's own settings are already back,
 * so a DEMO bundle was recorded as anchored to nineteen unrelated repositories. Both were decisions
 * about session state taken in a surface. Rule 9 says they belong here.
 */
public class BundleAnchor implements EventLogSource {

    private final OpenBundle openBundle;
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private java.util.List<String> visibleRoots = java.util.List.of();
    private java.util.List<String> rememberedRoots = java.util.List.of();

    public BundleAnchor(OpenBundle openBundle, EffectQueue effects) {
        this.openBundle = openBundle;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /**
     * A bundle is now in force. Ask for whatever was remembered for it — the adapter decides which of
     * those roots still exist, because that is a question about the filesystem, not about the session.
     *
     * <p><b>No "only once" key.</b> An earlier version remembered which bundle it had already asked for,
     * to survive a re-render. It cost the feature its main case: open a bundle, anchor it, open the SAME
     * bundle again, and the key suppressed the restore, which is precisely the moment the anchor exists
     * to serve. The key was never needed — {@code ProfileApplied} follows an accepted load, one apply per
     * transition, so a re-render does not reach here at all.
     */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        visibleRoots = java.util.List.of();
        rememberedRoots = java.util.List.of();
        var bundle = openBundle.provenance();
        if (!bundle.fromBundle() || bundle.source() == null) {
            return false;
        }
        effects.request(new SessionEffects.RestoreBundleAnchorEffect(event.opId(), bundle.source()));
        auditLog.info("anchor", "restoreRequested").info("bundle", bundle.source());
        return true;
    }

    @OnEventHandler
    public boolean onBundleRootsRestored(SessionEvents.BundleRootsRestored event) {
        if (!java.util.Objects.equals(openBundle.provenance().source(), event.bundleSource())) return false;
        visibleRoots = event.visible();
        rememberedRoots = event.remembered();
        return false;
    }

    /**
     * The person changed the source roots while a bundle was in force, so those roots ARE the answer to
     * where its code lives — <b>including when the answer is "nowhere"</b>.
     *
     * <p>An empty set used to be refused here, to stop a profile load wiping a good anchor. That
     * protection is real but it belongs to the TRANSITION, not to emptiness: the frame does not report
     * at all during a transition's rendering half, so a transient empty never reaches this node. What
     * the refusal actually did was make a deliberate deletion meaningless — delete a bundle's source
     * root, close, reopen, and it came back, because the deletion was never recorded (found in use,
     * 2026-09-30). An observation here may instead be an unrelated config edit (#95). Compare it with
     * the last restored/observed VISIBLE roots: remove only a root that disappeared from that set,
     * retaining remembered roots that were unavailable during restoration.
     */
    @OnEventHandler
    public boolean onSourceRootsObserved(SessionEvents.SourceRootsObserved event) {
        var bundle = openBundle.provenance();
        if (!bundle.fromBundle() || bundle.source() == null) {
            return false;
        }
        // A config observation is not necessarily a root edit. Restore may have omitted offline
        // directories; only removal of a previously VISIBLE root is a person's deletion (#95).
        var changed = new java.util.LinkedHashSet<>(rememberedRoots);
        visibleRoots.stream().filter(root -> !event.roots().contains(root)).forEach(changed::remove);
        changed.addAll(event.roots());
        visibleRoots = event.roots();
        if (rememberedRoots.equals(java.util.List.copyOf(changed))) return false;
        rememberedRoots = java.util.List.copyOf(changed);
        effects.request(new SessionEffects.RememberBundleAnchorEffect(0L, bundle.source(), rememberedRoots));
        auditLog.info("anchor", "remember").info("roots", event.roots().size());
        return true;
    }
}
