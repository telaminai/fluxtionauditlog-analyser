package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The onboard assistant's lifecycle (spec-onboard-assistant-journeys.md §4–§5, OA-1): every decision the assistant
 * panel used to make for itself — whether a question may be sent, which round follows, whether an action may run,
 * when a budget is spent, whether a late reply still counts, and what a workspace change means for a conversation.
 *
 * <p><b>Rule 9.</b> The panel reports Send, Cancel, New chat and host requests as facts and renders the published
 * {@link AssistantState}; the adapter performs the effects asked for here and reports what happened. Nothing outside
 * this node decides whether a reply, an action or a result belongs to the current turn.
 *
 * <p><b>Tickets.</b> Every effect carries the current turn ticket, and every outcome names it. Cancel, New chat, a
 * handoff and a superseding workspace change move the ticket before anything else happens, so a reply, an error or an
 * action result that arrives afterwards is refused here, whatever the transport managed to stop.
 *
 * <p><b>One action at a time.</b> Actions from a reply run in order, each asked for only when the previous one has
 * answered and only while the ticket is current, so the check happens before EACH action, not once per reply.
 *
 * <p><b>What it never holds.</b> A credential, or the words of a question, an answer or a result. Those are transcript
 * entries, named here by id, so none of them reaches the session audit record.
 */
public class AssistantLoop implements EventLogSource {

    private final OpenLog openLog;
    private final OpenGraph openGraph;
    private final ActiveProject activeProject;
    private final OperationGate gate;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private long conversation = 1;
    private long ticket;
    private long turn;
    private String phase = "IDLE";
    private int round;
    private int actionsRun;
    private int actionsThisReply;
    private long runningAction;
    private String runningVerb;
    /** The one asynchronous log open this ticket/action actually requested, never a time window for all opens. */
    private long ownedLogOp;
    private boolean ownedLogApplied;
    private long heldOpenResult;
    private String heldOpenVerb;
    private long ownedProjectOp;
    /** The session's view fact, included in the basis without publishing filter/search text. */
    private String viewKey;
    private List<AssistantState.Entry> entries = new ArrayList<>();
    private List<SessionEffects.HistoryMessage> history = new ArrayList<>();
    private ArrayDeque<Long> pending = new ArrayDeque<>();
    private List<Long> roundResults = new ArrayList<>();
    private boolean manifestSent;
    private boolean manifestInFlight;
    private boolean docked = true;
    private boolean frozen;
    private SessionEvents.AssistantRoute route;
    private String basisKey;
    private String basisLabel = "";
    private String reason = "";
    private AssistantState.Answer answer = AssistantState.Answer.NONE;
    /** The published state, rebuilt only when something changed, so an unrelated snapshot does not copy the entries. */
    private AssistantState published = AssistantState.IDLE;
    private boolean dirty = true;

    public AssistantLoop(OpenLog openLog, OpenGraph openGraph, ActiveProject activeProject, OperationGate gate,
                         EffectQueue effects) {
        this.openLog = openLog;
        this.openGraph = openGraph;
        this.activeProject = activeProject;
        this.gate = gate;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    // ---- requests -------------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onSendRequested(SessionEvents.AssistantSendRequested e) {
        String refusal = refusal(e);
        if (refusal != null) {
            reason = refusal;
            if (e.request() != 0) answer = new AssistantState.Answer(e.request(), false, refusal);
            auditLog.info("assistantRefused", refusal);
            return changed();
        }
        if (entries.isEmpty()) captureBasis();   // a fresh thread is about the workspace it is asked in
        turn++;
        ticket++;
        phase = "PREPARING";
        reason = "";
        round = 0;
        actionsRun = 0;
        clearOwnership();
        route = e.route();
        pending.clear();
        roundResults = new ArrayList<>();
        entries.add(new AssistantState.Entry(e.draft(), AssistantState.USER, turn, "", "", "", 0));
        if (e.request() != 0) answer = new AssistantState.Answer(e.request(), true, "");
        boolean includeManifest = route.actions() && !manifestSent;
        manifestInFlight = includeManifest;
        boolean includeRecordContext = history.isEmpty();   // the record context rides on a conversation's first question
        auditLog.info("assistantTurn", turn).info("ticket", ticket).info("manifest", includeManifest)
                .info("recordContext", includeRecordContext);
        effects.request(new SessionEffects.PrepareAssistantContextEffect(0L, ticket, e.draft(), includeManifest,
                includeRecordContext, route.maxActionsPerReply()));
        return changed();
    }

