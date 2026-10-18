package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupDerived;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Origin;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileOrigin;

/**
 * M77 (spec-ai-setup-wizard.md §8.1, OD-77.13 — one node): <i>Set up AI</i> and the assistant profile, in ONE place
 * (rule 9). It grew out of M74's {@code AssistantProfileChoice} (the profile cards), because every decision the wizard
 * needs — "nothing set up", the first-run block, the derived choice, the AI line, a refused mid-turn Apply, Safe
 * re-selected after a cancelled Full — needs the profile in force and the Apply/confirm outcomes, and a node cannot read
 * its child within a cycle.
 *
 * <p><b>Kept from the cards.</b> The observed profile; what the in-app assistant can do; the Apply decision with its
 * mid-turn refusals; Full's confirmation; the applied result, which asks for the mode; the first-use handlers; the
 * start-page interception; {@link ProfileState} in the snapshot (the header chip renders it).
 *
 * <p><b>Added.</b> The wizard's steps and choice ({@link AiSetupState}); the two checks — {@code claude --version} and
 * {@code claude auth status --json} — asked for as effects and reported back as facts, each with its probe id so a late
 * result is stale; the first-run block and the AI line; the two machine-tier keys.
 *
 * <p><b>The first use</b> opens the wizard instead of the cards once {@link #wizardEntry} is on — the one switch between
 * M77-1 (the node and the checks, the window not yet reachable) and M77-2 (the wizard).
 *
 * <p><b>Order.</b> Downstream of {@code localAgentSession} and {@code assistantLoop} (it reads whether a turn is open
 * after they handled the fact), and of {@code actionListener} and {@code openLog} (JavaBean parents); upstream of
 * {@code startPagePrompt}, which reads {@link #interceptedStartPage} and {@link #finished}.
 *
 * <p><b>What it never holds.</b> A key, a model the person typed, a path, raw CLI output, an e-mail or an organisation.
 */
public class AiSetup implements EventLogSource {

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
    /**
     * The one line for the in-app assistant with no key (or no provider that can answer). Robot re-walk #4 (2026-10-18):
     * the panel shows it with Configure provider beside it — "No API key — Configure provider" — and Send disabled; it
     * no longer sits under a "Ready" with "Press Send to ask Claude" below it.
     */
    public static final String IN_APP_NO_KEY = "No API key";

    /** The start page's Build entry (the catalogue's S7): an authoring question, which only Full Claude Code can answer. */
    public static final String BUILD_ENTRY = "S7";

    /** The start page's note when its question opened the choices instead of an answer. */
    public static final String FIRST_USE_NOTE = StartPagePrompt.CARRIED_NOTE;

    /**
     * The switch between M77-1 and M77-2 (spec §18): whether the first use — a Send, a Start, a start-page question or a
     * record's Ask with nothing set up — opens the Set up AI wizard ({@code true}) or the profile cards ({@code false}).
     * Read once per decision; tested both ways. M77-2: on.
     */
    private static volatile boolean wizardEntry = true;

    /** For the tests, and the one line M77-2 changes: whether the first use opens the wizard. */
    public static void useWizardForFirstUse(boolean on) {
        wizardEntry = on;
    }

    public static boolean wizardForFirstUse() {
        return wizardEntry;
    }

    private final LocalAgentSession localAgent;
    private final AssistantLoop assistantLoop;
    @PushReference
    private final EffectQueue effects;

    /** M77: the action listener (is external access on?) — a JavaBean parent, read for the AI line only. */
    private ActionListener actionListener;
    /** M77: the open log — a JavaBean parent (the Summary's suggested action, M77-2). */
    private OpenLog openLog;

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
    /** M77: which surface asked for the Full confirmation that is showing (the guard reads that surface). */
    private ProfileOrigin confirmingOrigin = ProfileOrigin.CARDS;
    /** What the frame last said the in-app assistant can do (StartPageAvailability), once it has said it. */
    private boolean inAppCanAnswer;
    private boolean availabilityObserved;
    /**
     * Robot walkthrough #1 (2026-10-18): a question asked while no assistant is chosen (by reference into the frame's
     * store; never its words), held unbound until the person applies a profile, then placed for that profile's assistant.
     */
    private long pendingDraft;

    // ---- M77: the wizard ------------------------------------------------------------------------------------------------
    private boolean wizardOpen;
    private boolean startupObserved;
    private boolean firstRunDismissed;
    private boolean restLaunch;
    private AiSetupState.Client setupClient = AiSetupState.Client.NONE;
    private boolean claudeFound;
    private boolean codexFound;
    private Origin origin = Origin.NONE;
    private Step step = Step.CHOICE;
    private Choice choice = Choice.NONE;
    private Posture posture = Posture.SAFE;
    private String fullMode = "default";
    private AiSetupState.Check installed = AiSetupState.Check.UNCHECKED;
    private AiSetupState.Auth signedIn = AiSetupState.Auth.UNCHECKED;
    private long probeInstalled;
    private long probeAuth;
    private long nextProbe;
    /** The Apply request this wizard asked for (negative ids: never one a card or the menu minted); 0 for none. */
    private long wizardRequest;
    private long nextWizardRequest;
    private boolean applying;

