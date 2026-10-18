package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Access;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Client;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.LiveVerdict;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Origin;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Registration;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AccessOrigin;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CopyWhat;
import telamin.fluxtion.audit.analyser.analyser.session.node.AiSetup;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.AiSetupTest.fresh;
import static telamin.fluxtion.audit.analyser.analyser.session.AiSetupTest.setup;
import static telamin.fluxtion.audit.analyser.analyser.session.AiSetupTest.toInside;

/**
 * M77-3 (spec-ai-setup-wizard.md §6.3, §9, §16) — the external client path on the REAL generated processor: the one
 * owner of external access ({@code actionListener}), Screen 2b ({@code aiSetup}), registration behind its confirmation,
 * and the live check's watch. Facts in; the effects asked for and the snapshot out. Each ★ test names its witness — the
 * mutation that turns it red, each confirmed red on 2026-10-18.
 */
class AiSetupExternalTest {

    private boolean before;

    @BeforeEach
    void remember() {
        before = AiSetup.wizardForFirstUse();
        AiSetup.useWizardForFirstUse(true);
    }

    @AfterEach
    void restore() {
        AiSetup.useWizardForFirstUse(before);
    }

    /** What the SetExternalAccessEffect performer does: the setting written, and the switch reported. */
    static void written(SessionDriver d, FakeSessionAdapter a) {
        var w = a.accessWrites.get(a.accessWrites.size() - 1);
        d.post(new SessionEvents.RestSwitchObserved(w.on()));
    }

