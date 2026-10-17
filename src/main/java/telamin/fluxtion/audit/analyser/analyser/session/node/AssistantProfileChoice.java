package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * M74 UX (owner, 2026-10-17): the assistant profile — one of four cards (<i>Safe analysis</i>, <i>Full Claude Code</i>,
 * <i>In-app assistant</i>, <i>Off</i>) instead of switch-by-switch set-up. Every decision about the profile overlay, in
 * one place (rule 9):
 *
 * <ul>
 *   <li><b>When it opens.</b> On the person's click in the assistant header; and BY ITSELF only the first time the person
 *       presses Start, sends a question (either assistant) or asks one on the start page while no profile is chosen —
 *       once per session, and never again after a profile is chosen.</li>
 *   <li><b>Whether Apply may be applied now.</b> Never while a turn of either assistant is in progress — refused by name,
 *       as a mode switch mid-turn is; otherwise it asks for {@link SessionEffects.ApplyAssistantProfileEffect}, whose
 *       performer writes the same machine-tier settings Settings ▸ Assistant writes and posts the same consent facts, so
 *       {@code localAgentSession} and {@code assistantLoop} decide what the new settings mean, as for any settings
 *       change.</li>
 * </ul>
 *
 * <p>"Not chosen" is what the frame REPORTS ({@link SessionEvents.AssistantProfileObserved}); before any report the node
 * opens nothing by itself.
 *
 * <p><b>Order.</b> Downstream of {@code localAgentSession} and {@code assistantLoop}, so it reads whether a turn is open
 * after they have handled the fact; upstream of {@code startPagePrompt} (a JavaBean parent of it), so a first-use
 * question on the start page opens the choices instead of the no-AI fallback. The Start or Send that opened the choices
 * is refused by its own node, as it is today when nothing is set up — this node only adds the choices beside it.
 *
 * <p><b>What it never holds.</b> The overlay's details — an API key, a model, a mode — never enter a fact; they stay in
 * the overlay and reach the settings through the effect's performer.
 */
public class AssistantProfileChoice implements EventLogSource {

    /** Apply refused while Claude Code answers (as a mode switch mid-turn is). */
    public static final String BUSY_LOCAL_AGENT_TEXT = "Claude is still answering — Stop or wait, then change how the "
            + "assistant works.";
    /** Apply refused while the in-app assistant answers. */
    public static final String BUSY_IN_APP_TEXT = "The in-app assistant is still answering — Stop or wait, then change how "
            + "the assistant works.";
    /** The start page's note when its question opened the choices instead of an answer. */
    public static final String FIRST_USE_NOTE = StartPagePrompt.CARRIED_NOTE;

    private final LocalAgentSession localAgent;
    private final AssistantLoop assistantLoop;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // node-local state (not final: a final field is constructor-mapped by the generator)
    private String profile = AssistantProfile.NONE.name();
    /** The frame has reported the profile at least once: "not chosen" is a FACT, never this node's default. */
    private boolean observed;
    private boolean open;
    private String why = "";
    private boolean offered;
    private long request;
    private String refusal = "";
    /** The start-page request this node answered by opening the choices, so startPagePrompt shows no fallback for it. */
    private long interceptedStartPage;
    private ProfileState published = ProfileState.NONE;
    private boolean dirty = true;

    public AssistantProfileChoice(LocalAgentSession localAgent, AssistantLoop assistantLoop, EffectQueue effects) {
        this.localAgent = localAgent;
        this.assistantLoop = assistantLoop;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    // ---- what the machine says --------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onProfileObserved(SessionEvents.AssistantProfileObserved e) {
        String next = (e.profile() == null ? AssistantProfile.NONE : e.profile()).name();
        if (observed && next.equals(profile)) return false;
        observed = true;
        profile = next;
        auditLog.info("assistantProfile", profile);
        return changed();
    }

    // ---- the person's acts ------------------------------------------------------------------------------------------

    @OnEventHandler
    public boolean onProfilesRequested(SessionEvents.AssistantProfilesRequested e) {
        open = true;
        why = ProfileState.ASKED;
        refusal = "";
        auditLog.info("assistantProfiles", "OPEN").info("origin", String.valueOf(e.origin()));
        return changed();
    }

    @OnEventHandler
    public boolean onProfilesClosed(SessionEvents.AssistantProfilesClosed e) {
        if (!open) return false;
        open = false;
        why = "";
        refusal = "";
        auditLog.info("assistantProfiles", "CLOSED").info("origin", String.valueOf(e.origin()));
        return changed();
    }