    /**
     * Robot re-walk #3: the hold of an evidence question asked with no log open — a JavaBean parent, upstream, so neither
     * the wizard nor the cards open for a press it holds. Null only in a processor generated before it existed.
     */
    private StartPageNoLogHint noLogHint;

    public StartPageNoLogHint getNoLogHint() {
        return noLogHint;
    }

    public void setNoLogHint(StartPageNoLogHint noLogHint) {
        this.noLogHint = noLogHint;
    }
    private long finished;
    private boolean startBoxWaiting;
    /**
     * Authoring walk (2026-10-18): the start-box question waiting behind the wizard is a Build one (S7) — the wizard
     * preselects Claude inside with Full Claude Code (its confirmation still asked) and says why Safe cannot do it.
     */
    private boolean authoringWaiting;
    /** The wizard's full mode is Build's preselection (acceptEdits), not one the person chose. */
    private boolean buildModePreselected;
    private String line = "";

    private ProfileState published = ProfileState.NONE;
    private boolean dirty = true;

    public AiSetup(LocalAgentSession localAgent, AssistantLoop assistantLoop, EffectQueue effects) {
        this.localAgent = localAgent;
        this.assistantLoop = assistantLoop;
        this.effects = effects;
    }

    public ActionListener getActionListener() {
        return actionListener;
    }

    public void setActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public OpenLog getOpenLog() {
        return openLog;
    }

    public void setOpenLog(OpenLog openLog) {
        this.openLog = openLog;
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

    /** What can answer, and whether the resolver found {@code claude} (a file check; nothing run). */
    @OnEventHandler
    public boolean onAvailability(SessionEvents.StartPageAvailability e) {
        if (availabilityObserved && e.inAppCanAnswer() == inAppCanAnswer && e.claudeCodeFound() == claudeFound) {
            return false;
        }
        availabilityObserved = true;
        inAppCanAnswer = e.inAppCanAnswer();
        claudeFound = e.claudeCodeFound();
        // §6.0: the first-run block preselects Claude inside when claude was found (a file check; Continue only checks)
        if (!wizardOpen && choice == Choice.NONE && claudeFound) choice = Choice.INSIDE;
        return changed();
    }

    @OnEventHandler
    public boolean onClientsObserved(SessionEvents.AiClientsObserved e) {
        if (e.codexFound() == codexFound) return false;
        codexFound = e.codexFound();
        return changed();
    }

    /** M77: the two machine-tier keys and whether {@code --rest} started this process — once, at start-up. */
    @OnEventHandler
    public boolean onStartupObserved(SessionEvents.AiSetupStartupObserved e) {
        startupObserved = true;
        firstRunDismissed = e.dismissed();
        setupClient = e.client() == null ? AiSetupState.Client.NONE : e.client();
        restLaunch = e.restLaunch();
        auditLog.info("aiSetupStartup", setupClient.name()).info("dismissed", firstRunDismissed)
                .info("restLaunch", restLaunch);
        return changed();
    }

    /** The listener's state changed (external access on or off): the AI line follows it. */
    @OnEventHandler
    public boolean onRestSwitch(SessionEvents.RestSwitchObserved e) {
        return changed();
    }

    // ---- the person's acts: the profile cards (kept until M77-4) --------------------------------------------------------

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
        if (confirmingOrigin == ProfileOrigin.CARDS) confirmingFull = 0;
        auditLog.info("assistantProfiles", "CLOSED").info("origin", String.valueOf(e.origin()));
        return changed();
    }

    /** Apply: never mid-turn (refused by name, the surface stays open); otherwise the settings are written by effect. */
    @OnEventHandler
    public boolean onProfileChosen(SessionEvents.AssistantProfileChosen e) {
        AssistantProfile p = e.profile() == null ? AssistantProfile.NONE : e.profile();
        request = e.request();
        if (p == AssistantProfile.NONE) {
            refusal = "choose how the assistant works first";
            auditLog.info("assistantProfileRefused", "NONE");
            return changed();
        }
        return apply(e.request(), p, e.origin());
    }

    /** The one Apply decision, for every surface: a refusal mid-turn, Full's confirmation, or the write. */
    private boolean apply(long req, AssistantProfile p, ProfileOrigin from) {
        request = req;
        String busy = busy();
        if (from == ProfileOrigin.SETUP) {
            line = busy;
        } else {
            refusal = busy;
        }
        if (!busy.isEmpty()) {
            auditLog.info("assistantProfileRefused", p.name()).info("request", req).info("origin", from.name());
            if (from == ProfileOrigin.SETUP) applying = false;
            return changed();
        }
        if (p == AssistantProfile.FULL && !AssistantProfile.FULL.name().equals(profile)) {
            // review PR151 F4: the same consent Settings asks for — nothing is applied until it is confirmed
            confirmingFull = req;
            confirmingOrigin = from;
            auditLog.info("assistantProfileConfirm", p.name()).info("request", req).info("origin", from.name());
            effects.request(new SessionEffects.ConfirmFullProfileEffect(0L, req));
            return changed();
        }
        auditLog.info("assistantProfileApply", p.name()).info("request", req).info("origin", from.name());
        effects.request(new SessionEffects.ApplyAssistantProfileEffect(0L, req, p, from, modeFor(p, from)));
        return changed();
    }

