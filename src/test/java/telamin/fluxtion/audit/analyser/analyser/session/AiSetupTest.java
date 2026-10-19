package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Client;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Origin;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ProfileOrigin;
import telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77 (spec-ai-setup-wizard.md §16) — {@code aiSetup} on the REAL generated processor: facts in, the effects asked for
 * and the snapshot out. Each ★ test names its witness: the mutation of the node that turns it red.
 */
class AiSetupTest {

    static AiSetupState setup(SessionDriver d) {
        return d.snapshot().aiSetup();
    }

    /** A fresh machine: nothing set up, the start-up facts reported, {@code claude} found or not (a file check). */
    static SessionDriver fresh(FakeSessionAdapter a, boolean claudeFound) {
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AiSetupStartupObserved(false, Client.NONE, false));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        d.post(new SessionEvents.StartPageAvailability(false, claudeFound, false, false));
        return d;
    }

    /** Screen 1 → Continue on {@code c}; for Claude inside, the version check answered {@code 2.1.282}. */
    static void toInside(SessionDriver d, FakeSessionAdapter a, Choice c) {
        d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        d.post(new SessionEvents.AiSetupChoiceMade(c));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(Step.INSIDE, setup(d).step());
        found(d, a);
    }

    static void found(SessionDriver d, FakeSessionAdapter a) {
        var probe = a.claudeProbes.get(a.claudeProbes.size() - 1);
        d.post(new SessionEvents.ClaudeObserved(probe.probe(), SessionEvents.ProbeOutcome.FOUND, "2.1.282", 3));
    }

    static void signedIn(SessionDriver d, FakeSessionAdapter a) {
        var probe = a.authProbes.get(a.authProbes.size() - 1);
        d.post(new SessionEvents.ClaudeAuthObserved(probe.probe(), SessionEvents.AuthOutcome.READ, true,
                AiSetupState.Method.CLAUDE_AI, AiSetupState.Plan.MAX, AiSetupState.KeySource.NONE));
    }

    // ---- ★ T1: each choice writes the right settings, and the derived choice round-trips ----------------------------

    /**
     * ★ T1. Witness: map {@code CLIENT} to {@code SAFE} in {@link AiSetupDerived#profileFor} — the round trip for "your
     * own client" fails (Both is derived instead).
     */
    @Test
    @DisplayName("★ T1: each Screen 1 choice applies the right profile through the one Apply; the derived choice round-trips")
    void eachChoiceAppliesTheRightProfile() {
        // No AI: Off at once, and no external client any more
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.NO_AI));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(1, a.profileApplies.size());
        assertEquals(AssistantProfile.OFF, a.profileApplies.get(0).profile());
        assertEquals(ProfileOrigin.SETUP, a.profileApplies.get(0).origin(), "the wizard's Apply, through the one performer");
        assertEquals(List.of(Client.NONE), a.clientWrites.stream().map(SessionEffects.PersistSetupClientEffect::client).toList());
        assertEquals(Step.SUMMARY, setup(d).step(), "nothing waits: the Summary");

        // Claude inside, Safe analysis
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        toInside(e, b, Choice.INSIDE);
        e.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(List.of(AssistantProfile.SAFE), b.profileApplies.stream()
                .map(SessionEffects.ApplyAssistantProfileEffect::profile).toList());
        assertEquals(Step.SUMMARY, setup(e).step());
        assertTrue(setup(e).open());

        // Both: Claude inside, then (M77-3) Screen 2b — the window stays open
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = fresh(c, true);
        toInside(f, c, Choice.BOTH);
        f.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(AssistantProfile.SAFE, c.profileApplies.get(0).profile());
        assertEquals(0, c.clientSetups.size(), "M77-3: Screen 2b, not today's dialog");
        assertEquals(Step.CLIENT, setup(f).step());
        assertTrue(setup(f).open());

        // Your own client: (M77-3) Screen 2b; nothing is written until its Finish (AiSetupExternalTest)
        FakeSessionAdapter g = new FakeSessionAdapter();
        SessionDriver h = fresh(g, true);
        h.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        h.post(new SessionEvents.AiSetupChoiceMade(Choice.CLIENT));
        h.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(0, g.profileApplies.size());
        assertEquals(0, g.clientSetups.size());
        assertEquals(Step.CLIENT, setup(h).step());

        // Other options: the in-app assistant, refused with no key at all
        FakeSessionAdapter k = new FakeSessionAdapter();
        SessionDriver m = fresh(k, false);
        m.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        m.post(new SessionEvents.AiSetupChoiceMade(Choice.IN_APP));
        m.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(Step.OTHER, setup(m).step());
        m.post(new SessionEvents.AiSetupContinue(Step.OTHER, false));
        assertEquals(0, k.profileApplies.size(), "no key stored and none entered");
        assertEquals(AiSetupLines.ADD_A_KEY, setup(m).line());
        m.post(new SessionEvents.AiSetupContinue(Step.OTHER, true));
        assertEquals(AssistantProfile.IN_APP, k.profileApplies.get(0).profile());

        // the derived choice round-trips for every row of §14.1
        for (Choice ch : List.of(Choice.NO_AI, Choice.CLIENT, Choice.INSIDE, Choice.BOTH, Choice.IN_APP)) {
            Client client = ch == Choice.CLIENT || ch == Choice.BOTH ? Client.CLAUDE_CODE : Client.NONE;
            for (Posture p : Posture.values()) {
                assertEquals(ch, AiSetupDerived.choice(AiSetupDerived.profileFor(ch, p), client), ch + "/" + p);
            }
        }
        assertEquals(Choice.NONE, AiSetupDerived.choice(AssistantProfile.NONE, Client.NONE),
                "external access alone is not a client choice: NONE stays not set up");
        assertEquals(AssistantProfile.FULL, AiSetupDerived.profileFor(Choice.BOTH, Posture.FULL));
    }

    // ---- ★ T2: the first use opens the wizard (M77-4: the cards are gone) ------------------------------------------

    /** ★ T2. Witness: make {@code firstUse} return false, or ignore {@code AssistantDraftPending} — the wizard stays shut. */
    @Test
    @DisplayName("★ T2: a Send, a Start, a start-page question or a record's Ask with nothing set up opens Set up AI")
    void theFirstUseOpensTheWizard() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        d.post(new SessionEvents.LocalAgentStartRequested(1, LocalAgentSessionTest.consent(false, true, false, "p", 0)));
        assertTrue(setup(d).open(), "the wizard opens");
        assertEquals(Origin.SEND, setup(d).origin());
        assertEquals(Choice.INSIDE, setup(d).choice(), "Claude inside preselected: claude was found");
        assertEquals(1, a.setupShows.size());
        d.post(new SessionEvents.AiSetupCancelled("test"));
        d.post(new SessionEvents.LocalAgentStartRequested(2, LocalAgentSessionTest.consent(false, true, false, "p", 0)));
        assertTrue(setup(d).open(), "no once-per-session latch: the next press reopens it");
        d.post(new SessionEvents.AiSetupCancelled("test"));

        d.post(new SessionEvents.AssistantDraftPending(7, "Q1", "ask Q1 from records"));
        assertTrue(setup(d).open(), "a record's Ask opens it too");
        assertEquals(Origin.ASK, setup(d).origin());
        assertEquals(new AiSetupState.Pending(AiSetupState.PendingKind.ASK_DRAFT, 7), setup(d).pending(),
                "the Ask's question is held, by reference");
        assertTrue(a.pendingPlaced.isEmpty(), "and placed for nobody yet");

        // an assistant that can answer is never interrupted: a key stored before profiles existed
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, false);
        e.post(new SessionEvents.StartPageAvailability(false, false, true, false));
        e.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertFalse(setup(e).open());
        assertEquals(Choice.IN_APP, setup(e).inForce(), "a stored key IS set up");
    }

    // ---- ★ T3 / T4: Full from the wizard is confirmed, and only while the wizard is open ---------------------------------

    /** ★ T3. Witness: apply on {@code FULL} directly (drop the FULL branch in {@code apply}) — written before consent. */
    @Test
    @DisplayName("★ T3: Full from the wizard — confirmed: applied; Cancel: Safe re-selected, the step unchanged, nothing applied")
    void fullFromTheWizardIsConfirmed() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        toInside(d, a, Choice.INSIDE);
        d.post(new SessionEvents.AiSetupPostureChosen(Posture.FULL, "acceptEdits"));
        assertEquals(2, a.claudeProbes.size(), "the probe's environment follows the sub-choice: checked again");
        assertTrue(a.claudeProbes.get(1).full());
        found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(0, a.profileApplies.size(), "nothing until the warning is confirmed");
        assertEquals(1, a.fullConfirms.size());
        assertEquals(AiSetupState.Confirming.FULL, setup(d).confirming());
        assertFalse(d.snapshot().profiles().confirming(), "the cards' flag is not raised by the wizard's confirmation");
        d.post(new SessionEvents.AssistantFullProfileAnswered(a.fullConfirms.get(0).request(), false));
        assertEquals(0, a.profileApplies.size(), "Cancel applies nothing");
        assertEquals(Posture.SAFE, setup(d).posture(), "Safe analysis re-selected");
        assertEquals(Step.INSIDE, setup(d).step(), "on the same screen");
        found(d, a);
        d.post(new SessionEvents.AiSetupPostureChosen(Posture.FULL, "default"));
        found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        d.post(new SessionEvents.AssistantFullProfileAnswered(a.fullConfirms.get(1).request(), true));
        assertEquals(List.of(AssistantProfile.FULL), a.profileApplies.stream()
                .map(SessionEffects.ApplyAssistantProfileEffect::profile).toList(), "confirmed: applied");
        assertEquals(ProfileOrigin.SETUP, a.profileApplies.get(0).origin());
    }

    /**
     * ★ T4. Witness: keep the guard reading only the cards' {@code open} — the wizard's confirmed Full is silently
     * dropped (the first assertion fails); or drop the guard — the late answer applies.
     */
    @Test
    @DisplayName("★ T4: a Full answered after the wizard closed applies nothing; answered while it is open, it applies")
    void aLateFullAppliesNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        toInside(d, a, Choice.INSIDE);
        d.post(new SessionEvents.AiSetupPostureChosen(Posture.FULL, "default"));
        found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        d.post(new SessionEvents.AssistantFullProfileAnswered(a.fullConfirms.get(0).request(), true));
        assertEquals(1, a.profileApplies.size(), "★ open: the wizard's Full is applied (the cards are shut)");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        toInside(e, b, Choice.INSIDE);
        e.post(new SessionEvents.AiSetupPostureChosen(Posture.FULL, "default"));
        found(e, b);
        e.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        e.post(new SessionEvents.AiSetupCancelled("test"));
        e.post(new SessionEvents.AssistantFullProfileAnswered(b.fullConfirms.get(0).request(), true));
        assertEquals(0, b.profileApplies.size(), "★ closed: the late consent applies nothing");
        assertNotEquals(AssistantProfile.FULL, e.snapshot().profiles().profile(), "the profile in force is unchanged");
    }

    // ---- ★ T5 / T6: nothing is sent; no process before the act --------------------------------------------------------

    /** ★ T5. Witness: request a {@code SendLocalAgentTurnEffect} (or post a Send) on Finish — the sends list fills. */
    @Test
    @DisplayName("★ T5: across every choice, posture and act, the wizard sends nothing and starts no assistant")
    void theWizardSendsNothing() {
        for (Choice ch : Choice.values()) {
            for (Posture p : Posture.values()) {
                FakeSessionAdapter a = new FakeSessionAdapter();
                SessionDriver d = fresh(a, true);
                d.post(new SessionEvents.AssistantDraftPending(5, "Q1", "ask"));
                d.post(new SessionEvents.AiSetupChoiceMade(ch));
                d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
                if (!a.claudeProbes.isEmpty()) {
                    found(d, a);
                    signedIn(d, a);
                    d.post(new SessionEvents.AiSetupPostureChosen(p, "default"));
                    if (a.claudeProbes.size() > 1) found(d, a);
                }
                d.post(new SessionEvents.AiSetupCheckAgain(AiSetupState.CheckKind.SIGNED_IN));
                d.post(new SessionEvents.AiSetupContinue(setup(d).step(), true));
                for (var c : a.fullConfirms) d.post(new SessionEvents.AssistantFullProfileAnswered(c.request(), true));
                d.post(new SessionEvents.AiSetupBack(setup(d).step()));
                d.post(new SessionEvents.AiSetupContinue(setup(d).step(), true));
                d.post(new SessionEvents.AiSetupCancelled("test"));
                String where = ch + "/" + p;
                assertEquals(0, a.agentSends.size(), "no Claude Code turn: " + where);
                assertEquals(0, a.agentStarts.size(), "no Claude Code process: " + where);
                assertEquals(0, a.assistantRequests.size(), "no in-app completion: " + where);
                assertEquals(0, a.assistantContexts.size(), "no in-app context assembled: " + where);
            }
        }
    }

    /** ★ T6. Witness: request {@code ProbeClaudeEffect} in {@code openWizard} — a probe runs on window open. */
    @Test
    @DisplayName("★ T6: opening the wizard and choosing run no process; the Continue that needs a check is the act")
    void noProbeBeforeTheAct() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        assertTrue(setup(d).firstRun(), "the first-run block is shown");
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.INSIDE));
        assertEquals(0, a.claudeProbes.size(), "choosing in the block runs nothing");
        d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        assertEquals(1, a.clientObservations.size(), "Screen 1's badges: the file check only");
        assertEquals(0, a.claudeProbes.size(), "no process on window open");
        assertEquals(0, a.authProbes.size());
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.BOTH));
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.INSIDE));
        assertEquals(0, a.claudeProbes.size(), "nor on choosing");
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(1, a.claudeProbes.size(), "Continue on the choice that needs it");
        assertEquals(0, a.authProbes.size(), "the sign-in check only after Installed passed");
        found(d, a);
        assertEquals(1, a.authProbes.size());
        assertFalse(a.authProbes.get(0).full(), "Safe analysis's environment");
    }

    // ---- ★ T8: stale results change nothing ----------------------------------------------------------------------------

    /** ★ T8. Witness: drop the probe-id comparison in {@code onClaudeObserved} — the old result paints the check. */
    @Test
    @DisplayName("★ T8: a result for an old probe — after Check again, Back or Cancel — changes nothing")
    void staleResultsChangeNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.INSIDE));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        long first = a.claudeProbes.get(0).probe();
        d.post(new SessionEvents.AiSetupCheckAgain(AiSetupState.CheckKind.INSTALLED));
        assertEquals(AiSetupLines.ALREADY_CHECKING, setup(d).line(), "at most one probe of a kind");
        assertEquals(1, a.claudeProbes.size());
        d.post(new SessionEvents.AiSetupBack(Step.INSIDE));
        d.post(new SessionEvents.ClaudeObserved(first, SessionEvents.ProbeOutcome.FOUND, "9.9.9", 1));
        assertEquals(AiSetupState.Installed.UNCHECKED, setup(d).installed().verdict(), "★ after Back: stale");
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        long second = a.claudeProbes.get(1).probe();
        assertNotEquals(first, second);
        d.post(new SessionEvents.ClaudeObserved(first, SessionEvents.ProbeOutcome.NOT_FOUND, "", 0));
        assertEquals(AiSetupState.Installed.RUNNING, setup(d).installed().verdict(), "★ an old id: stale");
        d.post(new SessionEvents.ClaudeObserved(second, SessionEvents.ProbeOutcome.FOUND, "2.1.282", 2));
        assertEquals("2.1.282", setup(d).installed().version());
        long auth = a.authProbes.get(0).probe();
        d.post(new SessionEvents.AiSetupCancelled("test"));
        d.post(new SessionEvents.ClaudeAuthObserved(auth, SessionEvents.AuthOutcome.READ, true,
                AiSetupState.Method.CLAUDE_AI, AiSetupState.Plan.MAX, AiSetupState.KeySource.NONE));
        assertEquals(AiSetupState.SignedIn.RUNNING, setup(d).signedIn().verdict(), "★ after Cancel: stale");
    }

    /** The sign-in check's verdicts: never "signed in" for an API key or a key helper (§9). */
    @Test
    @DisplayName("the sign-in verdicts: a subscription is signed in; an API key or helper is a key; signed out; unreadable")
    void signInVerdicts() {
        record Case(SessionEvents.AuthOutcome outcome, boolean in, AiSetupState.Method m, AiSetupState.SignedIn want) {
        }
        for (Case c : List.of(
                new Case(SessionEvents.AuthOutcome.READ, true, AiSetupState.Method.CLAUDE_AI, AiSetupState.SignedIn.SIGNED_IN),
                new Case(SessionEvents.AuthOutcome.READ, true, AiSetupState.Method.API_KEY, AiSetupState.SignedIn.KEY),
                new Case(SessionEvents.AuthOutcome.READ, true, AiSetupState.Method.API_KEY_HELPER, AiSetupState.SignedIn.KEY),
                new Case(SessionEvents.AuthOutcome.READ, false, AiSetupState.Method.NONE, AiSetupState.SignedIn.NOT_SIGNED_IN),
                new Case(SessionEvents.AuthOutcome.READ, true, AiSetupState.Method.OTHER, AiSetupState.SignedIn.UNKNOWN),
                new Case(SessionEvents.AuthOutcome.UNREADABLE, false, AiSetupState.Method.NONE, AiSetupState.SignedIn.UNKNOWN),
                new Case(SessionEvents.AuthOutcome.TIMED_OUT, false, AiSetupState.Method.NONE, AiSetupState.SignedIn.UNKNOWN))) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = fresh(a, true);
            toInside(d, a, Choice.INSIDE);
            d.post(new SessionEvents.ClaudeAuthObserved(a.authProbes.get(0).probe(), c.outcome(), c.in(), c.m(),
                    AiSetupState.Plan.NONE, AiSetupState.KeySource.ANTHROPIC_API_KEY));
            assertEquals(c.want(), setup(d).signedIn().verdict(), c.toString());
            String words = AiSetupLines.signedInText(setup(d).signedIn());
            if (c.want() != AiSetupState.SignedIn.SIGNED_IN) assertFalse(words.contains("Signed in"), words);
            assertTrue(setup(d).insideMayProceed(), "the sign-in check is an observation, never a gate");
        }
        // Not found: the primary button stays disabled, and the node refuses it too
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, false);
        d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.INSIDE));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        d.post(new SessionEvents.ClaudeObserved(a.claudeProbes.get(0).probe(), SessionEvents.ProbeOutcome.NOT_FOUND, "", 0));
        assertFalse(setup(d).insideMayProceed());
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(0, a.profileApplies.size(), "not installed: nothing applied");
        assertEquals(0, a.authProbes.size(), "and no sign-in check");
    }

    // ---- ★ T12: the pending question --------------------------------------------------------------------------------

    /**
     * ★ T12. Witness: place on Finish alone (request {@code PlacePendingDraftEffect} in {@code onContinue}) — it is placed
     * before the Apply and the mode, so Claude Code's node does not know its binding; or append instead of replace.
     */
    @Test
    @DisplayName("★ T12: Ask before set-up → wizard → Finish: exactly one draft, placed after the Apply and the mode, Send starts")
    void thePendingQuestionIsPlacedAfterFinish() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.AssistantDraftPending(7, "Q1", "ask"));
        d.post(new SessionEvents.AssistantDraftPending(8, "Q1", "ask"));      // a second Ask replaces the first
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        found(d, a);
        assertTrue(a.pendingPlaced.isEmpty(), "nothing is placed before Finish");
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(1, a.pendingPlaced.size(), "★ exactly one draft");
        assertEquals(8, a.pendingPlaced.get(0).draftRef());
        assertEquals(AssistantMode.LOCAL_AGENT, a.pendingPlaced.get(0).mode());
        List<String> order = a.performed.stream().map(SessionDriver::name).toList();
        assertTrue(order.indexOf("applyAssistantProfile") < order.indexOf("chooseAssistantMode")
                && order.indexOf("chooseAssistantMode") < order.indexOf("placePendingDraft"), "★ " + order);
        assertFalse(setup(d).open(), "OD-77.2: a question waits, so no Summary — the window closes");
        assertEquals(AiSetupLines.placedLine(AssistantProfile.SAFE), setup(d).line());

        // the frame, as it would: the settings reported, the mode, then the draft composed for Claude Code
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "assistant profile"));
        d.post(new SessionEvents.LocalAgentDraftComposed(80, "Q1", null, 0, LocalAgentSessionTest.consent(), "placed"));
        LocalAgentSessionTest.startToReady(d, a);
        d.post(new SessionEvents.LocalAgentSendRequested(5, 80, SessionEvents.SlashClass.TEXT, null,
                LocalAgentSessionTest.consent(), List.of(80L)));
        assertEquals(1, a.agentSends.size(), "★ Send starts a turn: " + d.snapshot().localAgent().verdict());
        assertNotEquals("STALE_DRAFT", d.snapshot().localAgent().verdict());

        // Cancel returns it, with one line
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        e.post(new SessionEvents.AssistantDraftPending(9, "Q1", "ask"));
        e.post(new SessionEvents.AiSetupCancelled("test"));
        assertEquals(AiSetupLines.CANCELLED_ASK, setup(e).line());
        assertEquals(AiSetupState.Pending.NONE, setup(e).pending());
        e.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        e.post(new SessionEvents.AiSetupChoiceMade(Choice.INSIDE));
        e.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        found(e, b);
        e.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertTrue(b.pendingPlaced.isEmpty(), "a cancelled question is never placed later");
    }

    /**
     * ★ Authoring walk (2026-10-18, shots 03/15): "Start building" with nothing set up was a dead end — a second "No AI…"
     * line and two template buttons. It now opens the wizard with the Build question waiting, the same first-use door a
     * Send uses; the wizard says plainly that Safe cannot write code and preselects Claude inside with Full Claude Code,
     * whose confirmation is still asked; after Finish the build pathway runs as a press would (the starter catalogue is
     * read, then the draft is placed — never sent). Witnesses: (1) drop {@code build ||} from {@code question} in
     * {@code AiSetup.onStartPageSent} — the wizard does not open; (2) drop the {@code authoringWaiting} preselection in
     * {@code openWizard} — Safe analysis is selected; (3) compose S7 at once in {@code StartPagePrompt.onProfileObserved}
     * — no catalogue read.
     */
    @Test
    @DisplayName("★ Start building with nothing set up opens the wizard, Full recommended and confirmed; then the pathway runs")
    void startBuildingOpensTheWizardWithFullRecommended() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(StartPagePromptTest.press(1, "S7", 7, false, true, false, false));
        assertTrue(setup(d).open(), "the first-use door opens for Build");
        assertEquals(AiSetupState.PendingKind.START_BOX, setup(d).pending().kind(), "the Build question waits");
        assertEquals(0, a.startFallbacks.size(), "no no-AI dead end beside the wizard");
        assertEquals(Choice.INSIDE, setup(d).choice(), "Claude inside is preselected");
        assertEquals(Posture.FULL, setup(d).posture(), "Full Claude Code is preselected: Safe cannot write code");
        assertEquals(AiSetupLines.BUILD_NEEDS_FULL, setup(d).line(), "and the wizard says why, plainly");
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(Step.INSIDE, setup(d).step());
        assertEquals(AiSetupLines.BUILD_NEEDS_FULL, setup(d).line(), "said on the posture screen too");
        assertTrue(a.claudeProbes.get(a.claudeProbes.size() - 1).full(), "checked in the Full environment");
        found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(0, a.profileApplies.size(), "★ nothing is applied before Full is confirmed");
        assertEquals(1, a.fullConfirms.size(), "the existing confirmation is asked");
        d.post(new SessionEvents.AssistantFullProfileAnswered(a.fullConfirms.get(0).request(), true));
        assertEquals(List.of(AssistantProfile.FULL), a.profileApplies.stream()
                .map(SessionEffects.ApplyAssistantProfileEffect::profile).toList());
        StartPageCarryTest.reportedAfterApply(d, AssistantProfile.FULL, true, false);
        assertFalse(setup(d).open(), "the window closes: a question waits, so no Summary");
        assertEquals(0, a.startComposes.size(), "not composed before the starter catalogue is read");
        var flow = a.startFlows.get(a.startFlows.size() - 1);
        assertEquals("S4", flow.entryId(), "the build pathway's flow: read the starter catalogue");
        assertEquals(1, flow.request());
        d.post(new SessionEvents.StartPageFlowDone(1, SessionEvents.FlowOutcome.DONE, 0, 0, 11));
        assertEquals(1, a.startComposes.size(), "then the Build draft is placed");
        assertEquals("S7", a.startComposes.get(0).entryId());
        assertEquals(11, a.startComposes.get(0).templateNamesRef(), "naming the catalogue's templates");
        assertEquals(7, a.startComposes.get(0).textRef(), "with the person's words");
        assertEquals(0, a.agentSends.size(), "placed, never sent");

        // a plain question still opens the wizard with Safe as it always did: the recommendation is Build's alone
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        e.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        assertEquals(Posture.SAFE, setup(e).posture());
        assertNotEquals(AiSetupLines.BUILD_NEEDS_FULL, setup(e).line());
    }

    /**
     * ★ Owner follow-up (2026-10-18): a Build question preselects Full in Claude Code's own {@code acceptEdits} — edits
     * accepted by Claude Code in the folder, other approvals follow its rules — and Apply WRITES that mode (before, the wizard's
     * mode was never written, so the live authoring run asked 14 times under {@code default}). The person can still pick
     * {@code default}; a plain question is not preselected; the Full confirmation is still asked. Witnesses: (1) drop
     * {@code fullMode = AiSetupLines.BUILD_FULL_MODE} in {@code openWizard} — the mode is {@code default}; (2) pass null
     * for the mode in {@code onFullProfileAnswered}'s Apply — the written mode is lost.
     */
    @Test
    @DisplayName("★ a Build question preselects Full acceptEdits, applied as chosen; default still pickable; a plain one is not")
    void buildPreselectsAcceptEdits() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(StartPagePromptTest.press(1, "S7", 7, false, true, false, false));
        assertEquals(Posture.FULL, setup(d).posture());
        assertEquals("acceptEdits", setup(d).fullMode(), "Claude Code's own acceptEdits is preselected");
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(0, a.profileApplies.size(), "the Full confirmation is still asked first");
        d.post(new SessionEvents.AssistantFullProfileAnswered(a.fullConfirms.get(0).request(), true));
        assertEquals("acceptEdits", a.profileApplies.get(0).fullMode(), "★ the mode is written as chosen");

        // the person may still pick default
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        e.post(StartPagePromptTest.press(1, "S7", 7, false, true, false, false));
        e.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        e.post(new SessionEvents.AiSetupPostureChosen(Posture.FULL, "default"));
        assertEquals("default", setup(e).fullMode());
        found(e, b);
        e.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        e.post(new SessionEvents.AssistantFullProfileAnswered(b.fullConfirms.get(0).request(), true));
        assertEquals("default", b.profileApplies.get(0).fullMode());

        // a plain question: no preselection, and not after a cancelled Build either
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = fresh(c, true);
        f.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        assertEquals("default", setup(f).fullMode());
        FakeSessionAdapter g = new FakeSessionAdapter();
        SessionDriver h = fresh(g, true);
        h.post(StartPagePromptTest.press(1, "S7", 7, false, true, false, false));
        h.post(new SessionEvents.AiSetupCancelled("test"));
        h.post(StartPagePromptTest.press(2, "NONE", 7, false, true, false, false));
        assertEquals(Posture.SAFE, setup(h).posture());
        assertEquals("default", setup(h).fullMode(), "a plain question never inherits Build's preselection");
    }

    /** Full Claude Code set up and RUNNING in {@code mode}: the profile, what can answer, and a session at READY. */
    static SessionDriver fullRunning(FakeSessionAdapter a, LocalAgentPosture.FullMode mode) {
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.AiSetupStartupObserved(true, Client.NONE, false));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.FULL));
        d.post(new SessionEvents.StartPageAvailability(false, true, false, false));
        LocalAgentFullPostureTest.startFull(d, a, mode);
        assertEquals("READY", d.snapshot().localAgent().phase());
        return d;
    }

    static SessionEvents.StartPagePromptSent build(long request, LocalAgentPosture.FullMode mode) {
        return new SessionEvents.StartPagePromptSent(request, AiSetup.BUILD_ENTRY, 7, LocalAgentFullPostureTest.full(mode),
                true, false, true, true);
    }

    /**
     * ★ Owner decision (2026-10-18): with Full Claude Code ALREADY set up and running in {@code default}, Start building
     * still offers {@code acceptEdits} — the hands-on session ran entirely in default because the Build preselection lives
     * only in the wizard, which an assistant that can answer never opens. The offer is aiSetup's (rule 9); the question
     * still goes on to Claude. Keep asking changes nothing; Switch applies Full with acceptEdits through the one performer
     * as a quick switch (no second consent: Full is confirmed), and localAgentSession restarts the running session at
     * once on the new mode. Witness: put back {@code if (canAnswer) return false;} in {@code AiSetup.onStartPageSent} —
     * no offer is published and the first assertion fails.
     */
    @Test
    @DisplayName("★ Full already in default: Start building offers acceptEdits; Keep asking keeps it; Switch writes it and restarts")
    void fullInDefaultIsOfferedAcceptEditsForBuild() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fullRunning(a, LocalAgentPosture.FullMode.DEFAULT);
        int flows = a.startFlows.size();
        d.post(build(1, LocalAgentPosture.FullMode.DEFAULT));
        assertTrue(d.snapshot().profiles().buildModeOffered(), "★ the offer is published");
        assertFalse(setup(d).open(), "no wizard: Full is already set up");
        assertEquals(0, a.fullConfirms.size(), "no second consent");
        assertEquals(flows + 1, a.startFlows.size(), "the Build question still goes on (the starter catalogue is read)");
        assertEquals(0, a.profileApplies.size(), "never switched without the person");

        // Keep asking: the mode is unchanged and the offer is gone
        long offer = d.snapshot().profiles().buildModeOffer();
        d.post(new SessionEvents.AiSetupBuildModeAnswered(offer, false));
        assertFalse(d.snapshot().profiles().buildModeOffered());
        assertEquals(0, a.profileApplies.size(), "★ Keep asking writes nothing");
        assertEquals(0, LocalAgentSessionTest.kills(a).size(), "and restarts nothing");
        d.post(new SessionEvents.AiSetupBuildModeAnswered(offer, true));
        assertEquals(0, a.profileApplies.size(), "an answered offer is stale");

        // Start building again: offered again; Switch writes acceptEdits through the one Apply, as a quick switch
        d.post(build(2, LocalAgentPosture.FullMode.DEFAULT));
        long again = d.snapshot().profiles().buildModeOffer();
        assertTrue(again != 0 && again != offer, "a new offer");
        d.post(new SessionEvents.AiSetupBuildModeAnswered(again, true));
        assertEquals(1, a.profileApplies.size());
        var apply = a.profileApplies.get(0);
        assertEquals(AssistantProfile.FULL, apply.profile());
        assertEquals("acceptEdits", apply.fullMode(), "★ the mode written is acceptEdits (never bypassPermissions)");
        assertEquals(ProfileOrigin.MENU, apply.origin(), "applied as the person's own switch");
        assertEquals(0, a.fullConfirms.size(), "Full was confirmed already: no second consent");
        assertFalse(d.snapshot().profiles().buildModeOffered());
        // the performer reports the consent, then the result: the running session restarts at once on acceptEdits
        d.post(new SessionEvents.LocalAgentConsentChanged(LocalAgentFullPostureTest.full(LocalAgentPosture.FullMode.ACCEPT_EDITS)));
        d.post(new SessionEvents.AssistantProfileApplied(901, apply.request(), AssistantProfile.FULL, true, "",
                ProfileOrigin.MENU));
        assertEquals(1, LocalAgentSessionTest.kills(a).size(), "★ the old process stopped");
        var restarted = a.agentStarts.get(a.agentStarts.size() - 1);
        assertEquals(2, a.agentStarts.size(), "★ and a new one started");
        assertEquals(LocalAgentPosture.FullMode.ACCEPT_EDITS, restarted.posture().fullMode(), "★ in acceptEdits");
    }

    /** ★ No offer when Full is not in default (acceptEdits already, or Safe analysis), or for a plain question. */
    @Test
    @DisplayName("★ no Build-mode offer for acceptEdits, Safe analysis or a plain question")
    void noBuildModeOfferOtherwise() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fullRunning(a, LocalAgentPosture.FullMode.ACCEPT_EDITS);
        d.post(build(1, LocalAgentPosture.FullMode.ACCEPT_EDITS));
        assertFalse(d.snapshot().profiles().buildModeOffered(), "already acceptEdits");
        d.post(new SessionEvents.StartPagePromptSent(2, "NONE", 7,
                LocalAgentFullPostureTest.full(LocalAgentPosture.FullMode.DEFAULT), true, false, true, true));
        assertFalse(d.snapshot().profiles().buildModeOffered(), "a plain question is not Build");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = LocalAgentSessionTest.driver(b);
        e.post(new SessionEvents.AiSetupStartupObserved(true, Client.NONE, false));
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        e.post(new SessionEvents.StartPageAvailability(false, true, false, false));
        e.post(StartPagePromptTest.press(1, AiSetup.BUILD_ENTRY, 7, true, true, false, true));
        assertFalse(e.snapshot().profiles().buildModeOffered(), "Safe analysis: not this offer");
    }

    /**
     * ★ Authoring walk (shot 03): a no-AI fallback says "No AI assistant can answer here yet" ONCE — as the fallback
     * row's label — not again as the page's note. Witness: put the sentence back as {@code note} in
     * {@code StartPagePrompt.onSent} — the note repeats it.
     */
    @Test
    @DisplayName("★ the no-AI fallback is not said twice: the note stays empty, the effect carries the words")
    void theNoAiFallbackIsSaidOnce() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, false);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.OFF));      // the person chose No AI
        d.post(StartPagePromptTest.press(1, "S7", 7, false, false, false, false));
        assertFalse(setup(d).open(), "No AI was chosen: no wizard");
        assertEquals(1, a.startFallbacks.size(), "the build pathway's no-AI buttons");
        assertEquals("", d.snapshot().startPage().note(), "★ not said a second time as the note");
        assertEquals(telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt.NO_AI_FALLBACK,
                a.startFallbacks.get(0).why());
    }

    /** §12 for the start box: carried behind the wizard, placed only after Finish, kept in the box on Cancel. */
    @Test
    @DisplayName("★ T12: the start box's question waits behind the wizard; Cancel keeps it in the box; Finish places it")
    void theStartBoxQuestionWaits() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        assertTrue(setup(d).open());
        assertEquals(AiSetupState.PendingKind.START_BOX, setup(d).pending().kind());
        assertEquals(AiSetupLines.QUESTION_WAITS, d.snapshot().startPage().note());
        assertEquals(0, a.startFallbacks.size(), "no no-AI fallback beside the wizard");
        d.post(new SessionEvents.AiSetupCancelled("test"));
        assertEquals(AiSetupLines.CANCELLED_BOX, d.snapshot().startPage().note(), "★ returned, not dropped silently");

        // an Apply from another surface while the wizard holds the question places nothing: it waits for Finish (§12)
        FakeSessionAdapter w = new FakeSessionAdapter();
        SessionDriver x = fresh(w, true);
        x.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        x.post(new SessionEvents.AssistantProfileChosen(50, AssistantProfile.SAFE, ProfileOrigin.MENU));
        StartPageCarryTest.reportedAfterApply(x, AssistantProfile.SAFE, true, false);
        assertEquals(1, w.profileApplies.size(), "control: the other surface's Apply was written");
        assertEquals(0, w.startComposes.size(), "★ not placed: the wizard's Finish was not pressed");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        e.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        e.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        found(e, b);
        e.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        StartPageCarryTest.reportedAfterApply(e, AssistantProfile.SAFE, true, false);
        assertEquals(1, b.startComposes.size(), "placed after Finish, the Apply and the observation");
        assertEquals(7, b.startComposes.get(0).textRef());
        assertEquals(AssistantMode.LOCAL_AGENT, b.startComposes.get(0).target());
        assertEquals(0, b.agentSends.size(), "placed, never sent");
    }

    /**
     * The merge of the first-run polish with M77: an evidence question asked with no log open AND nothing set up meets
     * the no-log line first, then the wizard, then its placement after Finish. Witness: drop the {@code holds} check from
     * {@code AiSetup.onStartPageSent} — the wizard opens on the first press, behind the no-log line.
     */
    @Test
    @DisplayName("★ no log and nothing set up: the no-log line first, then the wizard (question carried), placed after Finish")
    void theNoLogLineComesBeforeTheWizard() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(StartPageNoLogHintTest.evidence(1, false));
        assertEquals(1, d.snapshot().startPage().noLogHeld(), "the no-log line is first");
        assertFalse(setup(d).open(), "★ the wizard does not open behind the no-log line");
        assertTrue(a.startComposes.isEmpty() && a.startFallbacks.isEmpty(), "nothing placed");

        d.post(new SessionEvents.StartPageNoLogAnswered(1, SessionEvents.NoLogChoice.ASK_ANYWAY));
        assertEquals(1, a.startContinues.size(), "Ask anyway continues the question");
        // what the performer posts: the same question, pressed again, acknowledged
        d.post(StartPageNoLogHintTest.evidence(2, true));
        assertEquals(0, d.snapshot().startPage().noLogHeld());
        assertTrue(setup(d).open(), "★ then the wizard, nothing being set up");
        assertEquals(AiSetupState.PendingKind.START_BOX, setup(d).pending().kind(), "the question is carried");
        assertEquals(AiSetupLines.QUESTION_WAITS, d.snapshot().startPage().note());
        assertTrue(a.startComposes.isEmpty(), "not placed before Finish");

        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        StartPageCarryTest.reportedAfterApply(d, AssistantProfile.SAFE, true, false);
        assertEquals(1, a.startComposes.size(), "★ placed after Finish");
        assertEquals(5, a.startComposes.get(0).textRef(), "the same words, by reference");
        assertEquals(0, a.agentSends.size(), "placed, never sent");
    }

    // ---- ★ T13: folder trust is not the wizard's ---------------------------------------------------------------------

    /** ★ T13. Witness: request a folder confirmation or trust from {@code aiSetup} — the effect lists fill. */
    @Test
    @DisplayName("★ T13: the wizard never asks about or records folder trust")
    void noFolderTrust() throws Exception {
        for (Choice ch : List.of(Choice.INSIDE, Choice.BOTH)) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = fresh(a, true);
            toInside(d, a, ch);
            d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
            assertEquals(0, a.agentConfirms.size());
            assertEquals(0, a.agentTrusts.size());
        }
        String node = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/session/node/AiSetup.java"));
        for (String never : List.of("TrustLocalAgentFolder", "ConfirmLocalAgentFolder", "LocalAgentFolderConfirmed",
                "claudeCodeTrusted")) {
            assertFalse(node.contains(never), "aiSetup must not touch " + never);
        }
    }

    // ---- ★ T23 and the lines: one owner ------------------------------------------------------------------------------

    /**
     * ★ T23. Witness: return "" from {@code status()}; or compose the mid-turn refusal outside the node (the wizard's line
     * stays empty).
     */
    @Test
    @DisplayName("★ T23: one line with nothing set up; a mid-turn Apply keeps the step and shows exactly one line")
    void oneLine() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        assertEquals(AiSetupLines.PANEL_NOTHING_SET_UP, setup(d).panelLine());
        assertEquals(AiSetupLines.PANEL_NOTHING_SET_UP, d.snapshot().profiles().status(), "the chip's line is the same one");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = new SessionDriver(b);
        e.post(new SessionEvents.AiSetupStartupObserved(false, Client.NONE, false));
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.IN_APP));
        e.post(new SessionEvents.StartPageAvailability(false, true, true, false));
        e.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(e.snapshot().assistant().busy(), "control: the in-app turn runs");
        toInside(e, b, Choice.INSIDE);
        e.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(0, b.profileApplies.size(), "nothing written mid-turn");
        assertEquals(Step.INSIDE, setup(e).step(), "the step does not advance");
        assertEquals(AiSetup.BUSY_IN_APP_TEXT, setup(e).line(), "★ the ONE line, the node's words");
        assertFalse(setup(e).applying());
    }

    /** §6.0: the AI line for every row of its table, from the node. */
    @Test
    @DisplayName("T15 (node side): the AI line for every row of §6.0's table")
    void theAiLine() {
        assertEquals("AI: not set up · Claude Code found", AiSetupLines.aiLine(Choice.NONE, AssistantProfile.NONE, true,
                false, false));
        assertEquals("AI: not set up", AiSetupLines.aiLine(Choice.NONE, AssistantProfile.NONE, false, false, false));
        assertEquals("AI: off", AiSetupLines.aiLine(Choice.NO_AI, AssistantProfile.OFF, true, false, false));
        assertEquals("AI: Claude inside " + AiSetupLines.PRODUCT + " (Safe analysis)",
                AiSetupLines.aiLine(Choice.INSIDE, AssistantProfile.SAFE, true, false, false));
        assertEquals("AI: Claude inside " + AiSetupLines.PRODUCT + " (Full Claude Code)",
                AiSetupLines.aiLine(Choice.INSIDE, AssistantProfile.FULL, true, false, false));
        assertEquals("AI: the in-app assistant (your API key) — no key yet",
                AiSetupLines.aiLine(Choice.IN_APP, AssistantProfile.IN_APP, false, false, false));
        assertTrue(AiSetupLines.aiLine(Choice.INSIDE, AssistantProfile.SAFE, true, false, true)
                .endsWith(" · your own client can connect"));
        assertEquals(AiSetupLines.SET_UP_LINK, AiSetupLines.aiLineLink(Choice.NONE));
        assertEquals(AiSetupLines.SET_UP_LINK, AiSetupLines.aiLineLink(Choice.NO_AI));
        assertEquals(AiSetupLines.CHANGE_LINK, AiSetupLines.aiLineLink(Choice.INSIDE));
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        assertEquals("AI: not set up · Claude Code found", setup(d).aiLine(), "published by the node");
    }

    // ---- the first-run block ---------------------------------------------------------------------------------------

    /**
     * §6.0, OD-77.1/.9: the block is a state of the start page, offered once while nothing is set up, never under
     * {@code --rest}. Witness: drop the {@code restLaunch} check in {@code firstRun} — an agent's process shows it.
     */
    @Test
    @DisplayName("★ the first-run block: shown while nothing is set up, never under --rest; Not now and No AI end it")
    void theFirstRunBlock() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        assertTrue(setup(d).firstRun());
        d.post(new SessionEvents.AiSetupFirstRunDismissed());
        assertFalse(setup(d).firstRun(), "Not now");
        assertEquals(1, a.dismissedWrites.size(), "stored, machine tier");

        for (SessionEvents.AiSetupStartupObserved startup : List.of(
                new SessionEvents.AiSetupStartupObserved(false, Client.NONE, true),
                new SessionEvents.AiSetupStartupObserved(true, Client.NONE, false),
                new SessionEvents.AiSetupStartupObserved(false, Client.CODEX, false))) {
            FakeSessionAdapter b = new FakeSessionAdapter();
            SessionDriver e = new SessionDriver(b);
            e.post(startup);
            e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
            e.post(new SessionEvents.StartPageAvailability(false, true, false, false));
            assertFalse(setup(e).firstRun(), "★ not offered: " + startup);
        }

        // No AI + Continue from the block: Off at once, no window
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = fresh(c, true);
        f.post(new SessionEvents.AiSetupChoiceMade(Choice.NO_AI));
        f.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(AssistantProfile.OFF, c.profileApplies.get(0).profile());
        assertEquals(0, c.setupShows.size(), "no window: there is nothing to guide");
        assertFalse(setup(f).open());
        f.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.OFF));
        assertFalse(setup(f).firstRun());
        assertEquals("AI: off", setup(f).aiLine());

        // Claude inside + Continue from the block: the window at Screen 2a, the check started
        FakeSessionAdapter n = new FakeSessionAdapter();
        SessionDriver o = fresh(n, false);
        assertEquals(Choice.NONE, setup(o).choice(), "claude not found: nothing preselected");
        o.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(0, n.profileApplies.size() + n.claudeProbes.size(), "nothing chosen in the block: Continue does nothing");
        assertFalse(setup(o).open());

        FakeSessionAdapter g = new FakeSessionAdapter();
        SessionDriver h = fresh(g, true);
        assertEquals(Choice.INSIDE, setup(h).choice(), "★ claude found: Claude inside preselected in the block");
        h.post(new SessionEvents.AiSetupChoiceMade(Choice.INSIDE));
        h.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertTrue(setup(h).open());
        assertEquals(Origin.FIRST_RUN, setup(h).origin());
        assertEquals(Step.INSIDE, setup(h).step());
        assertEquals(1, g.claudeProbes.size());
    }

    // ---- §6.4: the Summary's one suggested action ---------------------------------------------------------------------

    /**
     * ★ M77-2: the Summary offers one first action, chosen by the node — the sample (Claude inside, no log), the tour (No
     * AI) — and pressing it asks for the frame's existing action and closes. Nothing is sent. Witness: drop the
     * {@code RunSuggestedActionEffect} request in {@code onSuggestedTaken}.
     */
    @Test
    @DisplayName("★ the Summary's suggested action: the sample for Claude inside, the tour for No AI; nothing is sent")
    void theSuggestedAction() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        toInside(d, a, Choice.INSIDE);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(Step.SUMMARY, setup(d).step());
        assertEquals(AiSetupState.Suggested.OPEN_SAMPLE, setup(d).suggested());
        d.post(new SessionEvents.AiSetupSuggestedTaken(AiSetupState.Suggested.TOUR));
        assertEquals(0, a.suggestedRuns.size(), "a press for another suggestion is stale");
        d.post(new SessionEvents.AiSetupSuggestedTaken(AiSetupState.Suggested.OPEN_SAMPLE));
        assertEquals(1, a.suggestedRuns.size(), "★ the frame's existing action is asked for");
        assertEquals(AiSetupState.Suggested.OPEN_SAMPLE, a.suggestedRuns.get(0).suggested());
        assertFalse(setup(d).open(), "and the window closes");
        assertEquals(0, a.agentSends.size() + a.agentStarts.size() + a.assistantRequests.size(), "nothing is sent");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, false);
        e.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        e.post(new SessionEvents.AiSetupChoiceMade(Choice.NO_AI));
        e.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(AiSetupState.Suggested.TOUR, setup(e).suggested());
        e.post(new SessionEvents.AiSetupContinue(Step.SUMMARY));
        assertFalse(setup(e).open(), "Done closes");
        assertEquals(AiSetupState.Suggested.NONE, setup(e).suggested());
    }
}
