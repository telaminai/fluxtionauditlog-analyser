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
 * {@link SessionEvents.OpenProjectRequested} carries it. The request also resets the pending plan, and an
 * {@link SessionEvents.EffectFailed} ends the transition, so a bundle that verified and then failed to apply
 * cannot label the next ordinary project as evidence.
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
     * Records which {@code .fexp} this transition is for, and <b>resets any plan the last transition left
     * behind</b>. {@code sessionBoundary} clears its identical accumulator in exactly the same place
     * ({@code inFlightBundle = null} is its first statement) and for the same reason.
     *
     * <p><b>This reset was briefly removed, and that was a defect (2026-09-29).</b> A mutation control for it
     * survived, and the conclusion drawn was "the gate already refuses a superseded transition, so the line is
     * dead". The gate does refuse a superseded transition from SETTING a plan. It does nothing about an
     * ACCEPTED plan SURVIVING a transition that then dies — an {@code ApplyProfileEffect} that throws becomes
     * {@link SessionEvents.EffectFailed} and the batch continues, leaving a plan held with no fact to settle
     * it. The next ordinary project then published {@code fromBundle() == true}, with a stranger's identity
     * and notes attached to the person's own work. A surviving mutation means the line is dead OR the path is
     * untested; here it was untested. {@code BundleProvenanceTest#aBundleWhoseApplyFailsCannotLabelTheNextProject}
     * is the test that was missing.
     */
    @OnEventHandler
    public boolean onRequested(SessionEvents.OpenProjectRequested event) {
        pending = null;
        requestedSource = event.kind() == TransitionKind.OPEN_BUNDLE ? event.profilePath() : null;
        return false;
    }

    /** The effect whose failure means the project is GONE, not merely that something went wrong. */
    private static final String RESTORE = "restoreSettings";

    /**
     * A transition that died. An effect failure does not stop the batch, so without this a half-finished bundle
     * open leaves a plan held for whatever settles next.
     *
     * <p>Two different things, deliberately not merged. The <b>pending</b> plan is always dropped: the transition
     * carrying it is over and nothing will settle it. What is already <b>in force</b> is dropped only when the
     * failure was the restore, because that is the one failure meaning there is no project any more. Clearing it
     * on any failure would be a false NEGATIVE — an unrelated effect failing while a bundle is genuinely open
     * would stop the window saying so, which is the same class of lie in the other direction.
     */
    @OnEventHandler
    public boolean onEffectFailed(SessionEvents.EffectFailed event) {
        if (!gate.accepted()) {
            return false;
        }
        pending = null;
        requestedSource = null;
        if (RESTORE.equals(event.effect()) && current.fromBundle()) {
            current = BundleProvenance.NONE;
            auditLog.info("fromBundle", false).info("because", "restoreSettings failed");
            return true;
        }
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
                : new BundleProvenance(pending.identity(), requestedSource, pending.workingCopy(),
                        pending.limits(), pending.notes());
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