    private String busy() {
        if (localAgent.turnInProgress()) return BUSY_LOCAL_AGENT_TEXT;
        if (assistantLoop.turnInProgress()) return BUSY_IN_APP_TEXT;
        return "";
    }

    /**
     * Review PR151 F4: the person answered Full Claude Code's confirmation. Confirmed: applied, if the surface that asked
     * is still open (M77, ★ T4: the wizard's {@code open} for a wizard's Full, the cards' for theirs, nothing for the
     * quick switch) and it still may be now (a turn may have begun while the warning was read).
     */
    @OnEventHandler
    public boolean onFullProfileAnswered(SessionEvents.AssistantFullProfileAnswered e) {
        if (confirmingFull == 0 || e.request() != confirmingFull) {
            auditLog.info("staleFact", "AssistantFullProfileAnswered").info("request", e.request()).info("open",
                    confirmingFull);
            return false;
        }
        ProfileOrigin from = confirmingOrigin;
        confirmingFull = 0;
        boolean surfaceOpen = switch (from) {
            case SETUP -> wizardOpen;
            case CARDS -> open;
            case MENU, SETTINGS -> true;
        };
        if (!e.confirmed() || !surfaceOpen) {
            auditLog.info("assistantProfileConfirm", "CANCELLED").info("request", e.request()).info("origin", from.name());
            if (from == ProfileOrigin.SETUP) {
                // §6.2: Cancel returns to Screen 2a with Safe analysis re-selected and nothing applied; the step stays
                applying = false;
                if (wizardOpen && posture != Posture.SAFE) {
                    posture = Posture.SAFE;
                    startInstalledProbe();            // the probes' environment follows the sub-choice (R1-15)
                }
            }
            return changed();
        }
        String busy = busy();
        if (from == ProfileOrigin.SETUP) line = busy;
        else refusal = busy;
        if (!busy.isEmpty()) {
            auditLog.info("assistantProfileRefused", AssistantProfile.FULL.name()).info("request", e.request());
            if (from == ProfileOrigin.SETUP) applying = false;
            return changed();
        }
        auditLog.info("assistantProfileApply", AssistantProfile.FULL.name()).info("request", e.request())
                .info("confirmed", true);
        effects.request(new SessionEffects.ApplyAssistantProfileEffect(0L, e.request(), AssistantProfile.FULL, from,
                modeFor(AssistantProfile.FULL, from)));
        return changed();
    }

    /** The settings were written: the surface closes; the profile in force follows the observation that came with it. */
    @OnEventHandler
    public boolean onProfileApplied(SessionEvents.AssistantProfileApplied e) {
        if (e.request() != request) {
            auditLog.info("staleFact", "AssistantProfileApplied").info("request", e.request()).info("open", request);
            return false;
        }
        boolean fromWizard = e.origin() == ProfileOrigin.SETUP && e.request() == wizardRequest;
        if (!e.ok()) {
            String reason = e.reason() == null || e.reason().isBlank() ? "the settings could not be written" : e.reason();
            if (fromWizard) {
                line = reason;
                applying = false;
            } else {
                refusal = reason;
            }
            auditLog.info("assistantProfileApplied", "FAILED");
            return changed();
        }
        if (e.profile() != null) profile = e.profile().name();
        if (!fromWizard) {
            open = false;
            why = "";
            refusal = "";
        }
        auditLog.info("assistantProfileApplied", profile).info("origin", e.origin().name());
        // review P3+UX S4: the tab follows the profile — decided here, after the settings were written, never by the
        // performer of the write
        if (e.profile() != null && e.profile() != AssistantProfile.NONE) {
            effects.request(new SessionEffects.ChooseAssistantModeEffect(0L, e.profile().mode(), "assistant profile"));
        }
        if (fromWizard) {
            applying = false;
            finishWizard(e.profile());
            return changed();
        }
        placeOrReturnPending(e.profile());
        return changed();
    }

    /**
     * Robot walkthrough #1: the question that waited is placed for the assistant just chosen — after the mode, so that
     * assistant's node binds it (it was never bound to the default the mode had before). A profile that cannot answer
     * from the panel (Off) places nothing: the draft is returned with one line.
     */
    private void placeOrReturnPending(AssistantProfile applied) {
        if (pendingDraft == 0) return;
        if (applied == null || applied == AssistantProfile.NONE || applied == AssistantProfile.OFF) {
            auditLog.info("assistantPendingDraft", "RETURNED").info("profile", String.valueOf(applied));
            line = AiSetupLines.CANNOT_ANSWER_ASK;
            pendingDraft = 0;
            return;
        }
        auditLog.info("assistantPendingDraft", "PLACED").info("mode", applied.mode().name());
        effects.request(new SessionEffects.PlacePendingDraftEffect(0L, pendingDraft, applied.mode()));
        pendingDraft = 0;
    }

