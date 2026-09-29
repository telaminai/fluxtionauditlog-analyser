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
 * <p><b>One rule, not a pile of guards.</b> A held plan labels the session only when the profile that
 * ACTUALLY applied is that plan's profile. Earlier versions reset the plan on a new request and dropped it on a
 * failed effect; with the match in place both are redundant, and a mutation control proved it by surviving.
 * A transition that verified a bundle and then died leaves a plan behind, and it is harmless: the next
 * profile to apply is somebody else's, so the match fails and nothing is claimed.
 */
public class OpenBundle implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    /** The verified plan, held from the verification until the profile is genuinely in force. */
    private SessionEvents.BundlePlan pending;
    private BundleProvenance current = BundleProvenance.NONE;

    public OpenBundle(OperationGate gate) {
        this.gate = gate;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
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
            pending = event.bundlePlan();
        }
        return false;
    }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied event) {
        if (!gate.accepted()) {
            return false;
        }
        // Decide from the profile that was ACTUALLY applied, never from which effects failed. Two reviews
        // found both directions of that guess wrong: "any failure ends the pending plan" killed a bundle
        // whose closeLog failed but which then applied fine, and "a restore failure means the project is
        // gone" erased a bundle that was still in force. A profile either IS the bundle's or it is not,
        // and ProfileApplied names it.
        current = pending != null && pending.profilePath().equals(event.profilePath())
                ? new BundleProvenance(pending.identity(), pending.source(), pending.workingCopy(),
                        pending.limits(), pending.notes())
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
}
