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
import telamin.fluxtion.audit.analyser.analyser.session.ListenerState;
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
 * start-page interception; {@link ProfileState} in the snapshot (the header chip and the quick switch render it).
 *
 * <p><b>Added.</b> The wizard's steps and choice ({@link AiSetupState}); the two checks — {@code claude --version} and
 * {@code claude auth status --json} — asked for as effects and reported back as facts, each with its probe id so a late
 * result is stale; the first-run block and the AI line; the two machine-tier keys.
 *
 * <p><b>M77-4: the cards are gone</b> (OD-77.3). The first use opens the wizard; the person's other door to the profile
 * is the Assistant ▾ quick switch, whose press is {@code AssistantProfileChosen(origin=MENU)} — decided here like every
 * Apply, and opening the wizard at the screen that can finish it when it cannot be applied as it stands (§14.3: Claude
 * inside while {@code claude} was not found; the in-app assistant with no stored key).
 *
 * <p><b>Order.</b> Downstream of {@code localAgentSession} and {@code assistantLoop} (it reads whether a turn is open
 * after they handled the fact), and of {@code actionListener} and {@code openLog} (JavaBean parents); upstream of
 * {@code startPagePrompt}, which reads {@link #interceptedStartPage} and {@link #finished}.
 *
 * <p><b>M77-3: Screen 2b, your own client.</b> Which client; whether external access is on (read from
 * {@code actionListener}, which alone decides turning it on or off, §9.2); the client's registration (checked and run
 * only on the person's explicit press, behind a confirmation that shows the exact command); and the live check — a watch
 * on the action server, armed only while part 4 is current and access is on, reporting the first external call once
 * (§9.4). Every probe, request and watch carries an id this node minted; a fact for an old one is stale.
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
    /**
     * The one line for the in-app assistant with no key (or no provider that can answer). Robot re-walk #4 (2026-10-18):
     * the panel shows it with Configure provider beside it — "No API key — Configure provider" — and Send disabled; it
     * no longer sits under a "Ready" with "Press Send to ask Claude" below it. M78 D2 (§4.1 item 26): it is the
     * conditional banner's sentence, "No API key — nothing is sent.", with Add key… beside it.
     */
    public static final String IN_APP_NO_KEY = "No API key — nothing is sent.";

    /** The start page's Build entry (the catalogue's S7): an authoring question, which only Full Claude Code can answer. */
    public static final String BUILD_ENTRY = "S7";

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
    private long request;
    /** The start-page request this node answered by opening the choices, so startPagePrompt shows no fallback for it. */
    private long interceptedStartPage;
    /** Review PR151 F4: the Apply request whose Full Claude Code confirmation is showing; 0 for none. */
    private long confirmingFull;
    /** M77: which surface asked for the Full confirmation that is showing (the guard reads that surface). */
    private ProfileOrigin confirmingOrigin = ProfileOrigin.MENU;
    /** What the frame last said the in-app assistant can do (StartPageAvailability), once it has said it. */
    private boolean inAppCanAnswer;
    /** M77-4: an in-app key is stored (it may not answer, under Off) — the quick switch's In-app needs only this. */
    private boolean inAppKeyStored;
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
    /** OD-77.9 (M77-4): the first-run block was offered in this run — recorded once, so the next run shows the AI line. */
    private boolean firstRunOffered;
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
    /** Owner decision 2026-10-18: the open Build-mode offer (0 for none), and the last id minted. */
    private long buildModeOffer;
    private long nextBuildModeOffer;
    /** The Apply request this wizard asked for (negative ids: never one a card or the menu minted); 0 for none. */
    private long wizardRequest;
    private long nextWizardRequest;
    private boolean applying;

    // ---- M77-3: Screen 2b ---------------------------------------------------------------------------------------------
    /** A supported launcher was found (this copy can be registered); reported with the clients' badges. */
    private boolean launcher = true;
    private AiSetupState.Client client = AiSetupState.Client.CLAUDE_CODE;
    private AiSetupState.Registration registration = AiSetupState.Registration.UNCHECKED;
    private long registrationProbe;
    private long registerRequest;
    private boolean confirmingRegister;
    private long watch;
    private AiSetupState.LiveVerdict liveVerdict = AiSetupState.LiveVerdict.IDLE;
    private long liveAt;
    private long liveLabelRef;
    private boolean liveFull;
    /** A Full session was running when the watch was armed, and the count of Full sessions then (§9.4). */
    private boolean fullAtArm;
    private long fullMark;
    private SessionEvents.McpReadiness readiness = SessionEvents.McpReadiness.OFF;
    /** The wizard's own turn-on request, while it waits for its answer; 0 for none. */
    private long wizardAccessRequest;
    /** OD-77.11: this wizard turned external access on (confirmed) since it opened. */
    private boolean accessTurnedOnHere;
    /** OD-77.11: the closing line offers Turn it off. */
    private boolean offerTurnOff;
    /** OD-77.5: the box under No AI — ticked by default (turning off is the safe direction). */
    private boolean alsoTurnOff = true;
    private long nextAccessRequest;
    /** Screen 2b's Check connection (usability fix, 2026-10-18): the bridge check's verdict and its probe (0: none). */
    private AiSetupState.Bridge bridge = AiSetupState.Bridge.UNCHECKED;
    private long bridgeProbe;
    /** Check connection was pressed with access off: the check runs once access is on and serving external clients. */
    private boolean bridgeAfterAccess;

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
        noteFirstRunOffered();
        return changed();
    }

    /** What can answer, and whether the resolver found {@code claude} (a file check; nothing run). */
    @OnEventHandler
    public boolean onAvailability(SessionEvents.StartPageAvailability e) {
        if (availabilityObserved && e.inAppCanAnswer() == inAppCanAnswer && e.claudeCodeFound() == claudeFound
                && e.inAppKeyStored() == inAppKeyStored) {
            return false;
        }
        availabilityObserved = true;
        inAppCanAnswer = e.inAppCanAnswer();
        inAppKeyStored = e.inAppKeyStored();
        claudeFound = e.claudeCodeFound();
        // §6.0: the first-run block preselects Claude inside when claude was found (a file check; Continue only checks)
        if (!wizardOpen && choice == Choice.NONE && claudeFound) choice = Choice.INSIDE;
        noteFirstRunOffered();
        return changed();
    }

    @OnEventHandler
    public boolean onClientsObserved(SessionEvents.AiClientsObserved e) {
        if (e.codexFound() == codexFound && e.launcher() == launcher) return false;
        codexFound = e.codexFound();
        launcher = e.launcher();
        if (wizardOpen && step == Step.CLIENT) {
            // §6.3: no supported launcher — parts 3 and 4 cannot be done; Finish without a client is offered
            if (!launcher) registration = AiSetupState.Registration.NO_LAUNCHER;
            else if (registration == AiSetupState.Registration.NO_LAUNCHER) registration = AiSetupState.Registration.UNCHECKED;
            rearm();
        }
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
        noteFirstRunOffered();
        return changed();
    }

    /**
     * OD-77.9 (M77-4): the first-run block is offered ONCE — to a new person and to an upgrader whose profile is still
     * NONE alike. The first time this node decides to show it, the offer is recorded (the machine-tier
     * {@code assistant.setup.dismissed}), so the next run shows the AI line; in this run the block stays until the
     * person answers it. It is not a modal, so nothing is lost: the AI line's Set up AI… is the same door.
     */
    private void noteFirstRunOffered() {
        if (firstRunOffered || !firstRun()) return;
        firstRunOffered = true;
        auditLog.info("aiSetup", "FIRST_RUN_OFFERED");
        effects.request(new SessionEffects.PersistSetupDismissedEffect(0L));
    }

    /** The listener's state changed (external access on or off): the AI line, part 2 and the live check follow it. */
    @OnEventHandler
    public boolean onRestSwitch(SessionEvents.RestSwitchObserved e) {
        if (!e.on() && offerTurnOff) {
            offerTurnOff = false;                      // OD-77.11: it is off now; the offer has done its job
            if (AiSetupLines.ACCESS_STILL_ON.equals(line)) line = "";
        }
        // a bridge verdict is about access as it was: off now, so "works" is no longer true
        if (!e.on() && bridge != AiSetupState.Bridge.CHECKING) bridge = AiSetupState.Bridge.UNCHECKED;
        rearm();
        return changed();
    }

    // ---- M77-3: external access, as actionListener decided it (§9.2) ---------------------------------------------------

    /** A door asked: the wizard remembers its own turn-on request, to know later whether IT turned access on. */
    @OnEventHandler
    public boolean onExternalAccessRequested(SessionEvents.ExternalAccessRequested e) {
        if (e.origin() == SessionEvents.AccessOrigin.SETUP && e.on() && wizardOpen) wizardAccessRequest = e.request();
        rearm();
        return changed();
    }

    @OnEventHandler
    public boolean onExternalAccessAnswered(SessionEvents.ExternalAccessAnswered e) {
        if (wizardAccessRequest != 0 && e.request() == wizardAccessRequest) {
            if (e.confirmed()) accessTurnedOnHere = true;
            wizardAccessRequest = 0;
        }
        // Check connection asked for access first: Cancel (or Escape) means the check does not run either
        if (!e.confirmed() && bridgeAfterAccess) {
            bridgeAfterAccess = false;
            auditLog.info("aiSetupBridge", "ACCESS_DECLINED");
        }
        rearm();
        return changed();
    }

    /** Review B2's choice to close external access while a Full session runs: the live check is disarmed (R1-18). */
    @OnEventHandler
    public boolean onExternalChosen(SessionEvents.ExternalAccessChosen e) {
        rearm();
        return changed();
    }

    /** The readiness poll: another window may own the client connection (§9.4 OTHER_INSTANCE). */
    @OnEventHandler
    public boolean onReadiness(SessionEvents.McpReadinessObserved e) {
        if (e.status() == readiness) return false;
        readiness = e.status();
        auditLog.info("mcpReadiness", readiness.name());
        rearm();
        return changed();
    }

    // ---- the person's acts: an Apply, from the wizard or the Assistant ▾ quick switch -----------------------------

    /**
     * Apply: never mid-turn (refused by name); otherwise the settings are written by effect. M77-4 (§14.3, review R1-24):
     * the quick switch's press is decided here, not by the menu — Claude inside while {@code claude} was not found (the
     * resolver's file check), or the in-app assistant with no stored key, cannot be applied as it stands, so the wizard
     * opens at the screen that can finish it instead.
     */
    @OnEventHandler
    public boolean onProfileChosen(SessionEvents.AssistantProfileChosen e) {
        AssistantProfile p = e.profile() == null ? AssistantProfile.NONE : e.profile();
        request = e.request();
        if (p == AssistantProfile.NONE) {
            line = AiSetupLines.CHOOSE_FIRST;
            auditLog.info("assistantProfileRefused", "NONE");
            return changed();
        }
        if (e.origin() == ProfileOrigin.MENU && !AssistantProfile.valueOf(profile).equals(p)) {
            if (p.claudeCode() && !claudeFound) {
                auditLog.info("aiSetup", "QUICK_SWITCH_NEEDS_CLAUDE").info("profile", p.name());
                openWizard(Origin.MENU, setupClient != AiSetupState.Client.NONE ? Choice.BOTH : Choice.INSIDE,
                        Step.INSIDE);
                if (wizardOpen && step == Step.INSIDE) {
                    posture = AiSetupDerived.postureOf(p);
                    startInstalledProbe();            // the person's press on Claude inside is the act (§6.2)
                }
                return changed();
            }
            if (p == AssistantProfile.IN_APP && !(availabilityObserved && inAppKeyStored)) {
                auditLog.info("aiSetup", "QUICK_SWITCH_NEEDS_KEY");
                openWizard(Origin.MENU, Choice.IN_APP, Step.OTHER);
                return changed();
            }
        }
        return apply(e.request(), p, e.origin());
    }

    /** The one Apply decision, for every surface: a refusal mid-turn, Full's confirmation, or the write. */
    private boolean apply(long req, AssistantProfile p, ProfileOrigin from) {
        request = req;
        String busy = busy();
        line = busy;                                  // the ONE line: the wizard's, or the status bar's for the menu
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
        line = busy;
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
            line = reason;
            if (fromWizard) applying = false;
            auditLog.info("assistantProfileApplied", "FAILED");
            return changed();
        }
        if (e.profile() != null) profile = e.profile().name();
        if (!fromWizard) line = "";
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
     * replaces the first — and the wizard opens, as a first Send opens it.
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
        openWizard(Origin.ASK, null, Step.CHOICE);
        return changed();
    }

    /** The mode the node asked for was chosen (the answer to {@code ChooseAssistantModeEffect}). */
    @OnEventHandler(propagate = false)
    public boolean onProfilesShown(SessionEvents.AssistantProfilesShown e) {
        auditLog.info("assistantModeChosen", e.opId());
        return false;
    }

    // ---- the first use: Set up AI, while nothing is set up ----------------------------------------------------------

    /**
     * The first Start: only when {@code localAgentSession} — which handled it first — did not accept it. An assistant that
     * can answer is never interrupted, whatever the last observation said.
     */
    @OnEventHandler
    public boolean onStartRequested(SessionEvents.LocalAgentStartRequested e) {
        if (accepted(localAgent.state().answer(), e.request())) return false;
        return firstUse("start");
    }

    @OnEventHandler
    public boolean onLocalAgentSend(SessionEvents.LocalAgentSendRequested e) {
        if (accepted(localAgent.state().answer(), e.request())) return false;
        return firstUse("claude code send");
    }

    @OnEventHandler
    public boolean onAssistantSend(SessionEvents.AssistantSendRequested e) {
        var a = assistantLoop.state().answer();
        if (a.request() == e.request() && a.accepted()) return false;
        return firstUse("in-app send");
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
        // the wizard's door (M77-4: the only one, the cards are gone)
        boolean build = id.equals(BUILD_ENTRY);
        boolean question = build || (id.equals("NONE") ? e.textRef() != 0
                : telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.byId(id)
                        .map(x -> x.kind() != telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue.Kind.FLOW)
                        .orElse(true));
        if (!question) return false;
        boolean canAnswer = e.inAppCanAnswer() || (e.consent() != null && e.consent().allowed() && e.claudeCodeFound());
        if (canAnswer) {
            // an assistant can answer it: the question goes there. Owner decision 2026-10-18: Build with Full Claude Code
            // ALREADY in force in default (the whole hands-on session ran so) is offered acceptEdits too — the person's
            // choice, never switched for them; Full is already confirmed, so the offer asks no second consent
            if (build && offersBuildMode(e.consent())) {
                buildModeOffer = ++nextBuildModeOffer;
                auditLog.info("aiSetupBuildMode", "OFFERED").info("offer", buildModeOffer);
                return changed();
            }
            return false;
        }
        boolean wasOpen = wizardOpen;
        if (!wasOpen) authoringWaiting = build;
        if (!firstUse("start page")) {
            if (!wasOpen) authoringWaiting = false;
            return false;
        }
        interceptedStartPage = e.request();
        startBoxWaiting = true;
        return true;
    }

    /** Full Claude Code is in force (the profile, and the consent the press carried) in {@code default}. */
    private boolean offersBuildMode(SessionEvents.LocalAgentConsent c) {
        return observed && AssistantProfile.FULL.name().equals(profile) && c != null && c.allowed() && c.full()
                && c.fullMode() == telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode.DEFAULT;
    }

    /**
     * Owner decision 2026-10-18: the person answered the Build-mode offer. Keep asking changes nothing. Switch applies
     * Full Claude Code with {@code acceptEdits} through the one Apply performer as a quick switch does (origin MENU), so
     * {@code localAgentSession} restarts a running session at once — in a new chat, because the mode is part of the
     * posture. Never mid-turn (refused by name, the offer kept); never when Full is no longer in force (that would skip
     * Full's confirmation); never {@code bypassPermissions}.
     */
    @OnEventHandler
    public boolean onBuildModeAnswered(SessionEvents.AiSetupBuildModeAnswered e) {
        if (buildModeOffer == 0 || e.offer() != buildModeOffer) {
            auditLog.info("staleFact", "AiSetupBuildModeAnswered").info("offer", e.offer()).info("open", buildModeOffer);
            return false;
        }
        if (!e.switchMode()) {
            buildModeOffer = 0;
            auditLog.info("aiSetupBuildMode", "KEPT").info("offer", e.offer());
            return changed();
        }
        if (!AssistantProfile.FULL.name().equals(profile)) {
            buildModeOffer = 0;
            auditLog.info("aiSetupBuildMode", "STALE_PROFILE").info("profile", profile);
            return changed();
        }
        String busy = busy();
        line = busy;
        if (!busy.isEmpty()) {
            auditLog.info("aiSetupBuildMode", "BUSY").info("offer", e.offer());
            return changed();                         // the offer stays: it can be answered when the turn ends
        }
        buildModeOffer = 0;
        long req = -(++nextWizardRequest);
        request = req;
        auditLog.info("aiSetupBuildMode", "SWITCH").info("offer", e.offer()).info("request", req);
        effects.request(new SessionEffects.ApplyAssistantProfileEffect(0L, req, AssistantProfile.FULL, ProfileOrigin.MENU,
                AiSetupLines.BUILD_FULL_MODE));
        return changed();
    }

    /**
     * Nothing is set up (§5): the profile observed NONE and no stored in-app key that can answer. Every press reopens the
     * wizard (no once-per-session latch — the latch is what made the cards' second press silent).
     */
    private boolean firstUse(String from) {
        if (!observed || !AssistantProfile.NONE.name().equals(profile)) return false;
        if (availabilityObserved && inAppCanAnswer) return false;
        auditLog.info("aiSetup", "FIRST_USE").info("origin", from);
        openWizard(Origin.SEND, null, Step.CHOICE);
        return changed();
    }

    // ---- M77: the wizard ------------------------------------------------------------------------------------------------

    /**
     * The person asked for Set up AI (a menu, the start page's link, Settings): Screen 1, the choice in force selected.
     * M77-4 (§14.4): a door about your own client ({@link Origin#CONNECT} — AI ▸ Set up AI…, the panel's Connect a CLI
     * assistant) opens at Screen 2b when the choice in force includes a client, and at Screen 1 otherwise.
     */
    @OnEventHandler
    public boolean onSetupRequested(SessionEvents.AiSetupRequested e) {
        Origin why = e.origin() == null ? Origin.MENU : e.origin();
        Choice inForce = inForce();
        if (!wizardOpen && why == Origin.ONBOARD_BUILD) {
            firstRunDismissed = true;
            openWizard(why, Choice.INSIDE, Step.INSIDE);
            startInstalledProbe();
            return changed();
        }
        if (!wizardOpen && why == Origin.ONBOARD_CLIENT) {
            firstRunDismissed = true;
            openWizard(why, inForce == Choice.INSIDE || inForce == Choice.BOTH ? Choice.BOTH : Choice.CLIENT, Step.CLIENT);
            enterClient();
            return changed();
        }
        if (why == Origin.CONNECT && !wizardOpen && (inForce == Choice.CLIENT || inForce == Choice.BOTH)) {
            openWizard(why, inForce, Step.CLIENT);
            enterClient();
            return changed();
        }
        openWizard(why, null, Step.CHOICE);
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
        // edit (the live walk: 14 approvals under default); command approvals follow the CLI's rules and mode
        if (authoringWaiting || why == Origin.ONBOARD_BUILD) {
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
        accessTurnedOnHere = false;
        offerTurnOff = false;
        wizardAccessRequest = 0;
        alsoTurnOff = true;
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
                    if (!firstRunOffered) effects.request(new SessionEffects.PersistSetupDismissedEffect(0L));
                }
                switch (choice) {
                    case NONE -> {
                        return false;
                    }
                    case NO_AI -> {
                        // nothing to guide: Off at once, and no external client is set up any more
                        if (fromFirstRun) origin = Origin.FIRST_RUN;
                        applyFromWizard(AssistantProfile.OFF);
                        // OD-77.5: the ticked box also turns external access off — asked of actionListener, as a door
                        if (alsoTurnOff && restOn()) {
                            auditLog.info("aiSetup", "ALSO_TURN_OFF_ACCESS");
                            effects.request(new SessionEffects.RequestExternalAccessEffect(0L, -(++nextAccessRequest),
                                    false));
                        }
                    }
                    case INSIDE, BOTH -> {
                        if (fromFirstRun) openWizard(Origin.FIRST_RUN, choice, Step.INSIDE);
                        step = Step.INSIDE;
                        startInstalledProbe();        // the person's Continue on the choice that needs it is the act
                    }
                    case CLIENT -> {
                        // M77-3: Screen 2b (it replaces M77-2's interim dialog)
                        if (fromFirstRun) openWizard(Origin.FIRST_RUN, choice, Step.CLIENT);
                        step = Step.CLIENT;
                        enterClient();
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
                // §6.3 Finish: enabled once part 2 is on (the live check is never a gate), or without a client when this
                // copy cannot be registered. §7.2: the client is remembered, and the panel follows the choice
                boolean noLauncher = registration == AiSetupState.Registration.NO_LAUNCHER;
                AiSetupState.Access a = access();
                if (!noLauncher && (a == AiSetupState.Access.OFF || a == AiSetupState.Access.CONFIRMING)
                        || confirmingRegister) {
                    auditLog.info("aiSetupRefused", "ACCESS_OFF");
                    return false;                     // the button is disabled; the node says no too
                }
                disarm();
                if (!noLauncher) {
                    setupClient = client;
                    effects.request(new SessionEffects.PersistSetupClientEffect(0L, client));
                }
                // Both re-applies 2a's profile (Full in force asks nothing again), so the question waits for THIS Finish
                applyFromWizard(choice == Choice.BOTH ? AiSetupDerived.profileFor(Choice.BOTH, posture)
                        : AssistantProfile.OFF);
            }
        }
        return changed();
    }

    /** Screen 2b opens: the client set up before (or the one found), its registration not yet checked, no watch. */
    private void enterClient() {
        client = setupClient != AiSetupState.Client.NONE ? setupClient
                : claudeFound ? AiSetupState.Client.CLAUDE_CODE
                : codexFound ? AiSetupState.Client.CODEX : AiSetupState.Client.CLAUDE_CODE;
        registration = launcher ? AiSetupState.Registration.UNCHECKED : AiSetupState.Registration.NO_LAUNCHER;
        registrationProbe = 0;
        registerRequest = 0;
        confirmingRegister = false;
        liveVerdict = AiSetupState.LiveVerdict.IDLE;
        liveAt = 0;
        liveLabelRef = 0;
        liveFull = false;
        bridge = AiSetupState.Bridge.UNCHECKED;
        bridgeProbe = 0;
        bridgeAfterAccess = false;
        auditLog.info("aiSetup", "CLIENT").info("client", client.name()).info("launcher", launcher);
        rearm();
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
        if (choice == Choice.BOTH && step == Step.INSIDE) {
            // M77-3: Both — Claude inside is applied; Screen 2b next. Nothing is placed until 2b's Finish (§12)
            step = Step.CLIENT;
            enterClient();
            return;
        }
        finished = wizardRequest;
        if (choice == Choice.NO_AI) {
            setupClient = AiSetupState.Client.NONE;
            effects.request(new SessionEffects.PersistSetupClientEffect(0L, AiSetupState.Client.NONE));
        }
        boolean waiting = pendingDraft != 0 || startBoxWaiting;
        auditLog.info("aiSetupFinished", String.valueOf(applied)).info("waiting", waiting);
        if (waiting || !wizardOpen) {
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
            case INSIDE, OTHER -> Step.CHOICE;
            case CLIENT -> choice == Choice.BOTH ? Step.INSIDE : Step.CHOICE;   // one screen back; it undoes nothing
            case CHOICE, SUMMARY -> null;
        };
        if (back == null) return false;
        if (step == Step.CLIENT) {
            disarm();
            registrationProbe = 0;                    // a check still running answers a screen that is gone: stale
            forgetBridgeCheck();
            if (registration == AiSetupState.Registration.RUNNING && registerRequest == 0) {
                registration = AiSetupState.Registration.UNCHECKED;
            }
        }
        step = back;
        // Back undoes nothing; a check still running answers a screen that is no longer shown — stale
        probeInstalled = 0;
        probeAuth = 0;
        if (installed.verdict() == AiSetupState.Installed.RUNNING) installed = AiSetupState.Check.UNCHECKED;
        if (signedIn.verdict() == AiSetupState.SignedIn.RUNNING) signedIn = AiSetupState.Auth.UNCHECKED;
        // M77-4: Both opened straight at 2b (a client door) has not checked Claude yet — 2a checks when it is reached
        if (step == Step.INSIDE && installed.verdict() == AiSetupState.Installed.UNCHECKED) startInstalledProbe();
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
        // OD-77.11 (§7.4): what this wizard turned on stays on, and the closing line offers to turn it off
        boolean stillOn = !summary && accessTurnedOnHere && restOn();
        offerTurnOff = stillOn;
        if (pendingDraft != 0) {
            pendingDraft = 0;
            line = stillOn ? AiSetupLines.CANCELLED_ASK + " " + AiSetupLines.ACCESS_STILL_ON : AiSetupLines.CANCELLED_ASK;
        } else {
            line = stillOn ? AiSetupLines.ACCESS_STILL_ON : "";
        }
        startBoxWaiting = false;
        authoringWaiting = false;
        auditLog.info("aiSetup", summary ? "DONE" : "CANCELLED").info("origin", String.valueOf(e.origin()));
        return changed();
    }

    private void closeWizard() {
        disarm();
        forgetBridgeCheck();
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
        if (want == AiSetupState.Suggested.COPY_QUESTION) {
            effects.request(new SessionEffects.CopyToClipboardEffect(0L, SessionEvents.CopyWhat.FIRST_QUESTION, client));
        } else {
            effects.request(new SessionEffects.RunSuggestedActionEffect(0L, want));
        }
        closeWizard();
        line = "";
        return changed();
    }

    /** §7.3: the suggested first action, from the choice in force and whether a log is open. */
    private AiSetupState.Suggested suggested() {
        if (!wizardOpen || step != Step.SUMMARY) return AiSetupState.Suggested.NONE;
        if (origin == Origin.ONBOARD_BUILD && AssistantProfile.valueOf(profile) == AssistantProfile.FULL) {
            return AiSetupState.Suggested.BUILD;
        }
        return switch (inForce()) {
            case INSIDE, BOTH, IN_APP -> openLog != null && openLog.isOpen() ? AiSetupState.Suggested.RIGHT_CLICK
                    : AiSetupState.Suggested.OPEN_SAMPLE;
            case NO_AI -> AiSetupState.Suggested.TOUR;
            case CLIENT -> AiSetupState.Suggested.COPY_QUESTION;   // §6.4: your own client only
            case NONE -> AiSetupState.Suggested.NONE;
        };
    }

    @OnEventHandler
    public boolean onFirstRunDismissed(SessionEvents.AiSetupFirstRunDismissed e) {
        if (firstRunDismissed) return false;
        firstRunDismissed = true;
        auditLog.info("aiSetup", "FIRST_RUN_DISMISSED");
        // OD-77.9: already recorded when it was offered; written here only for a block offered before that existed
        if (!firstRunOffered) effects.request(new SessionEffects.PersistSetupDismissedEffect(0L));
        return changed();
    }

    @OnEventHandler
    public boolean onCheckAgain(SessionEvents.AiSetupCheckAgain e) {
        if (!wizardOpen || e.check() == null) return false;
        if (step == Step.CLIENT) return checkAgainClient(e.check());
        if (step != Step.INSIDE) return false;
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

    // ---- M77-3: Screen 2b (§6.3, §9.3, §9.4) ----------------------------------------------------------------------------

    /** Part 1: which client. Its registration is not known for the new client; the watch starts again. */
    @OnEventHandler
    public boolean onClientChosen(SessionEvents.AiSetupClientChosen e) {
        if (!wizardOpen || step != Step.CLIENT || confirmingRegister || e.client() == null
                || e.client() == AiSetupState.Client.NONE) {
            auditLog.info("staleFact", "AiSetupClientChosen");
            return false;
        }
        if (e.client() == client) return false;
        client = e.client();
        disarm();
        registration = launcher ? AiSetupState.Registration.UNCHECKED : AiSetupState.Registration.NO_LAUNCHER;
        registrationProbe = 0;
        registerRequest = 0;
        if (liveVerdict != AiSetupState.LiveVerdict.SEEN) liveVerdict = AiSetupState.LiveVerdict.IDLE;
        line = "";
        auditLog.info("aiSetupClient", client.name());
        rearm();
        return changed();
    }

    /** A Copy button: the command or configuration copied counts as part 3 done ("Copied — run it in a terminal"). */
    @OnEventHandler
    public boolean onCopyRequested(SessionEvents.AiSetupCopyRequested e) {
        if (!wizardOpen || step != Step.CLIENT || e.what() == null || e.what() == SessionEvents.CopyWhat.FIRST_QUESTION) {
            auditLog.info("staleFact", "AiSetupCopyRequested");
            return false;
        }
        boolean config = e.what() == SessionEvents.CopyWhat.REGISTER_COMMAND
                || e.what() == SessionEvents.CopyWhat.GENERIC_CONFIG;
        if (config && !launcher) return false;        // nothing to copy: this copy cannot be registered
        auditLog.info("aiSetupCopy", e.what().name());
        effects.request(new SessionEffects.CopyToClipboardEffect(0L, e.what(), client));
        if (config && registration != AiSetupState.Registration.PRESENT
                && registration != AiSetupState.Registration.REGISTERED
                && registration != AiSetupState.Registration.RUNNING) {
            registration = AiSetupState.Registration.COPIED;
        }
        rearm();
        return changed();
    }

    /** <i>Register for me…</i> (OD-77.10): never run before the confirmation that shows the exact command (S5). */
    @OnEventHandler
    public boolean onRegisterRequested(SessionEvents.AiSetupRegisterRequested e) {
        if (!wizardOpen || step != Step.CLIENT || !launcher || client == AiSetupState.Client.GENERIC
                || confirmingRegister || registration == AiSetupState.Registration.RUNNING) {
            auditLog.info("staleFact", "AiSetupRegisterRequested");
            return false;
        }
        registerRequest = ++nextProbe;
        confirmingRegister = true;
        boolean replace = registration == AiSetupState.Registration.PRESENT;
        auditLog.info("aiSetupRegister", "CONFIRM").info("request", registerRequest).info("replace", replace);
        effects.request(new SessionEffects.ConfirmRegisterEffect(0L, registerRequest, client, replace));
        return changed();
    }

    @OnEventHandler
    public boolean onRegisterAnswered(SessionEvents.RegisterAnswered e) {
        if (!confirmingRegister || e.request() != registerRequest) {
            auditLog.info("staleFact", "RegisterAnswered").info("request", e.request()).info("open", registerRequest);
            return false;
        }
        confirmingRegister = false;
        if (!e.confirmed() || !wizardOpen) {
            auditLog.info("aiSetupRegister", "CANCELLED").info("request", e.request());
            registerRequest = 0;
            return changed();
        }
        disarm();                                     // never while the live check is armed (§9.4)
        registration = AiSetupState.Registration.RUNNING;
        auditLog.info("aiSetupRegister", "RUN").info("request", e.request());
        effects.request(new SessionEffects.RegisterMcpEffect(0L, e.request(), client));
        return changed();
    }

    @OnEventHandler
    public boolean onRegistrationChanged(SessionEvents.McpRegistrationChanged e) {
        if (registerRequest == 0 || e.request() != registerRequest || !wizardOpen) {
            auditLog.info("staleFact", "McpRegistrationChanged").info("request", e.request()).info("open", registerRequest);
            return false;
        }
        registerRequest = 0;
        registration = e.ok() ? AiSetupState.Registration.REGISTERED : AiSetupState.Registration.FAILED;
        auditLog.info("aiSetupRegistered", registration.name());
        rearm();
        return changed();
    }

    @OnEventHandler
    public boolean onRegistrationObserved(SessionEvents.McpRegistrationObserved e) {
        if (registrationProbe == 0 || e.probe() != registrationProbe || !wizardOpen) {
            auditLog.info("staleFact", "McpRegistrationObserved").info("probe", e.probe()).info("current",
                    registrationProbe);
            return false;
        }
        registrationProbe = 0;
        registration = switch (e.status() == null ? SessionEvents.RegistrationOutcome.INDETERMINATE : e.status()) {
            case PRESENT -> AiSetupState.Registration.PRESENT;
            case ABSENT -> AiSetupState.Registration.ABSENT;
            case INDETERMINATE -> AiSetupState.Registration.UNKNOWN;
        };
        auditLog.info("aiSetupRegistration", registration.name());
        rearm();
        return changed();
    }

    /** The action server saw the first counted external call after this watch was armed (S8). */
    @OnEventHandler
    public boolean onClientSeen(SessionEvents.ExternalClientSeen e) {
        if (watch == 0 || e.watch() != watch || !wizardOpen) {
            auditLog.info("staleFact", "ExternalClientSeen").info("watch", e.watch()).info("current", watch);
            return false;
        }
        watch = 0;                                    // the server reports once, then disarms itself
        ListenerState ls = actionListener == null ? ListenerState.NONE : actionListener.state();
        liveVerdict = AiSetupState.LiveVerdict.SEEN;
        liveAt = e.at();
        liveLabelRef = e.labelRef();
        // §9.4 (review B2, R1-18): a Full session in the panel can reach the endpoint too — say so if one ran meanwhile
        liveFull = fullAtArm || ls.fullSession() || ls.fullSessions() > fullMark;
        auditLog.info("aiSetupLive", "SEEN").info("fullSession", liveFull);
        return changed();
    }

    @OnEventHandler
    public boolean onTimerFired(SessionEvents.AiSetupTimerFired e) {
        if (watch == 0 || e.watch() != watch || liveVerdict != AiSetupState.LiveVerdict.WAITING) {
            auditLog.info("staleFact", "AiSetupTimerFired").info("watch", e.watch()).info("current", watch);
            return false;
        }
        liveVerdict = AiSetupState.LiveVerdict.WAITING_LONG;   // the tips appear; the wait continues
        return changed();
    }

    /** <i>More options…</i> (OD-77.12): today's client dialog, for what the wizard does not do. */
    @OnEventHandler
    public boolean onMoreOptions(SessionEvents.AiSetupMoreOptions e) {
        if (!wizardOpen || step != Step.CLIENT) return false;
        effects.request(new SessionEffects.OpenClientSetupEffect(0L, client));
        return false;
    }

    /** OD-77.5: the box under No AI. */
    @OnEventHandler
    public boolean onAlsoTurnOff(SessionEvents.AiSetupAlsoTurnOffChosen e) {
        if (alsoTurnOff == e.turnOff()) return false;
        alsoTurnOff = e.turnOff();
        return changed();
    }

    private boolean checkAgainClient(AiSetupState.CheckKind k) {
        switch (k) {
            case REGISTRATION -> {
                if (!launcher || client == AiSetupState.Client.GENERIC || confirmingRegister) return false;
                if (registration == AiSetupState.Registration.RUNNING) {
                    line = AiSetupLines.ALREADY_CHECKING;
                    return changed();
                }
                disarm();                             // mcp get never runs while the live check is armed (§9.4)
                registrationProbe = ++nextProbe;
                registration = AiSetupState.Registration.RUNNING;
                auditLog.info("aiSetupProbe", "REGISTRATION").info("probe", registrationProbe);
                effects.request(new SessionEffects.CheckMcpRegistrationEffect(0L, registrationProbe, client));
            }
            case BRIDGE -> {
                // Check connection: the bridge your client will start, asked what is open. With access off it asks for
                // access first — the same confirmation as the switch (OD-77.4) — and its confirm also runs the check
                if (!launcher) return false;
                if (bridge == AiSetupState.Bridge.CHECKING || bridgeAfterAccess) {
                    line = AiSetupLines.ALREADY_CHECKING;
                    return changed();
                }
                AiSetupState.Access a = access();
                if (a == AiSetupState.Access.CONFIRMING || a == AiSetupState.Access.CLOSED_FOR_SESSION) return false;
                bridgeAfterAccess = true;
                bridge = AiSetupState.Bridge.UNCHECKED;
                if (a == AiSetupState.Access.OFF) {
                    auditLog.info("aiSetupBridge", "ACCESS_FIRST");
                    effects.request(new SessionEffects.RequestExternalAccessEffect(0L, -(++nextAccessRequest), true));
                } else {
                    maybeCheckBridge();
                }
            }
            case LIVE, READINESS -> {
                if (liveVerdict == AiSetupState.LiveVerdict.SEEN || liveVerdict == AiSetupState.LiveVerdict.ACCESS_OFF
                        || liveVerdict == AiSetupState.LiveVerdict.OTHER_INSTANCE) {
                    liveVerdict = AiSetupState.LiveVerdict.IDLE;
                }
                rearm();
            }
            default -> {
                return false;
            }
        }
        line = "";
        return changed();
    }

    /** External access as Screen 2b shows it — from actionListener's state and the readiness poll (R1-18). */
    private AiSetupState.Access access() {
        if (actionListener == null) return AiSetupState.Access.OFF;
        ListenerState ls = actionListener.state();
        if (ls.confirming()) return AiSetupState.Access.CONFIRMING;
        if (!ls.restOn()) return AiSetupState.Access.OFF;
        if (ls.closedForSession()) return AiSetupState.Access.CLOSED_FOR_SESSION;
        if (readiness == SessionEvents.McpReadiness.OTHER_INSTANCE) return AiSetupState.Access.OTHER_INSTANCE;
        return AiSetupState.Access.ON;
    }

    private boolean restOn() {
        return actionListener != null && actionListener.state().restOn();
    }

    /**
     * §9.4: the watch is armed only while part 4 is current — Screen 2b, part 3 done, no registration running — AND
     * access is on for external clients; it is disarmed otherwise. One rule, run after every fact that can change it.
     */
    private void rearm() {
        boolean wasArmed = watch != 0;
        AiSetupState.Access a = access();
        boolean part3 = registration == AiSetupState.Registration.PRESENT
                || registration == AiSetupState.Registration.REGISTERED
                || registration == AiSetupState.Registration.COPIED;
        boolean want = wizardOpen && step == Step.CLIENT && a == AiSetupState.Access.ON && part3 && !confirmingRegister
                && liveVerdict != AiSetupState.LiveVerdict.SEEN;
        if (want && watch == 0) {
            ListenerState ls = actionListener.state();
            watch = ++nextProbe;
            fullAtArm = ls.fullSession();
            fullMark = ls.fullSessions();
            liveVerdict = AiSetupState.LiveVerdict.WAITING;
            auditLog.info("aiSetupWatch", "ARMED").info("watch", watch);
            effects.request(new SessionEffects.WatchExternalClientEffect(0L, watch));
            effects.request(new SessionEffects.AiSetupTimerEffect(0L, watch, LIVE_TIP_SECONDS));
            return;
        }
        if (!want) {
            disarm();
            if (wizardOpen && step == Step.CLIENT && liveVerdict != AiSetupState.LiveVerdict.SEEN) {
                if (a == AiSetupState.Access.OTHER_INSTANCE) liveVerdict = AiSetupState.LiveVerdict.OTHER_INSTANCE;
                else if (a == AiSetupState.Access.OFF && (wasArmed
                        || liveVerdict == AiSetupState.LiveVerdict.ACCESS_OFF)) {
                    liveVerdict = AiSetupState.LiveVerdict.ACCESS_OFF;
                } else {
                    liveVerdict = AiSetupState.LiveVerdict.IDLE;
                }
            }
        }
    }

    /** The listener now serves (or stopped serving) external clients: a Check connection waiting for access runs. */
    @OnEventHandler
    public boolean onListenerConfigured(SessionEvents.ActionListenerConfigured e) {
        if (!bridgeAfterAccess) return false;
        if (e.failure() != null) {
            bridgeAfterAccess = false;                // the listener could not start: there is nothing to reach
            bridge = AiSetupState.Bridge.REST_OFF;
            auditLog.info("aiSetupBridge", "LISTENER_FAILED");
            return changed();
        }
        maybeCheckBridge();
        return changed();
    }

    /** The bridge check's answer, for the probe this node asked for; any other is stale. */
    @OnEventHandler
    public boolean onBridgeObserved(SessionEvents.BridgeObserved e) {
        if (bridgeProbe == 0 || e.probe() != bridgeProbe) {
            auditLog.info("staleFact", "BridgeObserved").info("probe", e.probe()).info("current", bridgeProbe);
            return false;
        }
        bridgeProbe = 0;
        bridge = e.status() == null || e.status() == AiSetupState.Bridge.UNCHECKED
                || e.status() == AiSetupState.Bridge.CHECKING ? AiSetupState.Bridge.LAUNCH_FAILED : e.status();
        auditLog.info("aiSetupBridge", bridge.name());
        line = "";
        return changed();
    }

    /** Check connection waits for access: it runs once the listener serves external clients, on Screen 2b only. */
    private void maybeCheckBridge() {
        if (!bridgeAfterAccess) return;
        if (!wizardOpen || step != Step.CLIENT) {
            bridgeAfterAccess = false;
            return;
        }
        if (actionListener == null || !actionListener.state().external()) return;
        bridgeAfterAccess = false;
        bridgeProbe = ++nextProbe;
        bridge = AiSetupState.Bridge.CHECKING;
        auditLog.info("aiSetupProbe", "BRIDGE").info("probe", bridgeProbe);
        effects.request(new SessionEffects.ProbeBridgeEffect(0L, bridgeProbe));
    }

    /** A bridge check still running answers a screen that is gone: stale. */
    private void forgetBridgeCheck() {
        bridgeProbe = 0;
        bridgeAfterAccess = false;
        if (bridge == AiSetupState.Bridge.CHECKING) bridge = AiSetupState.Bridge.UNCHECKED;
    }

    /** The 2-minute tip (§6.3 part 4). */
    public static final int LIVE_TIP_SECONDS = 120;

    private void disarm() {
        if (watch == 0) return;
        auditLog.info("aiSetupWatch", "DISARMED").info("watch", watch);
        effects.request(new SessionEffects.UnwatchExternalClientEffect(0L, watch));
        watch = 0;
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
        return startupObserved && !firstRunDismissed && !restLaunch && observed && inForce() == Choice.NONE;
    }

    /** The one status line of the panel (owner, 2026-10-18): only once the frame has reported the profile. */
    private String status() {
        if (!observed) return "";
        boolean inAppCannot = availabilityObserved && !inAppCanAnswer;
        if (AssistantProfile.NONE.name().equals(profile)) {
            if (availabilityObserved && inAppCanAnswer) return "";
            // §6.6 (review R1-17): one owner, one line
            return pendingDraft != 0 ? AiSetupLines.QUESTION_WAITS : AiSetupLines.PANEL_NOTHING_SET_UP;
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
            published = new ProfileState(AssistantProfile.valueOf(profile), status(),
                    confirmingFull != 0 && confirmingOrigin != ProfileOrigin.SETUP, observed, pendingDraft != 0,
                    buildModeOffer);
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
        AiSetupState.Access access = access();
        AiSetupState.Confirming confirming = confirmingFull != 0 && confirmingOrigin == ProfileOrigin.SETUP
                ? AiSetupState.Confirming.FULL
                : confirmingRegister ? AiSetupState.Confirming.REGISTER
                : wizardOpen && access == AiSetupState.Access.CONFIRMING ? AiSetupState.Confirming.EXTERNAL
                : AiSetupState.Confirming.NONE;
        AiSetupState.External ext = new AiSetupState.External(client, access, registration,
                new AiSetupState.Live(liveVerdict, liveAt, liveLabelRef, liveFull), launcher, offerTurnOff, alsoTurnOff,
                choice == Choice.NO_AI && restOn(), bridge, bridgeAfterAccess);
        AiSetupState.Pending pending = pendingDraft != 0
                ? new AiSetupState.Pending(AiSetupState.PendingKind.ASK_DRAFT, pendingDraft)
                : startBoxWaiting ? new AiSetupState.Pending(AiSetupState.PendingKind.START_BOX, interceptedStartPage)
                : AiSetupState.Pending.NONE;
        AssistantProfile p = AssistantProfile.valueOf(profile);
        return new AiSetupState(wizardOpen, firstRun(), origin, step, choice, inForce, posture, fullMode,
                installed, signedIn, confirming,
                pending, finished, shownLine(),
                observed ? AiSetupLines.aiLine(inForce, p, claudeFound, inAppCanAnswer, external,
                        access == AiSetupState.Access.OTHER_INSTANCE) : "",
                observed ? AiSetupLines.aiLineLink(inForce) : "",
                status(), claudeFound, codexFound, applying, suggested(), ext);
    }
}