    /**
     * Robot walkthrough #1: a question composed while no assistant is chosen. If the in-app assistant can already answer
     * (a key stored before profiles existed), it is placed for it now; otherwise it waits, unbound — a second question
     * replaces the first — and the choices open (the wizard, M77; the cards before it), as a first Send opens them.
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
        if (wizardEntry) {
            openWizard(Origin.ASK, null, Step.CHOICE);
            return changed();
        }
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

    // ---- the first use: Set up AI (M77-2) or the cards (M77-1), while nothing is set up --------------------------------

    /**
     * The first Start: only when {@code localAgentSession} — which handled it first — did not accept it. An assistant that
     * can answer is never interrupted, whatever the last observation said.
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
        // robot re-walk #3: held for want of a log — nothing opens for it until the person acts on the page's line
        if (noLogHint != null && noLogHint.holds(e.request())) return false;
        String id = e.entryId() == null ? "NONE" : e.entryId();
        // a flow entry (the catalogue's kind, so Teach counts too) is not a question. Authoring walk (2026-10-18, shot
        // 03): Build (S7) IS one — with nothing set up it ended in a duplicate "No AI…" line and two template buttons,
        // a dead end for the pathway that needs an assistant most; it opens the same first-use door a Send does. Only
        // the wizard's door: the cards (kept until M77-4) have no Full recommendation, so there Build stays a flow
        boolean build = wizardEntry && id.equals(BUILD_ENTRY);
        boolean question = build || (id.equals("NONE") ? e.textRef() != 0
                : telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.byId(id)
                        .map(x -> x.kind() != telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Kind.FLOW)
                        .orElse(true));
        if (!question) return false;
        boolean canAnswer = e.inAppCanAnswer() || (e.consent() != null && e.consent().allowed() && e.claudeCodeFound());
        if (canAnswer) return false;                  // an assistant can answer it: the question goes there
        boolean wasOpen = wizardOpen;
        if (wizardEntry && !wasOpen) authoringWaiting = build;
        if (!firstUse("start page", !e.workspaceOpen())) {
            if (!wasOpen) authoringWaiting = false;
            return false;
        }
        interceptedStartPage = e.request();
        startBoxWaiting = wizardEntry;
        return true;
    }

    /**
     * Nothing is set up (§5): the profile observed NONE and no stored in-app key that can answer. Under the wizard, every
     * press reopens it (no once-per-session latch — the latch is what made the cards' second press silent); under the
     * cards, once per session as M74 decided.
     */
    private boolean firstUse(String from, boolean popOut) {
        if (!observed || !AssistantProfile.NONE.name().equals(profile)) return false;
        if (wizardEntry) {
            if (availabilityObserved && inAppCanAnswer) return false;
            auditLog.info("aiSetup", "FIRST_USE").info("origin", from);
            openWizard(Origin.SEND, null, Step.CHOICE);
            return changed();
        }
        if (offered) return false;
        offered = true;
        open = true;
        why = ProfileState.FIRST_USE;
        refusal = "";
        auditLog.info("assistantProfiles", "FIRST_USE").info("origin", from);
        effects.request(new SessionEffects.ShowAssistantProfilesEffect(0L, popOut));
        return changed();
    }

    // ---- M77: the wizard ------------------------------------------------------------------------------------------------

    /** The person asked for Set up AI (a menu, the start page's link, Settings): Screen 1, the choice in force selected. */
    @OnEventHandler
    public boolean onSetupRequested(SessionEvents.AiSetupRequested e) {
        openWizard(e.origin() == null ? Origin.MENU : e.origin(), null, Step.CHOICE);
        return changed();
    }

