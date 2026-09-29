package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.CaptureState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.List;

/**
 * Evidence bundle capture (convergence, 2026-09-28): the {@code capture-evidence-bundle} skill's seven steps, as the
 * session's decisions. Rule 9: this node decides whether a capture may run and, afterwards, whether what was written
 * stands; the frame performs the effects and reports what happened as facts.
 *
 * <ol>
 *   <li>Refuse, by name, on: no log; a load pending; the file's identity {@code replacement}/{@code unverified};
 *       the file changed on disk; not exactly one plain file. And on a capture already being written.</li>
 *   <li>Record the log generation the capture is decided in, and pause Follow when it is on.</li>
 *   <li>Ask for the bundle to be written ({@link SessionEffects.CaptureBundleEffect}).</li>
 *   <li>When it is written, apply the walk-save rule to the copy: if another log was opened, or the log was closed,
 *       while it was being written, it mixes two sessions, so it is REFUSED and deleted.</li>
 *   <li>Restore Follow, whatever happened.</li>
 * </ol>
 *
 * <p>The coherence rule is decided HERE, from the generation the capture recorded and the one {@link OpenLog} holds
 * when the result arrives, as {@link WalkPlayback} decides a walk step's. (The walk SAVE applies the same rule in the
 * frame, {@code WalkAuthoring}; that is a rule 9 departure this node does not copy.)
 */
public class EvidenceCapture implements EventLogSource {

    private final OpenLog openLog;
    private final OperationGate gate;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private String phase = "IDLE";
    private long ticket;
    private long generation = -1;
    private boolean resumeFollow;
    private String path;
    private String identity;
    private String reason = "";
    private List<String> lines = List.of();
    private CaptureState.Answer answer = CaptureState.Answer.NONE;
    // the replay this capture carries (replay spec §4.1), for the lines the node publishes when it is written
    private int replayRecords;
    private int serviceCalls;
    private int uncarried;
    private int unproven;
    private boolean withReplay;