    /** Screen 1 → Your own client → Continue: Screen 2b. */
    static void toClient(SessionDriver d) {
        d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.CLIENT));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        assertEquals(Step.CLIENT, setup(d).step());
    }

    /** Part 2 on, through the confirmation, as every door does it. */
    static void accessOn(SessionDriver d, FakeSessionAdapter a, long request, AccessOrigin origin) {
        d.post(new SessionEvents.ExternalAccessRequested(request, true, origin));
        d.post(new SessionEvents.ExternalAccessAnswered(request, true));
        written(d, a);
    }

    // ---- ★ T9: external access has one owner, for every door ---------------------------------------------------------

    /**
     * ★ T9 (node side, OD-77.4). From each of the four doors, turning ON asks for the confirmation and writes nothing;
     * only a confirmed answer for THAT request writes it; Cancel writes nothing; OFF is written at once with no question;
     * a stale answer changes nothing. Witness: in {@code ActionListener.onExternalAccessRequested}, request the
     * {@code SetExternalAccessEffect} for an ON at once (today's menu checkbox) — the "nothing written before the answer"
     * assertion fails.
     */
    @Test
    @DisplayName("★ T9: every door's 'on' is confirmed first (Cancel the default), 'off' is at once — actionListener decides")
    void externalAccessHasOneOwner() {
        long request = 10;
        for (AccessOrigin door : AccessOrigin.values()) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = fresh(a, true);
            d.post(new SessionEvents.RestSwitchObserved(false));
            long r = ++request;
            d.post(new SessionEvents.ExternalAccessRequested(r, true, door));
            assertEquals(List.of(r), a.accessConfirms.stream().map(SessionEffects.ConfirmExternalAccessEffect::request)
                    .toList(), door + ": the confirmation is asked for");
            assertTrue(a.accessWrites.isEmpty(), door + ": nothing is written before the answer");
            assertTrue(d.snapshot().listener().confirming(), door + ": the listener says a confirmation shows");

            d.post(new SessionEvents.ExternalAccessAnswered(r, false));          // Cancel, Escape or the close
            assertTrue(a.accessWrites.isEmpty(), door + ": Cancel writes nothing");

            long r2 = ++request;
            d.post(new SessionEvents.ExternalAccessRequested(r2, true, door));
            d.post(new SessionEvents.ExternalAccessAnswered(r2 + 100, true));    // a stale answer
            assertTrue(a.accessWrites.isEmpty(), door + ": an answer for another request writes nothing");
            d.post(new SessionEvents.ExternalAccessAnswered(r2, true));
            assertEquals(1, a.accessWrites.size(), door + ": the confirmed answer writes it");
            assertTrue(a.accessWrites.get(0).on());
            written(d, a);

            long r3 = ++request;
            d.post(new SessionEvents.ExternalAccessRequested(r3, false, door));
            assertEquals(2, a.accessWrites.size(), door + ": off is written at once");
            assertFalse(a.accessWrites.get(1).on());
            assertEquals(2, a.accessConfirms.size(), door + ": off asks nothing");
        }
    }

    // ---- ★ T1 (M77-3 rows): your own client, and Both ------------------------------------------------------------------

    /**
     * ★ T1 (§7.2, the M77-3 rows). Your own client: 2b's Finish needs part 2 on, writes {@code assistant.setup.client}
     * and turns the panel Off through the one Apply; the Summary suggests copying a first question. Witness: persist no
     * client at 2b's Finish (drop the {@code PersistSetupClientEffect}) — the client write and the derived choice fail.
     */
    @Test
    @DisplayName("★ T1: Your own client — Finish needs access on, remembers the client and turns the panel off")
    void yourOwnClientWritesTheClientAndOff() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.RestSwitchObserved(false));
        toClient(d);
        assertEquals(Client.CLAUDE_CODE, setup(d).external().client(), "claude was found: Claude Code first");
        assertTrue(a.clientSetups.isEmpty(), "M77-3: Screen 2b, not today's dialog");
        assertFalse(setup(d).clientMayFinish(), "Finish needs part 2 on");
        d.post(new SessionEvents.AiSetupContinue(Step.CLIENT));
        assertTrue(a.profileApplies.isEmpty() && a.clientWrites.isEmpty(), "★ refused with access off");

        accessOn(d, a, 1, AccessOrigin.SETUP);
        assertEquals(Access.ON, setup(d).external().access());
        assertTrue(setup(d).clientMayFinish());
        d.post(new SessionEvents.AiSetupClientChosen(Client.CODEX));
        d.post(new SessionEvents.AiSetupContinue(Step.CLIENT));
        assertEquals(List.of(Client.CODEX), a.clientWrites.stream().map(SessionEffects.PersistSetupClientEffect::client)
                .toList(), "★ the client of part 1 is remembered");
        assertEquals(List.of(AssistantProfile.OFF), a.profileApplies.stream()
                .map(SessionEffects.ApplyAssistantProfileEffect::profile).toList(), "the panel is turned off");
        assertEquals(Step.SUMMARY, setup(d).step());
        assertEquals(Choice.CLIENT, setup(d).inForce(), "★ the derived choice round-trips: your own client");
        assertEquals(AiSetupState.Suggested.COPY_QUESTION, setup(d).suggested());
        d.post(new SessionEvents.AiSetupSuggestedTaken(AiSetupState.Suggested.COPY_QUESTION));
        assertEquals(CopyWhat.FIRST_QUESTION, a.copies.get(a.copies.size() - 1).what(), "copied, never sent");
        assertFalse(setup(d).open());
    }

    /**
     * ★ T12 (Both, §12). Both: 2a's Apply leads to Screen 2b — no dialog, the window stays open, and a waiting question is
     * NOT placed; 2b's Finish re-applies the profile and only then places it. Witness: M77-2's interim — finish the
     * wizard on 2a's Apply for Both (close and place) — the "not placed yet" assertion fails.
     */
    @Test
    @DisplayName("★ T12: Both — 2a applies Claude inside, 2b follows; the waiting question is placed only at 2b's Finish")
    void bothPlacesOnlyAtTheClientFinish() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.RestSwitchObserved(false));
        d.post(new SessionEvents.AssistantDraftPending(7, "Q1", "ask Q1 from records"));
        assertTrue(setup(d).open(), "the Ask opened the wizard");
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.BOTH));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        AiSetupTest.found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        assertEquals(List.of(AssistantProfile.SAFE), a.profileApplies.stream()
                .map(SessionEffects.ApplyAssistantProfileEffect::profile).toList());
        assertEquals(Step.CLIENT, setup(d).step(), "Both: Screen 2b next");
        assertTrue(setup(d).open());
        assertTrue(a.clientSetups.isEmpty(), "no interim dialog");
        assertTrue(a.pendingPlaced.isEmpty(), "★ nothing is placed behind the wizard");
        assertEquals(0, setup(d).finished(), "Finish has not been pressed");

        accessOn(d, a, 3, AccessOrigin.SETUP);
        d.post(new SessionEvents.AiSetupContinue(Step.CLIENT));
        assertEquals(List.of(AssistantProfile.SAFE, AssistantProfile.SAFE), a.profileApplies.stream()
                .map(SessionEffects.ApplyAssistantProfileEffect::profile).toList(), "2b's Finish re-applies 2a's profile");
        assertEquals(1, a.pendingPlaced.size(), "★ placed once, at 2b's Finish");
        assertFalse(setup(d).open(), "a waiting question closes the window (OD-77.2)");
        assertEquals(Choice.BOTH, setup(d).inForce());

        // Back on 2b goes to 2a for Both (it undoes nothing)
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        toInside(e, b, Choice.BOTH);
        e.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
        e.post(new SessionEvents.AiSetupBack(Step.CLIENT));
        assertEquals(Step.INSIDE, setup(e).step());
    }

    // ---- ★ T10 / T6: registration only behind its confirmation, on the person's press ---------------------------------

    /**
     * ★ T10 (node side, S5) and ★ T6 (S2). Entering 2b runs nothing; Check registration runs {@code mcp get} only on its
     * press; Register for me asks for the confirmation and runs nothing until it is confirmed; Cancel runs nothing; the
     * confirmed request runs the registration for THAT request; a stale result changes nothing. Witness: request the
     * {@code RegisterMcpEffect} on {@code AiSetupRegisterRequested} (no confirmation) — "nothing runs before the answer"
     * fails.
     */
    @Test
    @DisplayName("★ T10/T6: nothing runs on entering 2b; registration runs only after its confirmation, for that request")
    void registrationOnlyAfterItsConfirmation() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        toClient(d);
        assertTrue(a.registrationChecks.isEmpty() && a.registerConfirms.isEmpty() && a.registerRuns.isEmpty(),
                "★ T6: opening Screen 2b starts no process");

        d.post(new SessionEvents.AiSetupCheckAgain(AiSetupState.CheckKind.REGISTRATION));
        assertEquals(1, a.registrationChecks.size(), "mcp get only on its button");
        long probe = a.registrationChecks.get(0).probe();
        d.post(new SessionEvents.McpRegistrationObserved(probe + 9, Client.CLAUDE_CODE,
                SessionEvents.RegistrationOutcome.PRESENT));
        assertEquals(Registration.RUNNING, setup(d).external().registration(), "★ T8: a stale probe changes nothing");
        d.post(new SessionEvents.McpRegistrationObserved(probe, Client.CLAUDE_CODE,
                SessionEvents.RegistrationOutcome.ABSENT));
        assertEquals(Registration.ABSENT, setup(d).external().registration());

        d.post(new SessionEvents.AiSetupRegisterRequested());
        assertEquals(1, a.registerConfirms.size(), "the confirmation is asked for");
        assertFalse(a.registerConfirms.get(0).replace(), "nothing to replace: an add");
        assertTrue(a.registerRuns.isEmpty(), "★ nothing runs before the answer");
        assertEquals(AiSetupState.Confirming.REGISTER, setup(d).confirming(), "the wizard is inert behind it");
        long first = a.registerConfirms.get(0).request();
        d.post(new SessionEvents.RegisterAnswered(first, false));
        assertTrue(a.registerRuns.isEmpty(), "★ Cancel runs nothing");

        d.post(new SessionEvents.AiSetupRegisterRequested());
        long second = a.registerConfirms.get(1).request();
        d.post(new SessionEvents.RegisterAnswered(first, true));
        assertTrue(a.registerRuns.isEmpty(), "★ T8: an answer to an old confirmation runs nothing");
        d.post(new SessionEvents.RegisterAnswered(second, true));
        assertEquals(List.of(second), a.registerRuns.stream().map(SessionEffects.RegisterMcpEffect::request).toList());
        assertEquals(Registration.RUNNING, setup(d).external().registration());
        d.post(new SessionEvents.McpRegistrationChanged(first, Client.CLAUDE_CODE, true));
        assertEquals(Registration.RUNNING, setup(d).external().registration(), "★ T8: a stale result changes nothing");
        d.post(new SessionEvents.McpRegistrationChanged(second, Client.CLAUDE_CODE, true));
        assertEquals(Registration.REGISTERED, setup(d).external().registration());

        // a present registration offers a replace (remove, then add — both shown)
        d.post(new SessionEvents.AiSetupCheckAgain(AiSetupState.CheckKind.REGISTRATION));
        d.post(new SessionEvents.McpRegistrationObserved(a.registrationChecks.get(1).probe(), Client.CLAUDE_CODE,
                SessionEvents.RegistrationOutcome.PRESENT));
        d.post(new SessionEvents.AiSetupRegisterRequested());
        assertTrue(a.registerConfirms.get(2).replace());

        // I'll run it myself: copies the command, and the step says so
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        toClient(e);
        e.post(new SessionEvents.AiSetupCopyRequested(CopyWhat.REGISTER_COMMAND));
        assertEquals(Registration.COPIED, setup(e).external().registration());
        assertEquals(CopyWhat.REGISTER_COMMAND, b.copies.get(0).what());
        assertTrue(b.registerRuns.isEmpty() && b.registrationChecks.isEmpty(), "a copy runs nothing");
    }

    // ---- ★ T11 (node side) / T8: the live check's watch --------------------------------------------------------------

    /**
     * ★ T11 (node side, S8) and ★ T8. The watch is armed only when Screen 2b is current, access is on and part 3 is done;
     * a sighting for an old watch is stale; the current one is ✓, with whether a Full session ran meanwhile; turning
     * access off disarms it and says so; another window owning the endpoint shows its fail state; the 2-minute tip is a
     * fact for the current watch only. Witness: drop the {@code e.watch() != watch} comparison in {@code onClientSeen} —
     * the stale sighting turns the check ✓.
     */
    @Test
    @DisplayName("★ T11/T8: the watch is armed only for part 4 with access on; stale sightings and timers change nothing")
    void theLiveCheckWatch() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.RestSwitchObserved(false));
        toClient(d);
        d.post(new SessionEvents.AiSetupCopyRequested(CopyWhat.REGISTER_COMMAND));    // part 3 done
        assertTrue(a.watches.isEmpty(), "★ not armed while access is off");
        assertEquals(LiveVerdict.IDLE, setup(d).external().live().verdict());

        accessOn(d, a, 5, AccessOrigin.SETUP);
        assertEquals(1, a.watches.size(), "armed: part 4 is current and access is on");
        long w1 = a.watches.get(0).watch();
        assertEquals(LiveVerdict.WAITING, setup(d).external().live().verdict());
        assertEquals(List.of(w1), a.setupTimers.stream().map(SessionEffects.AiSetupTimerEffect::watch).toList());
        assertEquals(AiSetup.LIVE_TIP_SECONDS, a.setupTimers.get(0).seconds());

        // access off while waiting: disarmed, and part 4 says why
        d.post(new SessionEvents.ExternalAccessRequested(6, false, AccessOrigin.MENU));
        written(d, a);
        assertEquals(List.of(w1), a.unwatches.stream().map(SessionEffects.UnwatchExternalClientEffect::watch).toList());
        assertEquals(LiveVerdict.ACCESS_OFF, setup(d).external().live().verdict());
        d.post(new SessionEvents.ExternalClientSeen(w1, 1_000, 0));
        assertEquals(LiveVerdict.ACCESS_OFF, setup(d).external().live().verdict(), "★ a disarmed watch's sighting is stale");
        d.post(new SessionEvents.AiSetupTimerFired(w1));
        assertEquals(LiveVerdict.ACCESS_OFF, setup(d).external().live().verdict(), "a stale timer changes nothing");

        // on again: a NEW watch; the old one's sighting is still stale
        accessOn(d, a, 7, AccessOrigin.SETUP);
        long w2 = a.watches.get(1).watch();
        assertNotEquals(w1, w2);
        d.post(new SessionEvents.ExternalClientSeen(w1, 2_000, 0));
        assertEquals(LiveVerdict.WAITING, setup(d).external().live().verdict(), "★ an old watch's sighting is stale");
        d.post(new SessionEvents.AiSetupTimerFired(w2));
        assertEquals(LiveVerdict.WAITING_LONG, setup(d).external().live().verdict(), "the tips; the wait continues");

        // mcp get never runs while the watch is armed: Check registration disarms it first
        d.post(new SessionEvents.AiSetupCheckAgain(AiSetupState.CheckKind.REGISTRATION));
        assertTrue(a.unwatches.stream().anyMatch(u -> u.watch() == w2), "disarmed before mcp get");
        d.post(new SessionEvents.McpRegistrationObserved(a.registrationChecks.get(0).probe(), Client.CLAUDE_CODE,
                SessionEvents.RegistrationOutcome.PRESENT));
        long w3 = a.watches.get(2).watch();
        d.post(new SessionEvents.ExternalClientSeen(w3, 3_000, 4));
        AiSetupState.Live live = setup(d).external().live();
        assertEquals(LiveVerdict.SEEN, live.verdict());
        assertEquals(3_000, live.at());
        assertEquals(4, live.labelRef(), "the self-reported name, by reference (OD-77.8)");
        assertFalse(live.fullSessionSeen(), "no Full session ran in the panel");

        // another window owns the endpoint: the fail state instead of waiting for ever
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        toClient(e);
        e.post(new SessionEvents.AiSetupCopyRequested(CopyWhat.REGISTER_COMMAND));
        accessOn(e, b, 8, AccessOrigin.SETUP);
        e.post(new SessionEvents.McpReadinessObserved(SessionEvents.McpReadiness.OTHER_INSTANCE));
        assertEquals(Access.OTHER_INSTANCE, setup(e).external().access());
        assertEquals(LiveVerdict.OTHER_INSTANCE, setup(e).external().live().verdict());
        assertEquals(1, b.unwatches.size(), "disarmed");
        assertTrue(setup(e).aiLine().endsWith(" · another " + AiSetupLines.PRODUCT + " window holds the client connection"),
                setup(e).aiLine());
    }

    // ---- OD-77.11 / OD-77.5 ------------------------------------------------------------------------------------------

    /**
     * ★ OD-77.11 (§7.4). Cancel after THIS wizard turned access on keeps it on and offers Turn it off, one click with no
     * confirmation; access turned on by another door offers nothing. Witness: offer Turn it off whenever access is on at
     * Cancel — the menu-turned-on case fails.
     */
    @Test
    @DisplayName("★ OD-77.11: Cancel after the wizard turned access on keeps it on and offers Turn it off")
    void cancelOffersTurnItOff() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.RestSwitchObserved(false));
        toClient(d);
        accessOn(d, a, 1, AccessOrigin.SETUP);
        d.post(new SessionEvents.AiSetupCancelled("test"));
        assertEquals(1, a.accessWrites.size(), "what was turned on stays on: no roll back");
        assertTrue(setup(d).external().offerTurnOff(), "★ Turn it off is offered");
        assertEquals(AiSetupLines.ACCESS_STILL_ON, setup(d).line());
        d.post(new SessionEvents.ExternalAccessRequested(2, false, AccessOrigin.SETUP));    // Turn it off
        assertEquals(1, a.accessConfirms.size(), "off needs no confirmation");
        assertFalse(a.accessWrites.get(1).on());
        written(d, a);
        assertFalse(setup(d).external().offerTurnOff(), "done");
        assertEquals("", setup(d).line());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = fresh(b, true);
        e.post(new SessionEvents.RestSwitchObserved(false));
        accessOn(e, b, 1, AccessOrigin.MENU);                   // on before, from the AI menu
        toClient(e);
        e.post(new SessionEvents.AiSetupCancelled("test"));
        assertFalse(setup(e).external().offerTurnOff(), "★ the wizard did not turn it on: nothing to offer");
    }

    /**
     * ★ OD-77.5. No AI while access is on offers a ticked box; Continue with it ticked asks actionListener to turn access
     * off (as a door does); unticked, access stays. Witness: ignore the box in {@code onContinue} — the untick case
     * still asks, and fails.
     */
    @Test
    @DisplayName("★ OD-77.5: No AI offers 'Also turn off External MCP/REST access', ticked; the tick is honoured")
    void noAiOffersTurningAccessOff() {
        for (boolean tick : new boolean[]{true, false}) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = fresh(a, true);
            d.post(new SessionEvents.RestSwitchObserved(true));
            d.post(new SessionEvents.AiSetupRequested(Origin.MENU));
            d.post(new SessionEvents.AiSetupChoiceMade(Choice.NO_AI));
            assertTrue(setup(d).external().offerAlsoTurnOff(), "the box is shown");
            assertTrue(setup(d).external().alsoTurnOff(), "ticked by default");
            if (!tick) d.post(new SessionEvents.AiSetupAlsoTurnOffChosen(false));
            d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
            assertEquals(AssistantProfile.OFF, a.profileApplies.get(0).profile());
            assertEquals(tick ? 1 : 0, a.accessAsks.size(), "★ tick " + tick);
            if (tick) assertFalse(a.accessAsks.get(0).on());
        }
        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = fresh(c, true);
        f.post(new SessionEvents.RestSwitchObserved(false));
        f.post(new SessionEvents.AiSetupRequested(Origin.MENU));
        f.post(new SessionEvents.AiSetupChoiceMade(Choice.NO_AI));
        assertFalse(setup(f).external().offerAlsoTurnOff(), "access off: no box");
    }

    /** No supported launcher: parts 3–4 cannot be done; Finish without a client remembers no client. */
    @Test
    @DisplayName("no supported launcher: Finish without a client, and no client is remembered")
    void noLauncherFinishesWithoutAClient() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = fresh(a, true);
        d.post(new SessionEvents.AiClientsObserved(false, false));
        toClient(d);
        assertEquals(Registration.NO_LAUNCHER, setup(d).external().registration());
        assertTrue(setup(d).clientMayFinish(), "Finish without a client is offered");
        d.post(new SessionEvents.AiSetupRegisterRequested());
        assertTrue(a.registerConfirms.isEmpty(), "nothing to register");
        d.post(new SessionEvents.AiSetupContinue(Step.CLIENT));
        assertTrue(a.clientWrites.isEmpty(), "no client was set up");
    }
}
