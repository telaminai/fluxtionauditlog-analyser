package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * M69 — walk playback: which walk is showing, which step, and every transition between them
 * (the M69 walk spec, §3.8). The owner, 2026-09-27: "the logic, transitions and state mutation in the orchestrator
 * in a single place" — so this node decides, and the frame only performs what it asks and reports what happened.
 *
 * <p><b>What it decides.</b> The next step (bounded — never past either end); when to ask for a step's view
 * ({@link SessionEffects.ApplyWalkViewEffect}); when to light ({@link SessionEffects.LightWalkTargetsEffect}), only for
 * its current ticket and log generation; when to re-resolve (a changed log identity); and when the walk ends — on a
 * request, on a new log generation, or when the log it was showing over closes.
 *
 * <p><b>What it refuses.</b> Any answer carrying a ticket or generation that is not current: a late preparation from
 * a superseded step cannot light anything, advance the walk, or overwrite the reason (review R8).
 *
 * <p>It holds no walk definitions — those are configuration, like reports. A play request carries the step count the
 * adapter read; a step's content is fetched by the adapter when the node asks for it.
 */
public class WalkPlayback implements EventLogSource {

    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private String walk;
    private int step;
    private int count;
    private String phase = "IDLE";
    private String reason = "";
    private long ticket;
    private long generation = -1;
    private boolean logOpenAtStart;
    private String identityAtStart;
    private List<SessionEvents.WalkTargetState> targets = List.of();
    private Map<String, Integer> lastShown = new HashMap<>();

    public WalkPlayback(OpenLog openLog, EffectQueue effects) {
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onWalkPlayRequested(SessionEvents.WalkPlayRequested e) {
        if (e.count() <= 0) {
            reason = "walk '" + e.walk() + "' has no steps";
            auditLog.info("walkRefused", reason);
            return true;
        }
        int from = e.step() < 0 ? lastShown.getOrDefault(e.walk(), 0) : e.step();
        if (from >= e.count()) {
            reason = "walk '" + e.walk() + "' has " + e.count() + " step(s) — there is no step " + (from + 1);
            auditLog.info("walkRefused", reason);
            return true;
        }
        walk = e.walk();
        count = e.count();
        step = from;
        logOpenAtStart = openLog.isOpen();
        generation = logOpenAtStart ? openLog.generation() : -1;
        identityAtStart = openLog.identity();
        auditLog.info("walkPlay", walk).info("step", step + 1).info("origin", String.valueOf(e.origin()));
        prepare();
        return true;
    }

    @OnEventHandler
    public boolean onWalkNavigated(SessionEvents.WalkNavigated e) {
        if (walk == null) {
            auditLog.info("noOp", "WalkNavigated").info("reason", "no walk is showing");
            return false;
        }
        int to = step + e.delta();
        if (to < 0 || to >= count) {
            reason = to < 0 ? "this is the first step" : "this is the last step";
            auditLog.info("walkBoundary", reason);
            return true;
        }
        step = to;
        auditLog.info("walkStep", step + 1);
        prepare();
        return true;
    }

    @OnEventHandler
    public boolean onWalkEndRequested(SessionEvents.WalkEndRequested e) {
        if (walk == null) return false;
        end(e.reason());
        return true;
    }

    @OnEventHandler
    public boolean onWalkViewApplied(SessionEvents.WalkViewApplied e) {
        if (stale(e.ticket(), "WalkViewApplied")) return false;
        if (e.ok()) return false;                     // applied: the preparation's result is what changes the state
        phase = "NOT_SHOWN";
        reason = e.reason();
        targets = List.of();
        lastShown.put(walk, step);
        auditLog.info("walkViewRefused", reason);
        return true;
    }

    @OnEventHandler
    public boolean onWalkStepPrepared(SessionEvents.WalkStepPrepared e) {
        if (stale(e.ticket(), "WalkStepPrepared")) return false;
        // Defensive: a new generation already ended the walk and moved the ticket, so the ticket check above refuses
        // first; this only catches an adapter answering with a generation it was not given. No control can reach it.
        if (e.generation() != generation) {
            auditLog.warn("staleFact", "WalkStepPrepared").warn("generation", e.generation()).warn("current", generation);
            return false;
        }
        targets = e.targets();
        List<SessionEvents.WalkTargetState> available = targets.stream().filter(SessionEvents.WalkTargetState::available).toList();
        phase = available.size() == targets.size() ? "SHOWN" : available.isEmpty() ? "NOT_SHOWN" : "PARTLY_SHOWN";
        reason = e.note();
        lastShown.put(walk, step);
        auditLog.info("walkShown", phase).info("lit", available.size()).info("of", targets.size());
        if (!available.isEmpty()) effects.request(new SessionEffects.LightWalkTargetsEffect(0L, ticket, available));
        return true;
    }

    @OnEventHandler
    public boolean onWalkTargetsLit(SessionEvents.WalkTargetsLit e) {
        if (stale(e.ticket(), "WalkTargetsLit")) return false;
        long wanted = targets.stream().filter(SessionEvents.WalkTargetState::available).count();
        if (e.lit() < wanted) {
            phase = e.lit() == 0 ? "NOT_SHOWN" : "PARTLY_SHOWN";
            reason = e.reason();
            auditLog.warn("walkLitFewer", e.lit()).warn("wanted", wanted);
            return true;
        }
        return false;
    }

    @OnEventHandler(propagate = false)
    public boolean onWalkAcknowledged(SessionEvents.WalkAcknowledged e) {
        auditLog.info("walkAck", e.what());
        return false;
    }

    /** The log moved: a close or a new generation ends the walk; a changed identity re-resolves the showing step. */
    @OnTrigger
    public boolean onLogChanged() {
        if (walk == null) return false;
        if (logOpenAtStart && !openLog.isOpen()) {
            end("the log was closed");
            return true;
        }
        if (openLog.isOpen() && openLog.generation() != generation) {
            end(logOpenAtStart ? "another log was opened" : "a log was opened");
            return true;
        }
        if (!java.util.Objects.equals(identityAtStart, openLog.identity())) {
            identityAtStart = openLog.identity();
            ticket++;
            phase = "PREPARING";
            auditLog.info("walkReresolve", String.valueOf(identityAtStart));
            effects.request(new SessionEffects.ResolveWalkTargetsEffect(0L, ticket, generation, walk, step,
                    "the log's identity is now " + identityAtStart
                            + (openLog.identityReason() == null ? "" : ": " + openLog.identityReason())));
            return true;
        }
        return false;
    }

    private void prepare() {
        ticket++;
        phase = "PREPARING";
        reason = "";
        targets = List.of();
        auditLog.info("decision", "applyWalkView").info("ticket", ticket);
        effects.request(new SessionEffects.ApplyWalkViewEffect(0L, ticket, generation, walk, step));
    }

    private void end(String why) {
        lastShown.put(walk, step);
        auditLog.info("walkEnded", walk).info("reason", why);
        walk = null;
        count = 0;
        phase = "IDLE";
        reason = "ended: " + why;
        targets = List.of();
        ticket++;
        effects.request(new SessionEffects.EndWalkEffect(0L, ticket, why));
    }

    private boolean stale(long named, String what) {
        if (walk == null || named != ticket) {
            auditLog.info("staleFact", what).info("ticket", named).info("current", ticket);
            return true;
        }
        return false;
    }

    /** The published state — immutable, for the snapshot. */
    public WalkPlaybackState state() {
        return new WalkPlaybackState(walk, step, count, phase, reason, ticket, targets, lastShown);
    }
}
