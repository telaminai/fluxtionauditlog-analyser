package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M74 (spec-claude-code-provider.md §4): the local agent's whole lifecycle — consent and folder confirmation, the spawn,
 * the start handshake and the posture assertion, the binding verdict, one turn at a time, Stop with its fallback, the
 * session budget, New chat and the one-mode rule. A SIBLING of {@code assistantLoop}, not a mode of it: that node needs
 * a key and treats any open it did not issue as a competing request.
 *
 * <p><b>Rule 9.</b> The tab reports Start, Send, Stop and New chat as facts and renders the published
 * {@link LocalAgentState}; {@code LocalAgentAdapter} performs the effects asked for here and reports what the CLI said,
 * as facts carrying the generation and the ticket. Every verdict — logged in, bound, the posture, the end of a turn, a
 * deadline passing — is decided HERE, from those facts.
 *
 * <p><b>Generations and tickets.</b> Every spawn is a new generation with a new nonce; every turn a new ticket. A fact
 * naming an old generation (a late line from a killed process) or another ticket (a stale result) is refused and
 * recorded as such. Nothing is ever queued: Send while a ticket is open is refused (OD-74.7).
 *
 * <p><b>What it never holds.</b> The words of a turn, account data, a path or a credential. The session id and nonce
 * are kept to compare and to ask for, never published in the snapshot and never written to the audit record.
 */
public class LocalAgentSession implements EventLogSource {

    /** The CLI's own connect timeout is 30 s [P0b-2]; this is the analyser's bound on the whole start. */
    public static final long START_LIMIT_MS = 35_000;
    /** The binding poll cadence (§4.2 step 4). */
    public static final long POLL_MS = 250;

    public static final String BUSY_TEXT = "Claude is still answering — Stop or wait.";
    public static final String NOT_LOGGED_IN_TEXT = "Claude Code is not logged in — run `claude` once in a terminal to log in.";
    public static final String OLD_BRIDGE_TEXT = "the installed analyser bridge is older than this analyser — update it";
    /** PB-3: with a model that does not support it, the CLI starts in {@code default} instead of {@code auto}, silently. */
    public static final String AUTO_UNAVAILABLE_TEXT = "Claude Code started in default mode instead of auto: auto mode is "
            + "not available for the configured Claude Code model — choose a model that supports it (sonnet or opus) or "
            + "another mode in Settings ▸ Assistant; it was stopped";
    /**
     * PB-1's {@code asyncAgent} denial: a request the mode would ask about, denied because nobody could be asked. From P3
     * every such request reaches the analyser over stdio, so this should not occur; if a CLI ever reports one, it says so.
     */
    public static final String APPROVAL_UNAVAILABLE_TEXT = "an action needed your approval and Claude Code did not ask "
            + "the analyser for it, so it was denied — allow it with your own Claude Code permission rules, or choose "
            + "another mode in Settings ▸ Assistant";

    /**
     * Pushed into, not read from upstream (review M2): this node decides FIRST whether the person may leave Claude Code
     * now (not while a turn is open), and tells the mode node to keep the mode when it may not. So the edge runs
     * localAgentSession → assistantMode → assistantLoop.
     */
    @PushReference
    private final AssistantModeChoice mode;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private long conversation = 1;
    private long generation;
    private long sessionRef;
    private String sessionId;
    private boolean resumable;
    private String postureRef;
    private String nonce;
    private String phase = "OFF";
    private String verdict = "";
    private String reason = "";
    private boolean bound;
    private boolean postureChecked;
    private boolean endAfterTurn;
    private boolean killRequested;
    private long ticket;
    private long openTicket;
    private boolean interruptAcked;
    private long pendingDraft;
    private long pendingRequest;
    /** Final review #12: the bound log generation of the held draft (0: none). */
    private long pendingLogGeneration;
    /** Review M8: the Start request whose folder confirmation is open, and the posture it was asked for. */
    private long confirmRequest;
    private String confirmPosture;
    private long headerRef;
    private long headerRevision;
    private long detailRef;
    private long stderrRef;
    private long bindingElapsedMs;
    private long budgetMicros;
    private boolean costUnderReported;
    private boolean newChatOffered;
    private Map<Long, Long> costBySession = new LinkedHashMap<>();
    /** The posture the current process was started in: what the handshake and every init are checked against. */
    private LocalAgentPosture posture = LocalAgentPosture.ANALYSER_ASSISTANT;
    /** Full posture: the person's servers' {@code name:status} at the last init, for the diagnostics line. */
    private List<String> personServerStates = new ArrayList<>();
    private int personServers;
    /** How the session is paid for, from {@code initialize.account} at the parse boundary: decides whether a cost is shown. */
    private SessionEvents.Billing billing = SessionEvents.Billing.UNKNOWN;
    /** One coalesced note per hook event and turn (review finding 12): its entry index and counts (started, ok, failed). */
    private Map<String, long[]> hookNotes = new LinkedHashMap<>();
    private List<LocalAgentState.Entry> entries = new ArrayList<>();
    private LocalAgentState.Answer answer = LocalAgentState.Answer.NONE;
    /**
     * M74 P2 (§8.2): the open log, a JavaBean parent so the generator wires it without changing the constructor — what a
     * bound draft is compared with at Send. Null only in a processor generated before P2: no draft is then bound.
     */
    private OpenLog openLog;
    /** P2: each composed draft's binding (the log identity digest and generation it names), by draft reference. */
    private Map<Long, String[]> bindings = new LinkedHashMap<>();
    /** P2 (§5.2 item 6): Start armed with these words (a bundle key), or null; nothing starts because it is armed. */
    private String armedWhy;
    /** P2 (owner request 2026-10-17): the posture the last consent fact named, for the full-posture cue; null when off. */
    private LocalAgentPosture consentPosture;
    /** P2b (§5.6 step 1): the set-up directory the person chose, held for this run; null for none. */
    private long setupDirRef;
    private String setupDirShown;
    /** P2b (§5.2): the diagnostics counts the adapter last reported for this generation. */
    private int unknownEvents;
    private int unclassifiedCommands;
    private int droppedLines;
    /** P2b (§5.9 per turn): each answered turn's cost and time, by ticket (the last 500). */
    private Map<Long, LocalAgentState.Turn> turns = new LinkedHashMap<>();
    /** P2b (§4.5): entries trimmed from this conversation's start. */
    private int trimmed;
    /** The node keeps at most this many entries per conversation (§4.5). */
    public static final int MAX_ENTRIES = 500;
    /**
     * Owner, 2026-10-18 (the restart offer): what the running process was started with — compared with every consent the
     * frame reports, so a change is classified as one the person may defer (offered) or one that ends the session at once.
     */
    private String processRef;
    private long processBudget;
    private String processModel;
    private String processFolder;
    /** The offer in force: its id, its state ({@link LocalAgentState.Restart}), what changed, whether Yes begins a new chat. */
    private long restartOffer;
    private String restartState = "";
    private String restartChanged = "";
    private boolean restartNewChat;
    /** AFTER_TURN: the Start request the restart answers, and the consent it was answered under. */
    private long restartRequest;
    private SessionEvents.LocalAgentConsent restartConsent;
    /** The person chose to keep the session in its folder (the project-switch offer's Keep): a folder change is theirs. */
    private boolean folderKept;
    /** M78 D1 (§5.1): a permission dialog is open (localAgentPermission's state), for the header's "needs you". */
    private boolean permissionWaiting;
    /** M78 D1 (§5.2): the process generation whose first READY appended the PLAN note (0: none yet). */
    private long planNoted;
    private LocalAgentState published = LocalAgentState.IDLE;
    private boolean dirty = true;

