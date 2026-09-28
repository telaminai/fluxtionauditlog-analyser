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
 * <p>Review PR57 R6: it holds the definition it is showing, FROZEN from the play request, and every effect carries the
 * step it names — so a later step can never be read from a definition edited meanwhile. A definition change reaches it
 * as {@link SessionEvents.WalkDefinitionChanged}, and it decides: a rename keeps the frozen version under the new name;
 * a delete, or a save that changes the steps, ends the showing and says why.
 */
public class WalkPlayback implements EventLogSource {

    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private String walk;
    private telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec definition;
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
    private WalkPlaybackState.Answer answer = WalkPlaybackState.Answer.NONE;
    /** M69.F3: whether this showing has already stated the unassessed-log caveat. */
    private boolean caveatStated;

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
        String name = e.walk().name();
        int steps = e.walk().steps().size();
        if (steps <= 0) {
            reason = "walk '" + name + "' has no steps";
            answer(e, false);
            auditLog.info("walkRefused", reason);
            return true;
        }
        int from = e.step() < 0 ? lastShown.getOrDefault(name, 0) : e.step();
        if (from >= steps) {
            reason = "walk '" + name + "' has " + steps + " step(s) — there is no step " + (from + 1);
            answer(e, false);
            auditLog.info("walkRefused", reason);
            return true;
        }
        answer(e, true);
        definition = e.walk();
        walk = name;
        count = steps;
        step = from;
        logOpenAtStart = openLog.isOpen();
        generation = logOpenAtStart ? openLog.generation() : -1;
        identityAtStart = openLog.identity();
        caveatStated = false;                 // M69.F3: a new showing states the caveat again, once
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

    /** Review PR57 R6: the node decides what a change to the SHOWING definition means; the adapter only reports it. */
    @OnEventHandler
    public boolean onWalkDefinitionChanged(SessionEvents.WalkDefinitionChanged e) {
        if (walk == null || !walk.equals(e.name())) return false;
        if (e.renamedTo() != null) {
            Integer at = lastShown.remove(walk);
            if (at != null) lastShown.put(e.renamedTo(), at);
            walk = e.renamedTo();
            definition = definition.renamed(e.renamedTo());
            auditLog.info("walkRenamed", walk);                   // the frozen version keeps showing, under its new name
            return true;
        }
        if (e.now() == null) {
            end("the walk was deleted");
            return true;
        }
        if (e.now().steps().equals(definition.steps())) return false;   // saved unchanged: nothing shown differs
        end("the walk was changed while it was showing — play it again to see the new version");
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
        reason = withIdentityCaveat(e.note());
        lastShown.put(walk, step);
        auditLog.info("walkShown", phase).info("lit", available.size()).info("of", targets.size());
        // review PR57 R1: ALWAYS say what is lit, even nothing — a re-resolution that makes every target unavailable must
        // take down the light the previous preparation put up, not leave it pointing at what can no longer be certified
        effects.request(new SessionEffects.LightWalkTargetsEffect(0L, ticket, available));
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
            effects.request(new SessionEffects.ResolveWalkTargetsEffect(0L, ticket, generation, definition, step,
                    "the log's identity is now " + identityAtStart
                            + (openLog.identityReason() == null ? "" : ": " + openLog.identityReason()),
                    recordsTrusted()));
            return true;
        }
        return false;
    }

    /**
     * Review PR57 R5/R1 (second round): what "current" can and cannot mean when the file behind the log has never
     * been re-checked.
     *
     * <p>{@link telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity#recordsTrusted} is false only for a
     * verdict that says the file CHANGED. A null verdict — Follow has not polled, or this reader cannot say — is
     * not a change, so the step resolves and its record and chart bases compare equal against the same store they
     * were saved from. That comparison is sound for what it claims (§3.5: a record digest binds one record's text
     * under one store representation) and says nothing about the file on disk, which nothing here has looked at.
     *
     * <p>The run basis cannot close that gap either: its file digests are taken when the log opens and the record
     * count it now carries does not move for an in-place rewrite of the same length. The verdict is the only thing
     * that sees one, and an unassessed log has no verdict.
     *
     * <p>So the walk says so, in the words {@code context} already uses for the same state, rather than presenting
     * a bare "current" that reads as "verified". Stated only when the step actually rests on a record or chart basis —
     * a structural step claims nothing about the log's contents.
     *
     * <p>M69.F3: stated ONCE per showing, on the first such step. A bundle opens with Follow off, so the verdict is
     * never formed, and repeating it on every step filled the strip's three reason lines with the same sentence.
     */
    private String withIdentityCaveat(String note) {
        if (openLog.identity() != null || !restsOnTheLogsContents() || caveatStated) return note;
        caveatStated = true;
        String caveat = "the file behind this log has not been re-checked since it was read, so 'current' here means "
                + "unchanged since this step was saved, not unchanged on disk";
        return note == null || note.isBlank() ? caveat : note + "; " + caveat;
    }

    /** Whether the step being shown rests on the log's contents — a record or a chart basis (§3.5). */
    private boolean restsOnTheLogsContents() {
        if (definition == null || step < 0 || step >= definition.steps().size()) return false;
        return definition.steps().get(step).targets().stream()
                .anyMatch(t -> "record".equals(t.basis().kind()) || "chart".equals(t.basis().kind()));
    }

    /**
     * Review PR57 R1: the node decides whether this step's record text may be read to certify anything. When the
     * session's verdict is that the file changed after it was read, the presenter reads NO record text, so record
     * targets resolve unresolved and are not lit, and charts are marked unresolved.
     */
    private boolean recordsTrusted() {
        return telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity.recordsTrusted(openLog.identity());
    }

    /** Review PR57 R7: the answer to THIS request, published for the caller that carried its id. */
    private void answer(SessionEvents.WalkPlayRequested e, boolean accepted) {
        if (e.request() != 0) answer = new WalkPlaybackState.Answer(e.request(), accepted, accepted ? "" : reason);
    }

    private void prepare() {
        ticket++;
        phase = "PREPARING";
        reason = "";
        targets = List.of();
        auditLog.info("decision", "applyWalkView").info("ticket", ticket);
        effects.request(new SessionEffects.ApplyWalkViewEffect(0L, ticket, generation, definition, step, recordsTrusted()));
    }

    private void end(String why) {
        lastShown.put(walk, step);
        auditLog.info("walkEnded", walk).info("reason", why);
        walk = null;
        definition = null;
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
        return new WalkPlaybackState(walk, step, count, phase, reason, ticket, targets, lastShown, definition, answer);
    }
}