    /** Opens the one window at Screen 1 — or, when it is open, brings it to the front at its current step. */
    private void openWizard(Origin why, Choice preselected, Step at) {
        if (wizardOpen) {
            auditLog.info("aiSetup", "FRONT").info("step", step.name());
            effects.request(new SessionEffects.ShowAiSetupEffect(0L, step));
            return;
        }
        wizardOpen = true;
        origin = why;
        step = at;
        Choice inForce = inForce();
        choice = preselected != null ? preselected
                : inForce != Choice.NONE ? inForce : claudeFound || authoringWaiting ? Choice.INSIDE : Choice.NONE;
        posture = AiSetupDerived.postureOf(AssistantProfile.valueOf(profile));
        // a Build question waits: only Full Claude Code can write code, so it is the sub-choice preselected — its
        // confirmation is still asked at Finish (apply's ConfirmFullProfileEffect), nothing is applied before it.
        // Owner follow-up (2026-10-18): in Claude Code's own acceptEdits mode, so an authoring run does not ask for every
        // edit (the live walk: 14 approvals under default); commands still ask, and the person may pick default
        if (authoringWaiting) {
            posture = Posture.FULL;
            fullMode = AiSetupLines.BUILD_FULL_MODE;
            buildModePreselected = true;
        } else if (buildModePreselected) {
            fullMode = "default";                     // a plain question never inherits Build's preselection
            buildModePreselected = false;
        }
        installed = AiSetupState.Check.UNCHECKED;
        signedIn = AiSetupState.Auth.UNCHECKED;
        probeInstalled = 0;
        probeAuth = 0;
        finished = 0;
        applying = false;
        line = "";
        auditLog.info("aiSetup", "OPEN").info("origin", why.name()).info("choice", choice.name());
        // Screen 1's badges: file existence only, no process (S2, ★ T6)
        effects.request(new SessionEffects.ObserveAiClientsEffect(0L));
        effects.request(new SessionEffects.ShowAiSetupEffect(0L, at));
    }

    @OnEventHandler
    public boolean onChoiceMade(SessionEvents.AiSetupChoiceMade e) {
        if (wizardOpen && step != Step.CHOICE || !wizardOpen && !firstRun()) {
            auditLog.info("staleFact", "AiSetupChoiceMade").info("step", step.name());
            return false;
        }
        Choice next = e.choice() == null ? Choice.NONE : e.choice();
        if (next == choice) return false;
        choice = next;
        line = "";
        auditLog.info("aiSetupChoice", choice.name());
        return changed();
    }

    @OnEventHandler
    public boolean onPostureChosen(SessionEvents.AiSetupPostureChosen e) {
        if (!wizardOpen || step != Step.INSIDE || applying) {
            auditLog.info("staleFact", "AiSetupPostureChosen");
            return false;
        }
        Posture next = e.posture() == null ? Posture.SAFE : e.posture();
        String mode = e.fullMode() == null || e.fullMode().isBlank() ? fullMode : e.fullMode();
        boolean postureChanged = next != posture;
        if (!postureChanged && mode.equals(fullMode)) return false;
        posture = next;
        if (!mode.equals(fullMode)) buildModePreselected = false;     // the person's own choice from now on
        fullMode = mode;
        auditLog.info("aiSetupPosture", posture.name());
        if (postureChanged) startInstalledProbe();     // the probes' environment follows the sub-choice (R1-15)
        return changed();
    }

    /** Continue (or Finish) on {@code step}: the next screen, or the Apply the screen stands for (§7.1, §7.2). */
    @OnEventHandler
    public boolean onContinue(SessionEvents.AiSetupContinue e) {
        boolean fromFirstRun = !wizardOpen && e.step() == Step.CHOICE && firstRun();
        if (!fromFirstRun && (!wizardOpen || e.step() != step) || applying || confirmingFull != 0) {
            auditLog.info("staleFact", "AiSetupContinue").info("step", String.valueOf(e.step()));
            return false;
        }
        switch (step == null || !wizardOpen ? Step.CHOICE : step) {
            case CHOICE -> {
                if (choice == Choice.NONE) return false;  // Continue is disabled until a choice is made
                if (fromFirstRun) {
                    // the block has done its job: the AI line takes its place from now on (OD-77.9: offered once)
                    firstRunDismissed = true;
                    effects.request(new SessionEffects.PersistSetupDismissedEffect(0L));
                }
                switch (choice) {
                    case NONE -> {
                        return false;
                    }
                    case NO_AI -> {
                        // nothing to guide: Off at once, and no external client is set up any more
                        if (fromFirstRun) origin = Origin.FIRST_RUN;
                        applyFromWizard(AssistantProfile.OFF);
                    }
                    case INSIDE, BOTH -> {
                        if (fromFirstRun) openWizard(Origin.FIRST_RUN, choice, Step.INSIDE);
                        step = Step.INSIDE;
                        startInstalledProbe();        // the person's Continue on the choice that needs it is the act
                    }
                    case CLIENT -> {
                        // M77-2's interim (review R1-11): today's dialog does it, until M77-3 builds Screen 2b
                        auditLog.info("aiSetup", "CLIENT_INTERIM");
                        effects.request(new SessionEffects.OpenClientSetupEffect(0L, AiSetupState.Client.CLAUDE_CODE));
                        closeWizard();
                        line = AiSetupLines.CLIENT_INTERIM;
                    }
                    case IN_APP -> {
                        if (fromFirstRun) openWizard(Origin.FIRST_RUN, choice, Step.OTHER);
                        step = Step.OTHER;
                    }
                }
            }
            case INSIDE -> {
                if (installed.verdict() != AiSetupState.Installed.FOUND) {
                    auditLog.info("aiSetupRefused", "NOT_INSTALLED");
                    return false;                     // the button is disabled; the node says no too
                }
                applyFromWizard(AiSetupDerived.profileFor(choice, posture));
            }
            case OTHER -> {
                if (!e.keyEntered() && !inAppCanAnswer) {
                    line = AiSetupLines.ADD_A_KEY;    // §6.5: a blank field with a stored key keeps the stored key
                    return changed();
                }
                applyFromWizard(AssistantProfile.IN_APP);
            }
            case SUMMARY -> closeWizard();
            case CLIENT -> {
                return false;                         // M77-3
            }
        }
        return changed();
    }

