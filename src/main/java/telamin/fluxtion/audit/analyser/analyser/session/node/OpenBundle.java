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
 * <p><b>Why the request is observed at all.</b> The plan names the unpacked profile, not the {@code .fexp} the
 * person chose — and the {@code .fexp} is what a recents list must remember and what a title should name. Only
 * {@link SessionEvents.OpenProjectRequested} carries it. The request also resets the pending plan, so a bundle
 * that verified and then failed to apply cannot label the next ordinary project as evidence.
 */
public class OpenBundle implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    /** The {@code .fexp} the transition in flight was asked to open, when it is a bundle transition. */
    private String requestedSource;
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

    /**
     * Records which {@code .fexp} this transition is for. It deliberately does <b>not</b> reset {@link #pending}:
     * a mutation removing such a reset survived the gate (2026-09-29), because {@code operationGate} already
     * refuses a superseded transition's {@code ProfileLoaded} before this node sees it, so a plan can only ever
     * be set by the transition in force. {@code BundleOpenReplayTest#lateFirstBundleCannotStealASecondPendingBundle}
     * is where that refusal is pinned. Defence that cannot be made to fail is not defence, it is noise.
     */
    @OnEventHandler
    public boolean onRequested(SessionEvents.OpenProjectRequested event) {
        requestedSource = event.kind() == TransitionKind.OPEN_BUNDLE ? event.profilePath() : null;
        return false;
    }

    @OnEventHandler
    public boolean onProfileLoaded(SessionEvents.ProfileLoaded event) {
        if (!gate.accepted()) {
            return false;
        }
        if (!event.ok()) {
            pending = null;
            requestedSource = null;
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
        current = pending == null
                ? BundleProvenance.NONE
                : new BundleProvenance(pending.identity(), requestedSource, pending.workingCopy(), pending.limits());
        pending = null;
        requestedSource = null;
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
        requestedSource = null;
        auditLog.info("fromBundle", false);
        return true;
    }

    /** What a surface renders, and what a recents list keys on. Never null. */
    public BundleProvenance provenance() {
        return current;
    }
}
