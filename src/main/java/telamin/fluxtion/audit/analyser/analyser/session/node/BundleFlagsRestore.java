package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * #178: when an evidence bundle's flags are put back on its log, and what that found.
 *
 * <p>A bundle carries the flags its report's {@code finding} sections render, each anchored by the flagged record's
 * digest ({@code BundleFlags}). This node decides WHEN they go back: once, on the first log opened after a bundle's
 * profile is genuinely applied — that log is the bundle's own, opened as part of opening it. A later log opened in the
 * same session is not the bundle's evidence, and no flag is attached to it. The adapter only reads the working copy,
 * compares the records and reports the outcome as a fact; this node keeps it, so {@code context} and the status line
 * say how many came back and why any did not, instead of the recipient reading findings with no text.
 *
 * <p>Sibling of {@link BundleAnchor}, for the same reason: both are decisions about a bundle's arrival that a surface
 * could only take by guessing from the order effects happen to run in.
 */
public class BundleFlagsRestore implements EventLogSource {

    private final OpenBundle openBundle;
    private final OpenLog openLog;
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    /** The bundle whose flags are waiting for its log, or null. */
    private String awaiting;
    private long requestedGeneration = -1;
    private String bundle;
    private int carried;
    private int restored;
    private java.util.List<String> withheld = java.util.List.of();

    public BundleFlagsRestore(OpenBundle openBundle, OpenLog openLog, EffectQueue effects) {
        this.openBundle = openBundle;
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** A profile came into force: a bundle's arms the restore for its log; anything else ends what was kept. */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        var provenance = openBundle.provenance();
        awaiting = provenance.fromBundle() ? provenance.identity() : null;
        requestedGeneration = -1;
        bundle = null;
        carried = 0;
        restored = 0;
        withheld = java.util.List.of();
        if (awaiting != null && openLog.isOpen()) return request(event.opId());   // its log landed first
        return false;
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored event) {
        awaiting = null;
        bundle = null;
        carried = 0;
        restored = 0;
        withheld = java.util.List.of();
        return false;
    }

    @OnEventHandler
    public boolean onLogOpened(SessionEvents.LogOpened event) {
        if (awaiting == null || !openLog.isOpen()) return false;
        return request(event.opId());
    }

    private boolean request(long opId) {
        if (!awaiting.equals(openBundle.provenance().identity())) {
            awaiting = null;
            return false;
        }
        requestedGeneration = openLog.generation();
        effects.request(new SessionEffects.RestoreBundleFlagsEffect(opId, requestedGeneration, awaiting));
        auditLog.info("flags", "restoreRequested").info("bundle", awaiting).info("generation", requestedGeneration);
        awaiting = null;                       // once: a later log in this session is not the bundle's evidence
        return true;
    }

    @OnEventHandler
    public boolean onBundleFlagsRestored(SessionEvents.BundleFlagsRestored event) {
        if (event.generation() != requestedGeneration || event.generation() != openLog.generation()
                || !java.util.Objects.equals(event.bundleIdentity(), openBundle.provenance().identity())) {
            auditLog.info("staleFact", "BundleFlagsRestored").info("generation", event.generation());
            return false;
        }
        bundle = event.bundleIdentity();
        carried = event.carried();
        restored = event.restored();
        withheld = event.withheld();
        auditLog.info("flags", "restored").info("carried", carried).info("restored", restored)
                .info("withheld", withheld.size());
        if (carried > 0) {
            effects.request(new SessionEffects.ShowStatusEffect(event.opId(), line()));
        }
        return true;
    }

    /** The node's one sentence about the bundle's flags, or "" when the bundle carried none. */
    public String line() {
        if (bundle == null || carried == 0) return "";
        return "Bundle flags: " + restored + " of " + carried + " put back on their records"
                + (withheld.isEmpty() ? "" : "; " + withheld.size() + " withheld — " + withheld.get(0)
                        + (withheld.size() > 1 ? " (and " + (withheld.size() - 1) + " more, in context)" : ""));
    }

    /** For {@code context.project.bundle.flags}: null when no bundle's flags were restored in this session. */
    public java.util.Map<String, Object> state() {
        if (bundle == null) return null;
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("carried", carried);
        out.put("restored", restored);
        if (!withheld.isEmpty()) out.put("withheld", withheld);
        return out;
    }
}