    /** The wizard's Apply: one request id of its own (negative: never one a card or the menu minted). */
    private void applyFromWizard(AssistantProfile p) {
        wizardRequest = -(++nextWizardRequest);
        applying = true;
        line = "";
        apply(wizardRequest, p, ProfileOrigin.SETUP);
    }

    /**
     * The wizard's Apply was written. A waiting question means no Summary (OD-77.2): the window closes and the question
     * is placed — after the mode, so the assistant now in force binds it (§12). Otherwise the Summary.
     */
    private void finishWizard(AssistantProfile applied) {
        finished = wizardRequest;
        if (choice == Choice.NO_AI) {
            setupClient = AiSetupState.Client.NONE;
            effects.request(new SessionEffects.PersistSetupClientEffect(0L, AiSetupState.Client.NONE));
        }
        if (choice == Choice.BOTH) {
            // M77-2's interim: the client half is today's dialog (review R1-11)
            effects.request(new SessionEffects.OpenClientSetupEffect(0L, AiSetupState.Client.CLAUDE_CODE));
        }
        boolean waiting = pendingDraft != 0 || startBoxWaiting;
        auditLog.info("aiSetupFinished", String.valueOf(applied)).info("waiting", waiting);
        if (waiting || !wizardOpen || choice == Choice.BOTH) {
            boolean placeable = applied != null && applied != AssistantProfile.OFF && applied != AssistantProfile.NONE;
            placeOrReturnPending(applied);
            closeWizard();
            line = waiting && placeable ? AiSetupLines.placedLine(applied) : line;
            startBoxWaiting = false;
            authoringWaiting = false;
            return;
        }
        step = Step.SUMMARY;
    }

    @OnEventHandler
    public boolean onBack(SessionEvents.AiSetupBack e) {
        if (!wizardOpen || e.step() != step || applying || confirmingFull != 0) {
            auditLog.info("staleFact", "AiSetupBack").info("step", String.valueOf(e.step()));
            return false;
        }
        Step back = switch (step) {
            case INSIDE, OTHER, CLIENT -> Step.CHOICE;
            case CHOICE, SUMMARY -> null;
        };
        if (back == null) return false;
        step = back;
        // Back undoes nothing; a check still running answers a screen that is no longer shown — stale
        probeInstalled = 0;
        probeAuth = 0;
        if (installed.verdict() == AiSetupState.Installed.RUNNING) installed = AiSetupState.Check.UNCHECKED;
        if (signedIn.verdict() == AiSetupState.SignedIn.RUNNING) signedIn = AiSetupState.Auth.UNCHECKED;
        line = "";
        return changed();
    }

    /**
     * Cancel, Escape or the window's close (§7.4): nothing further is applied; what was applied stays. A waiting record
     * question is returned with one line; a start-box question stays in the box ({@code startPagePrompt} says so).
     */
    @OnEventHandler
    public boolean onCancelled(SessionEvents.AiSetupCancelled e) {
        if (!wizardOpen) return false;
        boolean summary = step == Step.SUMMARY;
        closeWizard();
        if (pendingDraft != 0) {
            pendingDraft = 0;
            line = AiSetupLines.CANCELLED_ASK;
        } else {
            line = "";
        }
        startBoxWaiting = false;
        authoringWaiting = false;
        auditLog.info("aiSetup", summary ? "DONE" : "CANCELLED").info("origin", String.valueOf(e.origin()));
        return changed();
    }

    private void closeWizard() {
        wizardOpen = false;
        probeInstalled = 0;
        probeAuth = 0;
        applying = false;
    }

    /** §6.4: the Summary's suggested action — performed by the frame's existing actions; the window closes. */
    @OnEventHandler
    public boolean onSuggestedTaken(SessionEvents.AiSetupSuggestedTaken e) {
        AiSetupState.Suggested want = suggested();
        if (!wizardOpen || step != Step.SUMMARY || e.suggested() != want || want == AiSetupState.Suggested.NONE
                || want == AiSetupState.Suggested.RIGHT_CLICK) {
            auditLog.info("staleFact", "AiSetupSuggestedTaken").info("suggested", String.valueOf(e.suggested()));
            return false;
        }
        auditLog.info("aiSetupSuggested", want.name());
        effects.request(new SessionEffects.RunSuggestedActionEffect(0L, want));
        closeWizard();
        line = "";
        return changed();
    }

