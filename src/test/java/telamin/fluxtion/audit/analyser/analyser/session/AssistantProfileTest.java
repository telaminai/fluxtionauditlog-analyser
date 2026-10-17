package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.AssistantProfileChoice;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 UX (owner, 2026-10-17) — {@code assistantProfile} on the REAL generated processor: when the four cards open by
 * themselves (once, on the first Start or question with no profile chosen, and never after one is chosen), and whether an
 * Apply may be applied now (never mid-turn). Facts in; the effects asked for and the snapshot out.
 *
 * <p>Each ★ test names its witness: the mutation of the node that turns it red (run, recorded in the spec).
 */
class AssistantProfileTest {

    static ProfileState profiles(SessionDriver d) {
        return d.snapshot().profiles();
    }

    static SessionDriver unchosen(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        return d;
    }

    // ---- ★ the first use ------------------------------------------------------------------------------------------

    /**
     * ★ Witness: make {@code firstUse} return false at once (no first-run trigger) — the overlay never opens and the
     * first assertion fails; or drop its {@code offered} check — the second Start opens it again.
     */
    @Test
    @DisplayName("★ with no profile chosen, the first Start opens the cards once; Start itself is refused as before")
    void theFirstStartOpensTheCardsOnce() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        d.post(new SessionEvents.LocalAgentStartRequested(1, LocalAgentSessionTest.consent(false, true, false, "p", 0)));
        assertTrue(profiles(d).open(), "the cards open by themselves on the first Start");
        assertEquals(ProfileState.FIRST_USE, profiles(d).why());
        assertEquals(1, a.profileShows.size(), "the panel is brought forward");
        assertFalse(a.profileShows.get(0).popOut(), "from the panel itself: no pop-out");
        assertEquals(0, a.agentStarts.size(), "nothing starts: Claude Code is not set up");
        assertEquals("SWITCHED_OFF", d.snapshot().localAgent().verdict(), "localAgentSession refuses Start as before");
        d.post(new SessionEvents.AssistantProfilesClosed("test"));
        assertFalse(profiles(d).open());
        d.post(new SessionEvents.LocalAgentStartRequested(2, LocalAgentSessionTest.consent(false, true, false, "p", 0)));
        assertFalse(profiles(d).open(), "only the first time: closed without choosing, they do not open again");
        assertEquals(1, a.profileShows.size());
    }

    /** ★ Witness: drop the {@code NONE} check in {@code firstUse} — the cards open with a profile chosen. */
    @Test
    @DisplayName("★ no auto-open once a profile is chosen (or before the frame has reported one)")
    void noAutoOpenAfterAProfileIsChosen() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(false, 3, 20)));
        assertFalse(profiles(d).open(), "nothing reported yet: 'not chosen' is a fact, never the node's default");
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.OFF));
        d.post(new SessionEvents.AssistantSendRequested(2, 2, AssistantLoopTest.route(false, 3, 20)));
        d.post(new SessionEvents.LocalAgentStartRequested(3, LocalAgentSessionTest.consent(false, true, false, "p", 0)));
        assertFalse(profiles(d).open(), "Off is a choice: the cards never open by themselves after it");
        assertEquals(0, a.profileShows.size());
        assertEquals(AssistantProfile.OFF, profiles(d).profile());
    }

    /**
     * ★ An assistant that can answer is never interrupted: a stale "not chosen" with a key in the route sends, and the
     * cards stay shut. Witness: drop the accepted-answer check in {@code onAssistantSend} — the cards open over a turn.
     */
    @Test
    @DisplayName("★ a Send an assistant accepts never opens the cards, whatever the last observation said")
    void anAcceptedSendIsNeverInterrupted() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(d.snapshot().assistant().busy(), "control: the turn runs");
        assertFalse(profiles(d).open(), "the person asked an assistant that can answer: no cards over it");
        assertEquals(0, a.profileShows.size());
        d.post(StartPagePromptTest.press(2, "NONE", 7, false, false, true, true));
        assertFalse(profiles(d).open(), "the start page's question goes to the assistant that can answer");
    }

    @Test
    @DisplayName("the first in-app question opens the cards too; the person's own Profile… opens them at any time")
    void aQuestionAndTheHeaderOpenThem() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(false, 3, 20)));
        assertTrue(profiles(d).open());
        assertTrue(d.snapshot().assistant().reason().contains("no provider is configured"), "the Send is refused by name");
        d.post(new SessionEvents.AssistantProfilesClosed("test"));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        d.post(new SessionEvents.AssistantProfilesRequested("assistant header"));
        assertTrue(profiles(d).open(), "the header opens them whatever is chosen");
        assertEquals(ProfileState.ASKED, profiles(d).why());
    }

    /**
     * ★ Witness: drop the interception in {@code StartPagePrompt.onSent} — the no-AI fallback is asked for beside the
     * cards. A flow entry (S3, open a bundle) keeps its own fallback: it works with no AI.
     */
    @Test
    @DisplayName("★ the start page's first question opens the cards (popped out, with nothing open) instead of the no-AI fallback")
    void theStartPagesFirstQuestionOpensTheCards() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(StartPagePromptTest.press(1, "S3", 0, false, false, false, false));
        assertFalse(profiles(d).open(), "a flow entry is not a question: it runs with no AI");
        assertEquals(1, a.startFlows.size());
        d.post(StartPagePromptTest.press(2, "NONE", 7, false, false, false, false));
        assertTrue(profiles(d).open(), "a question with nothing chosen opens the cards");
        assertTrue(a.profileShows.get(0).popOut(), "with no workspace the assistant pops out, as a start-page draft does");
        assertEquals(0, a.startFallbacks.size(), "and no no-AI fallback beside them");
        assertEquals(AssistantProfileChoice.FIRST_USE_NOTE, d.snapshot().startPage().note());
        d.post(StartPagePromptTest.press(3, "S6", 0, false, false, false, false));
        assertEquals(1, a.startFallbacks.size(), "the next question gets the fallback as before");
    }

    // ---- ★ Apply ----------------------------------------------------------------------------------------------------

    /**
     * ★ Witness: drop the {@code localAgent.turnInProgress()} check — the Apply is written mid-turn and the
     * no-effect assertion fails. (The in-app half: drop {@code assistantLoop.turnInProgress()}.)
     */
    @Test
    @DisplayName("★ a profile change mid-turn is refused by name, in either assistant; after the turn it applies")
    void aProfileChangeMidTurnIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        var start = LocalAgentSessionTest.startToReady(d, a);
        var t = LocalAgentSessionTest.send(d, a, 10);
        d.post(new SessionEvents.AssistantProfilesRequested("assistant header"));
        d.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.IN_APP));
        assertEquals(0, a.profileApplies.size(), "nothing is written while Claude is answering");
        assertEquals(AssistantProfileChoice.BUSY_LOCAL_AGENT_TEXT, profiles(d).refusal());
        assertTrue(profiles(d).open(), "the cards stay open, saying why");
        d.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), t.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(new SessionEvents.AssistantProfileChosen(2, AssistantProfile.IN_APP));
        assertEquals(1, a.profileApplies.size(), "after the turn the profile is applied");
        assertEquals(AssistantProfile.IN_APP, a.profileApplies.get(0).profile());
        assertFalse(profiles(d).open(), "applied: the cards close");
        assertEquals("", profiles(d).refusal());
        assertEquals(AssistantProfile.IN_APP, profiles(d).profile());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = new SessionDriver(b);
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.IN_APP));
        e.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(e.snapshot().assistant().busy(), "control: the in-app turn is in progress");
        e.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.SAFE));
        assertEquals(0, b.profileApplies.size());
        assertEquals(AssistantProfileChoice.BUSY_IN_APP_TEXT, e.snapshot().profiles().refusal());
    }

    @Test
    @DisplayName("an Apply with no card is refused; a stale Applied answer changes nothing")
    void applyNeedsACard() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.NONE));
        assertEquals(0, a.profileApplies.size());
        assertFalse(profiles(d).refusal().isEmpty());
        d.post(new SessionEvents.AssistantProfileApplied(99, 42, AssistantProfile.FULL, true, ""));
        assertEquals(AssistantProfile.NONE, profiles(d).profile(), "an answer to another request is refused");
    }
}