    public LocalAgentSession(AssistantModeChoice mode, EffectQueue effects) {
        this.mode = mode;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    public OpenLog getOpenLog() {
        return openLog;
    }

    public void setOpenLog(OpenLog openLog) {
        this.openLog = openLog;
    }

    /**
     * Review B5 (2026-10-17): the action listener's lifecycle node, a JavaBean parent. This node tells it when a session
     * needs the hosted bridge and when it no longer does — in the same dispatch, so the listener is asked for before the
     * process that connects to it — and it decides the listener.
     */
    private ActionListener actionListener;

    public ActionListener getActionListener() {
        return actionListener;
    }

    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    private void needBridge(long gen) {
        if (actionListener != null) actionListener.need(gen, posture != null && posture.isFull());
        else effects.request(new SessionEffects.ServeHostedBridgeEffect(0L, gen));
    }

    private void releaseBridge(long gen) {
        if (actionListener != null) actionListener.released(gen);
        else effects.request(new SessionEffects.ReleaseHostedBridgeEffect(0L, gen));
    }

    // ---- the person's requests ------------------------------------------------------------------------------------

    /** The mode turned in the IN_APP direction while a turn is open (review M2): the reason, by name. */
    public static final String MODE_SWITCH_REFUSED_TEXT = "Claude is still answering — Stop or wait, then switch to the "
            + "in-app assistant.";

    @OnEventHandler
    public boolean onModeChosen(SessionEvents.AssistantModeChosen e) {
        // runs BEFORE the mode node (this node pushes into it): the mode in force is still the old one here
        boolean leaving = e.mode() != null && e.mode() != AssistantMode.LOCAL_AGENT && mode.localAgent();
        if (!leaving) return changed();
        if (openTicket != 0 || "STOPPING".equals(phase) || pendingDraft != 0) {
            // review M2: a turn in progress is never killed by a mode switch — the node refuses it, by name
            mode.refuseThisChoice();
            verdict = "MODE_SWITCH_REFUSED";
            reason = MODE_SWITCH_REFUSED_TEXT;
            auditLog.info("localAgentRefused", verdict).info("ticket", openTicket);
            return changed();
        }
        armedWhy = null;                                  // P2: a mode switch replaces an armed Start
        if (running()) kill("the Assistant tab was switched to the in-app assistant, so Claude Code was stopped");
        return changed();
    }

    @OnEventHandler
    public boolean onStartRequested(SessionEvents.LocalAgentStartRequested e) {
        observeConsent(e.consent());
        return start(e.request(), e.consent());
    }

    /**
     * Review M8: the answer to this node's own folder question. A stale answer (another request, or none open) is
     * refused; a yes whose folder moved while the dialog was open starts nothing; a yes asks for the trust record and
     * the start, as effects.
     */
    @OnEventHandler
    public boolean onFolderAnswered(SessionEvents.LocalAgentFolderAnswered e) {
        if (confirmRequest == 0 || e.request() != confirmRequest) {
            auditLog.info("staleFact", "LocalAgentFolderAnswered").info("request", e.request()).info("open", confirmRequest);
            return false;
        }
        String asked = confirmPosture;
        confirmRequest = 0;
        confirmPosture = null;
        if (!e.yes()) {
            refuse(e.request(), "FOLDER_NOT_CONFIRMED: the folder was not confirmed for Claude Code, so nothing was started");
            return changed();
        }
        SessionEvents.LocalAgentConsent c = e.consent();
        if (c == null || asked == null || !asked.equals(c.postureRef())) {
            refuse(e.request(), "FOLDER_CHANGED: the folder changed while the confirmation was open, so nothing was "
                    + "started — press Start again");
            return changed();
        }
        if (c.full() && !e.contentUnchanged()) {
            // review H6/M8: the trust is bound to what the dialog showed; a hook, server or instruction file changed
            // while it was open is asked about again, never started on
            refuse(e.request(), "FOLDER_CHANGED: the folder's hooks, MCP servers or instruction files changed while the "
                    + "confirmation was open, so nothing was started — press Start again");
            return changed();
        }
        effects.request(new SessionEffects.TrustLocalAgentFolderEffect(0L, e.request()));
        // the person's yes confirms the folder for the posture it was asked for (the full posture's trust is separate)
        start(e.request(), new SessionEvents.LocalAgentConsent(c.allowed(), c.full() ? c.folderConfirmed() : true,
                c.bundleWorkingCopy(), c.postureRef(), c.budgetMicros(), c.folder(), c.full(), c.fullMode(),
                c.full() || c.fullFolderConfirmed(), c.model(), c.folderRef()));
        return changed();
    }

    private boolean start(long request, SessionEvents.LocalAgentConsent consent) {
        if (running()) {
            auditLog.info("noOp", "LocalAgentStartRequested").info("phase", phase);
            answer(request, true, "");
            return changed();
        }
        String refusal = startRefusal(consent, request);
        if (refusal != null) {
            refuse(request, refusal);
            return changed();
        }
        spawn(consent);
        answer(request, true, "");
        return changed();
    }

    /**
     * Review M6: the hosted bridge's call is admitted HERE, on the session's thread and in its order: a posture kill
     * processed before this fact refuses it; one processed after it finds the call already admitted. The verdict
     * leaves as an effect the adapter completes; nothing on the HTTP thread decides from a snapshot.
     */
    /** Final review #5: the server's refusal of an asked verb under a mode that does not ask. */
    public static final String HIDDEN_IN_THIS_MODE = "`{verb}` is not available to Claude Code in this mode: the "
            + "analyser's verbs that change what is open or write files are hidden under auto and plan";

    @OnEventHandler(propagate = false)
    public boolean onCallAdmission(SessionEvents.LocalAgentCallAdmissionRequested e) {
        long admitted = admittedGeneration();
        String refusal = admitted != 0 && admitted == e.generation() ? null
                : "the Claude Code session is not connected to this analyser yet, or its set-up is not yet checked, so "
                + "the analyser's own tools are refused for it (session " + e.generation() + ")";
        if (refusal == null && LocalAgentPosture.hiddenWhereNotAsked(e.verb()) && (posture == null || !posture.asks())) {
            // final review #5 (defence in depth, OD-74.16): under auto and plan the asked verbs are hidden, and
            // localAgentPermission's policy denies every request for them — so the server admits none of them either,
            // not even the sub-actions that change nothing (which reach this admission without a grant)
            refusal = HIDDEN_IN_THIS_MODE.replace("{verb}", e.verb());
        }
        // scorecard R1: the verb the call names and the turn it was made in, beside the verdict
        auditLog.info("localAgentCall", refusal == null ? "ADMITTED" : "REFUSED").info("generation", e.generation())
                .info("verb", String.valueOf(e.verb())).info("ticket", openTicket);
        effects.request(new SessionEffects.AnswerHostedCallEffect(0L, e.call(), refusal));
        return false;
    }

    /** Scorecard R1: the action server refused a hosted call itself; recorded with its verb, its code and the turn. */
    @OnEventHandler(propagate = false)
    public boolean onHostedRefusedByServer(SessionEvents.HostedCallRefusedByServer e) {
        auditLog.info("localAgentCall", "REFUSED").info("generation", e.generation()).info("verb", String.valueOf(e.verb()))
                .info("ticket", openTicket).info("by", "server").info("reason", String.valueOf(e.reason()));
        return false;
    }

    /** Review H4: whether the hosted bridge has a listener; without one the spawn is refused. */
    @OnEventHandler
    public boolean onBridgeServed(SessionEvents.LocalAgentBridgeServed e) {
        if (stale(e.generation(), "LocalAgentBridgeServed")) return false;
        auditLog.info("localAgentBridgeServed", e.listening()).info("hostedOnly", e.hostedOnly());
        if (e.listening() || !"SPAWNING".equals(phase)) return false;
        refuseSession("BRIDGE_UNAVAILABLE", "the analyser could not start the listener Claude Code's bridge reaches it "
                + "through, so nothing was started", true);
        return changed();
    }

    /**
     * Review finding 4, owner 2026-10-18: Settings applied while a session runs. A change that takes powers away ends the
     * session AT ONCE, mid-turn too, and is never offered ({@link #mandatoryStop}). Any other change is OFFERED: the
     * session keeps running on the settings it was started with until the person answers — <i>Restart now</i> or
     * <i>Later</i> ({@link SessionEvents.LocalAgentRestartAnswered}); a session is never stopped by a change it could
     * have waited for. A change put back before it is answered withdraws the offer. With nothing running the next Start
     * decides with the new consent, and a stale "switched off" is cleared once Claude Code is allowed again.
     */
    @OnEventHandler
    public boolean onConsentChanged(SessionEvents.LocalAgentConsentChanged e) {
        boolean cue = observeConsent(e.consent());
        if (!running()) {
            boolean cleared = false;
            if ("SWITCHED_OFF".equals(verdict) && e.consent() != null && e.consent().allowed()) {
                // probe 2026-10-18: switched back on, the panel still said it was off — Start is possible again
                verdict = "";
                reason = ALLOWED_AGAIN_TEXT;
                cleared = true;
                auditLog.info("localAgentConsent", "ALLOWED_AGAIN");
            }
            return (cue || cleared) && changed();
        }
        if (!consentWhileRunning(e.consent(), true)) return cue && changed();
        return changed();
    }

    /** Said once Claude Code is allowed again after it was switched off (probe, owner 2026-10-18). */
    public static final String ALLOWED_AGAIN_TEXT = "starting Claude Code is allowed again — press Send to ask";

    /**
     * Whether {@code c} changed anything for the running session, and if so what was done: a mandatory change ended it at
     * once; another was offered (or the offer updated); a change put back withdrew the offer. {@code trust} also checks
     * the folder's confirmation (a Settings change, not every Send: full Claude Code may edit its own folder's files).
     */
    private boolean consentWhileRunning(SessionEvents.LocalAgentConsent c, boolean trust) {
        String[] stop = mandatoryStop(c, trust);
        if (stop != null) {
            stopNow(stop[0], stop[1], c);
            return true;
        }
        java.util.List<String> changes = changes(c);
        if (folderKept && changes.size() == 1 && changes.get(0).equals(FOLDER_WORDS)) changes = java.util.List.of();
        if (changes.isEmpty()) {
            if (!restartState.isEmpty()) {
                auditLog.info("localAgentRestart", "WITHDRAWN").info("offer", restartOffer);
                clearRestart();
                effects.request(new SessionEffects.ShowStatusEffect(0L, WITHDRAWN_STATUS));
                return true;
            }
            return false;
        }
        String words = String.join(", ", changes);
        boolean newChat = needsNewChat(c);
        if (words.equals(restartChanged) && newChat == restartNewChat && !restartState.isEmpty()) return false;
        restartChanged = words;
        restartNewChat = newChat;
        if (LocalAgentState.Restart.AFTER_TURN.equals(restartState)) {
            restartConsent = c;                       // Restart was already chosen: it applies what is in force then
            return true;
        }
        restartOffer++;
        restartState = LocalAgentState.Restart.OFFERED;
        auditLog.info("localAgentRestart", "OFFERED").info("offer", restartOffer).info("newChat", newChat)
                .info("ticket", openTicket);
        effects.request(new SessionEffects.OfferLocalAgentRestartEffect(0L, generation, restartOffer, RESTART_STATUS));
        return true;
    }

    /** The main window's line while an offer waits (the panel shows the offer itself). */
    public static final String RESTART_STATUS = "Settings changed — Claude Code keeps running on the previous settings "
            + "until you restart it: answer in the Assistant panel";
    static final String FOLDER_WORDS = "the folder it works in";
    /** The main window's line after Later, and after a change was put back (the offer's earlier line is then stale). */
    public static final String LATER_STATUS = "Claude Code keeps running on the previous settings — the \"settings "
            + "changed — restart to apply\" chip in the assistant header restarts it";
    public static final String WITHDRAWN_STATUS = "The settings are as Claude Code was started with again — nothing to "
            + "restart";

    /**
     * The changes that end a running session at once, never offered (owner, 2026-10-18): consent withdrawn; Full Claude
     * Code switched off; the full mode changed to one that asks more; the folder no longer confirmed for the posture
     * running (only with {@code trust}). Each takes away a power the running process still has, so waiting is never
     * offered. Returns {verdict, words}, or null.
     */
    private String[] mandatoryStop(SessionEvents.LocalAgentConsent c, boolean trust) {
        if (c == null || !c.allowed()) {
            return new String[]{"SWITCHED_OFF", "starting Claude Code was switched off in Settings, so the running session "
                    + "was stopped"};
        }
        if (posture.isFull() && !c.full()) {
            return new String[]{"FULL_OFF", "Full Claude Code was switched off in Settings, so the session running with "
                    + "your own tools and permissions was stopped at once; Start begins Safe analysis"};
        }
        if (posture.isFull() && c.full() && strictness(c.fullMode()) > strictness(posture.fullMode())) {
            return new String[]{"MODE_STRICTER", "the full Claude Code mode was changed to " + c.fullMode().cli()
                    + ", which asks more than the " + posture.fullMode().cli() + " this session runs in, so it was "
                    + "stopped at once"};
        }
        if (trust && c.full() == posture.isFull() && !folderChanged(c) && !c.confirmedForPosture()) {
            return new String[]{"TRUST_CHANGED", posture.isFull()
                    ? "this folder is no longer confirmed for full Claude Code (its hooks, MCP servers or instruction "
                    + "files changed since you confirmed it), so the running session was stopped at once"
                    : "this folder is no longer confirmed for Claude Code, so the running session was stopped at once"};
        }
        return null;
    }

    /** How much a full mode asks: plan asks most (nothing is changed), auto least. */
    private static int strictness(LocalAgentPosture.FullMode m) {
        return switch (m == null ? LocalAgentPosture.FullMode.DEFAULT : m) {
            case PLAN -> 3;
            case DEFAULT -> 2;
            case ACCEPT_EDITS -> 1;
            case AUTO -> 0;
        };
    }

    /** The changes a running session may wait for, in words; empty when the session runs as {@code c} says. */
    private java.util.List<String> changes(SessionEvents.LocalAgentConsent c) {
        java.util.List<String> out = new ArrayList<>();
        if (!posture.isFull() && c.full()) {
            out.add("Safe analysis → Full Claude Code (" + c.fullMode().cli() + ")"
                    + (c.confirmedForPosture() ? "" : " — Yes first asks you to confirm this folder for it"));
        }
        else if (posture.isFull() && c.full() && c.fullMode() != posture.fullMode()) {
            out.add("the full Claude Code mode (" + posture.fullMode().cli() + " → " + c.fullMode().cli() + ")");
        }
        if (folderChanged(c)) out.add(FOLDER_WORDS);
        String model = model(c.model());
        if (c.model() != null && processModel != null && !model.equals(processModel)) {
            out.add("the model (" + safe(processModel) + " → " + safe(model) + ")");
        }
        if (c.budgetMicros() != processBudget) out.add("the spend limit");
        if (out.isEmpty() && processRef != null && !processRef.equals(c.postureRef())) out.add("how Claude Code works");
        return out;
    }

    /** The folder differs from the process's: by its digest when observed, else a posture ref changed with the kind kept. */
    private boolean folderChanged(SessionEvents.LocalAgentConsent c) {
        if (c.folderRef() != null && processFolder != null) return !c.folderRef().equals(processFolder);
        boolean sameKind = c.full() == posture.isFull() && (!c.full() || c.fullMode() == posture.fullMode());
        return sameKind && processRef != null && !processRef.equals(c.postureRef());
    }

    /** Restarting under {@code c} begins a new chat: the conversation has turns and {@code c} is another posture. */
    private boolean needsNewChat(SessionEvents.LocalAgentConsent c) {
        return sessionId != null && resumable && postureRef != null && !postureRef.equals(c.postureRef());
    }

    private static String model(String m) {
        return m == null || m.isBlank() ? "" : m.strip();
    }

    /** A mandatory change: the session ends now, mid-turn too, saying why; any offer goes with it. */
    private void stopNow(String code, String words, SessionEvents.LocalAgentConsent c) {
        auditLog.info("localAgentConsent", code);
        boolean newChat = c != null && c.allowed() && needsNewChat(c);
        verdict = code;
        kill(words);
        newChatOffered = newChat;
    }

    /**
     * Owner, 2026-10-18: the person answered the restart offer. Later keeps the session on its old settings and leaves the
     * chip; Restart now stops it and starts it again under what the machine says NOW — resuming the conversation when
     * the posture is the same, else beginning a new chat — or, while Claude is answering, when that answer ends. A
     * change that became mandatory meanwhile ends the session at once; one put back withdraws the offer.
     */
    @OnEventHandler
    public boolean onRestartAnswered(SessionEvents.LocalAgentRestartAnswered e) {
        if (restartOffer == 0 || e.offer() != restartOffer || !restartState.equals(LocalAgentState.Restart.OFFERED)
                || !running()) {
            auditLog.info("staleFact", "LocalAgentRestartAnswered").info("offer", e.offer()).info("open", restartOffer);
            return false;
        }
        observeConsent(e.consent());
        if (!e.restart()) {
            restartState = LocalAgentState.Restart.DEFERRED;
            auditLog.info("localAgentRestart", "LATER").info("offer", restartOffer);
            effects.request(new SessionEffects.ShowStatusEffect(0L, LATER_STATUS));
            return changed();
        }
        String[] stop = mandatoryStop(e.consent(), true);
        if (stop != null) {
            stopNow(stop[0], stop[1], e.consent());
            return changed();
        }
        if (changes(e.consent()).isEmpty()) {
            auditLog.info("localAgentRestart", "WITHDRAWN").info("offer", restartOffer);
            clearRestart();
            return changed();
        }
        if (openTicket != 0 || "STOPPING".equals(phase) || pendingDraft != 0) {
            restartState = LocalAgentState.Restart.AFTER_TURN;
            restartRequest = e.request();
            restartConsent = e.consent();
            auditLog.info("localAgentRestart", "AFTER_TURN").info("offer", restartOffer).info("ticket", openTicket);
            return changed();
        }
        restartNow(e.request(), e.consent());
        return changed();
    }

    /** The chip: the offer again (Later chosen). */
    @OnEventHandler
    public boolean onRestartOfferRequested(SessionEvents.LocalAgentRestartOfferRequested e) {
        if (!LocalAgentState.Restart.DEFERRED.equals(restartState) || !running()) return false;
        restartState = LocalAgentState.Restart.OFFERED;
        auditLog.info("localAgentRestart", "REOFFERED").info("offer", restartOffer);
        return changed();
    }

    /** Stop this process and start one under {@code c}: resumed when the posture is the same, else a new chat. */
    private void restartNow(long request, SessionEvents.LocalAgentConsent c) {
        boolean newChat = needsNewChat(c);
        auditLog.info("localAgentRestart", "RESTART").info("offer", restartOffer).info("newChat", newChat);
        clearRestart();
        kill(newChat ? RESTARTED_NEW_CHAT_TEXT : RESTARTED_TEXT, RESTARTED);
        if (newChat) {
            beginConversation();
            reason = RESTARTED_NEW_CHAT_TEXT;
            note(RESTARTED, RESTARTED_NEW_CHAT_TEXT);
        }
        start(request, c);
    }

    /**
     * Robot re-walk #6 (2026-10-18): the person ASKED for this restart, so its note is no alarm — no "Stopped:" label
     * (the note's status is {@code RESTARTED}, which carries none) and a sentence short enough for the docked column.
     */
    public static final String RESTARTED_TEXT = "Restarted with the new settings — the conversation continues";
    public static final String RESTARTED_NEW_CHAT_TEXT = "Restarted with the new settings — a new chat begins, because a "
            + "conversation is never resumed under other settings";
    /** The status of the restart's own note: a restart the person asked for, not a stop. */
    public static final String RESTARTED = "RESTARTED";

    private void clearRestart() {
        restartState = "";
        restartChanged = "";
        restartNewChat = false;
        restartRequest = 0;
        restartConsent = null;
    }

    @OnEventHandler
    public boolean onSendRequested(SessionEvents.LocalAgentSendRequested e) {
        observeConsent(e.consent());
        // review finding 4: a Send re-checks the consent the running session was started under. Owner, 2026-10-18: only a
        // change that takes powers away refuses it and ends the session; one the person may defer is offered, and the
        // Send goes to the session running on its old settings. Review of PR #147, finding 3: the FULL posture's trust is
        // bound to the folder's files, so a Send under a full session re-checks it — files changed since the person
        // trusted them (the agent, a git pull, another process) end the session, and Start asks about the folder again
        if (running() && openTicket == 0 && !"STOPPING".equals(phase)) {
            String[] stop = mandatoryStop(e.consent(), posture != null && posture.isFull());
            if (stop != null) {
                stopNow(stop[0], stop[1], e.consent());
                answer(e.request(), false, reason);
                return changed();
            }
            consentWhileRunning(e.consent(), false);
        }
        String refusal = sendRefusal(e);
        if (refusal != null) {
            refuse(e.request(), refusal);
            return changed();
        }
        if (!running()) {
            String startRefusal = startRefusal(e.consent(), e.request());
            if (startRefusal != null) {
                refuse(e.request(), startRefusal);
                return changed();
            }
            spawn(e.consent());
        }
        if (!"READY".equals(phase)) {
            // accepted before the process is ready: the one draft waits for READY, it is never handed to the CLI's queue
            pendingDraft = e.draft();
            pendingRequest = e.request();
            pendingLogGeneration = boundGeneration(e.draftBindings());
            reason = "";
            verdict = "";
            auditLog.info("localAgentDraftHeld", e.draft());
            return changed();
        }
        sendTurn(e.draft(), e.request(), boundGeneration(e.draftBindings()));
        return changed();
    }

    /**
     * Final review #12 (owner decision, 2026-10-17): the log generation a message's composed drafts were checked against
     * at Send — the open log's, since a stale one was refused — or 0 when none names a log. It goes to the MODEL with the
     * turn; the person's draft no longer says it.
     */
    private long boundGeneration(java.util.List<Long> draftBindings) {
        for (long draftBinding : draftBindings) {
            String[] b = bindings.get(draftBinding);
            if (b != null && b[0] != null) return Long.parseLong(b[1]);
        }
        return 0;
    }

    @OnEventHandler
    public boolean onStopRequested(SessionEvents.LocalAgentStopRequested e) {
        if (openTicket == 0) {
            if (pendingDraft != 0) {
                pendingDraft = 0;
                note("STOPPED", "the held question was not sent");
            }
            if (starting()) kill("the start was stopped");
            else auditLog.info("noOp", "LocalAgentStopRequested").info("phase", phase);
            return changed();
        }
        if ("STOPPING".equals(phase)) {
            auditLog.info("noOp", "LocalAgentStopRequested").info("reason", "an interrupt is already in flight");
            return false;
        }
        phase = "STOPPING";
        interruptAcked = false;
        auditLog.info("localAgentInterrupt", openTicket).info("generation", generation);
        effects.request(new SessionEffects.StopLocalAgentEffect(0L, generation, openTicket,
                SessionEffects.StopHow.INTERRUPT));
        return changed();
    }

    @OnEventHandler
    public boolean onNewChatRequested(SessionEvents.LocalAgentNewChatRequested e) {
        if (running()) kill("New chat: the previous conversation's process was stopped");
        beginConversation();
        armedWhy = null;                                 // P2: New chat replaces an armed Start
        verdict = "";
        reason = "a new conversation: it starts with the next Start or Send";
        auditLog.info("localAgentConversation", conversation);
        return changed();
    }

    /** A new conversation: a new id at the next spawn, an empty transcript. */
    private void beginConversation() {
        conversation++;
        sessionId = null;
        resumable = false;
        postureRef = null;
        pendingDraft = 0;
        newChatOffered = false;
        entries = new ArrayList<>();
        trimmed = 0;
        turns = new LinkedHashMap<>();
    }

    // ---- the adapter's facts --------------------------------------------------------------------------------------

    @OnEventHandler(propagate = false)
    public boolean onEffectStarted(SessionEvents.LocalAgentEffectStarted e) {
        auditLog.info("localAgentEffect", e.what()).info("generation", e.generation());
        return false;
    }

    @OnEventHandler
    public boolean onSpawned(SessionEvents.LocalAgentSpawned e) {
        if (stale(e.generation(), "LocalAgentSpawned") || !"SPAWNING".equals(phase)) return false;
        if (e.failure() != SessionEvents.SpawnFailure.NONE) {
            phase = "OFF";
            detailRef = e.detailRef();
            verdict = e.failure().name();
            reason = switch (e.failure()) {
                case NOT_FOUND -> "Claude Code (`claude`) was not found. Install it (https://docs.claude.com/en/docs/claude-code/setup), "
                        + "log in once in a terminal, then press Start; every place searched is listed below";
                case LAUNCHER_UNAVAILABLE -> "the analyser bridge launcher is unavailable: run the analyser from its jar "
                        + "(java -jar) or install it with JBang — an IDE or multi-entry classpath cannot launch the bridge";
                case LOCKED -> "This conversation is open in another analyser window.";
                case PREVIOUS_STILL_STOPPING -> "the previous Claude Code process for this conversation is still "
                        + "stopping; try again in a moment";
                case INVALID_MODEL -> "the Claude Code model setting is not a model name (letters, digits, . _ - [ ]): "
                        + "fix it in Settings ▸ Assistant";
                case ENDPOINT_FAILED -> "the analyser could not write the bridge's endpoint file, so nothing was started";
                case TRUST_CHANGED -> "the folder's settings, hooks, MCP servers or instruction files changed after you "
                        + "confirmed it for full Claude Code, so nothing was started — press Start to see what changed";
                case FOLDER_CHANGED -> "the working folder changed after Start was decided (a project was opened or "
                        + "closed), so nothing was started — press Start again";
                default -> "Claude Code could not be started";
            };
            pendingDraft = 0;
            note(verdict, reason);
            releaseBridge(generation);
            auditLog.info("localAgentSpawnFailed", verdict).info("generation", generation);
            return changed();
        }
        phase = "HANDSHAKE";
        auditLog.info("localAgentSpawned", generation);
        return changed();
    }

    @OnEventHandler
    public boolean onInitialized(SessionEvents.LocalAgentInitialized e) {
        if (stale(e.generation(), "LocalAgentInitialized") || !"HANDSHAKE".equals(phase)) return false;
        headerRef = e.headerRef();
        headerRevision++;
        billing = e.billing();
        if (!e.loggedIn()) {
            refuseSession("NOT_LOGGED_IN", NOT_LOGGED_IN_TEXT, true);
            return changed();
        }
        if (!posture.modeMatches(e.permissionMode())) {
            refuseSession("MODE_MISMATCH", modeMismatchText(e.permissionMode()), true);
            return changed();
        }
        phase = "BINDING";
        auditLog.info("localAgentHandshake", "OK").info("generation", generation);
        effects.request(new SessionEffects.PollLocalAgentStatusEffect(0L, generation, 0));
        return changed();
    }

    @OnEventHandler
    public boolean onStatusObserved(SessionEvents.LocalAgentStatusObserved e) {
        if (stale(e.generation(), "LocalAgentStatusObserved") || !"BINDING".equals(phase)) return false;
        bindingElapsedMs = e.elapsedMs();
        if (posture.isFull()) {
            // the person's own servers may be there, in any state; only the hosted bridge is the analyser's (§4.4 step 3)
            if (!e.servers().contains(LocalAgentPosture.HOSTED_SERVER)) {
                refuseSession("SERVERS_MISMATCH", "Claude Code did not report the analyser's own tools among its MCP "
                        + "servers, so the session was refused", true);
                return changed();
            }
            personServers = LocalAgentPosture.personsServers(e.servers()).size();
        } else if (e.servers().size() != 1 || !LocalAgentPosture.HOSTED_SERVER.equals(e.servers().get(0))) {
            refuseSession("SERVERS_MISMATCH", "Claude Code reported MCP servers other than the analyser's own tools, "
                    + "so the session was refused", true);
            return changed();
        }
        String status = e.hostedStatus() == null ? "" : e.hostedStatus();
        switch (status) {
            case "connected" -> {
                if (e.hostedNonceDigest() == null) {
                    refuseSession("OLD_BRIDGE", OLD_BRIDGE_TEXT, true);
                } else if (!e.hostedNonceDigest().equals(LocalAgentPosture.digest(nonce))) {
                    refuseSession("NONCE_MISMATCH", "the bridge Claude Code connected to is bound to another analyser "
                            + "instance (or an earlier start), so the session was refused", true);
                } else {
                    bound = true;
                    phase = "READY";
                    verdict = "BOUND";
                    reason = "";
                    auditLog.info("localAgentBound", generation);
                    notePlan();
                    if (pendingDraft != 0) {
                        long draft = pendingDraft;
                        pendingDraft = 0;
                        sendTurn(draft, pendingRequest, pendingLogGeneration);
                    }
                }
            }
            case "failed" -> refuseSession("FAILED", "the analyser bridge failed to connect, so the session was refused",
                    true);
            default -> {
                if (e.elapsedMs() >= START_LIMIT_MS) {
                    refuseSession("TIMEOUT", "the analyser bridge did not connect within 35 s, so the session was refused",
                            true);
                } else {
                    effects.request(new SessionEffects.PollLocalAgentStatusEffect(0L, generation, POLL_MS));
                }
            }
        }
        return changed();
    }

    @OnEventHandler
    public boolean onStartDeadline(SessionEvents.LocalAgentStartDeadline e) {
        if (stale(e.generation(), "LocalAgentStartDeadline")) return false;
        if (!starting()) return false;
        refuseSession("TIMEOUT", "Claude Code did not finish starting within 35 s, so it was stopped", true);
        return changed();
    }

    @OnEventHandler
    public boolean onInitObserved(SessionEvents.LocalAgentInitObserved e) {
        if (stale(e.generation(), "LocalAgentInitObserved")) return false;
        if (e.headerRef() != 0) {
            headerRef = e.headerRef();
            headerRevision++;
        }
        LocalAgentPosture.Verdict v = posture.verdict(sessionId, e.permissionMode(), e.tools(), e.servers(),
                e.capabilities(), e.sessionDigest());
        auditLog.info("localAgentPosture", v.name()).info("ticket", e.ticket()).info("first", !postureChecked);
        if (v == LocalAgentPosture.Verdict.OK) {
            boolean first = !postureChecked;
            if (first) {
                postureChecked = true;
                verdict = "OK";
            }
            if (posture.isFull()) personServersObserved(e.servers(), first);
            return changed();
        }
        String why = switch (v) {
            case MODE_MISMATCH -> LocalAgentPosture.BYPASS.equals(e.permissionMode())
                    ? "Claude Code reported bypassPermissions, which the analyser never runs"
                    : "the permission mode changed to " + safe(e.permissionMode());
            case TOOLS_MISMATCH -> posture.isFull()
                    ? "the analyser's own tools are not the expected set (the pre-allowed analyser verbs, and never the "
                    + "tools of your own fluxtion-analyser registration)"
                    : "the tools are not Safe analysis's (reading files in the folder, and the analyser's own tools)";
            case SERVERS_MISMATCH -> posture.isFull() ? "the analyser's own tools are no longer connected"
                    : "the MCP servers changed";
            case CAPABILITY_MISSING -> "this Claude Code lacks a required capability (interrupt_receipt_v1, "
                    + "msg_lifecycle_v1); update it";
            case SESSION_CHANGED -> "the conversation's session id changed under the analyser";
            default -> v.name();
        };
        // TOOLS / MODE: a session already running with powers it was not started with is killed at once, no interrupt
        // first (r6). CAPABILITY_MISSING too: the interrupt is one of the missing capabilities.
        boolean immediately = v == LocalAgentPosture.Verdict.TOOLS_MISMATCH || v == LocalAgentPosture.Verdict.MODE_MISMATCH
                || v == LocalAgentPosture.Verdict.CAPABILITY_MISSING;
        String text = "the session no longer matched how you set Claude Code up — " + why + " — so it was stopped";
        if (immediately || openTicket == 0) {
            refuseSession(v.name(), text, true);
            return changed();
        }
        verdict = v.name();
        reason = text;
        endAfterTurn = true;
        postureChecked = false;               // the hosted bridge is refused from now on
        if (!"STOPPING".equals(phase)) {
            phase = "STOPPING";
            interruptAcked = false;
            effects.request(new SessionEffects.StopLocalAgentEffect(0L, generation, openTicket,
                    SessionEffects.StopHow.INTERRUPT));
        }
        return changed();
    }

    @OnEventHandler
    public boolean onTurnStarted(SessionEvents.LocalAgentTurnStarted e) {
        if (stale(e.generation(), "LocalAgentTurnStarted") || staleTicket(e.ticket(), "LocalAgentTurnStarted")) return false;
        auditLog.info("localAgentTurnStarted", e.ticket());
        // review M2: a conversation is resumable only once the CLI has started one of its turns — before that it has no
        // transcript, and --resume on it would fail
        if (resumable) return false;
        resumable = true;
        return changed();
    }

    @OnEventHandler
    public boolean onTextUpdated(SessionEvents.LocalAgentTextUpdated e) {
        if (stale(e.generation(), "LocalAgentTextUpdated") || staleTicket(e.ticket(), "LocalAgentTextUpdated")) return false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            LocalAgentState.Entry x = entries.get(i);
            if (LocalAgentState.ANSWER.equals(x.kind()) && x.ticket() == e.ticket()) {
                entries.set(i, new LocalAgentState.Entry(e.textRef(), LocalAgentState.ANSWER, e.ticket(), "STREAMING", "",
                        e.revision(), x.logGeneration()));
                return changed();
            }
        }
        // P2 (§5.9): the answer is stamped with the log generation it was written under — its chips go muted when the
        // log moves, decided here rather than remembered by the panel
        entries.add(new LocalAgentState.Entry(e.textRef(), LocalAgentState.ANSWER, e.ticket(), "STREAMING", "",
                e.revision(), openLog != null && openLog.isOpen() ? openLog.generation() : 0));
        return changed();
    }

