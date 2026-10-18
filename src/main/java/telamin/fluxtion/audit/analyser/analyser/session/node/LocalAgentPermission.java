package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.PermissionState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.PermissionBy;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * M74 P3 (spec-claude-code-provider.md §5.7, §8.3; OD-74.16, OD-74.17): <b>permission prompts in the analyser</b>. Every
 * decision about a {@code can_use_tool} request and every hosted destructive call, in one place (rule 9):
 *
 * <ul>
 *   <li><b>Which requests reach the person</b> (OD-74.16): in the default posture only the hosted destructive verbs; in
 *       the full posture also the person's own tools (built-ins and the person's MCP servers), and the hosted destructive
 *       verbs only in the modes that ask. Anything else is denied without a dialog, with its reason.</li>
 *   <li><b>One dialog at a time</b>; a second request while one is open WAITS (review M74 usability #10c, amending
 *       OD-74.17's "denied at once") and is shown when the first is answered — denied if its turn ends, on Stop, on New
 *       chat or when the settings change first, and at once only past {@link #MAX_QUEUED}; one that cannot be shown in
 *       full is denied; an unanswered one is denied at the 120 s deadline the adapter's clock reports.</li>
 *   <li><b>Never a futile question</b> (#10a): a call that writes a file while file exchange is off is refused with
 *       the reason, without a dialog — the analyser would refuse the write after the person's Allow.</li>
 *   <li><b>Non-mutating sub-actions</b> (#10b, a refinement of OD-74.16): a call of an asked verb that the adapter
 *       classified {@link SessionEvents.HostedCallKind#NON_MUTATING} (playing or ending a walk, listing a bin) is
 *       allowed without a dialog and without a grant — the server admits exactly those sub-actions itself, re-checking
 *       the call's own input; every other sub-action of the verb is still asked.</li>
 *   <li><b>The single-use grant</b> (OD-74.17): the person's <i>Allow once</i> on a hosted destructive request records a
 *       grant bound to the generation, the turn ticket, the verb, the input's canonical digest and the
 *       {@code tool_use_id} (P3-3); the bridge's call is admitted only by consuming a matching grant, here, on the
 *       session's thread. A grant ends with its turn, on Stop, with its generation, when the settings change, and 60 s
 *       after the Allow.</li>
 *   <li><b>Every answer to the CLI</b> is this node's {@link SessionEffects.AnswerPermissionEffect}: allow only after the
 *       person's Allow, deny for everything else.</li>
 * </ul>
 *
 * <p><b>Order.</b> This node PUSHES into {@code localAgentSession} (as that node pushes into {@code assistantMode}), so
 * for a fact both handle it runs first: on Stop the pending request is answered deny BEFORE {@code localAgentSession}
 * asks for the interrupt (§4.5). It reads that node's generation, open ticket, posture and admission as they are before
 * that node's own handler for the same fact runs.
 *
 * <p><b>What it never holds.</b> The request's words — the tool's name, its server, its input — stay in the adapter's
 * bounded store; facts carry a store reference, a class, a vocabulary name and digests. The audit record therefore
 * shows THAT the person approved a call of {@code report}, never what it said.
 */
public class LocalAgentPermission implements EventLogSource {

    /** How long the person has to answer a dialog (OD-74.17). */
    public static final long DEADLINE_MS = 120_000;
    /** How long a grant lives after the Allow (OD-74.17). */
    public static final long GRANT_MS = 60_000;

    public static final String DENIED_BY_PERSON = "Denied by the person in the analyser";
    /**
     * Owner hands-on test (2026-10-18): the person may not have SEEN the dialog (the popped-out assistant's window was
     * behind another), and Claude retried the same {@code mkdir} three times, each a fresh 120 s wait. Fixed text: it
     * tells the model not to ask the same thing again and to ask the person in its reply instead.
     */
    public static final String DENIED_TIMEOUT = "Not answered in the analyser within 120 s — the person may not have seen "
            + "the approval. Do not retry the same request: ask the person in your reply whether to go ahead, and wait for "
            + "their answer";
    public static final String DENIED_STOP = "Denied: the person pressed Stop in the analyser";
    public static final String DENIED_BUSY = "Denied: another approval is waiting for the person — ask again after it";
    public static final String DENIED_TOO_LARGE = "Denied: the request was too large to show the person in full";
    public static final String DENIED_DEFAULT_POSTURE = "Denied: this tool is not allowed in Safe analysis";
    public static final String DENIED_REGISTRATION = "Denied: the person's own fluxtion-analyser registration is not this "
            + "analyser";
    public static final String DENIED_PRE_ALLOWED = "Denied: this analyser tool is pre-allowed, so an approval request for "
            + "it is not expected";
    public static final String DENIED_UNKNOWN = "Denied: the analyser does not recognise this tool";
    public static final String DENIED_HIDDEN = "Denied: the analyser's verbs that change what is open or write files are "
            + "not available in this mode";
    public static final String DENIED_STALE = "Denied: the request belongs to an earlier turn or session";
    public static final String DENIED_ENDED = "Denied: the turn or session ended before the person answered";
    public static final String DENIED_SETTINGS = "Denied: the analyser's settings for Claude Code changed";
    /**
     * M74 UX (P3 leftover a): Claude Code's {@code AskUserQuestion} opens a question dialog in a terminal; the analyser has
     * none, so the request is denied by policy, with words that tell the model to ask in its reply instead.
     */
    public static final String DENIED_ASK_QUESTION = "Denied: the analyser shows no question dialog — ask the question in "
            + "your reply, and the person will answer it in the conversation";
    /** The transcript's note for that denial: what the person reads, in the conversation. */
    public static final String ASK_QUESTION_NOTE = "Claude asked a question — answer it in the conversation";
    /** The built-in name of Claude Code's question tool. */
    public static final String ASK_USER_QUESTION = "AskUserQuestion";
    /**
     * Review P3 N2 (2026-10-17): Claude Code's plan-mode tools change the session's MODE (in a terminal, ExitPlanMode is
     * the "approve this plan and start editing" question). The analyser starts a session in the mode the person chose and
     * never changes it underneath them, so both are denied by policy, with words for the model and a note for the person.
     */
    public static final java.util.Set<String> PLAN_MODE_TOOLS = java.util.Set.of("ExitPlanMode", "EnterPlanMode");
    public static final String DENIED_PLAN_MODE = "Denied: the analyser keeps the mode the person chose for this session — "
            + "give the plan in your reply; the person changes the mode and starts a New chat to act on it";
    public static final String PLAN_MODE_NOTE = "Claude asked to change its plan mode — the mode stays as you chose it; to "
            + "act on a plan, change the mode (the mode chip) and start a New chat";
    /** Review P3 N4: a request that arrives while a Stop is in progress is denied without asking. */
    public static final String DENIED_STOPPING = "Denied: the person stopped this turn";
    /** Review M74 usability #10a: a file write the settings would refuse is never asked about. */
    public static final String DENIED_FILE_EXCHANGE = "Denied: file exchange is off in the analyser (Settings ▸ Assistant ▸ "
            + "Allow assistant file exchange), so this write would be refused — the person was not asked";
    /** What the person reads for that denial. */
    public static final String FILE_EXCHANGE_NOTE = " was not asked about: it writes a file, and file exchange is off — "
            + "turn on Settings ▸ Assistant ▸ Allow assistant file exchange, then ask Claude again";
    /** The CLI's built-in web fetch tool, by its vocabulary name. */
    static final String WEB_FETCH = "WebFetch";

    /** Owner 2026-10-18: why a Full session's in-project {@code report} write was allowed without asking. */
    public static final String REPORT_IN_PROJECT = "it writes a new report inside this project";

    /** Review M74 usability #10c: how many requests may wait behind an open dialog; one more is denied as busy. */
    public static final int MAX_QUEUED = 8;

    @PushReference
    private final LocalAgentSession localAgent;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // node-local state (not final: a final field is constructor-mapped by the generator)
    private long pendingRef;
    private long pendingGeneration;
    private long pendingTicket;
    private String pendingClass;
    private String pendingName;
    private String pendingInput;
    private String pendingToolUse;
    private long grantCounter;
    private List<String[]> grants = new ArrayList<>();
    private List<String> used = new ArrayList<>();
    /** Review B4: the hosted calls admitted, by call id (verb, ticket) — so an abandoned one can be recorded as such. */
    private java.util.Map<Long, String[]> admittedCalls = new java.util.LinkedHashMap<>();
    private long decisions;
    /** #10c: requests waiting behind the open dialog — ref, generation, ticket, class, name, input, tool_use digests. */
    private List<String[]> queued = new ArrayList<>();
    /**
     * Owner 2026-10-18: the reports the current generation's session wrote in the active project without an approval —
     * name → the createdAt the report carried after it ({@link SessionEvents.HostedReportWritten}). Another generation's
     * fact starts the record again.
     */
    private long reportsGeneration;
    private java.util.Map<String, String> sessionReports = new java.util.LinkedHashMap<>();
    /** #10a: the frame's observation of file exchange; until it reports, nothing is refused for it. */
    private boolean fileExchangeObserved;
    private boolean fileExchangeOn;
    private PermissionState published = PermissionState.NONE;
    private boolean dirty = true;

    public LocalAgentPermission(LocalAgentSession localAgent, EffectQueue effects) {
        this.localAgent = localAgent;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    // ---- requests -----------------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onRequested(SessionEvents.LocalAgentPermissionRequested e) {
        ToolClass cls = e.toolClass() == null ? ToolClass.UNKNOWN : e.toolClass();
        auditLog.info("localAgentPermissionRequested", e.requestRef()).info("class", cls.name())
                .info("name", String.valueOf(e.name())).info("generation", e.generation()).info("ticket", e.ticket());
        if (e.generation() != localAgent.currentGeneration() || !localAgent.processRunning()
                || e.ticket() == 0 || e.ticket() != localAgent.currentTicket()) {
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.POLICY, DENIED_STALE);
            return changed();
        }
        if (localAgent.stopping()) {
            // review P3 N4: the person pressed Stop; a dialog now would ask about a turn they already ended
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.STOP, DENIED_STOPPING);
            return changed();
        }
        String refusal = policy(localAgent.currentPosture(), cls, e.name());
        if (refusal != null) {
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.POLICY, refusal);
            return changed();
        }
        if (!e.showable()) {
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.TOO_LARGE, DENIED_TOO_LARGE);
            return changed();
        }
        if (cls == ToolClass.BUILT_IN && WEB_FETCH.equals(e.name()) && e.kind() == SessionEvents.HostedCallKind.PRE_ALLOWED
                && localAgent.currentPosture() != null && localAgent.currentPosture().isFull()) {
            // owner, 2026-10-18: in Full, a WebFetch of the public Fluxtion docs — https, exactly fluxtion-playground.dev,
            // as the adapter observed it (LocalAgentPosture.docsFetch) — is the one built-in read allowed without asking,
            // as the CLI's own DOCS_FETCH_RULE allows it; any other URL reaches the person as before
            allowWithoutAsking(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.PRE_ALLOWED);
            return changed();
        }
        if (cls == ToolClass.HOSTED_DESTRUCTIVE && e.kind() == SessionEvents.HostedCallKind.NON_MUTATING) {
            // #10b: it changes nothing stored — allowed without asking and without a grant; the server admits only the
            // non-mutating sub-actions without one, judging the call's own input
            allowWithoutAsking(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.NON_MUTATING);
            return changed();
        }
        if (cls == ToolClass.HOSTED_DESTRUCTIVE && e.kind() == SessionEvents.HostedCallKind.PRE_ALLOWED) {
            // owner decision 2026-10-18: a graph call that creates a chart or changes only the view is what the pre-allow
            // list covered — allowed without asking and without a grant; the server re-judges the call and runs a
            // create as a create, so it can never replace a chart that appeared meanwhile. Owner, the same day: in Full,
            // an open of only a log or GraphML inside the session's real project folder too (McpTools.openInProject)
            allowWithoutAsking(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.PRE_ALLOWED);
            return changed();
        }
        if (cls == ToolClass.HOSTED_DESTRUCTIVE && e.kind() == SessionEvents.HostedCallKind.WRITES_FILE
                && fileExchangeObserved && !fileExchangeOn) {
            // #10a: never ask a question whose Allow the settings would refuse
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.POLICY, DENIED_FILE_EXCHANGE);
            return changed();
        }
        if (pendingRef != 0) {
            // #10c (amends OD-74.17): one dialog at a time, and a request arriving meanwhile WAITS for it
            if (queued.size() >= MAX_QUEUED) {
                deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.BUSY, DENIED_BUSY);
                return changed();
            }
            queued.add(new String[]{Long.toString(e.requestRef()), Long.toString(e.generation()), Long.toString(e.ticket()),
                    cls.name(), e.name(), e.inputDigest(), e.toolUseIdDigest()});
            auditLog.info("localAgentPermissionQueued", e.requestRef()).info("waiting", queued.size());
            return changed();
        }
        ask(e.requestRef(), e.generation(), e.ticket(), cls, e.name(), e.inputDigest(), e.toolUseIdDigest());
        return changed();
    }

    private void ask(long ref, long generation, long ticket, ToolClass cls, String name, String inputDigest,
                     String toolUseDigest) {
        pendingRef = ref;
        pendingGeneration = generation;
        pendingTicket = ticket;
        pendingClass = cls.name();
        pendingName = name;
        pendingInput = inputDigest;
        pendingToolUse = toolUseDigest;
        auditLog.info("localAgentPermissionAsked", pendingRef);
        effects.request(new SessionEffects.ShowPermissionDialogEffect(0L, generation, ref, DEADLINE_MS, cls, name));
        effects.request(new SessionEffects.StartPermissionClockEffect(0L, generation, ref, DEADLINE_MS));
    }

    /**
     * #10c: the open dialog was answered — the next waiting request is shown if it still belongs to the open turn of
     * the running session; one that no longer does is denied as ended.
     */
    private void askNext() {
        while (pendingRef == 0 && !queued.isEmpty()) {
            String[] q = queued.remove(0);
            long ref = Long.parseLong(q[0]), gen = Long.parseLong(q[1]), ticket = Long.parseLong(q[2]);
            ToolClass cls = ToolClass.valueOf(q[3]);
            if (gen != localAgent.currentGeneration() || ticket != localAgent.currentTicket() || !localAgent.processRunning()) {
                deny(gen, ref, ticket, cls, q[4], PermissionBy.POLICY, DENIED_ENDED);
            } else if (localAgent.stopping()) {
                deny(gen, ref, ticket, cls, q[4], PermissionBy.STOP, DENIED_STOPPING);
            } else {
                ask(ref, gen, ticket, cls, q[4], q[5], q[6]);
            }
        }
    }

    /** #10c: every waiting request is denied — Stop, New chat, a settings change, the session's end. */
    private boolean denyQueued(PermissionBy by, String message, java.util.function.Predicate<String[]> which) {
        boolean any = false;
        for (java.util.Iterator<String[]> it = queued.iterator(); it.hasNext(); ) {
            String[] q = it.next();
            if (!which.test(q)) continue;
            it.remove();
            any = true;
            deny(Long.parseLong(q[1]), Long.parseLong(q[0]), Long.parseLong(q[2]), ToolClass.valueOf(q[3]), q[4], by,
                    message);
        }
        return any;
    }

    /** #10b: an allow decided by the analyser for a call that changes nothing — recorded, never a grant. */
    private void allowWithoutAsking(long generation, long ref, long ticket, ToolClass cls, String name, PermissionBy by) {
        decisions++;
        auditLog.info("localAgentPermissionAnswered", ref).info("decision", "ALLOW").info("by",
                by.name()).info("class", cls.name()).info("ticket", ticket);
        effects.request(new SessionEffects.AnswerPermissionEffect(0L, generation, ticket, ref, true, null,
                by, 0, cls, name));
    }

    /** #10a: what the frame says about file exchange (Settings ▸ Assistant). */
    @OnEventHandler(propagate = false)
    public boolean onFileExchange(SessionEvents.AssistantFileExchangeObserved e) {
        fileExchangeObserved = true;
        fileExchangeOn = e.on();
        auditLog.info("localAgentFileExchange", e.on());
        return false;
    }

    /**
     * OD-74.16, §5.7's table: null when a request of {@code cls} reaches the person under {@code posture}, else the denial
     * words. The default posture asks only about the hosted destructive verbs (P3-4: nothing else asks there); the full
     * posture also about the person's own tools, and about the hosted destructive verbs only where they are visible.
     */
    static String policy(LocalAgentPosture posture, ToolClass cls) {
        return policy(posture, cls, null);
    }

    /**
     * As {@link #policy(LocalAgentPosture, ToolClass)}, and {@code AskUserQuestion} is denied in every posture (M74 UX,
     * P3 leftover a): the analyser has no question dialog, so the model is told to ask in its reply and the person is told
     * to answer in the conversation — never an approval dialog for a question.
     */
    static String policy(LocalAgentPosture posture, ToolClass cls, String name) {
        if (cls == ToolClass.BUILT_IN && ASK_USER_QUESTION.equals(name)) return DENIED_ASK_QUESTION;
        if (cls == ToolClass.BUILT_IN && name != null && PLAN_MODE_TOOLS.contains(name)) return DENIED_PLAN_MODE; // review P3 N2
        LocalAgentPosture p = posture == null ? LocalAgentPosture.ANALYSER_ASSISTANT : posture;
        return switch (cls) {
            case HOSTED_DESTRUCTIVE -> p.asks() ? null : DENIED_HIDDEN;
            case BUILT_IN, OTHER_MCP -> p.isFull() ? null : DENIED_DEFAULT_POSTURE;
            case HOSTED_OTHER -> DENIED_PRE_ALLOWED;
            case PERSONS_ANALYSER_REGISTRATION -> DENIED_REGISTRATION;
            case UNKNOWN -> p.isFull() ? DENIED_UNKNOWN : DENIED_DEFAULT_POSTURE;
        };
    }

    // ---- the person's answer, the deadline, the CLI's cancel -----------------------------------------------------------

    @OnEventHandler
    public boolean onChosen(SessionEvents.LocalAgentPermissionChosen e) {
        if (pendingRef == 0 || e.requestRef() != pendingRef) {
            auditLog.info("staleFact", "LocalAgentPermissionChosen").info("request", e.requestRef()).info("open", pendingRef);
            return false;
        }
        if (pendingGeneration != localAgent.currentGeneration() || pendingTicket != localAgent.currentTicket()
                || !localAgent.processRunning()) {
            resolvePending(PermissionBy.POLICY, DENIED_ENDED);
            return changed();
        }
        if (!e.allow()) {
            resolvePending(PermissionBy.PERSON, DENIED_BY_PERSON);
            askNext();
            return changed();
        }
        long grant = 0;
        if (ToolClass.HOSTED_DESTRUCTIVE.name().equals(pendingClass)) {
            // OD-74.17: the approval lifts the server-side refusal for exactly this one call
            grant = ++grantCounter;
            grants.add(new String[]{Long.toString(grant), Long.toString(pendingGeneration), Long.toString(pendingTicket),
                    pendingName, pendingInput, pendingToolUse, "live"});
            auditLog.info("localAgentGrant", grant).info("verb", String.valueOf(pendingName)).info("ticket", pendingTicket);
        }
        long ref = pendingRef;
        long gen = pendingGeneration;
        long ticket = pendingTicket;
        String name = pendingName;
        String cls = pendingClass;
        clearPending();
        decisions++;
        auditLog.info("localAgentPermissionAnswered", ref).info("decision", "ALLOW").info("by", PermissionBy.PERSON.name())
                .info("class", cls).info("ticket", ticket);
        effects.request(new SessionEffects.AnswerPermissionEffect(0L, gen, ticket, ref, true, null, PermissionBy.PERSON,
                grant, ToolClass.valueOf(cls), name));
        effects.request(new SessionEffects.ClosePermissionDialogEffect(0L, ref));
        // M78 D1 (spec-quiet-assistant-panel.md §5.3): no Approved note — the tool line's "approved by you" says it
        // (defect 5); a Denied, Not answered or Withdrawn note is still noted, because nothing else says those
        askNext();
        return changed();
    }

    @OnEventHandler
    public boolean onDeadline(SessionEvents.LocalAgentPermissionDeadline e) {
        if (pendingRef == 0 || e.requestRef() != pendingRef || e.generation() != pendingGeneration) return false;
        resolvePending(PermissionBy.TIMEOUT, DENIED_TIMEOUT);
        askNext();
        return changed();
    }

    /**
     * P3-2: the CLI withdrew the request itself (an interrupt); the dialog closes, nothing is answered. Final review
     * (2026-10-17) #1: a request withdrawn while it WAITS behind the open dialog leaves the queue the same way — it is never
     * shown, so it can never be approved, and nothing is answered for it (the CLI no longer waits for it).
     */
    @OnEventHandler
    public boolean onCancelled(SessionEvents.LocalAgentPermissionCancelled e) {
        for (java.util.Iterator<String[]> it = queued.iterator(); it.hasNext(); ) {
            String[] q = it.next();
            if (Long.parseLong(q[0]) != e.requestRef() || Long.parseLong(q[1]) != e.generation()) continue;
            it.remove();
            decisions++;
            auditLog.info("localAgentPermissionAnswered", e.requestRef()).info("decision", "WITHDRAWN").info("by",
                    PermissionBy.CANCELLED.name()).info("queued", true);
            if (e.generation() == localAgent.currentGeneration()) {
                localAgent.permissionNoted(Long.parseLong(q[2]), "WITHDRAWN", "Claude Code withdrew its request to use "
                        + shown(q[3], q[4]));
            }
            return changed();
        }
        if (pendingRef == 0 || e.requestRef() != pendingRef || e.generation() != pendingGeneration) return false;
        long ref = pendingRef;
        long ticket = pendingTicket;
        String name = pendingName;
        String cls = pendingClass;
        clearPending();
        decisions++;
        auditLog.info("localAgentPermissionAnswered", ref).info("decision", "WITHDRAWN").info("by",
                PermissionBy.CANCELLED.name());
        effects.request(new SessionEffects.ClosePermissionDialogEffect(0L, ref));
        localAgent.permissionNoted(ticket, "WITHDRAWN", "Claude Code withdrew its request to use " + shown(cls, name));
        askNext();
        return changed();
    }

    /** Scorecard R8: the answer as written — already recorded as a fact; nothing more to decide. */
    @OnEventHandler(propagate = false)
    public boolean onAnswered(SessionEvents.LocalAgentPermissionAnswered e) {
        auditLog.info("localAgentPermissionWritten", e.requestRef()).info("written", e.written());
        return false;
    }

    // ---- what ends a request or a grant -----------------------------------------------------------------------------

    /** §4.5, §5.7: Stop answers a pending request deny BEFORE the interrupt (this node runs first), and drops the grants. */
    @OnEventHandler
    public boolean onStopRequested(SessionEvents.LocalAgentStopRequested e) {
        boolean any = pendingRef != 0 || !grants.isEmpty() || !queued.isEmpty();
        if (pendingRef != 0) resolvePending(PermissionBy.STOP, DENIED_STOP);
        denyQueued(PermissionBy.STOP, DENIED_STOP, q -> true);
        dropGrants("stop");
        return any && changed();
    }

    @OnEventHandler
    public boolean onTurnEnded(SessionEvents.LocalAgentTurnEnded e) {
        boolean any = false;
        any |= denyQueued(PermissionBy.POLICY, DENIED_ENDED,
                q -> Long.parseLong(q[1]) == e.generation() && Long.parseLong(q[2]) == e.ticket());
        if (pendingRef != 0 && e.generation() == pendingGeneration && e.ticket() == pendingTicket) {
            resolvePending(PermissionBy.POLICY, DENIED_ENDED);
            any = true;
        }
        any |= grants.removeIf(g -> Long.parseLong(g[1]) == e.generation() && Long.parseLong(g[2]) == e.ticket());
        askNext();
        return any && changed();
    }

    @OnEventHandler
    public boolean onExited(SessionEvents.LocalAgentExited e) {
        boolean any = false;
        any |= denyQueued(PermissionBy.POLICY, DENIED_ENDED, q -> Long.parseLong(q[1]) == e.generation());
        if (pendingRef != 0 && e.generation() == pendingGeneration) {
            resolvePending(PermissionBy.POLICY, DENIED_ENDED);
            any = true;
        }
        any |= grants.removeIf(g -> Long.parseLong(g[1]) == e.generation());
        askNext();
        return any && changed();
    }

    @OnEventHandler
    public boolean onNewChat(SessionEvents.LocalAgentNewChatRequested e) {
        boolean any = pendingRef != 0 || !grants.isEmpty() || !queued.isEmpty();
        if (pendingRef != 0) resolvePending(PermissionBy.POLICY, DENIED_ENDED);
        denyQueued(PermissionBy.POLICY, DENIED_ENDED, q -> true);
        dropGrants("newChat");
        return any && changed();
    }

    /** Settings applied: a withdrawn consent or a changed posture (the switches, the mode) ends every request and grant. */
    @OnEventHandler
    public boolean onConsentChanged(SessionEvents.LocalAgentConsentChanged e) {
        SessionEvents.LocalAgentConsent c = e.consent();
        boolean same = c != null && c.allowed() && c.full() == localAgent.currentPosture().isFull()
                && Objects.equals(c.postureRef(), localAgent.currentPostureRef());
        if (same || pendingRef == 0 && grants.isEmpty() && queued.isEmpty()) return false;
        if (pendingRef != 0) resolvePending(PermissionBy.POLICY, DENIED_SETTINGS);
        denyQueued(PermissionBy.POLICY, DENIED_SETTINGS, q -> true);
        dropGrants("settings");
        return changed();
    }

    @OnEventHandler
    public boolean onGrantExpired(SessionEvents.LocalAgentGrantExpired e) {
        for (String[] g : grants) {
            if (Long.parseLong(g[0]) == e.grantId() && "live".equals(g[6])) {
                g[6] = "expired";
                auditLog.info("localAgentGrantExpired", e.grantId());
                return changed();
            }
        }
        return false;
    }

    // ---- the hosted bridge's destructive call ------------------------------------------------------------------------

    /**
     * OD-74.17 step 2: a hosted destructive call is admitted only by consuming the one unconsumed, unexpired grant that
     * matches its generation, the open ticket, its verb, its input digest and — when the grant has one — its
     * {@code tool_use_id} (review P3 S1: a call without the id does not match a grant bound to one). Consumption is this handler: two identical calls presented one after the other cannot both
     * pass. The verdict leaves as {@link SessionEffects.AnswerHostedCallEffect}, which the waiting HTTP call reads.
     */
    @OnEventHandler(propagate = false)
    public boolean onHostedCall(SessionEvents.HostedCallPresented e) {
        long admitted = localAgent.admittedGeneration();
        String refusal;
        if (admitted == 0 || admitted != e.generation()) {
            refusal = "the Claude Code session is not connected to this analyser yet, or its set-up is not yet checked, "
                    + "so the analyser's own tools are refused for it (session " + e.generation() + ")";
        } else {
            refusal = consume(e);
        }
        auditLog.info("localAgentHostedCall", refusal == null ? "ADMITTED" : "REFUSED").info("verb", String.valueOf(e.verb()))
                .info("generation", e.generation()).info("call", e.call());
        if (refusal == null) {
            admittedCalls.put(e.call(), new String[]{String.valueOf(e.verb()), Long.toString(localAgent.currentTicket())});
            while (admittedCalls.size() > 64) admittedCalls.remove(admittedCalls.keySet().iterator().next());
        }
        effects.request(new SessionEffects.AnswerHostedCallEffect(0L, e.call(), refusal));
        dirty = true;
        return false;
    }

    /**
     * Owner 2026-10-18: the server wrote a report for a hosted session without an approval (Full Claude Code, inside the
     * active project; {@code McpTools.reportInProject}). Recorded for that generation only — a fact of a generation that
     * is not the current one is stale and recorded nowhere; a new generation starts the record again. At most 64 kept.
     */
    @OnEventHandler(propagate = false)
    public boolean onHostedReportWritten(SessionEvents.HostedReportWritten e) {
        auditLog.info("localAgentHostedReport", e.generation()).info("current", localAgent.currentGeneration());
        if (e.generation() == 0 || e.generation() != localAgent.currentGeneration() || e.name() == null) return false;
        if (reportsGeneration != e.generation()) {
            reportsGeneration = e.generation();
            sessionReports = new java.util.LinkedHashMap<>();
        }
        sessionReports.remove(e.name());
        if (e.createdAt() != null) sessionReports.put(e.name(), e.createdAt());
        while (sessionReports.size() > 64) sessionReports.remove(sessionReports.keySet().iterator().next());
        dirty = true;
        return false;
    }

    /**
     * Holistic review B4 (2026-10-17): a hosted call gave up waiting and the server refused it. If this node admitted it
     * — consuming the person's one grant — the record must not say "admitted" alone: the call never ran. The grant stays
     * consumed (VOID: it is never restored, so a later identical call is refused as "used", consistently), the record says
     * ABANDONED, and the person reads that the approved call did not run.
     */
    @OnEventHandler(propagate = false)
    public boolean onHostedCallAbandoned(SessionEvents.HostedCallAbandoned e) {
        String[] admitted = admittedCalls.remove(e.call());
        auditLog.info("localAgentHostedCall", "ABANDONED").info("call", e.call()).info("generation", e.generation())
                .info("grantVoided", admitted != null);
        if (admitted != null && e.generation() == localAgent.currentGeneration()) {
            localAgent.permissionNoted(Long.parseLong(admitted[1]), "ABANDONED", "The approved call to analyser_"
                    + admitted[0] + " did not run: the analyser answered it too late and refused it. Your approval is "
                    + "used up — approve it again if Claude asks again");
        }
        dirty = true;
        return false;
    }

    private String consume(SessionEvents.HostedCallPresented e) {
        long ticket = localAgent.currentTicket();
        String key = e.generation() + "|" + ticket + "|" + e.verb() + "|" + e.inputDigest();
        boolean expiredMatch = false;
        for (int i = 0; i < grants.size(); i++) {
            String[] g = grants.get(i);
            if (Long.parseLong(g[1]) != e.generation() || Long.parseLong(g[2]) != ticket || !Objects.equals(g[3], e.verb())
                    || !Objects.equals(g[4], e.inputDigest())) continue;
            // review P3 S1: a grant bound to a tool_use_id admits only a call carrying THAT id — a call with none does not
            // match (the bridge forwards the id; a call without it is not the call the person approved)
            if (g[5] != null && !g[5].equals(e.toolUseIdDigest())) continue;
            if (!"live".equals(g[6])) {
                expiredMatch = true;
                continue;
            }
            grants.remove(i);
            used.add(key);
            while (used.size() > 64) used.remove(0);
            auditLog.info("localAgentGrantUsed", g[0]);
            return null;
        }
        String verb = e.verb() == null ? "this verb" : e.verb();
        if (expiredMatch) {
            return "`" + verb + "` needs your approval in the analyser for this exact call; the approval given expired "
                    + "(60 s), so it was refused — ask again";
        }
        if (used.contains(key)) {
            return "`" + verb + "` needs your approval in the analyser for this exact call; the approval given was for one "
                    + "call and has been used, so it was refused";
        }
        return "`" + verb + "` needs your approval in the analyser for this exact call; none was given";
    }

    // ---- helpers -----------------------------------------------------------------------------------------------------

    private void resolvePending(PermissionBy by, String message) {
        long ref = pendingRef;
        long gen = pendingGeneration;
        long ticket = pendingTicket;
        String cls = pendingClass;
        String name = pendingName;
        clearPending();
        effects.request(new SessionEffects.ClosePermissionDialogEffect(0L, ref));
        deny(gen, ref, ticket, cls == null ? ToolClass.UNKNOWN : ToolClass.valueOf(cls), name, by, message);
    }

    private void deny(long generation, long ref, long ticket, ToolClass cls, String name, PermissionBy by, String message) {
        decisions++;
        auditLog.info("localAgentPermissionAnswered", ref).info("decision", "DENY").info("by", by.name())
                .info("class", cls.name()).info("ticket", ticket);
        effects.request(new SessionEffects.AnswerPermissionEffect(0L, generation, ticket, ref, false, message, by, 0, cls,
                name));
        String what = shown(cls.name(), name);
        String words = switch (by) {
            case PERSON -> "You denied " + what;
            case TIMEOUT -> "Not answered within 120 s, so " + what + " was denied";
            case STOP -> "Stop: " + what + " was denied";
            case BUSY -> what + " was denied: another approval was waiting for you";
            case TOO_LARGE -> what + " was denied without asking you: the request was too large to show in full";
            case CANCELLED -> "Claude Code withdrew its request to use " + what;
            case POLICY -> DENIED_ASK_QUESTION.equals(message) ? ASK_QUESTION_NOTE
                    : DENIED_PLAN_MODE.equals(message) ? PLAN_MODE_NOTE
                    : DENIED_FILE_EXCHANGE.equals(message) ? what + FILE_EXCHANGE_NOTE
                    : what + " was denied without asking you — " + lower(message);
            case NON_MUTATING -> what + " was allowed: it changes nothing";
            case PRE_ALLOWED -> "open".equals(name) ? what + " was allowed: it only opens a log or GraphML inside this "
                    + "project's folder or the analyser's own sample"
                    : "report".equals(name) ? what + " was allowed: " + REPORT_IN_PROJECT
                    : WEB_FETCH.equals(name) ? what + " was allowed: it reads the Fluxtion documentation at "
                    + telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.DOCS_DOMAIN
                    : what + " was allowed: it creates or shows a chart";
        };
        // a request of an earlier generation belongs to a conversation entry nobody is reading: no note for it
        if (generation == localAgent.currentGeneration()) {
            localAgent.permissionNoted(ticket, by == PermissionBy.TIMEOUT ? "TIMED_OUT" : "DENIED", words);
        }
    }

    private static String lower(String message) {
        String m = message.startsWith("Denied: ") ? message.substring("Denied: ".length()) : message;
        return m;
    }

    /** How a request's tool is named in the node's own words: the hosted verb or the built-in (vocabulary), else its class. */
    static String shown(String cls, String name) {
        if (name != null && (ToolClass.HOSTED_DESTRUCTIVE.name().equals(cls) || ToolClass.HOSTED_OTHER.name().equals(cls))) {
            return "analyser_" + name;
        }
        if (name != null && ToolClass.BUILT_IN.name().equals(cls)) return name;
        if (ToolClass.OTHER_MCP.name().equals(cls)) return "a tool of one of your MCP servers";
        if (ToolClass.PERSONS_ANALYSER_REGISTRATION.name().equals(cls)) return "a tool of your own fluxtion-analyser server";
        return "a tool";
    }

    private void clearPending() {
        pendingRef = 0;
        pendingGeneration = 0;
        pendingTicket = 0;
        pendingClass = null;
        pendingName = null;
        pendingInput = null;
        pendingToolUse = null;
    }

    private void dropGrants(String why) {
        if (grants.isEmpty()) return;
        auditLog.info("localAgentGrantsDropped", grants.size()).info("why", why);
        grants = new ArrayList<>();
    }

    private boolean changed() {
        dirty = true;
        localAgent.permissionWaiting(pendingRef != 0);      // M78 D1 §5.1: the header's "needs you" is the node's
        return true;
    }

    private int liveGrants() {
        int n = 0;
        for (String[] g : grants) if ("live".equals(g[6])) n++;
        return n;
    }

    /** The published state — immutable, rebuilt only after a change. */
    public PermissionState state() {
        if (dirty) {
            published = pendingRef == 0
                    ? new PermissionState(0, 0, null, null, 0, liveGrants(), decisions, queued.size(), reportsGeneration,
                    sessionReports)
                    : new PermissionState(pendingRef, pendingGeneration, ToolClass.valueOf(pendingClass), pendingName,
                    DEADLINE_MS, liveGrants(), decisions, queued.size(), reportsGeneration, sessionReports);
            dirty = false;
        }
        return published;
    }
}
