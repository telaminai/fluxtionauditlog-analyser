package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Client;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Origin;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileOrigin;
import telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 UX → M77-4 (spec-ai-setup-wizard.md §14.3, OD-77.3, OD-77.9, §14.4) — the profile on the REAL generated processor
 * once the four cards are gone: the Assistant ▾ quick switch's press ({@code AssistantProfileChosen(origin=MENU)}) is
 * decided by {@code aiSetup} like every Apply — never mid-turn, Full only after its confirmation, and the wizard instead
 * of an Apply that cannot work as it stands; the AI menu's door opens at Your own AI client; the first-run block is
 * offered once. Facts in; the effects asked for and the snapshot out.
 *
 * <p>Each ★ test names its witness: the mutation of the node that turns it red.
 */
class AssistantProfileTest {

    static ProfileState profiles(SessionDriver d) {
        return d.snapshot().profiles();
    }

    static AiSetupState setup(SessionDriver d) {
        return d.snapshot().aiSetup();
    }

    /** Nothing chosen; {@code claude} found or not (a file check); no in-app key. */
    static SessionDriver unchosen(FakeSessionAdapter a, boolean claudeFound) {
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        d.post(new SessionEvents.StartPageAvailability(false, claudeFound, false, false));
        return d;
    }

    static SessionEvents.AssistantProfileChosen menu(long request, AssistantProfile p) {
        return new SessionEvents.AssistantProfileChosen(request, p, ProfileOrigin.MENU);
    }

    // ---- the first use no longer opens cards --------------------------------------------------------------------------

