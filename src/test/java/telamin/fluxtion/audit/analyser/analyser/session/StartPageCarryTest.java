package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #3 (2026-10-17), on the REAL generated processor — M77-4: through Set up AI, since the cards are
 * gone. The first question on the start page opens the wizard; the question is CARRIED through its Finish and placed
 * for the chosen assistant without the person pressing the button again. Placing is all it does: nothing is sent to a
 * model, nothing starts. The facts after an Apply are posted here as the frame's Apply performer posts them: what the
 * consent now is, what can answer, the profile in force (each its own operation, in that order).
 */
class StartPageCarryTest {

    static SessionDriver unchosen(FakeSessionAdapter a, boolean claudeFound) {
        return AiSetupTest.fresh(a, claudeFound);
    }

    /** The frame's report after a successful Apply of {@code p} (applyAssistantProfile → reportAssistantSettings). */
    static void reportedAfterApply(SessionDriver d, AssistantProfile p, boolean claudeFound, boolean inApp) {
        d.post(new SessionEvents.StartPageAvailability(p.claudeCode(), claudeFound, inApp, true));
        d.post(new SessionEvents.AssistantProfileObserved(p));
    }

    /** Screen 1 → Claude inside → the version check passes → Finish (Safe analysis). */
    static void finishInside(SessionDriver d, FakeSessionAdapter a) {
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.INSIDE));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        AiSetupTest.found(d, a);
        d.post(new SessionEvents.AiSetupContinue(Step.INSIDE));
    }

    /**
     * ★ Witness: drop the carry in {@code StartPagePrompt.onSent} (the old "then ask again") — nothing is composed after
     * Finish and the person has to press the button a second time; the second assertion fails.
     */
    @Test
    @DisplayName("★ the question that opened Set up AI is placed for Safe analysis after Finish — not sent, nothing started")
    void theQuestionIsCarriedThroughFinish() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, true);
        d.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        assertTrue(d.snapshot().aiSetup().open(), "control: the first question opens Set up AI");
        assertEquals(AiSetupLines.QUESTION_WAITS, d.snapshot().startPage().note(), "the page says the question waits");
        assertEquals(0, a.startComposes.size(), "nothing is placed before a choice is applied");

        finishInside(d, a);
        assertEquals(1, a.profileApplies.size(), "Finish wrote the settings");
        reportedAfterApply(d, AssistantProfile.SAFE, true, false);

        assertEquals(1, a.startComposes.size(), "the carried question is placed, without a second press");
        var placed = a.startComposes.get(0);
        assertEquals(1, placed.request(), "the person's own press");
        assertEquals(7, placed.textRef(), "with the words they typed");
        assertEquals(AssistantMode.LOCAL_AGENT, placed.target(), "for the profile's assistant");
        assertFalse(placed.chooseMode(), "the mode is the profile's to choose (aiSetup asks for it)");
        assertEquals(0, a.agentStarts.size(), "nothing starts: Start and the folder question still come first");
        assertEquals("", d.snapshot().startPage().note());
    }

    @Test
    @DisplayName("Other options, the in-app assistant with a key: placed in the in-app composer, never sent")
    void inAppIsPlacedToo() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, false);
        d.post(StartPagePromptTest.press(1, "NONE", 9, false, false, false, true));
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.IN_APP));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        d.post(new SessionEvents.AiSetupContinue(Step.OTHER, true));
        reportedAfterApply(d, AssistantProfile.IN_APP, false, true);
        assertEquals(1, a.startComposes.size());
        assertEquals(AssistantMode.IN_APP, a.startComposes.get(0).target());
        assertFalse(a.startComposes.get(0).popOut(), "a workspace was open: no pop-out");
    }

    /** ★ Witness: compose on Off — the "nothing is sent anywhere" choice would place a question for an assistant. */
    @Test
    @DisplayName("★ No AI, a cancelled wizard, or an assistant that still cannot answer: nothing placed, the box keeps its words")
    void nothingIsPlacedWhenNothingCanAnswer() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, false);
        d.post(StartPagePromptTest.press(1, "NONE", 7, false, false, false, false));
        d.post(new SessionEvents.AiSetupChoiceMade(Choice.NO_AI));
        d.post(new SessionEvents.AiSetupContinue(Step.CHOICE));
        reportedAfterApply(d, AssistantProfile.OFF, false, false);
        assertEquals(1, a.profileApplies.size(), "control: Off was applied");
        assertEquals(0, a.startComposes.size(), "Off sends and places nothing");
        assertTrue(d.snapshot().startPage().note().contains("still in the box"), d.snapshot().startPage().note());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = unchosen(b, true);
        e.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        e.post(new SessionEvents.AiSetupCancelled("test"));
        assertEquals(AiSetupLines.CANCELLED_BOX, e.snapshot().startPage().note());
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        assertEquals(0, b.startComposes.size(), "cancelled: returned, never placed later");

        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = unchosen(c, true);
        f.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        finishInside(f, c);
        reportedAfterApply(f, AssistantProfile.SAFE, false, false);       // claude went missing meanwhile
        assertEquals(0, c.startComposes.size(), "Claude Code cannot answer: not placed");
        assertTrue(f.snapshot().startPage().note().contains("claude was not found"), f.snapshot().startPage().note());
    }

    /**
     * ★ Final review #10 (2026-10-17): the carried question waits for the Apply, not for any report of the profile. While
     * the wizard is open the frame may report the profile in force again (a settings report, a theme change re-reading
     * the config); that report must neither place the question nor drop it. Witness (the review's M10): compose on any
     * {@code AssistantProfileObserved} — drop {@code || !carriedAwaitingObservation} in {@code onProfileObserved} — the
     * early report drops the question as "off" and nothing is placed after Finish.
     */
    @Test
    @DisplayName("★ a profile report BEFORE Finish neither places nor drops the carried question; Finish then places it")
    void onlyFinishReleasesTheCarriedQuestion() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a, true);
        d.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        assertTrue(d.snapshot().aiSetup().open());
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));        // a report while it is open
        d.post(new SessionEvents.StartPageAvailability(false, true, false, true));
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        assertEquals(0, a.startComposes.size(), "nothing is placed before Finish");
        assertEquals(AiSetupLines.QUESTION_WAITS, d.snapshot().startPage().note(), "and the question still waits");

        finishInside(d, a);
        reportedAfterApply(d, AssistantProfile.SAFE, true, false);
        assertEquals(1, a.startComposes.size(), "Finish places it, once");
        assertEquals(7, a.startComposes.get(0).textRef());
    }
}