    /** Why this send cannot run, by name, or null. Nothing is sent when it is refused. */
    private String refusal(SessionEvents.AssistantSendRequested e) {
        if (busy()) return "a turn is in progress: wait for it to finish, or Cancel it";
        if (e.draft() <= 0) return "there is no question to send";
        if (e.route() == null || !e.route().hasKey()) {
            return "no provider is configured, so nothing was sent: Configure provider, Copy prompt, or Connect a CLI "
                    + "assistant";
        }
        if (frozen) {
            return "the workspace changed since this conversation's last turn, so its history describes something that is "
                    + "no longer open: start a New chat to ask about the current workspace";
        }
        return null;
    }

    @OnEventHandler
    public boolean onCancelRequested(SessionEvents.AssistantCancelRequested e) {
        if (!busy()) {
            auditLog.info("noOp", "AssistantCancelRequested").info("reason", "no turn is in progress");
            return false;
        }
        end("CANCELLED", "cancelled" + (e.reason() == null || e.reason().isBlank() ? "" : " (" + e.reason() + ")")
                + completedActions());
        return changed();
    }

    @OnEventHandler
    public boolean onNewChatRequested(SessionEvents.AssistantNewChatRequested e) {
        fresh("new chat" + (e.reason() == null || e.reason().isBlank() ? "" : ": " + e.reason()));
        return changed();
    }

    @OnEventHandler
    public boolean onHandoffRequested(SessionEvents.AssistantHandoffRequested e) {
        fresh("a fresh conversation about the evidence on screen; the demonstration's dialogue is not part of it");
        return changed();
    }

    @OnEventHandler
    public boolean onHostRequested(SessionEvents.AssistantHostRequested e) {
        if (e.docked() == docked) return false;
        docked = e.docked();
        auditLog.info("assistantHost", docked ? "docked" : "window").info("origin", String.valueOf(e.origin()));
        effects.request(new SessionEffects.ShowAssistantHostEffect(0L, docked));
        return changed();
    }

    // ---- results --------------------------------------------------------------------------------------------------

    @OnEventHandler(propagate = false)
    public boolean onEffectStarted(SessionEvents.AssistantEffectStarted e) {
        auditLog.info("assistantEffect", e.what()).info("ticket", e.ticket());
        // The verb is presentation metadata. Ownership is only the origin on the actual request/fact.
        if (e.ticket() == ticket && "RUNNING_ACTION".equals(phase) && e.what() != null && e.what().startsWith("action:")) {
            runningVerb = e.what().substring("action:".length());
        }
        return false;
    }

    @OnEventHandler
    public boolean onHostShown(SessionEvents.AssistantHostShown e) {
        if (e.ok()) return false;
        docked = e.docked();                       // what the frame could actually do
        reason = e.reason();
        return changed();
    }

    @OnEventHandler
    public boolean onContextPrepared(SessionEvents.AssistantContextPrepared e) {
        if (stale(e.ticket(), "PREPARING", "AssistantContextPrepared")) return false;
        if (manifestInFlight) manifestSent = true;
        manifestInFlight = false;
        entries.add(new AssistantState.Entry(e.prompt(), AssistantState.PROMPT, turn, "", "", "", 0));
        history.add(new SessionEffects.HistoryMessage("user", "TEXT", List.of(e.prompt())));
        nextRound();
        return changed();
    }

    @OnEventHandler
    public boolean onContextFailed(SessionEvents.AssistantContextFailed e) {
        if (stale(e.ticket(), "PREPARING", "AssistantContextFailed")) return false;
        end("FAILED", "the question could not be prepared: " + e.reason());
        return changed();
    }