    @OnEventHandler
    public boolean onNoted(SessionEvents.LocalAgentNoted e) {
        if (stale(e.generation(), "LocalAgentNoted")) return false;
        switch (e.note()) {
            case COMPACTED -> note("COMPACTED", "Claude Code compacted the conversation");
            case RATE_LIMITED -> note("RATE_LIMITED", "Claude Code reports a rate or usage limit"
                    + (e.epochSeconds() > 0 ? "; it resets at " + java.time.Instant.ofEpochSecond(e.epochSeconds()) : ""));
            case AUTH_FAILED -> note("AUTH_FAILED", "Claude Code could not authenticate (an invalid inherited API key, "
                    + "or a login that expired): run `claude` in a terminal to log in again");
            case PERMISSION_DENIED -> note("DENIED", posture.isFull()
                    ? "an action" + named(e.tool()) + " was denied by your Claude Code permission rules or the mode in "
                    + "force (" + posture.fullMode().cli() + ")"
                    : "an action" + named(e.tool()) + " was denied: Safe analysis only reads files in this folder and "
                    + "uses the analyser's own tools");
            case APPROVAL_UNAVAILABLE -> note("DENIED", posture.isFull() ? APPROVAL_UNAVAILABLE_TEXT
                    : "an action" + named(e.tool()) + " was denied: it needed an approval Claude Code did not ask the "
                    + "analyser for");
        }
        return changed();
    }

