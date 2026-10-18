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
 *   <li><b>Full Claude Code is confirmed first</b> (review PR151 F4, owner 2026-10-18). Turning it on from the cards asks
 *       what Settings ▸ Assistant asks — {@link SessionEffects.ConfirmFullProfileEffect}, the full warning with Cancel the
 *       default — and only {@link SessionEvents.AssistantFullProfileAnswered} confirmed applies it. Re-applying Full while
 *       it is already in force turns nothing on, and asks nothing.</li>
 *   <li><b>The one status line</b> when no assistant can answer from the panel (owner, 2026-10-18): nothing chosen, or
 *       the in-app assistant with no key.</li>
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
    /** Owner, 2026-10-18: the one line while nothing is chosen and the cards are open. */
    public static final String NO_ASSISTANT_CHOOSING = "No assistant set up yet — choose one below.";
    /** Robot walkthrough #1: the line while a question waits for the choice. */
    public static final String QUESTION_WAITS = "No assistant set up yet — choose one below; your question waits and is "
            + "placed for the assistant you choose (nothing is sent).";
    /** The one line while nothing is chosen and the cards are shut. */
    public static final String NO_ASSISTANT = "No assistant set up yet — choose one with Assistant ▾ above.";
    /** The one line for the in-app assistant with no key (or no provider that can answer). */
    public static final String IN_APP_NO_KEY = "The in-app assistant has no API key yet, so nothing is sent — add one with "
            + "Assistant ▾ above or in Settings ▸ Assistant.";

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
    /** Review PR151 F4: the Apply request whose Full Claude Code confirmation is showing; 0 for none. */
    private long confirmingFull;
    /** What the frame last said the in-app assistant can do (StartPageAvailability), once it has said it. */
    private boolean inAppCanAnswer;
    private boolean availabilityObserved;
    /**
     * Robot walkthrough #1 (2026-10-18): a question asked while no assistant is chosen (by reference into the frame's
     * store; never its words), held unbound until the person applies a profile, then placed for that profile's assistant.
     */
    private long pendingDraft;
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

    /** Whether the in-app assistant can answer — only for the one status line; nothing else is decided from it. */
    @OnEventHandler
    public boolean onAvailability(SessionEvents.StartPageAvailability e) {
        if (availabilityObserved && e.inAppCanAnswer() == inAppCanAnswer) return false;
        availabilityObserved = true;
        inAppCanAnswer = e.inAppCanAnswer();
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
        confirmingFull = 0;
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
        if (p == AssistantProfile.FULL && !AssistantProfile.FULL.name().equals(profile)) {
            // review PR151 F4: the same consent Settings asks for — nothing is applied until it is confirmed
            confirmingFull = e.request();
            auditLog.info("assistantProfileConfirm", p.name()).info("request", e.request());
            effects.request(new SessionEffects.ConfirmFullProfileEffect(0L, e.request()));
            return changed();
        }
        auditLog.info("assistantProfileApply", p.name()).info("request", e.request());
        effects.request(new SessionEffects.ApplyAssistantProfileEffect(0L, e.request(), p));
        return changed();
    }

    /**
     * Review PR151 F4: the person answered Full Claude Code's confirmation. Confirmed: applied, if it still may be now
     * (a turn may have begun while the warning was read); otherwise nothing changes and the cards stay open.
     */
    @OnEventHandler
    public boolean onFullProfileAnswered(SessionEvents.AssistantFullProfileAnswered e) {
        if (confirmingFull == 0 || e.request() != confirmingFull) {
            auditLog.info("staleFact", "AssistantFullProfileAnswered").info("request", e.request()).info("open",
                    confirmingFull);
            return false;
        }
        confirmingFull = 0;
        if (!e.confirmed() || !open) {
            auditLog.info("assistantProfileConfirm", "CANCELLED").info("request", e.request());
            return changed();
        }
        refusal = localAgent.turnInProgress() ? BUSY_LOCAL_AGENT_TEXT : assistantLoop.turnInProgress() ? BUSY_IN_APP_TEXT : "";
        if (!refusal.isEmpty()) {
            auditLog.info("assistantProfileRefused", AssistantProfile.FULL.name()).info("request", e.request());
            return changed();
        }
        auditLog.info("assistantProfileApply", AssistantProfile.FULL.name()).info("request", e.request())
                .info("confirmed", true);
        effects.request(new SessionEffects.ApplyAssistantProfileEffect(0L, e.request(), AssistantProfile.FULL));
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
            if (pendingDraft != 0) {
                // robot walkthrough #1: the question that waited is placed for the assistant just chosen — after the
                // mode, so that assistant's node binds it (it was never bound to the default the mode had before)
                auditLog.info("assistantPendingDraft", "PLACED").info("mode", e.profile().mode().name());
                effects.request(new SessionEffects.PlacePendingDraftEffect(0L, pendingDraft, e.profile().mode()));
                pendingDraft = 0;
            }
        }
        return changed();
    }

    /**
     * Robot walkthrough #1: a question composed while no assistant is chosen. If the in-app assistant can already answer
     * (a key stored before profiles existed), it is placed for it now; otherwise it waits, unbound — a second question
     * replaces the first — and the choices open, as a first question's Send opens them.
     */
    @OnEventHandler
    public boolean onDraftPending(SessionEvents.AssistantDraftPending e) {
        if (observed && !AssistantProfile.NONE.name().equals(profile) || availabilityObserved && inAppCanAnswer) {
            auditLog.info("assistantPendingDraft", "PLACED_NOW").info("question", String.valueOf(e.questionId()));
            effects.request(new SessionEffects.PlacePendingDraftEffect(0L, e.draftRef(),
                    AssistantProfile.valueOf(profile).mode()));
            return false;
        }
        pendingDraft = e.draftRef();
        auditLog.info("assistantPendingDraft", "HELD").info("question", String.valueOf(e.questionId()))
                .info("origin", String.valueOf(e.origin()));
        if (!open) {
            open = true;
            why = offered ? ProfileState.ASKED : ProfileState.FIRST_USE;
            offered = true;
            refusal = "";
            effects.request(new SessionEffects.ShowAssistantProfilesEffect(0L, false));
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
     * up, open a bundle, the templates, open a log, build, teach) work with no AI and keep their own fallbacks.
     */
    @OnEventHandler
    public boolean onStartPageSent(SessionEvents.StartPagePromptSent e) {
        String id = e.entryId() == null ? "NONE" : e.entryId();
        // a flow entry (the catalogue's kind, so Build and Teach count too) is not a question
        boolean question = id.equals("NONE") ? e.textRef() != 0
                : telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.byId(id)
                        .map(x -> x.kind() != telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Kind.FLOW)
                        .orElse(true);
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

    /** The one status line (owner, 2026-10-18): only once the frame has reported the profile. */
    private String status() {
        if (!observed) return "";
        boolean inAppCannot = availabilityObserved && !inAppCanAnswer;
        if (AssistantProfile.NONE.name().equals(profile)) {
            // a key stored before profiles existed still answers: then something IS set up
            if (availabilityObserved && inAppCanAnswer) return "";
            if (pendingDraft != 0) return QUESTION_WAITS;
            return open ? NO_ASSISTANT_CHOOSING : NO_ASSISTANT;
        }
        if (AssistantProfile.IN_APP.name().equals(profile) && inAppCannot) return IN_APP_NO_KEY;
        return "";
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    /** The published state — immutable, for the snapshot, rebuilt only after a change. */
    public ProfileState state() {
        if (dirty) {
            published = new ProfileState(AssistantProfile.valueOf(profile), open, why, request, refusal, offered, status(),
                    confirmingFull != 0, observed, pendingDraft != 0);
            dirty = false;
        }
        return published;
    }
}