    @OnEventHandler
    public boolean onCompletionReceived(SessionEvents.AssistantCompletionReceived e) {
        if (stale(e.ticket(), "REQUESTING", "AssistantCompletionReceived")) return false;
        if (e.round() != round) {
            auditLog.info("staleFact", "AssistantCompletionReceived").info("round", e.round()).info("current", round);
            return false;
        }
        entries.add(new AssistantState.Entry(e.reply(), AssistantState.ANSWER, turn, "ACCEPTED", "", "", 0));
        history.add(new SessionEffects.HistoryMessage("assistant", "TEXT", List.of(e.reply())));
        pending = new ArrayDeque<>(e.actions());
        roundResults = new ArrayList<>();
        actionsThisReply = 0;
        auditLog.info("assistantReply", e.reply()).info("round", round).info("actions", e.actions().size());
        if (pending.isEmpty()) {
            end("COMPLETE", "");
            return changed();
        }
        if (!route.actions()) {
            notRun("actions are switched off in Settings ▸ Assistant");
            end("COMPLETE", "the reply asked for " + e.actions().size() + " action(s); none ran, because actions are off");
            return changed();
        }
        runNext();
        return changed();
    }

    @OnEventHandler
    public boolean onCompletionFailed(SessionEvents.AssistantCompletionFailed e) {
        if (stale(e.ticket(), "REQUESTING", "AssistantCompletionFailed")) return false;
        end("FAILED", "the provider request failed: " + e.reason() + completedActions());
        return changed();
    }

    @OnEventHandler
    public boolean onActionFinished(SessionEvents.AssistantActionFinished e) {
        if (stale(e.ticket(), "RUNNING_ACTION", "AssistantActionFinished")) return false;
        if (e.action() != runningAction) {
            auditLog.info("staleFact", "AssistantActionFinished").info("action", e.action()).info("current", runningAction);
            return false;
        }
        if (ownedLogOp != 0) {
            if (!e.ok()) {
                clearOwnership();
                end("FAILED", "the assistant's open action failed after starting a log load; remaining work was stopped");
                return changed();
            }
            // The action's immediate result says only "loading". Do not run its next action against the old log.
            heldOpenResult = e.result();
            heldOpenVerb = e.verb();
            if (!ownedLogApplied) {
                replaceEntry(e.action(), new AssistantState.Entry(e.action(), AssistantState.ACTION, turn,
                        "WAITING", e.verb(), "waiting for this action's log to finish opening", e.result()));
                return changed();
            }
            clearOwnership();
        }
        ownedProjectOp = 0;
        finishAction(e.verb(), e.ok(), e.result());
        return changed();
    }

    private void finishAction(String verb, boolean ok, long result) {
        replaceEntry(runningAction, new AssistantState.Entry(runningAction, AssistantState.ACTION, turn,
                ok ? "OK" : "REFUSED", verb, "", result));
        roundResults.add(result);
        auditLog.info("assistantAction", verb).info("ok", ok);
        runningAction = 0;
        runningVerb = null;
        runNext();
    }

    // ---- the workspace ----------------------------------------------------------------------------------------------

    /** The open operation itself names its owner. A concurrent person's request supersedes before its reader lands. */
    @OnEventHandler
    public boolean onOpenLogRequested(SessionEvents.OpenLogRequested e) {
        if (owns(e.assistantOrigin())) {
            ownedLogOp = e.opId();
            ownedLogApplied = false;
            heldOpenResult = 0;
            return false;
        }
        return competingRequest("a different log was requested");
    }

    @OnEventHandler
    public boolean onOpenProjectRequested(SessionEvents.OpenProjectRequested e) {
        if (owns(e.assistantOrigin())) {
            ownedProjectOp = e.opId();
            return false;
        }
        return competingRequest("a different project was requested");
    }

    @OnEventHandler
    public boolean onCloseRequested(SessionEvents.CloseRequested e) {
        return owns(e.assistantOrigin()) ? false : competingRequest("the workspace was closed by another request");
    }

    private boolean competingRequest(String why) {
        if (!busy()) return false;
        end("SUPERSEDED", why + " during this turn; its remaining work was stopped" + completedActions());
        frozen = true;
        return changed();
    }

    /** The gate and openLog are upstream, so a matching accepted result has the new generation by this handler. */
    @OnEventHandler
    public boolean onLogOpened(SessionEvents.LogOpened e) {
        if (!gate.accepted()) return false;
        if (e.opId() == ownedLogOp && "RUNNING_ACTION".equals(phase)) {
            captureBasis();
            return changed();
        }
        return onBasisMoved();
    }