    /** ★ Witness: drop the {@code NONE} check in {@code firstUse} — the wizard opens over a profile already chosen. */
    @Test
    @DisplayName("★ no auto-open once a profile is chosen (Off is a choice), or before the frame has reported one")
    void noAutoOpenAfterAProfileIsChosen() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(false, 3, 20)));
        assertFalse(setup(d).open(), "nothing reported yet: 'not chosen' is a fact, never the node's default");
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.OFF));
        d.post(new SessionEvents.AssistantSendRequested(2, 2, AssistantLoopTest.route(false, 3, 20)));
        d.post(new SessionEvents.LocalAgentStartRequested(3, LocalAgentSessionTest.consent(false, true, false, "p", 0)));
        assertFalse(setup(d).open(), "Off is a choice: nothing opens by itself after it");
        assertEquals(0, a.setupShows.size());
        assertEquals(AssistantProfile.OFF, profiles(d).profile());
    }

    /**
     * ★ An assistant that can answer is never interrupted. Witness: drop the accepted-answer check in
     * {@code onAssistantSend} — the wizard opens over a turn.
     */
    @Test
    @DisplayName("★ a Send an assistant accepts never opens Set up AI, whatever the last observation said")
    void anAcceptedSendIsNeverInterrupted() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, true);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(d.snapshot().assistant().busy(), "control: the turn runs");
        assertFalse(setup(d).open(), "the person asked an assistant that can answer");
        assertEquals(0, a.setupShows.size());
    }

    // ---- ★ the quick switch: an Apply, decided here ---------------------------------------------------------------

    /**
     * ★ Witness: drop the {@code localAgent.turnInProgress()} check — the switch is written mid-turn and the no-effect
     * assertion fails. (The in-app half: drop {@code assistantLoop.turnInProgress()}.)
     */
    @Test
    @DisplayName("★ a quick switch mid-turn is refused by name, in either assistant, as the ONE line; after the turn it applies")
    void aSwitchMidTurnIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        d.post(new SessionEvents.StartPageAvailability(false, true, true, false));
        var start = LocalAgentSessionTest.startToReady(d, a);
        var t = LocalAgentSessionTest.send(d, a, 10);
        d.post(menu(1, AssistantProfile.IN_APP));
        assertEquals(0, a.profileApplies.size(), "nothing is written while Claude is answering");
        assertEquals(AiSetup.BUSY_LOCAL_AGENT_TEXT, setup(d).line(), "the node's ONE line says why");
        assertFalse(setup(d).open(), "a refusal opens nothing");
        d.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(menu(2, AssistantProfile.IN_APP));
        assertEquals(1, a.profileApplies.size(), "after the turn the profile is applied");
        assertEquals(AssistantProfile.IN_APP, a.profileApplies.get(0).profile());
        assertEquals(ProfileOrigin.MENU, a.profileApplies.get(0).origin());
        assertEquals("", setup(d).line(), "applied: the line is cleared");
        assertEquals(AssistantProfile.IN_APP, profiles(d).profile());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = new SessionDriver(b);
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.IN_APP));
        e.post(new SessionEvents.StartPageAvailability(false, true, true, false));
        e.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(e.snapshot().assistant().busy(), "control: the in-app turn is in progress");
        e.post(menu(1, AssistantProfile.SAFE));
        assertEquals(0, b.profileApplies.size());
        assertEquals(AiSetup.BUSY_IN_APP_TEXT, setup(e).line());
    }

    @Test
    @DisplayName("a switch with no profile is refused with one line; a stale Applied answer changes nothing")
    void applyNeedsAProfile() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, true);
        d.post(menu(1, AssistantProfile.NONE));
        assertEquals(0, a.profileApplies.size());
        assertEquals(AiSetupLines.CHOOSE_FIRST, setup(d).line());
        d.post(new SessionEvents.AssistantProfileApplied(99, 42, AssistantProfile.FULL, true, ""));
        assertEquals(AssistantProfile.NONE, profiles(d).profile(), "an answer to another request is refused");
    }

    /**
     * ★ Review P3+UX S4: the tab's mode follows a profile because the node ASKS for it after the settings were written;
     * a failed write asks for nothing and says why. Witness: drop the ChooseAssistantModeEffect request in
     * {@code onProfileApplied} — no mode change is asked for.
     */
    @Test
    @DisplayName("★ a switched profile asks for its mode; a failed write says why and asks for nothing")
    void theNodeAsksForTheMode() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, true);
        d.post(menu(1, AssistantProfile.SAFE));
        assertEquals(1, a.modeChoices.size());
        assertEquals(AssistantMode.LOCAL_AGENT, a.modeChoices.get(0).mode());

        FakeSessionAdapter b = new FakeSessionAdapter();
        b.profileSaveFailure = "the settings could not be saved, so nothing changed (AccessDeniedException: …/config)";
        SessionDriver e = unchosen(b, true);
        e.post(menu(1, AssistantProfile.FULL));
        e.post(new SessionEvents.AssistantFullProfileAnswered(1, true));      // review PR151 F4: confirmed first
        assertEquals(1, b.profileApplies.size(), "control: the write was asked for");
        assertTrue(b.modeChoices.isEmpty(), "nothing was written, so the mode does not change");
        assertTrue(setup(e).line().startsWith("the settings could not be saved"), setup(e).line());
    }

    /**
     * ★ Review PR151 F4: Full Claude Code from the quick switch asks the confirmation Settings ▸ Assistant asks and
     * applies nothing until it is answered; Cancel leaves the profile as it was; only a confirmed answer, for that press,
     * writes it. Witness: drop the FULL branch in {@code apply} — the first press writes the full posture at once.
     */
    @Test
    @DisplayName("★ ◆ Full Claude Code from the quick switch: nothing is applied until the person confirms the warning")
    void fullFromTheSwitchNeedsItsConfirmation() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        d.post(new SessionEvents.StartPageAvailability(false, true, false, false));
        d.post(menu(1, AssistantProfile.FULL));
        assertEquals(0, a.profileApplies.size(), "the press alone writes nothing: the warning is asked first");
        assertEquals(1, a.fullConfirms.size(), "the confirmation is asked for");
        assertTrue(profiles(d).confirming(), "the switch renders it (its rows are disabled behind it)");
        d.post(new SessionEvents.AssistantFullProfileAnswered(1, false));
        assertEquals(0, a.profileApplies.size(), "Cancel: nothing is written");
        assertEquals(AssistantProfile.SAFE, profiles(d).profile());
        assertFalse(profiles(d).confirming());
        d.post(new SessionEvents.AssistantFullProfileAnswered(1, true));
        assertEquals(0, a.profileApplies.size(), "a second answer to an answered confirmation is stale");

        d.post(menu(2, AssistantProfile.FULL));
        d.post(new SessionEvents.AssistantFullProfileAnswered(1, true));
        assertEquals(0, a.profileApplies.size(), "an answer for another press is stale");
        d.post(new SessionEvents.AssistantFullProfileAnswered(2, true));
        assertEquals(1, a.profileApplies.size(), "confirmed: applied");
        assertEquals(AssistantProfile.FULL, a.profileApplies.get(0).profile());

        // already in force: re-applying turns nothing on, so nothing is asked
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.FULL));
        d.post(menu(3, AssistantProfile.FULL));
        assertEquals(2, a.profileApplies.size(), "Full in force: applies at once");
        assertEquals(2, a.fullConfirms.size(), "and asks nothing");
    }

    /**
     * ★ M77-4 (§14.3, review R1-24): the menu decides nothing — a switch that cannot work as it stands opens the wizard
     * at the screen that can finish it, and applies nothing. Witness: drop the two {@code origin == MENU} checks in
     * {@code onProfileChosen} — Safe is written with no {@code claude}, the in-app assistant with no key.
     */
    @Test
    @DisplayName("★ the switch to Claude with claude not found, or to In-app with no key, opens Set up AI at that screen")
    void aSwitchThatCannotApplyOpensTheWizard() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, false);
        d.post(menu(1, AssistantProfile.SAFE));
        assertEquals(0, a.profileApplies.size(), "★ nothing written: claude was not found");
        assertTrue(setup(d).open(), "the wizard opens");
        assertEquals(Step.INSIDE, setup(d).step(), "at Claude inside, which checks for it");
        assertEquals(Choice.INSIDE, setup(d).choice());
        assertEquals(1, a.claudeProbes.size(), "the check runs: the person's press on Claude is the act");
        assertFalse(a.claudeProbes.get(0).full(), "Safe's environment");
        d.post(new SessionEvents.AiSetupCancelled("test"));

        d.post(menu(2, AssistantProfile.FULL));
        assertEquals(AiSetupState.Posture.FULL, setup(d).posture(), "Full's press keeps its sub-choice");
        assertEquals(0, a.fullConfirms.size(), "the confirmation is the wizard's Continue, not the press");
        d.post(new SessionEvents.AiSetupCancelled("test"));

        d.post(menu(3, AssistantProfile.IN_APP));
        assertEquals(0, a.profileApplies.size(), "★ nothing written: no key is stored");
        assertEquals(Step.OTHER, setup(d).step(), "at Other options, the key field");
        d.post(new SessionEvents.AiSetupCancelled("test"));

        d.post(menu(4, AssistantProfile.OFF));
        assertEquals(1, a.profileApplies.size(), "Off always applies");
        assertFalse(setup(d).open());

        // with what each needs, each applies at once
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = new SessionDriver(b);
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.OFF));
        e.post(new SessionEvents.StartPageAvailability(false, true, true, false));
        e.post(menu(1, AssistantProfile.SAFE));
        e.post(menu(2, AssistantProfile.IN_APP));
        assertEquals(2, b.profileApplies.size());
        assertEquals(0, b.setupShows.size(), "no wizard when the switch can apply");

        // ★ under Off nothing answers, but a stored key is all In-app needs (witness: test inAppCanAnswer instead)
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = new SessionDriver(c);
        f.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.OFF));
        f.post(new SessionEvents.StartPageAvailability(false, true, false, false, true));
        f.post(menu(1, AssistantProfile.IN_APP));
        assertEquals(1, c.profileApplies.size(), "★ Off → In-app with a key stored: applied");
        assertEquals(0, c.setupShows.size());
    }

    /**
     * ★ M77-4 (§14.4): AI ▸ Set up AI… (and every "connect a client" door) opens at Your own AI client when the choice
     * in force includes one, Screen 1 otherwise. Witness: ignore {@link Origin#CONNECT} — the wizard opens at Screen 1.
     */
    @Test
    @DisplayName("★ the client doors open Set up AI at Your own AI client when one is set up, else at the first screen")
    void theClientDoorsOpenAtTheClientScreen() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AiSetupStartupObserved(false, Client.CODEX, false));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        d.post(new SessionEvents.StartPageAvailability(false, true, false, false));
        assertEquals(Choice.BOTH, setup(d).inForce(), "control: Claude inside and Codex set up");
        d.post(new SessionEvents.AiSetupRequested(Origin.CONNECT));
        assertEquals(Step.CLIENT, setup(d).step(), "★ straight to Your own AI client");
        assertEquals(Choice.BOTH, setup(d).choice(), "for Both: Finish keeps Claude in the panel");
        assertEquals(Client.CODEX, setup(d).external().client(), "the client set up before");
        d.post(new SessionEvents.AiSetupBack(Step.CLIENT));
        assertEquals(Step.INSIDE, setup(d).step(), "Back is Claude inside, for Both");
        assertEquals(1, a.claudeProbes.size(), "which checks Claude when it is first reached");
        d.post(new SessionEvents.AiSetupCancelled("test"));

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = unchosen(b, true);
        e.post(new SessionEvents.AiSetupRequested(Origin.CONNECT));
        assertEquals(Step.CHOICE, setup(e).step(), "nothing with a client set up: the first screen");
        e.post(new SessionEvents.AiSetupCancelled("test"));
        e.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        assertEquals(Step.CHOICE, setup(e).step(), "Assistant ▾ ▸ Set up AI… is always the first screen");
    }

    /**
     * ★ OD-77.9 (M77-4): the first-run block is offered once — to a new person and to an upgrader whose profile is still
     * NONE — so it is recorded the first time it is shown, and the next run shows the AI line. Never under {@code --rest}.
     * Witness: drop the {@code PersistSetupDismissedEffect} request in {@code noteFirstRunOffered} — no write, and the
     * next run offers it again.
     */
    @Test
    @DisplayName("★ the first-run block is offered once: recorded when shown, the AI line on the next run")
    void theFirstRunBlockIsOfferedOnce() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AiSetupStartupObserved(false, Client.NONE, false));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        d.post(new SessionEvents.StartPageAvailability(false, true, false, false));
        assertTrue(setup(d).firstRun(), "control: the block shows");
        assertEquals(1, a.dismissedWrites.size(), "★ the offer is recorded once");
        d.post(new SessionEvents.StartPageAvailability(false, false, false, false));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        assertTrue(setup(d).firstRun(), "in this run it stays until the person answers it");
        assertEquals(1, a.dismissedWrites.size(), "and is recorded only once");

        // the next run: an upgrader (or anyone) whose offer was recorded sees the AI line
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = new SessionDriver(b);
        e.post(new SessionEvents.AiSetupStartupObserved(true, Client.NONE, false));
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        e.post(new SessionEvents.StartPageAvailability(false, true, false, false));
        assertFalse(setup(e).firstRun());
        assertEquals(AiSetupLines.SET_UP_LINK, setup(e).aiLineLink(), "the AI line keeps the door");
        assertEquals(0, b.dismissedWrites.size());

        // an agent started the process: never offered, never recorded
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = new SessionDriver(c);
        f.post(new SessionEvents.AiSetupStartupObserved(false, Client.NONE, true));
        f.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        assertFalse(setup(f).firstRun());
        assertEquals(0, c.dismissedWrites.size());
    }

    /**
     * ★ Owner, 2026-10-18: one status line when no assistant can answer from the panel, decided here. Witness: return ""
     * from {@code status()} — no line.
     */
    @Test
    @DisplayName("★ the one status line: nothing set up, the in-app assistant with no key, else none")
    void theOneStatusLine() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        assertEquals("", profiles(d).status(), "nothing reported yet: no claim");
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        assertEquals(AiSetupLines.PANEL_NOTHING_SET_UP, profiles(d).status());
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.IN_APP));
        d.post(new SessionEvents.StartPageAvailability(false, false, false, false));
        assertEquals(AiSetup.IN_APP_NO_KEY, profiles(d).status());
        d.post(new SessionEvents.StartPageAvailability(false, false, true, false));
        assertEquals("", profiles(d).status(), "the in-app assistant can answer: the panel's own lines");
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        assertEquals("", profiles(d).status());
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        assertEquals("", profiles(d).status(), "nothing chosen, but a stored key answers: something is set up");
    }
}
