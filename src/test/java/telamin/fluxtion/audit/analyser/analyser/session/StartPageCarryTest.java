package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.node.StartPagePrompt;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #3 (2026-10-17), on the REAL generated processor: the first question on the start page opens the
 * profile cards; the question is CARRIED through Apply and placed for the chosen assistant — Start armed where it
 * applies — without the person pressing the button again. Placing is all it does: nothing is sent to a model, nothing
 * starts. The facts after Apply are posted here as the frame's Apply performer posts them: what the consent now is, what
 * can answer, the profile in force (each its own operation, in that order).
 */
class StartPageCarryTest {

    static SessionDriver unchosen(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.NONE));
        return d;
    }

    /** The frame's report after a successful Apply of {@code p} (applyAssistantProfile → reportAssistantSettings). */
    static void reportedAfterApply(SessionDriver d, AssistantProfile p, boolean claudeFound, boolean inApp) {
        d.post(new SessionEvents.StartPageAvailability(p.claudeCode(), claudeFound, inApp, true));
        d.post(new SessionEvents.AssistantProfileObserved(p));
    }

    /**
     * ★ Witness: drop the carry in {@code StartPagePrompt.onSent} (the old "then ask again") — nothing is composed after
     * Apply and the person has to press the button a second time; the second assertion fails.
     */
    @Test
    @DisplayName("★ the question that opened the cards is placed for Safe analysis after Apply — not sent, nothing started")
    void theQuestionIsCarriedThroughApply() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(StartPagePromptTest.press(1, "NONE", 7, false, true, false, false));
        assertTrue(d.snapshot().profiles().open(), "control: the first question opens the cards");
        assertEquals(StartPagePrompt.CARRIED_NOTE, d.snapshot().startPage().note(), "the page says the question waits");
        assertEquals(0, a.startComposes.size(), "nothing is placed before a profile is chosen");

        d.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.SAFE));
        assertEquals(1, a.profileApplies.size(), "Apply wrote the settings");
        reportedAfterApply(d, AssistantProfile.SAFE, true, false);

        assertEquals(1, a.startComposes.size(), "the carried question is placed, without a second press");
        var placed = a.startComposes.get(0);
        assertEquals(1, placed.request(), "the person's own press");
        assertEquals(7, placed.textRef(), "with the words they typed");
        assertEquals(AssistantMode.LOCAL_AGENT, placed.target(), "for the profile's assistant");
        assertFalse(placed.chooseMode(), "the mode is the profile's to choose (assistantProfile asks for it)");
        assertEquals(0, a.agentStarts.size(), "nothing starts: Start and the folder question still come first");
        assertEquals("", d.snapshot().startPage().note());
    }

    @Test
    @DisplayName("the in-app profile with a key: placed in the in-app composer, never sent")
    void inAppIsPlacedToo() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(StartPagePromptTest.press(1, "NONE", 9, false, false, false, true));
        d.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.IN_APP));
        reportedAfterApply(d, AssistantProfile.IN_APP, false, true);
        assertEquals(1, a.startComposes.size());
        assertEquals(AssistantMode.IN_APP, a.startComposes.get(0).target());
        assertFalse(a.startComposes.get(0).popOut(), "a workspace was open: no pop-out");
    }

    /** ★ Witness: compose on Off — the "nothing is sent anywhere" profile would place a question for an assistant. */
    @Test
    @DisplayName("★ Off, a closed card set, or an assistant that still cannot answer: nothing placed, the box keeps its words")
    void nothingIsPlacedWhenNothingCanAnswer() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = unchosen(a);
        d.post(StartPagePromptTest.press(1, "NONE", 7, false, false, false, false));
        d.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.OFF));
        reportedAfterApply(d, AssistantProfile.OFF, false, false);
        assertEquals(0, a.startComposes.size(), "Off sends and places nothing");
        assertTrue(d.snapshot().startPage().note().contains("still in the box"), d.snapshot().startPage().note());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = unchosen(b);
        e.post(StartPagePromptTest.press(1, "NONE", 7, false, false, false, false));
        e.post(new SessionEvents.AssistantProfilesClosed("test"));
        assertEquals(StartPagePrompt.CARRY_DROPPED_NOTE, e.snapshot().startPage().note());
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.SAFE));
        assertEquals(0, b.startComposes.size(), "closed without Apply: dropped, never placed later");

        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = unchosen(c);
        f.post(StartPagePromptTest.press(1, "NONE", 7, false, false, false, false));
        f.post(new SessionEvents.AssistantProfileChosen(1, AssistantProfile.SAFE));
        reportedAfterApply(f, AssistantProfile.SAFE, false, false);       // claude is not installed
        assertEquals(0, c.startComposes.size(), "Claude Code cannot answer: not placed");
        assertTrue(f.snapshot().startPage().note().contains("claude was not found"), f.snapshot().startPage().note());
    }
}