    /**
     * Review finding 2: the CLI reported a mode mid-session. A mode the posture does not expect (including
     * {@code bypassPermissions}) is a session running with powers it was not started with: killed at once, no interrupt.
     */
    @OnEventHandler
    public boolean onModeObserved(SessionEvents.LocalAgentModeObserved e) {
        if (stale(e.generation(), "LocalAgentModeObserved") || !running()) return false;
        auditLog.info("localAgentMode", posture.modeMatches(e.permissionMode()) ? "OK" : "MODE_MISMATCH");
        if (posture.modeMatches(e.permissionMode())) return false;
        refuseSession("MODE_MISMATCH", "the session no longer matched how you set Claude Code up — it changed its "
                + "permission mode to "
                + safe(e.permissionMode()) + " during the session, where the analyser started it in "
                + posture.permissionMode() + " — so the session was stopped", true);
        return changed();
    }

    /**
     * P1b §8.1: a hook ran. One note per event per generation, updated in place as hooks start and answer (coalesced),
     * naming the event and the outcome — never the hook's output, which never reaches a fact.
     */
    @OnEventHandler
    public boolean onHookObserved(SessionEvents.LocalAgentHookObserved e) {
        if (stale(e.generation(), "LocalAgentHookObserved") || !running()) return false;
        SessionEvents.HookEvent event = e.event() == null ? SessionEvents.HookEvent.OTHER : e.event();
        String key = event.name() + ":" + e.ticket();
        long[] n = hookNotes.get(key);
        if (n == null) {
            n = new long[]{entries.size(), 0, 0, 0};
            hookNotes.put(key, n);
            entries.add(new LocalAgentState.Entry(0, LocalAgentState.NOTE, e.ticket(), "HOOK", "", 0));
        }
        switch (e.outcome() == null ? SessionEvents.HookOutcome.STARTED : e.outcome()) {
            case STARTED -> n[1]++;
            case OK -> n[2]++;
            case FAILED -> n[3]++;
        }
        int at = (int) n[0];
        if (at < entries.size() && "HOOK".equals(entries.get(at).status())) {
            LocalAgentState.Entry was = entries.get(at);
            entries.set(at, new LocalAgentState.Entry(0, LocalAgentState.NOTE, was.ticket(), n[3] > 0 ? "HOOK_FAILED" : "HOOK",
                    hookText(event, n[1], n[2], n[3]), 0));
        } else if (at < entries.size() && "HOOK_FAILED".equals(entries.get(at).status())) {
            LocalAgentState.Entry was = entries.get(at);
            entries.set(at, new LocalAgentState.Entry(0, LocalAgentState.NOTE, was.ticket(), "HOOK_FAILED",
                    hookText(event, n[1], n[2], n[3]), 0));
        }
        auditLog.info("localAgentHook", event.name()).info("outcome", String.valueOf(e.outcome()));
        return changed();
    }

