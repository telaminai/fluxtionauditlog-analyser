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
 *   <li><b>One dialog at a time</b>; a second request while one is open is denied at once; one that cannot be shown in
 *       full is denied; an unanswered one is denied at the 120 s deadline the adapter's clock reports.</li>
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
    public static final String DENIED_TIMEOUT = "Not answered in the analyser within 120 s";
    public static final String DENIED_STOP = "Denied: the person pressed Stop in the analyser";
    public static final String DENIED_BUSY = "Denied: another approval is waiting for the person — ask again after it";
    public static final String DENIED_TOO_LARGE = "Denied: the request was too large to show the person in full";
    public static final String DENIED_DEFAULT_POSTURE = "Denied: this tool is not asked about in the analyser assistant's "
            + "posture";
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
    private long decisions;
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
        String refusal = policy(localAgent.currentPosture(), cls, e.name());
        if (refusal != null) {
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.POLICY, refusal);
            return changed();
        }
        if (!e.showable()) {
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.TOO_LARGE, DENIED_TOO_LARGE);
            return changed();
        }
        if (pendingRef != 0) {
            // one dialog at a time (OD-74.17); the CLI itself asks one at a time (P3-6), so this is a guard
            deny(e.generation(), e.requestRef(), e.ticket(), cls, e.name(), PermissionBy.BUSY, DENIED_BUSY);
            return changed();
        }
        pendingRef = e.requestRef();
        pendingGeneration = e.generation();
        pendingTicket = e.ticket();
        pendingClass = cls.name();
        pendingName = e.name();
        pendingInput = e.inputDigest();
        pendingToolUse = e.toolUseIdDigest();
        auditLog.info("localAgentPermissionAsked", pendingRef);
        effects.request(new SessionEffects.ShowPermissionDialogEffect(0L, e.generation(), e.requestRef(), DEADLINE_MS, cls,
                e.name()));
        effects.request(new SessionEffects.StartPermissionClockEffect(0L, e.generation(), e.requestRef(), DEADLINE_MS));
        return changed();
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
        localAgent.permissionNoted(ticket, "APPROVED", "You allowed " + shown(cls, name) + " — once"
                + (grant != 0 ? "; the analyser will run exactly this call" : ""));
        return changed();
    }

    @OnEventHandler
    public boolean onDeadline(SessionEvents.LocalAgentPermissionDeadline e) {
        if (pendingRef == 0 || e.requestRef() != pendingRef || e.generation() != pendingGeneration) return false;
        resolvePending(PermissionBy.TIMEOUT, DENIED_TIMEOUT);
        return changed();
    }

    /** P3-2: the CLI withdrew the request itself (an interrupt); the dialog closes, nothing is answered. */
    @OnEventHandler
    public boolean onCancelled(SessionEvents.LocalAgentPermissionCancelled e) {
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
        boolean any = pendingRef != 0 || !grants.isEmpty();
        if (pendingRef != 0) resolvePending(PermissionBy.STOP, DENIED_STOP);
        dropGrants("stop");
        return any && changed();
    }

    @OnEventHandler
    public boolean onTurnEnded(SessionEvents.LocalAgentTurnEnded e) {
        boolean any = false;
        if (pendingRef != 0 && e.generation() == pendingGeneration && e.ticket() == pendingTicket) {
            resolvePending(PermissionBy.POLICY, DENIED_ENDED);
            any = true;
        }
        any |= grants.removeIf(g -> Long.parseLong(g[1]) == e.generation() && Long.parseLong(g[2]) == e.ticket());
        return any && changed();
    }

    @OnEventHandler
    public boolean onExited(SessionEvents.LocalAgentExited e) {
        boolean any = false;
        if (pendingRef != 0 && e.generation() == pendingGeneration) {
            resolvePending(PermissionBy.POLICY, DENIED_ENDED);
            any = true;
        }
        any |= grants.removeIf(g -> Long.parseLong(g[1]) == e.generation());
        return any && changed();
    }

    @OnEventHandler
    public boolean onNewChat(SessionEvents.LocalAgentNewChatRequested e) {
        boolean any = pendingRef != 0 || !grants.isEmpty();
        if (pendingRef != 0) resolvePending(PermissionBy.POLICY, DENIED_ENDED);
        dropGrants("newChat");
        return any && changed();
    }

    /** Settings applied: a withdrawn consent or a changed posture (the switches, the mode) ends every request and grant. */
    @OnEventHandler
    public boolean onConsentChanged(SessionEvents.LocalAgentConsentChanged e) {
        SessionEvents.LocalAgentConsent c = e.consent();
        boolean same = c != null && c.allowed() && c.full() == localAgent.currentPosture().isFull()
                && Objects.equals(c.postureRef(), localAgent.currentPostureRef());
        if (same || pendingRef == 0 && grants.isEmpty()) return false;
        if (pendingRef != 0) resolvePending(PermissionBy.POLICY, DENIED_SETTINGS);
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
     * matches its generation, the open ticket, its verb, its input digest and — when both sides have one — its
     * {@code tool_use_id}. Consumption is this handler: two identical calls presented one after the other cannot both
     * pass. The verdict leaves as {@link SessionEffects.AnswerHostedCallEffect}, which the waiting HTTP call reads.
     */
    @OnEventHandler(propagate = false)
    public boolean onHostedCall(SessionEvents.HostedCallPresented e) {
        long admitted = localAgent.admittedGeneration();
        String refusal;
        if (admitted == 0 || admitted != e.generation()) {
            refusal = "the Claude Code session is not bound, or its posture is not yet checked, so the hosted bridge is "
                    + "refused (generation " + e.generation() + ")";
        } else {
            refusal = consume(e);
        }
        auditLog.info("localAgentHostedCall", refusal == null ? "ADMITTED" : "REFUSED").info("verb", String.valueOf(e.verb()))
                .info("generation", e.generation());
        effects.request(new SessionEffects.AnswerHostedCallEffect(0L, e.call(), refusal));
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
            if (g[5] != null && e.toolUseIdDigest() != null && !g[5].equals(e.toolUseIdDigest())) continue;
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
                    : what + " was denied without asking you — " + lower(message);
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
                    ? new PermissionState(0, 0, null, null, 0, liveGrants(), decisions)
                    : new PermissionState(pendingRef, pendingGeneration, ToolClass.valueOf(pendingClass), pendingName,
                    DEADLINE_MS, liveGrants(), decisions);
            dirty = false;
        }
        return published;
    }
}
