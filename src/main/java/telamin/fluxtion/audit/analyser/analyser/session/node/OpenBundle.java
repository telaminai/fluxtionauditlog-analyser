package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.BundleProvenance;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.TransitionKind;

/**
 * Whether the project <b>in force</b> came from a verified evidence bundle, and which one.
 *
 * <p>The sibling of {@link ActiveProject}, and it settles on the same fact for the same reason:
 * {@link SessionEvents.ProfileApplied} means the settings are genuinely applied. A bundle that verified but
 * whose profile never applied is not the session you are looking at.
 *
 * <p><b>Why a plan is held rather than read at the end.</b> A bundle transition emits {@code ProfileLoaded}
 * TWICE: once from the verification, carrying the plan, and once from loading the profile <em>inside</em> the
 * working copy, carrying none. A node that simply took the latest would be cleared by the second and would
 * publish "not a bundle" for every bundle. So a plan is remembered when one arrives and is not unset by a
 * later plan-less load.
 *
 * <p>A pathname is not ownership. Only the accepted operation that verified a plan can apply it,
 * and its internal profile load must report the same verified content. A failed operation cannot
 * lend its provenance to a replacement file, even at exactly the same path (#93).
 */
public class OpenBundle implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    /** The verified plan, held from the verification until the profile is genuinely in force. */
    private SessionEvents.BundlePlan pending;
    private long pendingOperation = -1;
    private boolean verifiedContentLoaded;
    private BundleProvenance current = BundleProvenance.NONE;

    public OpenBundle(OperationGate gate) {
        this.gate = gate;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onOpenProjectRequested(SessionEvents.OpenProjectRequested event) {
        pending = null;
        pendingOperation = event.kind() == TransitionKind.OPEN_BUNDLE ? event.opId() : -1;
        verifiedContentLoaded = false;
        return false;
    }

    @OnEventHandler
    public boolean onProfileLoaded(SessionEvents.ProfileLoaded event) {
        if (!gate.accepted()) {
            return false;
        }
        if (!event.ok()) {
            pending = null;
            return false;
        }
        // Only a load that CARRIES a plan sets one; the bundle's own profile load carries none (see above).
        if (event.bundlePlan() != null) {
            if (event.opId() == pendingOperation) pending = event.bundlePlan();
        } else if (pending != null && event.opId() == pendingOperation) {
            verifiedContentLoaded = pending.profileDigest() != null
                    && pending.profileDigest().equals(event.contentDigest());
        }
        return false;
    }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        if (!gate.accepted()) {
            return false;
        }
        // An unrelated failed effect does not revoke a genuinely applied bundle. A new application
        // does: both the causal operation and the exact loaded content must establish its provenance.
        current = pending != null && event.opId() == pendingOperation && verifiedContentLoaded
                && pending.profilePath().equals(event.profilePath())
                ? new BundleProvenance(pending.identity(), pending.source(), pending.workingCopy(),
                        pending.limits(), pending.notes(), pending.processor())
                : BundleProvenance.NONE;
        pending = null;
        auditLog.info("fromBundle", current.fromBundle()).info("bundleIdentity", current.identity());
        return true;
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored event) {
        if (!gate.accepted()) {
            return false;
        }
        current = BundleProvenance.NONE;
        pending = null;
        auditLog.info("fromBundle", false);
        return true;
    }

    /** What a surface renders, and what a recents list keys on. Never null. */
    public BundleProvenance provenance() {
        return current;
    }

    public boolean acceptsOperation(long opId) { return gate.expectedOpId() == opId; }
}