    /** The frame reports this only after the store, source graph and reset view are all installed. */
    @OnEventHandler
    public boolean onAssistantOpenApplied(SessionEvents.AssistantOpenApplied e) {
        if (e.opId() != ownedLogOp || !owns(e.assistantOrigin())) return false;
        ownedLogApplied = true;
        captureBasis();
        if (heldOpenResult != 0) {
            long result = heldOpenResult;
            String verb = heldOpenVerb;
            clearOwnership();
            finishAction(verb, true, result);
        }
        return changed();
    }

    @OnEventHandler
    public boolean onAssistantOpenApplyFailed(SessionEvents.AssistantOpenApplyFailed e) {
        return failOwnedOpen(e.opId(), "the assistant's log could not be applied");
    }

    @OnEventHandler
    public boolean onLogOpenFailed(SessionEvents.LogOpenFailed e) {
        return failOwnedOpen(e.opId(), "the assistant's log could not be opened");
    }

    @OnEventHandler
    public boolean onEffectFailed(SessionEvents.EffectFailed e) {
        if (e.opId() == ownedProjectOp && ownedProjectOp != 0) {
            clearOwnership();
            end("FAILED", "the assistant's project open failed; remaining work was stopped");
            return changed();
        }
        return failOwnedOpen(e.opId(), "the assistant's log open failed; remaining work was stopped");
    }

    private boolean failOwnedOpen(long opId, String why) {
        if (opId != ownedLogOp || ownedLogOp == 0 || !busy()) return false;
        clearOwnership();
        end("FAILED", why + completedActions());
        return changed();
    }

    /** A project transition can close its old log and graph before applying the new profile. All carry its opId. */
    @OnEventHandler
    public boolean onLogClosed(SessionEvents.LogClosed e) {
        return captureOwnedProjectStep(e.opId()) || onBasisMoved();
    }