    /** The words of a hook note: the event and the outcome, by count. */
    static String hookText(SessionEvents.HookEvent event, long started, long ok, long failed) {
        String name = event == SessionEvents.HookEvent.OTHER ? "a hook event" : event.name();
        long running = Math.max(0, started - ok - failed);
        StringBuilder sb = new StringBuilder(name).append(": ");
        List<String> parts = new ArrayList<>();
        if (ok > 0) parts.add(ok + " ran");
        if (failed > 0) parts.add(failed + " failed");
        if (running > 0) parts.add(running + " running");
        if (parts.isEmpty()) parts.add("reported");
        sb.append(String.join(", ", parts));
        if (failed > 0) sb.append(" — the hook is yours or the folder's: check it in Claude Code's own hook settings");
        return sb.toString();
    }

    @OnEventHandler
    public boolean onCostObserved(SessionEvents.LocalAgentCostObserved e) {
        if (stale(e.generation(), "LocalAgentCostObserved")) return false;
        // total_cost_usd is already the SESSION's running total, and it survives resume: never sum (§4.6). It is also
        // MONOTONE per session — an interrupted turn can report 0 [P0-22], which must not reset the total (review M1)
        long before = costBySession.getOrDefault(sessionRef, 0L);
        costBySession.merge(sessionRef, Math.max(0, e.totalCostMicros()), Math::max);
        if (e.ticket() > 0) {
            // P2b (§5.9): a turn's cost is the difference of successive totals; its time is the result's duration
            long cost = e.totalCostMicros() >= 0 ? Math.max(0, costBySession.get(sessionRef) - before) : -1;
            turns.put(e.ticket(), new LocalAgentState.Turn(cost, e.durationMs()));
            while (turns.size() > MAX_ENTRIES) turns.remove(turns.keySet().iterator().next());
        }
        return changed();
    }

    @OnEventHandler
    public boolean onTurnEnded(SessionEvents.LocalAgentTurnEnded e) {
        if (stale(e.generation(), "LocalAgentTurnEnded") || staleTicket(e.ticket(), "LocalAgentTurnEnded")) return false;
        endTurn(e.outcome());
        return changed();
    }

    @OnEventHandler
    public boolean onInterruptAcked(SessionEvents.LocalAgentInterruptAcked e) {
        if (stale(e.generation(), "LocalAgentInterruptAcked")) return false;
        if (e.ticket() == openTicket) interruptAcked = true;
        auditLog.info("localAgentInterruptAcked", e.ticket());
        return false;
    }

    @OnEventHandler
    public boolean onStopDeadline(SessionEvents.LocalAgentStopDeadline e) {
        if (stale(e.generation(), "LocalAgentStopDeadline")) return false;
        if (e.ticket() != openTicket || !"STOPPING".equals(phase)) return false;   // the turn ended in time
        if (e.which() == SessionEvents.StopDeadline.ACK && interruptAcked) return false;
        auditLog.info("localAgentStopFallback", e.which().name()).info("ticket", e.ticket());
        costUnderReported = true;
        kill(e.which() == SessionEvents.StopDeadline.ACK
                ? "the interrupt was not acknowledged within 5 s, so Claude Code was stopped; the next Send resumes the "
                + "conversation in a new process"
                : "the turn did not end within 15 s of Stop, so Claude Code was stopped; the next Send resumes the "
                + "conversation in a new process");
        return changed();
    }

    @OnEventHandler
    public boolean onExited(SessionEvents.LocalAgentExited e) {
        if (stale(e.generation(), "LocalAgentExited")) return false;
        stderrRef = e.stderrRef();
        if ("OFF".equals(phase)) {               // we asked for it: the kill already said why
            auditLog.info("localAgentExited", e.code()).info("requested", true);
            return changed();
        }
        if (openTicket != 0) endTurn(SessionEvents.TurnOutcome.PROCESS_EXIT);
        clearRestart();
        releaseBridge(generation);
        phase = "OFF";
        bound = false;
        postureChecked = false;
        pendingDraft = 0;
        verdict = "EXITED";
        reason = "Claude Code exited (code " + e.code() + ")" + (e.stderrRef() != 0 ? "; its last error line is below" : "");
        note("EXITED", reason);
        auditLog.info("localAgentExited", e.code()).info("requested", false);
        return changed();
    }

    // ---- P2b: onboarding's set-up directory, a project switch, diagnostics (§5.6, §5.3 simplified, §5.2) -----------

    /**
     * §5.6 step 1: the person chose the set-up directory. A usable one becomes the working directory of the NEXT spawn
     * (the frame reads it from this node's state); with a process running that is a posture change, answered by New
     * chat. A refused one is said, and the earlier choice stays.
     */
    @OnEventHandler
    public boolean onSetupDirectoryChosen(SessionEvents.LocalAgentSetupDirectoryChosen e) {
        if (e.cancelled()) {
            auditLog.info("localAgentSetupDir", "CANCELLED");
            return false;
        }
        if (e.verdict() != SessionEvents.FolderVerdict.OK) {
            verdict = e.verdict() == SessionEvents.FolderVerdict.EXPOSES_SECRETS ? "FOLDER_EXPOSES_SECRETS" : "NO_FOLDER";
            reason = e.verdict() == SessionEvents.FolderVerdict.EXPOSES_SECRETS
                    ? "that folder reaches credentials (your home folder, a folder containing it, or a settings folder), so "
                    + "it was not chosen as the set-up folder"
                    : "that folder cannot be used as the set-up folder";
            auditLog.info("localAgentSetupDir", verdict);
            return changed();
        }
        setupDirRef = e.dirRef();
        setupDirShown = e.dirShown();
        auditLog.info("localAgentSetupDir", "CHOSEN").info("running", running());
        if (running()) {
            newChatOffered = true;
            verdict = "SETUP_DIRECTORY";
            reason = "the set-up folder takes effect in a new session — press New chat, then Start";
        } else {
            verdict = "";
            reason = "";
        }
        return changed();
    }