    /** §7.3: the suggested first action, from the choice in force and whether a log is open. */
    private AiSetupState.Suggested suggested() {
        if (!wizardOpen || step != Step.SUMMARY) return AiSetupState.Suggested.NONE;
        return switch (inForce()) {
            case INSIDE, BOTH, IN_APP -> openLog != null && openLog.isOpen() ? AiSetupState.Suggested.RIGHT_CLICK
                    : AiSetupState.Suggested.OPEN_SAMPLE;
            case NO_AI -> AiSetupState.Suggested.TOUR;
            case CLIENT, NONE -> AiSetupState.Suggested.NONE;
        };
    }

    @OnEventHandler
    public boolean onFirstRunDismissed(SessionEvents.AiSetupFirstRunDismissed e) {
        if (firstRunDismissed) return false;
        firstRunDismissed = true;
        auditLog.info("aiSetup", "FIRST_RUN_DISMISSED");
        effects.request(new SessionEffects.PersistSetupDismissedEffect(0L));
        return changed();
    }

    @OnEventHandler
    public boolean onCheckAgain(SessionEvents.AiSetupCheckAgain e) {
        if (!wizardOpen || step != Step.INSIDE || e.check() == null) return false;
        switch (e.check()) {
            case INSTALLED -> {
                if (installed.verdict() == AiSetupState.Installed.RUNNING) {
                    line = AiSetupLines.ALREADY_CHECKING;
                    return changed();
                }
                startInstalledProbe();
            }
            case SIGNED_IN -> {
                if (installed.verdict() != AiSetupState.Installed.FOUND) return false;
                if (signedIn.verdict() == AiSetupState.SignedIn.RUNNING) {
                    line = AiSetupLines.ALREADY_CHECKING;
                    return changed();
                }
                startAuthProbe();
            }
            default -> {
                return false;                         // M77-3's checks
            }
        }
        line = "";
        return changed();
    }

    private void startInstalledProbe() {
        probeInstalled = ++nextProbe;
        probeAuth = 0;
        installed = new AiSetupState.Check(AiSetupState.Installed.RUNNING, "", 0);
        signedIn = AiSetupState.Auth.UNCHECKED;
        auditLog.info("aiSetupProbe", "INSTALLED").info("probe", probeInstalled).info("full", posture == Posture.FULL);
        effects.request(new SessionEffects.ProbeClaudeEffect(0L, probeInstalled, posture == Posture.FULL));
    }

    private void startAuthProbe() {
        probeAuth = ++nextProbe;
        signedIn = new AiSetupState.Auth(AiSetupState.SignedIn.RUNNING, AiSetupState.Method.NONE,
                AiSetupState.Plan.NONE, AiSetupState.KeySource.NONE);
        auditLog.info("aiSetupProbe", "SIGNED_IN").info("probe", probeAuth).info("full", posture == Posture.FULL);
        effects.request(new SessionEffects.ProbeClaudeAuthEffect(0L, probeAuth, posture == Posture.FULL));
    }

    /** {@code claude --version}: for the current probe only; a pass starts the sign-in check (§6.2). */
    @OnEventHandler
    public boolean onClaudeObserved(SessionEvents.ClaudeObserved e) {
        if (!wizardOpen || e.probe() == 0 || e.probe() != probeInstalled) {
            auditLog.info("staleFact", "ClaudeObserved").info("probe", e.probe()).info("current", probeInstalled);
            return false;
        }
        probeInstalled = 0;
        AiSetupState.Installed v = switch (e.outcome() == null ? SessionEvents.ProbeOutcome.DID_NOT_RUN : e.outcome()) {
            case FOUND -> AiSetupState.Installed.FOUND;
            case NOT_FOUND -> AiSetupState.Installed.NOT_FOUND;
            case DID_NOT_RUN, TIMED_OUT -> AiSetupState.Installed.DID_NOT_RUN;
        };
        installed = new AiSetupState.Check(v, v == AiSetupState.Installed.FOUND ? e.version() : "", e.pathRef());
        if (v != AiSetupState.Installed.NOT_FOUND) claudeFound = true;
        auditLog.info("aiSetupInstalled", v.name());
        if (v == AiSetupState.Installed.FOUND) startAuthProbe();
        return changed();
    }

    /**
     * {@code claude auth status --json}, already parsed at the boundary. Never "signed in" for an API key or a key
     * helper: {@code auth status} does not validate a key (verified 2026-10-18, §9). This is an observation, never a gate.
     */
    @OnEventHandler
    public boolean onClaudeAuthObserved(SessionEvents.ClaudeAuthObserved e) {
        if (!wizardOpen || e.probe() == 0 || e.probe() != probeAuth) {
            auditLog.info("staleFact", "ClaudeAuthObserved").info("probe", e.probe()).info("current", probeAuth);
            return false;
        }
        probeAuth = 0;
        AiSetupState.Method method = e.method() == null ? AiSetupState.Method.NONE : e.method();
        AiSetupState.SignedIn v;
        if (e.outcome() != SessionEvents.AuthOutcome.READ) {
            v = AiSetupState.SignedIn.UNKNOWN;
        } else if (!e.loggedIn()) {
            v = AiSetupState.SignedIn.NOT_SIGNED_IN;
        } else {
            v = switch (method) {
                case CLAUDE_AI -> AiSetupState.SignedIn.SIGNED_IN;
                case API_KEY, API_KEY_HELPER -> AiSetupState.SignedIn.KEY;
                case OTHER, NONE -> AiSetupState.SignedIn.UNKNOWN;
            };
        }
        signedIn = new AiSetupState.Auth(v, method, e.plan(), e.keySource());
        auditLog.info("aiSetupSignedIn", v.name()).info("method", method.name());
        return changed();
    }