    @OnEventHandler
    public boolean onGraphClosed(SessionEvents.GraphClosed e) {
        return captureOwnedProjectStep(e.opId()) || onBasisMoved();
    }

    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.ProfileApplied e) {
        if (captureOwnedProjectStep(e.opId())) {
            ownedProjectOp = 0;
            return true;
        }
        return onBasisMoved();
    }

    @OnEventHandler
    public boolean onSettingsRestored(SessionEvents.SettingsRestored e) {
        if (captureOwnedProjectStep(e.opId())) {
            ownedProjectOp = 0;
            return true;
        }
        return onBasisMoved();
    }

    @OnEventHandler
    public boolean onProfileLoaded(SessionEvents.ProfileLoaded e) {
        if (e.opId() != ownedProjectOp || e.ok()) return false;
        ownedProjectOp = 0;
        return false;
    }

    private boolean captureOwnedProjectStep(long opId) {
        if (opId != ownedProjectOp || ownedProjectOp == 0 || !gate.accepted() || !"RUNNING_ACTION".equals(phase))
            return false;
        captureBasis();
        return changed();
    }

    /** Synchronous view and graph facts carry the identity of the action that changed them. */
    @OnEventHandler
    public boolean onGraphOpened(SessionEvents.GraphOpened e) {
        if (owns(e.assistantOrigin())) {
            captureBasis();
            return changed();
        }
        return onBasisMoved();
    }

    @OnEventHandler
    public boolean onGraphCleared(SessionEvents.GraphCleared e) {
        if (owns(e.assistantOrigin())) {
            captureBasis();
            return changed();
        }
        return onBasisMoved();
    }

    @OnEventHandler
    public boolean onLogCleared(SessionEvents.LogCleared e) {
        if (owns(e.assistantOrigin())) {
            captureBasis();
            return changed();
        }
        return onBasisMoved();
    }

    @OnEventHandler
    public boolean onViewFilterChanged(SessionEvents.ViewFilterChanged e) {
        if (Objects.equals(viewKey, e.filterKey())) return false;
        viewKey = e.filterKey();
        if (owns(e.assistantOrigin())) {
            captureBasis();
            return changed();
        }
        // A completed turn has no pending reply/action to protect. Its next Send prepares fresh context
        // under this filter, while a project/log/graph change still freezes an idle conversation.
        if (!busy()) {
            if (frozen) return false; // a prior workspace change stays frozen until New chat
            captureBasis();
            return changed();
        }
        return onBasisMoved();
    }

    /** Any basis change without a matching causal fact belongs to someone else and ends the old authority. */
    private boolean onBasisMoved() {
        String now = basisKeyNow();
        if (Objects.equals(now, basisKey)) return false;
        if (busy()) {
            end("SUPERSEDED", "the workspace or investigation view changed during this turn (" + basisLabelNow() + "), so its remaining work was "
                    + "stopped; ask again" + completedActions());
            frozen = true;
            return changed();
        }
        if (entries.isEmpty()) {
            captureBasis();
            return changed();
        }
        if (!frozen) {
            frozen = true;
            reason = "the workspace changed (" + basisLabelNow() + "): this conversation is kept for reading; start a New "
                    + "chat to ask about what is open now";
            auditLog.info("assistantFrozen", basisLabelNow());
            return changed();
        }
        return false;
    }

    // ---- decisions --------------------------------------------------------------------------------------------------

    private void nextRound() {
        round++;
        phase = "REQUESTING";
        auditLog.info("assistantRound", round).info("ticket", ticket);
        effects.request(new SessionEffects.RequestAssistantCompletionEffect(0L, ticket, round, history, route));
    }

    /** Run the next action of this reply, or, when there is none, decide the next round or the end of the turn. */
    private void runNext() {
        int turnCap = Math.max(1, route.maxActionsPerTurn());   // its own setting: the rounds and per-reply caps cannot imply it
        if (!pending.isEmpty() && actionsThisReply >= Math.max(1, route.maxActionsPerReply())) {
            notRun("the per-reply action cap (" + route.maxActionsPerReply() + ") was reached");
        }
        if (!pending.isEmpty() && actionsRun >= turnCap) {
            notRun("the per-turn action cap (" + turnCap + ") was reached");
            end("LIMIT_REACHED", "reached this turn's action budget (" + turnCap + " actions); the remaining action(s) "
                    + "did not run and no further round was requested");
            return;
        }
        if (!pending.isEmpty()) {
            long action = pending.poll();
            runningAction = action;
            runningVerb = null;
            actionsRun++;
            actionsThisReply++;
            phase = "RUNNING_ACTION";
            entries.add(new AssistantState.Entry(action, AssistantState.ACTION, turn, "REQUESTED", "", "", 0));
            effects.request(new SessionEffects.RunAssistantActionEffect(0L, ticket, action));
            return;
        }
        // every action of this reply has answered: feed EVERY result back when another round is allowed
        if (round < Math.max(1, route.maxRounds())) {
            history.add(new SessionEffects.HistoryMessage("user", "RESULTS", roundResults));
            roundResults = new ArrayList<>();
            nextRound();
            return;
        }
        end("LIMIT_REACHED", "reached the action-round limit (" + route.maxRounds() + "); the last round's results were "
                + "not sent back to the model");
    }

    /** The actions still pending in this reply will not run; say why, visibly. */
    private void notRun(String why) {
        while (!pending.isEmpty()) {
            long action = pending.poll();
            entries.add(new AssistantState.Entry(action, AssistantState.ACTION, turn, "NOT_RUN", "", why, 0));
        }
    }

    /**
     * End the turn: move the ticket first, so nothing already on its way can count, ask the transport to stop, and leave
     * a visible terminal record. A provider message that was never answered is dropped from the history, so the next
     * turn still alternates user and assistant.
     */
    private void end(String terminal, String note) {
        long was = ticket;
        boolean active = busy();
        if (active) notRun("the turn ended (" + terminal.toLowerCase(java.util.Locale.ROOT) + ")");
        if ("RUNNING_ACTION".equals(phase) && runningAction != 0) {
            replaceEntry(runningAction, new AssistantState.Entry(runningAction, AssistantState.ACTION, turn, "NOT_RUN",
                    runningVerb, "the turn ended while it was running: its result is not accepted, and whatever it had already "
                    + "changed is not undone", 0));
        }
        clearOwnership();
        ticket++;
        phase = terminal;
        runningAction = 0;
        runningVerb = null;
        manifestInFlight = false;
        pending.clear();
        while (!history.isEmpty() && "user".equals(history.get(history.size() - 1).role())) {
            history.remove(history.size() - 1);
        }
        if (history.isEmpty()) manifestSent = false; // the discarded first prompt held the only manifest
        if (!note.isBlank()) {
            reason = note;
            entries.add(new AssistantState.Entry(0, AssistantState.NOTE, turn, terminal, "", note, 0));
        } else {
            reason = "";
        }
        auditLog.info("assistantTurnEnded", terminal).info("ticket", was);
        if (active && !"COMPLETE".equals(terminal)) effects.request(new SessionEffects.CancelAssistantTransportEffect(0L, was));
    }

    /** New chat or a handoff: stop anything pending, then start an empty conversation about what is open now. */
    private void fresh(String why) {
        long was = ticket;
        boolean active = busy();
        ticket++;
        conversation++;
        turn = 0;
        phase = "IDLE";
        round = 0;
        actionsRun = 0;
        clearOwnership();
        runningAction = 0;
        runningVerb = null;
        entries = new ArrayList<>();
        history = new ArrayList<>();
        pending = new ArrayDeque<>();
        roundResults = new ArrayList<>();
        manifestSent = false;
        manifestInFlight = false;
        frozen = false;
        reason = why;
        captureBasis();
        auditLog.info("assistantConversation", conversation).info("why", why);
        if (active) effects.request(new SessionEffects.CancelAssistantTransportEffect(0L, was));
    }

    private String completedActions() {
        long ok = entries.stream().filter(x -> AssistantState.ACTION.equals(x.kind()) && x.turn() == turn
                && ("OK".equals(x.status()) || "REFUSED".equals(x.status()))).count();
        return ok == 0 ? "" : "; " + ok + " action(s) of this turn had already completed and are not undone";
    }

    private void replaceEntry(long id, AssistantState.Entry with) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            AssistantState.Entry x = entries.get(i);
            if (x.id() == id && AssistantState.ACTION.equals(x.kind())) {
                entries.set(i, with);
                return;
            }
        }
        entries.add(with);
    }

    private boolean stale(long named, String expectedPhase, String what) {
        if (named != ticket || !expectedPhase.equals(phase)) {
            auditLog.info("staleFact", what).info("ticket", named).info("current", ticket).info("phase", phase);
            return true;
        }
        return false;
    }

    private boolean busy() {
        return "PREPARING".equals(phase) || "REQUESTING".equals(phase) || "RUNNING_ACTION".equals(phase);
    }

    private boolean owns(SessionEvents.AssistantActionOrigin origin) {
        return origin != null && "RUNNING_ACTION".equals(phase)
                && origin.ticket() == ticket && origin.action() == runningAction;
    }

    private void clearOwnership() {
        ownedLogOp = 0;
        ownedLogApplied = false;
        heldOpenResult = 0;
        heldOpenVerb = null;
        ownedProjectOp = 0;
    }

    private void captureBasis() {
        basisKey = basisKeyNow();
        basisLabel = basisLabelNow();
    }

    private String basisKeyNow() {
        return (activeProject.isActive() ? activeProject.profilePath() : "-") + "|"
                + (openLog.isOpen() ? openLog.generation() : -1) + "|" + (openGraph.isOpen() ? openGraph.revision() : -1)
                + "|" + (viewKey == null ? "no-filter" : "filter:" + viewKey.length() + ":" + viewKey);
    }

    private String basisLabelNow() {
        String project = activeProject.isActive() ? "project " + activeProject.name() : "no project";
        String log = openLog.isOpen() ? "log " + fileName(openLog.logPath()) + " (#" + openLog.generation() + ")" : "no log";
        String graph = openGraph.isOpen() ? "graph " + fileName(openGraph.graphPath()) : "no graph";
        return project + " · " + log + " · " + graph + (viewKey == null ? " · all records" : " · filtered view");
    }

    private static String fileName(String path) {
        if (path == null) return "?";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    /** The published state — immutable, for the snapshot, rebuilt only after a change. */
    public AssistantState state() {
        if (dirty) {
            published = new AssistantState(conversation, ticket, phase, round, actionsRun, runningVerb, entries, docked,
                    frozen, basisLabel, reason, answer);
            dirty = false;
        }
        return published;
    }
}