    /**
     * The simplified Move (owner, 2026-10-17): the person's project switch was held while Claude Code runs and the person
     * chose. STOP_AND_ARM stops the process and arms Start for the new project — nothing starts until Start is pressed,
     * and the new folder's own confirmation applies then. KEEP keeps it running where it is. CANCEL changes nothing here
     * ({@code sessionBoundary} drops the switch).
     */
    @OnEventHandler
    public boolean onSwitchChosen(SessionEvents.LocalAgentSwitchChosen e) {
        auditLog.info("localAgentSwitch", e.choice().name()).info("running", running());
        switch (e.choice()) {
            case STOP_AND_ARM -> {
                if (running()) kill("Claude Code was stopped for the project switch; press Start to begin a session in "
                        + "the new project");
                armedWhy = "armed.SWITCH";
                return changed();
            }
            case KEEP -> {
                if (running()) {
                    folderKept = true;            // owner, 2026-10-18: the folder change is the person's, never re-offered
                    reason = "this session keeps working in its own folder; its context describes the previous project";
                    note("KEPT", reason);
                    return changed();
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * §5.6 step 7: the analyser's own create in the set-up folder needs no switch offer — the session already works
     * there. It keeps running (Keep), and New chat is offered to start a session in the project.
     */
    @OnEventHandler
    public boolean onProposalItemApplied(SessionEvents.ProposalItemApplied e) {
        if (!"create".equals(e.itemId()) || !"APPLIED".equals(e.outcome())) return false;
        auditLog.info("localAgentProposalCreate", running());
        if (running()) {
            newChatOffered = true;
            reason = "this session started before the project existed — New chat starts a session in the project";
            note("KEPT", reason);
        } else {
            armedWhy = "armed.SETUP";
        }
        return changed();
    }

    /** §5.2: the diagnostics counts, coalesced by the adapter; shown only when one is non-zero. */
    @OnEventHandler
    public boolean onDiagnostics(SessionEvents.LocalAgentDiagnostics e) {
        if (stale(e.generation(), "LocalAgentDiagnostics")) return false;
        if (e.unknownEvents() == unknownEvents && e.unclassifiedCommands() == unclassifiedCommands
                && e.droppedLines() == droppedLines) return false;
        unknownEvents = Math.max(0, e.unknownEvents());
        unclassifiedCommands = Math.max(0, e.unclassifiedCommands());
        droppedLines = Math.max(0, e.droppedLines());
        auditLog.info("localAgentDiagnostics", unknownEvents).info("unclassified", unclassifiedCommands)
                .info("dropped", droppedLines);
        return changed();
    }

    /** The set-up directory, for the frame's working-directory read (it renders this; it decides nothing). */
    public long setupDirectory() {
        return setupDirRef;
    }

    /** Whether a Claude Code process runs now — what a held project switch is decided on. */
    public boolean processRunning() {
        return running();
    }

    // ---- P2: drafts from the Ask catalogue, a journey and the start page (§5.5, §8.2) -----------------------------

    /**
     * A draft was composed (the pop-up's Yes, a journey handoff, a start-page entry). The node keeps its binding — the
     * log identity and generation it names — to compare at Send, asks for it to be PLACED (appended and highlighted,
     * never sent), and ARMS Start when no process runs, only when Start would be accepted (r8 F2): if it would be
     * refused, the draft is still placed and the refusal is shown with its action instead.
     */
    @OnEventHandler
    public boolean onDraftComposed(SessionEvents.LocalAgentDraftComposed e) {
        if (!mode.localAgent()) {
            auditLog.info("noOp", "LocalAgentDraftComposed").info("reason", "the in-app assistant is the mode in force");
            return false;
        }
        observeConsent(e.consent());
        bindings.put(e.draftRef(), new String[]{e.logIdentity(), Long.toString(e.logGeneration())});
        while (bindings.size() > 64) bindings.remove(bindings.keySet().iterator().next());
        String arm = null;
        if (!running()) {
            String refusal = acceptable(e.consent());
            if (refusal == null) {
                arm = "JOURNEY".equals(e.questionId()) ? "armed.JOURNEY"
                        : "S2".equals(e.questionId()) ? "armed.SETUP"            // P2b: onboarding's set-up question
                        : e.questionId() != null && e.questionId().startsWith("Q") ? "armed.ASK" : "armed.START_PAGE";
                armedWhy = arm;
                verdict = "";
                reason = "";
            } else {
                armedWhy = null;
                verdict = verdictOf(refusal);
                reason = refusal.substring(refusal.indexOf(':') + 1).trim();
            }
        }
        auditLog.info("localAgentDraft", e.questionId()).info("bound", e.logIdentity() != null)
                .info("armed", arm != null).info("origin", String.valueOf(e.origin()));
        effects.request(new SessionEffects.PrimeLocalAgentDraftEffect(0L, e.draftRef(), true, arm,
                e.logIdentity() != null ? e.logGeneration() : 0));
        return changed();
    }

    @OnEventHandler(propagate = false)
    public boolean onDraftPlaced(SessionEvents.DraftPlaced e) {
        auditLog.info("draftPlaced", e.draftRef()).info("ok", e.ok());
        return false;
    }

    /**
     * Whether Start would be refused under {@code c} — the same checks as {@link #startRefusal}, with no side effect. An
     * unconfirmed folder is NOT a refusal here: pressing Start asks the folder question, which is Start's own path.
     */
    private String acceptable(SessionEvents.LocalAgentConsent c) {
        if (c == null || !c.allowed()) {
            return "SWITCHED_OFF: starting Claude Code is switched off — Settings ▸ Assistant ▸ Allow the analyser to start "
                    + "Claude Code; your question was placed, and nothing was sent";
        }
        if (c.folder() == SessionEvents.FolderVerdict.UNAVAILABLE) {
            return "NO_FOLDER: the analyser could not make its private scratch folder for Claude Code; your question was "
                    + "placed, and nothing was sent";
        }
        if (c.folder() == SessionEvents.FolderVerdict.EXPOSES_SECRETS) {
            return "FOLDER_EXPOSES_SECRETS: Claude Code is not started in a folder that reaches credentials; open a project "
                    + "in its own folder — your question was placed, and nothing was sent";
        }
        if (c.bundleWorkingCopy()) {
            return "BUNDLE: Start is refused in an evidence-bundle working copy; your question was placed, and nothing was "
                    + "sent";
        }
        // review H4: no REST_OFF — Start asks for a hosted-only listener when the external REST switch is off
        long remaining = c.budgetMicros() - spent();
        if (c.budgetMicros() > 0 && remaining <= 0) {
            return "BUDGET_SPENT: the Claude Code session budget is spent (at least US$" + usd(spent())
                    + "); raise it in Settings ▸ Assistant — your question was placed, and nothing was sent";
        }
        return null;
    }

    /**
     * §5.5 Send: a bound draft whose log has moved since it was composed is refused, with the generations named; an
     * unbound draft (0, or a draft that names no log) is not checked. Deleting the whole draft drops the binding: the
     * panel then reports 0.
     */
    private String staleDraft(java.util.List<Long> draftBindings) {
        // review S2: every draft the composer holds, ANY stale one refuses, and an unknown binding is stale
        boolean open = openLog != null && openLog.isOpen();
        String now = open ? LocalAgentPosture.digest(openLog.logPath()) : null;
        for (long draftBinding : draftBindings) {
            String[] b = bindings.get(draftBinding);
            if (b == null) return UNKNOWN_DRAFT_WORDS;
            if (b[0] == null) continue;
            long was = Long.parseLong(b[1]);
            if (open && b[0].equals(now) && openLog.generation() == was) continue;
            return staleWords(was, open ? Long.toString(openLog.generation()) : "none open");
        }
        return null;
    }

    /**
     * Review S2: a composed draft this assistant does not know — placed for the other assistant, or older than the bound
     * the node keeps — cannot be checked, so it is refused rather than counted as fresh.
     */
    public static final String UNKNOWN_DRAFT_WORDS = "This message holds a drafted question that cannot be checked against "
            + "the open log (it was drafted for the other assistant, or too many questions ago) — press Ask again for "
            + "Claude Code to place it for Claude Code, or delete it";
    /** The in-app assistant's words for the same refusal (it has no Ask again action). */
    public static final String UNKNOWN_DRAFT_WORDS_IN_APP = "This message holds a drafted question that cannot be checked "
            + "against the open log (it was drafted for the other assistant, or too many questions ago) — delete it, or ask "
            + "again from the records";

    /** Record the posture a consent fact names (for the full-posture cue); true when it changed. */
    private boolean observeConsent(SessionEvents.LocalAgentConsent c) {
        LocalAgentPosture now = c == null || !c.allowed() ? null : c.posture();
        if (java.util.Objects.equals(now, consentPosture)) return false;
        consentPosture = now;
        dirty = true;
        return true;
    }

    /** The words of a stale-draft refusal, the same in both assistants (§5.5). */
    public static String staleWords(long was, String now) {
        // review M74 usability #4: "load", the person's word for it — never the analyser's "generation"
        return "This question names records of a log that has been reloaded since it was drafted (load " + was + " → "
                + now + ") — ask again from the records";
    }

    // ---- decisions --------------------------------------------------------------------------------------------------

    /** Why a start cannot happen now, or null. A folder needing confirmation asks for the dialog. */
    private String startRefusal(SessionEvents.LocalAgentConsent c, long request) {
        if (!mode.localAgent()) return "MODE: choose Claude Code on the Assistant tab first";
        if (c == null || !c.allowed()) {
            return "SWITCHED_OFF: starting Claude Code is switched off — Settings ▸ Assistant ▸ Allow the analyser to start "
                    + "Claude Code";
        }
        if (c.folder() == SessionEvents.FolderVerdict.UNAVAILABLE) {
            return "NO_FOLDER: the analyser could not make its private scratch folder for Claude Code, so nothing was "
                    + "started; open a project, or check the temporary folder is writable";
        }
        if (c.folder() == SessionEvents.FolderVerdict.EXPOSES_SECRETS) {
            return "FOLDER_EXPOSES_SECRETS: Claude Code is not started in your home folder, a folder containing it, or the "
                    + "analyser's or Claude Code's own settings folder — reading there would reach credentials. Open a "
                    + "project in its own folder instead";
        }
        if (c.bundleWorkingCopy()) {
            return "BUNDLE: Start is refused in an evidence-bundle working copy — the analyser does not run anything from a "
                    + "received bundle";
        }
        // review H4: no REST_OFF refusal — the external REST switch is the person's choice about EXTERNAL clients;
        // spawn() asks for a hosted-only listener when it is off
        if (!c.confirmedForPosture()) {
            confirmRequest = request;
            confirmPosture = c.postureRef();
            effects.request(new SessionEffects.ConfirmLocalAgentFolderEffect(0L, request, c.full()));
            return c.full()
                    ? "CONFIRM_FOLDER: confirm this folder for full Claude Code first — its hooks, MCP servers and "
                    + "instruction files load at Start, and a folder trusted before is asked again when they change; "
                    + "nothing was started"
                    : "CONFIRM_FOLDER: confirm this folder for Claude Code first; nothing was started";
        }
        // owner, 2026-10-17: the budget is OPTIONAL and off by default — a session on the person's own Claude plan has
        // that plan's limits; only a set limit (budgetMicros > 0) is enforced here
        long remaining = c.budgetMicros() - spent();
        if (c.budgetMicros() > 0 && remaining <= 0) {
            return "BUDGET_SPENT: the Claude Code session budget is spent (at least US$" + usd(spent())
                    + "); raise it in Settings ▸ Assistant";
        }
        if (sessionId != null && resumable && postureRef != null && !postureRef.equals(c.postureRef())) {
            newChatOffered = true;
            return "POSTURE_CHANGED: how Claude Code works (Safe analysis or Full Claude Code, the mode, the folder or the "
                    + "system note) changed since this conversation started; a conversation is never resumed under other "
                    + "settings — start a New chat";
        }
        return null;
    }

    private String sendRefusal(SessionEvents.LocalAgentSendRequested e) {
        if (!mode.localAgent()) return "MODE: the Assistant tab is on the in-app assistant";
        if (openTicket != 0 || "STOPPING".equals(phase)) return "BUSY: " + BUSY_TEXT;
        if (e.draft() <= 0) return "EMPTY: there is nothing to send";
        if (pendingDraft != 0) return "BUSY: " + BUSY_TEXT;
        String stale = staleDraft(e.draftBindings());
        // robot walkthrough #1: a question drafted for the other assistant has its own verdict, and its one action
        if (UNKNOWN_DRAFT_WORDS.equals(stale)) return "UNKNOWN_DRAFT: " + stale;
        if (stale != null) return "STALE_DRAFT: " + stale;
        String named = e.command() == null ? "this command" : "/" + e.command();
        return switch (e.slash() == null ? SessionEvents.SlashClass.TEXT : e.slash()) {
            case NEW_CHAT -> {
                newChatOffered = true;
                yield "SLASH_NEW_CHAT: " + named + " would change this conversation's identity under the analyser, so it "
                        + "was not sent — use New chat";
            }
            case BLOCK -> "SLASH_BLOCKED: " + named + " is not available in the analyser — use Claude Code in a terminal "
                    + "for that.";
            case UNCLASSIFIED -> "SLASH_UNCLASSIFIED: " + named + " is not available in the analyser — use Claude Code in a "
                    + "terminal for that. (Not classified for the analyser.)";
            case SHORTCUT -> "SLASH_SHORTCUT: lines starting with ! or # are not available in the "
                    + "analyser — use Claude Code in a terminal for that.";
            default -> null;
        };
    }

    private void spawn(SessionEvents.LocalAgentConsent c) {
        generation++;
        if (sessionId == null) sessionRef++;
        if (sessionId == null || !resumable) {
            // review M2: a conversation that never had a turn has nothing to resume, so it starts afresh — a new id
            // (never --session-id on an id the CLI may already have recorded) and the posture in force now
            sessionId = java.util.UUID.randomUUID().toString();
            postureRef = c.postureRef();
            resumable = false;
        }
        nonce = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        budgetMicros = c.budgetMicros();
        posture = c.posture();
        // owner, 2026-10-18: what this process starts with, for the restart offer's comparison
        processRef = c.postureRef();
        processBudget = c.budgetMicros();
        processModel = c.model() == null ? null : model(c.model());
        processFolder = c.folderRef();
        folderKept = false;
        clearRestart();
        personServerStates = new ArrayList<>();
        personServers = 0;
        hookNotes = new LinkedHashMap<>();
        unknownEvents = 0;
        unclassifiedCommands = 0;
        droppedLines = 0;
        phase = "SPAWNING";
        armedWhy = null;                                 // P2: pressing an armed Start is a Start; the draft stays placed
        bound = false;
        postureChecked = false;
        endAfterTurn = false;
        killRequested = false;
        interruptAcked = false;
        bindingElapsedMs = 0;
        detailRef = 0;
        stderrRef = 0;
        verdict = "";
        reason = "";
        newChatOffered = false;
        // no limit set: 0, and the command line carries no --max-budget-usd at all
        long remaining = c.budgetMicros() > 0 ? Math.max(1, c.budgetMicros() - spent()) : 0;
        auditLog.info("localAgentSpawn", generation).info("resume", resumable).info("session", sessionRef)
                .info("posture", posture.toString());
        // review H4: the listener first (hosted-only when external REST is off), then the process that connects to it
        needBridge(generation);
        effects.request(new SessionEffects.StartLocalAgentEffect(0L, generation, sessionId, resumable, nonce, remaining,
                c.postureRef(), posture));
    }

    private void sendTurn(long draft, long request, long logGeneration) {
        ticket++;
        openTicket = ticket;
        phase = "BUSY";
        interruptAcked = false;
        verdict = "";
        reason = "";
        entries.add(new LocalAgentState.Entry(draft, LocalAgentState.USER, ticket, "SENT", "", 0));
        answer(request, true, "");
        auditLog.info("localAgentTurn", ticket).info("generation", generation);
        effects.request(new SessionEffects.SendLocalAgentTurnEffect(0L, generation, ticket, draft, logGeneration));
    }

    private void endTurn(SessionEvents.TurnOutcome outcome) {
        long was = openTicket;
        openTicket = 0;
        // a Send refused while this turn ran is answered by the turn ending: the refusal no longer describes anything
        // (P1a live smoke: "Ready — Claude is still answering" stayed on the status line after Stop)
        if ("BUSY".equals(verdict)) {
            verdict = "";
            reason = "";
        }
        if (running() && !"OFF".equals(phase)) phase = "READY";
        for (int i = entries.size() - 1; i >= 0; i--) {
            LocalAgentState.Entry x = entries.get(i);
            if (LocalAgentState.ANSWER.equals(x.kind()) && x.ticket() == was) {
                entries.set(i, new LocalAgentState.Entry(x.id(), x.kind(), x.ticket(), outcome.name(), "", x.revision(),
                        x.logGeneration()));
                break;
            }
        }
        auditLog.info("localAgentTurnEnded", outcome.name()).info("ticket", was);
        switch (outcome) {
            case CANCELLED -> {
                costUnderReported = true;
                note("CANCELLED", "the turn was stopped");
            }
            case ERROR -> note("ERROR", "the turn ended in an error reported by Claude Code");
            case BUDGET -> {
                note("BUDGET", "the session budget was reached (at least US$" + usd(spent()) + ")");
                kill("the Claude Code session budget was reached, so Claude Code was stopped; raise the budget in "
                        + "Settings ▸ Assistant to continue");
            }
            case PROCESS_EXIT -> note("PROCESS_EXIT", "the turn ended because Claude Code's process exited");
            default -> { }
        }
        if (endAfterTurn && running()) {
            String why = reason;
            kill(why.isBlank() ? "the session no longer matched how you set Claude Code up, so it was stopped" : why);
        } else if (LocalAgentState.Restart.AFTER_TURN.equals(restartState) && running()
                && outcome != SessionEvents.TurnOutcome.PROCESS_EXIT) {
            restartNow(restartRequest, restartConsent);   // owner, 2026-10-18: Restart was chosen while Claude answered
        }
    }

    private String modeMismatchText(String reported) {
        if (LocalAgentPosture.BYPASS.equals(reported)) {
            return "Claude Code reported bypassPermissions, which the analyser never runs; it was stopped";
        }
        if (posture.isFull() && posture.fullMode() == LocalAgentPosture.FullMode.AUTO
                && LocalAgentPosture.FullMode.DEFAULT.cli().equals(reported)) {
            return AUTO_UNAVAILABLE_TEXT;
        }
        return "Claude Code reported permission mode " + safe(reported) + " where the analyser started it in "
                + posture.permissionMode() + "; it was stopped";
    }

    /**
     * Full posture: the person's own servers are theirs, so a change among them is a diagnostics line, never a stop
     * (§4.4 step 5). At the first init, each that failed to connect is said once.
     */
    private void personServersObserved(List<String> servers, boolean first) {
        List<String> now = LocalAgentPosture.personsServers(servers);
        personServers = now.size();
        if (first) {
            for (String s : now) {
                if (s.endsWith(":failed")) {
                    note("MCP_SERVER", "your MCP server " + safe(s.substring(0, s.lastIndexOf(':')))
                            + " failed to connect; Claude Code runs without it");
                }
            }
        } else if (!new java.util.TreeSet<>(now).equals(new java.util.TreeSet<>(personServerStates))) {
            note("DIAGNOSTICS", "your own MCP servers changed during the session (" + now.size() + " now reported); "
                    + "the analyser's own tools are unaffected");
        }
        personServerStates = new ArrayList<>(now);
    }

    /** End this generation's process and refuse the session, saying why. */
    private void refuseSession(String code, String text, boolean killNow) {
        verdict = code;
        reason = text;
        pendingDraft = 0;
        note(code, text);
        auditLog.info("localAgentRefused", code).info("generation", generation);
        if (killNow) kill(null);
    }

    /** Kill the process tree of this generation, now: the turn in progress (if any) ends with it. */
    private void kill(String why) {
        kill(why, "STOPPED");
    }

    /** As {@link #kill(String)}, with the status of the note that says why ({@code STOPPED}, or {@link #RESTARTED}). */
    private void kill(String why, String noteStatus) {
        clearRestart();                                  // an offer belongs to the process it was made for
        long was = openTicket;
        if (was != 0) {
            openTicket = 0;
            note("STOPPED", "the turn in progress ended when Claude Code was stopped");
        }
        effects.request(new SessionEffects.StopLocalAgentEffect(0L, generation, was, SessionEffects.StopHow.KILL));
        releaseBridge(generation);
        phase = "OFF";
        bound = false;
        postureChecked = false;
        killRequested = true;
        endAfterTurn = false;
        if (why != null) {
            reason = why;
            note(noteStatus, why);
        }
        auditLog.info("localAgentKill", generation);
    }

    private void refuse(long request, String coded) {
        verdict = verdictOf(coded);
        reason = coded.substring(coded.indexOf(':') + 1).trim();
        answer(request, false, reason);
        auditLog.info("localAgentRefused", verdict);
    }

    private static String verdictOf(String coded) {
        int colon = coded.indexOf(':');
        return colon < 0 ? coded : coded.substring(0, colon);
    }

    private void answer(long request, boolean accepted, String why) {
        if (request != 0) answer = new LocalAgentState.Answer(request, accepted, why);
    }

    /**
     * M78 D1 (spec-quiet-assistant-panel.md §5.2): ONE {@link #PLAN} note on a session's first READY — a session is a
     * process generation, so a restart (a new generation) notes it again, and a turn's return to READY never does. It
     * says what the session is and how it is paid for; it never carries a cost (a cost note is the BUDGET one, at the
     * limit).
     */
    private void notePlan() {
        if (planNoted == generation) return;
        planNoted = generation;
        note(PLAN, planLine(posture, billing));
    }

    /** The PLAN note's status: the panel gives it no label, so its sentence stands alone. */
    public static final String PLAN = "PLAN";
    public static final String PLAN_SUBSCRIPTION = "uses your Claude plan";
    public static final String PLAN_API_KEY = "uses your API key";

    /** "Claude Code · Safe · uses your Claude plan", "Claude Code · Full (default) · your tools · uses your API key". */
    public static String planLine(LocalAgentPosture p, SessionEvents.Billing billing) {
        LocalAgentPosture posture = p == null ? LocalAgentPosture.ANALYSER_ASSISTANT : p;
        String line = "Claude Code · " + (posture.isFull() ? "Full (" + posture.fullMode().cli() + ") · your tools" : "Safe");
        return switch (billing == null ? SessionEvents.Billing.UNKNOWN : billing) {
            case SUBSCRIPTION -> line + " · " + PLAN_SUBSCRIPTION;
            case API_KEY -> line + " · " + PLAN_API_KEY;
            case UNKNOWN -> line;
        };
    }

    private void note(String status, String detail) {
        entries.add(new LocalAgentState.Entry(0, LocalAgentState.NOTE, openTicket, status, detail, 0));
    }

    private boolean stale(long named, String what) {
        if (named != generation) {
            auditLog.info("staleFact", what).info("generation", named).info("current", generation);
            return true;
        }
        return false;
    }

    private boolean staleTicket(long named, String what) {
        if (named == 0 || named != openTicket) {
            auditLog.info("staleFact", what).info("ticket", named).info("open", openTicket);
            return true;
        }
        return false;
    }

    private boolean running() {
        return !"OFF".equals(phase);
    }

    private boolean starting() {
        return "SPAWNING".equals(phase) || "HANDSHAKE".equals(phase) || "BINDING".equals(phase);
    }

    private long spent() {
        long sum = 0;
        for (long v : costBySession.values()) sum += v;
        return sum;
    }

    private static String usd(long micros) {
        return java.math.BigDecimal.valueOf(micros, 6).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** Scorecard R2: the tool a note is about, from the fixed vocabulary, as " (Bash)"; nothing when none is named. */
    private static String named(String tool) {
        return tool == null || tool.isBlank() ? "" : " (" + safe(tool) + ")";
    }

    /** CLI- or person-supplied text in the node's words: control, format and line-separator characters escaped. */
    static String safe(String s) {
        if (s == null) return "(none)";
        StringBuilder sb = new StringBuilder();
        s.codePoints().limit(40).forEach(cp -> {
            int t = Character.getType(cp);
            if (Character.isISOControl(cp) || t == Character.FORMAT || t == Character.LINE_SEPARATOR
                    || t == Character.PARAGRAPH_SEPARATOR) {
                sb.append(String.format("\\u%04X", cp));
            } else {
                sb.appendCodePoint(cp);
            }
        });
        return s.codePointCount(0, s.length()) > 40 ? sb + "…" : sb.toString();
    }

    private boolean changed() {
        trim();
        dirty = true;
        return true;
    }

    /**
     * §4.5 (P2b): at most {@link #MAX_ENTRIES} entries per conversation. The oldest are dropped and ONE note at the start
     * (not counted) says how many; the hook-note indices move with them. (The transcript store bounds its own words.)
     */
    private void trim() {
        boolean noted = !entries.isEmpty() && "TRIMMED".equals(entries.get(0).status());
        int from = noted ? 1 : 0;
        int drop = entries.size() - from - MAX_ENTRIES;      // the note is not counted: 500 entries are kept
        if (drop <= 0) return;
        List<LocalAgentState.Entry> kept = new ArrayList<>(entries.subList(from + drop, entries.size()));
        trimmed += drop;
        kept.add(0, new LocalAgentState.Entry(0, LocalAgentState.NOTE, 0, "TRIMMED",
                trimmed + " earlier entries are not shown", 0));
        int shift = drop + from - 1;          // old index i is now i - shift
        Map<String, long[]> moved = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> h : hookNotes.entrySet()) {
            long at = h.getValue()[0] - shift;
            if (at >= 1) {
                h.getValue()[0] = at;
                moved.put(h.getKey(), h.getValue());
            }
        }
        hookNotes = moved;
        entries = kept;
        auditLog.info("localAgentTrimmed", trimmed);
    }

    // ---- M74 P3: what localAgentPermission reads and writes (it pushes into this node, so it runs first) ------------

    /** The current process generation (0 before the first spawn). */
    public long currentGeneration() {
        return generation;
    }

    /** The turn in progress, or 0. */
    public long currentTicket() {
        return openTicket;
    }

    /**
     * M74 UX: a turn is open, being stopped, or a question is held for it (read by assistantProfile, downstream: a profile
     * is never applied mid-turn — the rule a mode switch already follows, review M2).
     */
    /** Review P3 N4: a Stop (or a kill) is in progress — a request arriving now is never asked of the person. */
    public boolean stopping() {
        return "STOPPING".equals(phase);
    }

    public boolean turnInProgress() {
        return openTicket != 0 || "STOPPING".equals(phase) || pendingDraft != 0;
    }

    /** The posture the current (or last) process was started in. */
    public LocalAgentPosture currentPosture() {
        return posture;
    }

    /** The posture digest the current conversation was started under, or null. */
    public String currentPostureRef() {
        return postureRef;
    }

    /**
     * P3 (§8.3 UI): a permission decision as one transcript note, in {@code localAgentPermission}'s words (a verb or a
     * built-in's name from a fixed vocabulary, never the request's input). Called by that node earlier in the same cycle.
     */
    public void permissionNoted(long ticket, String status, String detail) {
        entries.add(new LocalAgentState.Entry(0, LocalAgentState.NOTE, ticket, status, detail, 0));
        changed();
    }

    /**
     * M78 D1 (§5.1): whether {@code localAgentPermission} holds a permission dialog open — the header's "needs you".
     * Called by that node earlier in the same cycle, as {@link #permissionNoted} is; a change re-publishes the state.
     */
    public void permissionWaiting(boolean waiting) {
        if (waiting == permissionWaiting) return;
        permissionWaiting = waiting;
        dirty = true;
    }

    /** The generation whose hosted calls are admitted: bound, and its first turn's posture checked. */
    public long admittedGeneration() {
        return bound && postureChecked && running() ? generation : 0;
    }

    /** The published state — immutable, for the snapshot, rebuilt only after a change. */
    public LocalAgentState state() {
        if (dirty) {
            published = new LocalAgentState(mode.mode(), conversation, generation, phase, verdict, reason, openTicket,
                    admittedGeneration(), headerRef, headerRevision, detailRef, stderrRef, bindingElapsedMs, spent(),
                    budgetMicros, costUnderReported, newChatOffered, resumable && sessionId != null, pendingDraft,
                    entries, answer, posture, personServers, billing, armedWhy, consentPosture,
                    new LocalAgentState.Extra(setupDirRef, setupDirShown, unknownEvents, unclassifiedCommands, droppedLines, turns, trimmed,
                            attention(verdict, detailRef, stderrRef, unknownEvents + unclassifiedCommands + droppedLines),
                            new LocalAgentState.Restart(restartOffer, restartState, restartChanged, restartNewChat)));
            // robot walkthrough #3: the one primary action and the one instruction line, decided here from the state
            published = published.withExtra(withGuide(published.extra(), guide(published, permissionWaiting)));
            dirty = false;
        }
        return published;
    }

    /**
     * External utility review (owner-approved 2026-10-17): whether the operator details need the person. A mismatch first
     * (the session did not match its set-up: a {@code *_MISMATCH}, a changed session, a missing capability, an older
     * bridge), then a refusal (not logged in, no bridge, a failed or timed-out start, or a spawn failure's words), then a
     * warning (an exited process's last error line, or a diagnostics count). Everything else — a version, an endpoint, a
     * PATH — is detail the person can open, and never opens by itself.
     */
    public static LocalAgentState.Attention attention(String verdict, long detailRef, long stderrRef, int diagnostics) {
        String v = verdict == null ? "" : verdict;
        if (v.endsWith("_MISMATCH") || v.equals("SESSION_CHANGED") || v.equals("CAPABILITY_MISSING")
                || v.equals("OLD_BRIDGE")) {
            return LocalAgentState.Attention.MISMATCH;
        }
        if (v.equals("NOT_LOGGED_IN") || v.equals("BRIDGE_UNAVAILABLE") || v.equals("FAILED") || v.equals("TIMEOUT")
                || detailRef != 0) {
            return LocalAgentState.Attention.REFUSAL;
        }
        if (stderrRef != 0 || diagnostics > 0) return LocalAgentState.Attention.WARNING;
        return LocalAgentState.Attention.NONE;
    }

    // ---- robot walkthrough #3 (2026-10-18): one primary action, one instruction line -----------------------------------

    /** Said while not running: starting is what Send does first, and what that contacts. */
    /** What starting contacts, said once; both lines below end with it (robot re-walk #6: never "Send starts" twice). */
    static final String STARTING_CONTACTS = "that contacts Anthropic (sign-in check, MCP connect); no record content is "
            + "sent until you press Send. In full Claude Code, starting also runs your and the folder's hooks and connects "
            + "your MCP servers.";
    public static final String CONTACTS_ANTHROPIC = "Send starts Claude Code first — " + STARTING_CONTACTS;
    public static final String NOT_RUNNING_TEXT = "Not running. " + CONTACTS_ANTHROPIC;
    public static final String PLACED_NOT_RUNNING_TEXT = "Not running — your question is placed. Send starts Claude Code, "
            + "then asks it; starting " + STARTING_CONTACTS;

    /**
     * The one primary action and the one instruction line for {@code s} (pure; the node publishes it, the panel renders
     * it). While nothing runs and a reason stands, the reason IS the line — a refusal is never a suffix to a paragraph.
     */
    public static LocalAgentState.Guide guide(LocalAgentState s, boolean waiting) {
        boolean stoppable = s.busy() || s.starting() || "STOPPING".equals(s.phase()) || s.pendingDraft() != 0;
        String primary = stoppable ? LocalAgentState.Guide.STOP : LocalAgentState.Guide.SEND;
        String action = "UNKNOWN_DRAFT".equals(s.verdict()) ? LocalAgentState.Guide.ASK_AGAIN : "";
        String phase = switch (s.phase()) {
            case "SPAWNING", "HANDSHAKE" -> "Starting Claude Code…";
            case "BINDING" -> s.bindingElapsedMs() >= 2_000 ? "connecting to the analyser bridge…" : "Starting Claude Code…";
            case "READY" -> s.pendingDraft() != 0 ? "Sending your message…" : "Ready";
            case "BUSY" -> "Claude is answering…";
            case "STOPPING" -> "Stopping the turn…";
            default -> s.armedWhy() != null ? PLACED_NOT_RUNNING_TEXT : NOT_RUNNING_TEXT;
        };
        // a Send refused as busy already says what is happening: one sentence, not the phase and then the refusal
        if ("BUSY".equals(s.verdict()) && s.busy()) {
            return guide(s, primary, s.reason(), action, true, "", waiting);
        }
        if (s.reason().isBlank()) return guide(s, primary, phase, action, false, "", waiting);
        boolean refusal = !s.verdict().isBlank() && !"BOUND".equals(s.verdict()) && !"OK".equals(s.verdict());
        if (!s.running() || refusal && !s.busy()) {
            String r = s.reason();
            String line = Character.toUpperCase(r.charAt(0)) + r.substring(1);
            // M78 D1: a refusal or an end the person must read is the panel's one conditional banner
            return guide(s, primary, line, action, refusal, line, waiting);
        }
        return guide(s, primary, phase + " — " + s.reason(), action, refusal, "", waiting);
    }

    /** As {@link #guide(LocalAgentState)} with no permission dialog open (the node's tests and the panel's). */
    public static LocalAgentState.Guide guide(LocalAgentState s) {
        return guide(s, false);
    }

    private static LocalAgentState.Guide guide(LocalAgentState s, String primary, String instruction, String action,
                                               boolean refusal, String banner, boolean waiting) {
        return new LocalAgentState.Guide(primary, instruction, action, refusal, chipWord(s, banner, waiting), banner);
    }

    /**
     * M78 D1 (spec-quiet-assistant-panel.md §5.1): the header's one or two words, from the closed set — "needs you" while
     * {@code localAgentPermission} holds a dialog open ({@code waiting}), "answering…" while a turn runs, is held or is
     * being stopped, "starting…" while a start is under way, "stopped" when nothing runs and a banner stands, and nothing
     * when Claude Code is ready or simply not started. Pure; the panel only joins it to the mode word.
     */
    public static String chipWord(LocalAgentState s, String banner, boolean waiting) {
        if (waiting) return LocalAgentState.Guide.CHIP_NEEDS_YOU;
        if (s.busy() || s.pendingDraft() != 0 || "STOPPING".equals(s.phase())) return LocalAgentState.Guide.CHIP_ANSWERING;
        if (s.starting()) return LocalAgentState.Guide.CHIP_STARTING;
        if (!s.running() && banner != null && !banner.isEmpty()) return LocalAgentState.Guide.CHIP_STOPPED;
        return LocalAgentState.Guide.CHIP_READY;
    }

    private static LocalAgentState.Extra withGuide(LocalAgentState.Extra x, LocalAgentState.Guide g) {
        return new LocalAgentState.Extra(x.setupDirRef(), x.setupDirShown(), x.unknownEvents(), x.unclassifiedCommands(),
                x.droppedLines(), x.turns(), x.trimmed(), x.attention(), x.restart(), g);
    }

    /**
     * Robot walkthrough #1 (2026-10-18): "Ask again for Claude Code" on a refusal that the composer holds a question this
     * node does not know (drafted for the in-app assistant). The node asks for those questions to be composed again for
     * Claude Code — it binds and places them as any composed draft; the placement replaces the old one.
     */
    @OnEventHandler
    public boolean onAskAgain(SessionEvents.LocalAgentAskAgainRequested e) {
        if (!mode.localAgent()) {
            auditLog.info("noOp", "LocalAgentAskAgainRequested").info("reason", "the in-app assistant is the mode in force");
            return false;
        }
        List<Long> unknown = new ArrayList<>();
        for (long ref : e.draftBindings()) if (!bindings.containsKey(ref)) unknown.add(ref);
        if ("UNKNOWN_DRAFT".equals(verdict)) {
            verdict = "";
            reason = "";
        }
        auditLog.info("localAgentAskAgain", unknown.size());
        if (!unknown.isEmpty()) effects.request(new SessionEffects.RecomposeLocalAgentDraftsEffect(0L, unknown));
        return changed();
    }

    /** For the adapter's tests and the posture assertion: whether the given mode is the one in force. */
    public boolean modeIs(AssistantMode m) {
        return mode.mode() == m;
    }
}