    // ---- what children read -----------------------------------------------------------------------------------------

    /** Whether this node answered start-page request {@code request} by opening the choices (read by startPagePrompt). */
    public boolean interceptedStartPage(long request) {
        return request != 0 && request == interceptedStartPage;
    }

    /** §12 (a): the Apply request this wizard's Finish was pressed for, or 0 — read by startPagePrompt for placement. */
    public long finished() {
        return finished;
    }

    /** Whether the wizard's window is open (read by startPagePrompt: a start-box question waits behind it). */
    public boolean wizardOpen() {
        return wizardOpen;
    }

    public AssistantProfile profile() {
        return AssistantProfile.valueOf(profile);
    }

    /** §14.1: the choice in force, derived from the profile in force and {@code assistant.setup.client}. */
    private Choice inForce() {
        if (!observed) return Choice.NONE;
        AssistantProfile p = AssistantProfile.valueOf(profile);
        // a key stored before profiles existed still answers: then something IS set up
        if (p == AssistantProfile.NONE && availabilityObserved && inAppCanAnswer) p = AssistantProfile.IN_APP;
        return AiSetupDerived.choice(p, setupClient);
    }

    /** §6.0: the first-run block — offered once, while nothing is set up, never to a process an agent started. */
    private boolean firstRun() {
        return wizardEntry && startupObserved && !firstRunDismissed && !restLaunch && observed
                && inForce() == Choice.NONE;
    }

    /** The one status line of the panel (owner, 2026-10-18): only once the frame has reported the profile. */
    private String status() {
        if (!observed) return "";
        boolean inAppCannot = availabilityObserved && !inAppCanAnswer;
        if (AssistantProfile.NONE.name().equals(profile)) {
            if (availabilityObserved && inAppCanAnswer) return "";
            if (wizardEntry) {
                // §6.6 (review R1-17): one owner, one line — the cards' words point at cards that are not the entry
                return pendingDraft != 0 ? AiSetupLines.QUESTION_WAITS : AiSetupLines.PANEL_NOTHING_SET_UP;
            }
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

    /** The full mode the wizard's Apply writes (its sub-choice); null for any other surface, which keeps its own. */
    private String modeFor(AssistantProfile p, ProfileOrigin from) {
        return p == AssistantProfile.FULL && from == ProfileOrigin.SETUP ? fullMode : null;
    }

    /** The wizard's one line: the node's own words, or — while a Build question waits on its first screens — why Full. */
    private String shownLine() {
        if (!line.isBlank() || !authoringWaiting || !wizardOpen) return line;
        return step == Step.CHOICE || step == Step.INSIDE ? AiSetupLines.BUILD_NEEDS_FULL : line;
    }

    /** The published profile state — immutable, for the snapshot, rebuilt only after a change. */
    public ProfileState state() {
        if (dirty) {
            published = new ProfileState(AssistantProfile.valueOf(profile), open, why, request, refusal, offered, status(),
                    confirmingFull != 0 && confirmingOrigin != ProfileOrigin.SETUP, observed, pendingDraft != 0);
            dirty = false;
        }
        return published;
    }

    /**
     * M77: the published Set up AI state — immutable, for the snapshot. Built on every read: the AI line follows the
     * action listener, a parent whose changes this node does not handle as facts.
     */
    public AiSetupState setupState() {
        Choice inForce = inForce();
        boolean external = actionListener != null && actionListener.state().external();
        AiSetupState.Pending pending = pendingDraft != 0
                ? new AiSetupState.Pending(AiSetupState.PendingKind.ASK_DRAFT, pendingDraft)
                : startBoxWaiting ? new AiSetupState.Pending(AiSetupState.PendingKind.START_BOX, interceptedStartPage)
                : AiSetupState.Pending.NONE;
        AssistantProfile p = AssistantProfile.valueOf(profile);
        return new AiSetupState(wizardOpen, firstRun(), origin, step, choice, inForce, posture, fullMode,
                installed, signedIn,
                confirmingFull != 0 && confirmingOrigin == ProfileOrigin.SETUP ? AiSetupState.Confirming.FULL
                        : AiSetupState.Confirming.NONE,
                pending, finished, shownLine(),
                observed ? AiSetupLines.aiLine(inForce, p, claudeFound, inAppCanAnswer, external) : "",
                observed ? AiSetupLines.aiLineLink(inForce) : "",
                wizardEntry ? status() : "", claudeFound, codexFound, wizardEntry, applying, suggested());
    }
}