    /** Apply: never mid-turn (refused by name, the overlay stays open); otherwise the settings are written by effect. */
    @OnEventHandler
    public boolean onProfileChosen(SessionEvents.AssistantProfileChosen e) {
        AssistantProfile p = e.profile() == null ? AssistantProfile.NONE : e.profile();
        request = e.request();
        if (p == AssistantProfile.NONE) {
            refusal = "choose one of the four cards first";
            auditLog.info("assistantProfileRefused", "NONE");
            return changed();
        }
        if (localAgent.turnInProgress()) {
            refusal = BUSY_LOCAL_AGENT_TEXT;
        } else if (assistantLoop.turnInProgress()) {
            refusal = BUSY_IN_APP_TEXT;
        } else {
            refusal = "";
        }
        if (!refusal.isEmpty()) {
            auditLog.info("assistantProfileRefused", p.name()).info("request", e.request());
            return changed();
        }
        auditLog.info("assistantProfileApply", p.name()).info("request", e.request());
        effects.request(new SessionEffects.ApplyAssistantProfileEffect(0L, e.request(), p));
        return changed();
    }

    /** The settings were written: the choices close; the profile in force follows the observation that came with it. */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.AssistantProfileApplied e) {
        if (e.request() != request) {
            auditLog.info("staleFact", "AssistantProfileApplied").info("request", e.request()).info("open", request);
            return false;
        }
        if (!e.ok()) {
            refusal = e.reason() == null || e.reason().isBlank() ? "the settings could not be written" : e.reason();
            auditLog.info("assistantProfileApplied", "FAILED");
            return changed();
        }
        if (e.profile() != null) profile = e.profile().name();
        open = false;
        why = "";
        refusal = "";
        auditLog.info("assistantProfileApplied", profile);
        // review P3+UX S4: the tab follows the profile — decided here, after the settings were written, never by the
        // performer of the write
        if (e.profile() != null && e.profile() != AssistantProfile.NONE) {
            effects.request(new SessionEffects.ChooseAssistantModeEffect(0L, e.profile().mode(), "assistant profile"));
        }
        return changed();
    }

    @OnEventHandler(propagate = false)
    public boolean onProfilesShown(SessionEvents.AssistantProfilesShown e) {
        auditLog.info("assistantProfilesShown", e.opId());
        return false;
    }

    // ---- the first use: the choices open by themselves, once, while nothing is chosen ---------------------------------

    /**
     * The first Start: only when {@code localAgentSession} — which handled it first — did not accept it. An assistant that
     * can answer is never interrupted by the choices, whatever the last observation said.
     */
    @OnEventHandler
    public boolean onStartRequested(SessionEvents.LocalAgentStartRequested e) {
        if (accepted(localAgent.state().answer(), e.request())) return false;
        return firstUse("start", false);
    }

    @OnEventHandler
    public boolean onLocalAgentSend(SessionEvents.LocalAgentSendRequested e) {
        if (accepted(localAgent.state().answer(), e.request())) return false;
        return firstUse("claude code send", false);
    }

    @OnEventHandler
    public boolean onAssistantSend(SessionEvents.AssistantSendRequested e) {
        var a = assistantLoop.state().answer();
        if (a.request() == e.request() && a.accepted()) return false;
        return firstUse("in-app send", false);
    }

    private static boolean accepted(telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState.Answer a, long request) {
        return a != null && a.request() == request && a.accepted();
    }

    /**
     * The start page's button: only a QUESTION opens the choices — free text, or a prompt entry. The flows (the demo, set
     * up, open a bundle, the templates, open a log) work with no AI and keep their own fallbacks.
     */
    @OnEventHandler
    public boolean onStartPageSent(SessionEvents.StartPagePromptSent e) {
        String id = e.entryId() == null ? "NONE" : e.entryId();
        boolean question = id.equals("NONE") ? e.textRef() != 0 : !id.matches("S[1-5]");
        if (!question) return false;
        boolean canAnswer = e.inAppCanAnswer() || (e.consent() != null && e.consent().allowed() && e.claudeCodeFound());
        if (canAnswer) return false;                  // an assistant can answer it: the question goes there
        if (!firstUse("start page", !e.workspaceOpen())) return false;
        interceptedStartPage = e.request();
        return true;
    }

    private boolean firstUse(String origin, boolean popOut) {
        if (!observed || !AssistantProfile.NONE.name().equals(profile) || offered) return false;
        offered = true;
        open = true;
        why = ProfileState.FIRST_USE;
        refusal = "";
        auditLog.info("assistantProfiles", "FIRST_USE").info("origin", origin);
        effects.request(new SessionEffects.ShowAssistantProfilesEffect(0L, popOut));
        return changed();
    }

    // ---- what children read -----------------------------------------------------------------------------------------

    /** Whether this node answered start-page request {@code request} by opening the choices (read by startPagePrompt). */
    public boolean interceptedStartPage(long request) {
        return request != 0 && request == interceptedStartPage;
    }

    public AssistantProfile profile() {
        return AssistantProfile.valueOf(profile);
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    /** The published state — immutable, for the snapshot, rebuilt only after a change. */
    public ProfileState state() {
        if (dirty) {
            published = new ProfileState(AssistantProfile.valueOf(profile), open, why, request, refusal, offered);
            dirty = false;
        }
        return published;
    }
}
