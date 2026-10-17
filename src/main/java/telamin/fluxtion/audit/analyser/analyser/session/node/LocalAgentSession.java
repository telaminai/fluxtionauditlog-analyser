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
    /** P1b and P2: the full posture's {@code default} and {@code acceptEdits} ask, and nobody can be asked yet (PB-1). */
    public static final String APPROVAL_UNAVAILABLE_TEXT = "an action needed your approval, which the analyser cannot ask "
            + "for yet (approvals in the analyser come in a later release), so it was denied — allow it with your own "
            + "Claude Code permission rules, or choose another mode in Settings ▸ Assistant";

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
    /** One coalesced note per hook event of this generation: its entry index and counts (started, ok, failed). */
    private Map<SessionEvents.HookEvent, long[]> hookNotes = new LinkedHashMap<>();
    private List<LocalAgentState.Entry> entries = new ArrayList<>();
    private LocalAgentState.Answer answer = LocalAgentState.Answer.NONE;
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

    // ---- the person's requests ------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onModeChosen(SessionEvents.AssistantModeChosen e) {
        // the mode node is upstream: it already holds the new mode. Leaving the local agent stops its process.
        if (mode.localAgent() || !running()) return changed();
        kill("the Assistant tab was switched to the in-app assistant, so Claude Code was stopped");
        return changed();
    }

    @OnEventHandler
    public boolean onStartRequested(SessionEvents.LocalAgentStartRequested e) {
        if (running()) {
            auditLog.info("noOp", "LocalAgentStartRequested").info("phase", phase);
            if (!e.warm()) answer(e.request(), true, "");
            return e.warm() ? false : changed();
        }
        String refusal = startRefusal(e.consent(), e.warm(), e.request());
        if (refusal != null) {
            if (e.warm()) {                       // a warm start says nothing and asks nothing (OD-74.1 amendment)
                auditLog.info("warmStartSkipped", verdictOf(refusal));
                return false;
            }
            refuse(e.request(), refusal);
            return changed();
        }
        spawn(e.consent());
        answer(e.request(), true, "");
        return changed();
    }

    @OnEventHandler
    public boolean onSendRequested(SessionEvents.LocalAgentSendRequested e) {
        String refusal = sendRefusal(e);
        if (refusal != null) {
            refuse(e.request(), refusal);
            return changed();
        }
        if (!running()) {
            String startRefusal = startRefusal(e.consent(), false, e.request());
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
            reason = "";
            verdict = "";
            auditLog.info("localAgentDraftHeld", e.draft());
            return changed();
        }
        sendTurn(e.draft(), e.request());
        return changed();
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
        conversation++;
        sessionId = null;
        resumable = false;
        postureRef = null;
        pendingDraft = 0;
        newChatOffered = false;
        entries = new ArrayList<>();
        verdict = "";
        reason = "a new conversation: it starts with the next Start or Send";
        auditLog.info("localAgentConversation", conversation);
        return changed();
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
                default -> "Claude Code could not be started";
            };
            pendingDraft = 0;
            note(verdict, reason);
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
                refuseSession("SERVERS_MISMATCH", "Claude Code did not report the analyser's hosted bridge among its MCP "
                        + "servers, so the session was refused", true);
                return changed();
            }
            personServers = LocalAgentPosture.personsServers(e.servers()).size();
        } else if (e.servers().size() != 1 || !LocalAgentPosture.HOSTED_SERVER.equals(e.servers().get(0))) {
            refuseSession("SERVERS_MISMATCH", "Claude Code reported MCP servers other than the analyser's hosted bridge, "
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
                    if (pendingDraft != 0) {
                        long draft = pendingDraft;
                        pendingDraft = 0;
                        sendTurn(draft, pendingRequest);
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
                    : "the tool set is not the analyser assistant's (Read, Grep, Glob and the pre-allowed analyser verbs)";
            case SERVERS_MISMATCH -> posture.isFull() ? "the analyser's hosted bridge is no longer connected"
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
        String text = "the posture check failed — " + why + " — so the session was stopped";
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
                        e.revision()));
                return changed();
            }
        }
        entries.add(new LocalAgentState.Entry(e.textRef(), LocalAgentState.ANSWER, e.ticket(), "STREAMING", "",
                e.revision()));
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
                    ? "an action was denied by your Claude Code permission rules or the mode in force ("
                    + posture.fullMode().cli() + ")"
                    : "an action was denied by the analyser assistant's posture (only Read, Grep, Glob in this folder and "
                    + "the pre-allowed analyser verbs are available)");
            case APPROVAL_UNAVAILABLE -> note("DENIED", posture.isFull() ? APPROVAL_UNAVAILABLE_TEXT
                    : "an action was denied: it needed an approval, which the analyser assistant never asks for");
        }
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
        long[] n = hookNotes.get(event);
        if (n == null) {
            n = new long[]{entries.size(), 0, 0, 0};
            hookNotes.put(event, n);
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
        StringBuilder sb = new StringBuilder("Hook ").append(name).append(": ");
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
        costBySession.merge(sessionRef, Math.max(0, e.totalCostMicros()), Math::max);
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

    // ---- decisions --------------------------------------------------------------------------------------------------

    /** Why a start cannot happen now, or null. A folder needing confirmation asks for the dialog (never when warm). */
    private String startRefusal(SessionEvents.LocalAgentConsent c, boolean warm, long request) {
        if (!mode.localAgent()) return "MODE: choose Claude Code on the Assistant tab first";
        // OD-74.12: a tab click is too weak an act to run the folder's and the person's hooks on — never in the full
        // posture, whatever else holds (silent, as every warm refusal)
        if (warm && c != null && c.full()) return "WARM_FULL: warm start does not apply to full Claude Code";
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
        if (!c.restOn()) {
            return "REST_OFF: Claude Code reaches the analyser through the REST transport, which is off — turn it on to "
                    + "start Claude Code";
        }
        if (!c.confirmedForPosture()) {
            if (!warm) effects.request(new SessionEffects.ConfirmLocalAgentFolderEffect(0L, request, c.full()));
            return c.full()
                    ? "CONFIRM_FOLDER: confirm this folder for full Claude Code first — its hooks and MCP servers run at "
                    + "Start, and a folder trusted before is asked again when they change; nothing was started"
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
            return "POSTURE_CHANGED: the posture (a switch, the full posture's mode, the folder or the system note) changed "
                    + "since this conversation started; a conversation is never resumed under another posture — start a New "
                    + "chat";
        }
        return null;
    }

    private String sendRefusal(SessionEvents.LocalAgentSendRequested e) {
        if (!mode.localAgent()) return "MODE: the Assistant tab is on the in-app assistant";
        if (openTicket != 0 || "STOPPING".equals(phase)) return "BUSY: " + BUSY_TEXT;
        if (e.draft() <= 0) return "EMPTY: there is nothing to send";
        if (pendingDraft != 0) return "BUSY: " + BUSY_TEXT;
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
        personServerStates = new ArrayList<>();
        personServers = 0;
        hookNotes = new LinkedHashMap<>();
        phase = "SPAWNING";
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
        effects.request(new SessionEffects.StartLocalAgentEffect(0L, generation, sessionId, resumable, nonce, remaining,
                c.postureRef(), posture));
    }

    private void sendTurn(long draft, long request) {
        ticket++;
        openTicket = ticket;
        phase = "BUSY";
        interruptAcked = false;
        verdict = "";
        reason = "";
        entries.add(new LocalAgentState.Entry(draft, LocalAgentState.USER, ticket, "SENT", "", 0));
        answer(request, true, "");
        auditLog.info("localAgentTurn", ticket).info("generation", generation);
        effects.request(new SessionEffects.SendLocalAgentTurnEffect(0L, generation, ticket, draft));
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
                entries.set(i, new LocalAgentState.Entry(x.id(), x.kind(), x.ticket(), outcome.name(), "", x.revision()));
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
            kill(why.isBlank() ? "the posture check failed, so the session was stopped" : why);
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
                    + "the analyser's hosted bridge is unaffected");
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
        long was = openTicket;
        if (was != 0) {
            openTicket = 0;
            note("STOPPED", "the turn in progress ended when Claude Code was stopped");
        }
        effects.request(new SessionEffects.StopLocalAgentEffect(0L, generation, was, SessionEffects.StopHow.KILL));
        phase = "OFF";
        bound = false;
        postureChecked = false;
        killRequested = true;
        endAfterTurn = false;
        if (why != null) {
            reason = why;
            note("STOPPED", why);
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

    private static String safe(String s) {
        if (s == null) return "(none)";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private boolean changed() {
        dirty = true;
        return true;
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
                    entries, answer, posture, personServers, billing);
            dirty = false;
        }
        return published;
    }

    /** For the adapter's tests and the posture assertion: whether the given mode is the one in force. */
    public boolean modeIs(AssistantMode m) {
        return mode.mode() == m;
    }
}