    public EvidenceCapture(OpenLog openLog, OperationGate gate, EffectQueue effects) {
        this.openLog = openLog;
        this.gate = gate;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onBundleCaptureRequested(SessionEvents.BundleCaptureRequested e) {
        String refusal = refusal(e);
        if (refusal != null) {
            if (!"WRITING".equals(phase)) {
                phase = "REFUSED";
                reason = refusal;
                path = null;
                identity = null;
                lines = List.of();
            }
            answer = new CaptureState.Answer(e.request(), false, refusal);
            auditLog.info("captureRefused", refusal);
            return true;
        }
        ticket++;
        phase = "WRITING";
        reason = "";
        path = e.path();
        identity = null;
        lines = List.of();
        generation = openLog.generation();
        resumeFollow = openLog.following();
        withReplay = e.replay() != null;
        replayRecords = e.replayRecords();
        serviceCalls = e.serviceCalls();
        uncarried = e.replayUncarried();
        unproven = e.replayUnproven();
        answer = new CaptureState.Answer(e.request(), true, "");
        auditLog.info("capture", path).info("generation", generation).info("ticket", ticket);
        if (resumeFollow) effects.request(new SessionEffects.SetFollowEffect(0L, ticket, false));
        boolean readSoFar = "changed-on-disk".equals(e.freshness()) && openLog.following();
        effects.request(new SessionEffects.CaptureBundleEffect(0L, ticket, generation, e.path(), e.notes(), e.from(), e.to(),
                readSoFar, e.replay(), e.replayRecords(), e.serviceCalls(), e.replaySha256(), e.replayUnproven()));
        return true;
    }

    /** The skill's step 2, and one more: the reason this capture cannot run, by name, or null. */
    private String refusal(SessionEvents.BundleCaptureRequested e) {
        if ("WRITING".equals(phase)) return "a bundle is already being written (" + path + "); wait for it, then capture";
        if (!openLog.isOpen()) return "no log is open: open the log you are investigating first";
        if (gate.inFlightWhat() != null) return "a load is pending (" + gate.inFlightWhat() + "): wait for it to land, then capture";
        String session = openLog.identity() == null ? null : openLog.identity().toLowerCase(java.util.Locale.ROOT);
        for (String id : new String[]{e.observedIdentity(), session}) {
            if ("replacement".equals(id) || "unverified".equals(id)) {
                return "the log file is not established to be the one that was read (identity: " + id + "); reopen it first";
            }
        }
        if ("changed-on-disk".equals(e.freshness())) {
            // owner, 2026-09-28 (EB.F6): under Follow a changed file is a GROWING one (a replacement or a rewrite was
            // refused above, by identity), and the bundle holds what was read so far. Outside Follow it is refused.
            if (!openLog.following()) return "the log file changed on disk since it was read; reopen it first";
            if (openLog.total() == 0) {
                return "the log is still growing under Follow and nothing has been read from it yet: capture once a "
                        + "record has been read";
            }
        }
        if (!e.onePlainFile()) {
            return "the log is not one plain file (a rolled set, a directory or a remote store): a bundle carries one file";
        }
        // convergence review: the frame OBSERVES how many records the window selects; refusing an empty one is ours
        if ((e.from() != null || e.to() != null) && e.windowRecords() == 0) {
            return "no record's log time is between " + (e.from() == null ? "the start" : e.from()) + " and "
                    + (e.to() == null ? "the end" : e.to()) + ": nothing to excerpt";
        }
        if (e.replay() != null) {
            // replay spec §4.3: the processor's state at a window's start depends on every earlier input
            if (e.from() != null || e.to() != null) return "a replay needs the whole run: drop the window or the replay";
            // second review (S4): under Follow the producer may still be writing, so the log copied later off this
            // thread may hold records the replay records paired now do not: one moment needs Follow off, whether or not
            // growth has been seen yet
            if (openLog.following()) {
                return "the log is being followed, so the run may not have ended: a replay needs the whole run, so turn "
                        + "Follow off once it has ended, then capture";
            }
            // the frame OBSERVED whether the replay pairs with the open log; refusing one that does not is ours
            if (e.replayProblem() != null) return "the replay does not belong to this log: " + e.replayProblem();
        }
        return null;
    }

    @OnEventHandler
    public boolean onBundleWritten(SessionEvents.BundleWritten e) {
        if (stale(e.ticket(), "BundleWritten")) return false;
        if (!openLog.isOpen() || openLog.generation() != e.generation()) {
            // the walk-save rule, applied to the copy: it was read against a log that is no longer the open one
            phase = "REFUSED";
            reason = (openLog.isOpen() ? "another log was opened" : "the log was closed")
                    + " while the bundle was being written — it was deleted, because it would mix two sessions";
            identity = null;
            lines = List.of();
            auditLog.info("captureIncoherent", reason).info("generation", e.generation()).info("now", openLog.generation());
            effects.request(new SessionEffects.DeleteBundleEffect(0L, ticket, e.path()));
        } else {
            phase = "WRITTEN";
            identity = e.identity();
            lines = withReplay ? withReplayLines(e.lines()) : e.lines();
            auditLog.info("captured", identity);
        }
        restoreFollow();
        return true;
    }

    @OnEventHandler
    public boolean onBundleWriteFailed(SessionEvents.BundleWriteFailed e) {
        if (stale(e.ticket(), "BundleWriteFailed")) return false;
        phase = "REFUSED";
        reason = e.reason();
        identity = null;
        lines = List.of();
        auditLog.info("captureFailed", reason);
        restoreFollow();
        return true;
    }

    @OnEventHandler(propagate = false)
    public boolean onCaptureStarted(SessionEvents.CaptureStarted e) {
        auditLog.info("captureStarted", e.ticket());
        return false;
    }

    @OnEventHandler(propagate = false)
    public boolean onFollowSet(SessionEvents.FollowSet e) {
        auditLog.info("followSet", e.on());
        return false;
    }

    @OnEventHandler(propagate = false)
    public boolean onBundleDeleted(SessionEvents.BundleDeleted e) {
        auditLog.info("bundleDeleted", e.ok()).info("reason", String.valueOf(e.reason()));
        return false;
    }

    /** What the author is told about the replay the bundle carries: what it is, and what it cannot reproduce. */
    private List<String> withReplayLines(List<String> written) {
        List<String> out = new java.util.ArrayList<>(written);
        // PR #70 review, finding 1: say exactly how each input was matched to the log, never more
        out.add("replay: the run's " + replayRecords + " recorded inputs, matched to the log in order by type, instant and "
                + (unproven == 0 ? "content" : "content for " + (replayRecords - unproven) + " of them")
                + "; a recipient can replay them into their own build and compare");
        if (unproven > 0) {
            out.add("replay: " + unproven + " input(s) are matched by type and instant only: the log does not print their "
                    + "content, so it cannot show they are this run's inputs");
        }
        if (uncarried > 0) {
            // review S1: a replay cut short pairs too; say what it does not carry rather than read as the whole run
            out.add("replay: the log holds " + uncarried + " record(s) of the replay's own event types that it does not "
                    + "carry: raised by the graph itself, or inputs the replay is missing (a replay cut short); "
                    + "--replay-compare names the first that matters");
        }
        if (serviceCalls > 0) {
            out.add("replay: the log holds " + serviceCalls + " exported-service call(s) the replay does not carry, so a "
                    + "replay diverges from the first cycle that depends on one");
        }
        return List.copyOf(out);
    }

    /** The skill's step 1, second half: Follow comes back on whatever happened. */
    private void restoreFollow() {
        if (resumeFollow) {
            resumeFollow = false;
            effects.request(new SessionEffects.SetFollowEffect(0L, ticket, true));
        }
    }

    private boolean stale(long named, String what) {
        if (!"WRITING".equals(phase) || named != ticket) {
            auditLog.info("staleFact", what).info("ticket", named).info("current", ticket);
            return true;
        }
        return false;
    }

    /** The published state — immutable, for the snapshot. */
    public CaptureState state() {
        return new CaptureState(phase, ticket, path, identity, reason, lines, answer);
    }
}
