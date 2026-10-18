package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.EvidenceQuestion;
import telamin.fluxtion.audit.analyser.analyser.session.node.StartPageNoLogHint;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Robot re-walk #3 (2026-10-18), on the REAL generated processor: a start-page question about evidence, asked with no log
 * open, used to be placed (after the cards) with no hint that its answer would be general. Now {@code startPageNoLogHint}
 * holds it — no cards, no placement, nothing sent — until the person chooses Open the sample, Open a log… or Ask anyway.
 */
class StartPageNoLogHintTest {

    static SessionEvents.StartPagePromptSent evidence(long request, boolean acknowledged) {
        return new SessionEvents.StartPagePromptSent(request, "NONE", 5, StartPagePromptTest.consent(false), false, false,
                true, false, true, acknowledged);
    }

    /**
     * ★ Held: nothing opens, nothing is placed or sent, and the page has its one line. Witnesses: drop the
     * {@code holds} check from {@code AssistantProfileChoice.onStartPageSent} — the cards open; drop it from
     * {@code StartPagePrompt.onSent} — with an assistant that can answer, a draft is composed.
     */
    @Test
    @DisplayName("★ an evidence question with no log open is held: no cards, no draft, nothing sent — the page shows its line")
    void heldBeforeAnythingSeesIt() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = FirstRunGuideTest.unchosen(a);
        d.post(evidence(1, false));
        assertEquals(1, d.snapshot().startPage().noLogHeld(), "★ the page shows the line for press 1");
        assertFalse(d.snapshot().profiles().open(), "★ the cards do not open for it yet");
        assertTrue(a.profileShows.isEmpty());
        assertTrue(a.startComposes.isEmpty() && a.startFlows.isEmpty() && a.startFallbacks.isEmpty(), "★ nothing placed");
        assertTrue(a.startContinues.isEmpty());
        // an assistant that can answer: still held, never composed
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = new SessionDriver(b);
        e.post(new SessionEvents.AssistantProfileObserved(AssistantProfile.IN_APP));
        e.post(new SessionEvents.StartPagePromptSent(2, "NONE", 5, StartPagePromptTest.consent(false), false, true, true,
                false, true, false));
        assertEquals(2, e.snapshot().startPage().noLogHeld());
        assertTrue(b.startComposes.isEmpty(), "★ not placed for the in-app assistant either");
    }

    /** ★ Ask anyway: the question continues as asked. Witness: make ASK_ANYWAY clear without proceeding — no continue. */
    @Test
    @DisplayName("★ Ask anyway continues the question as it was asked; pressed again acknowledged, it is never held again")
    void askAnywayContinues() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = FirstRunGuideTest.unchosen(a);
        d.post(evidence(1, false));
        d.post(new SessionEvents.StartPageNoLogAnswered(1, SessionEvents.NoLogChoice.ASK_ANYWAY));
        assertEquals(1, a.startContinues.size(), "★ it continues");
        var c = a.startContinues.get(0);
        assertEquals(1, c.request());
        assertEquals("NONE", c.entryId());
        assertEquals(5, c.textRef(), "the same words, by reference");
        assertEquals(0, d.snapshot().startPage().noLogHeld(), "the line goes");
        // what the performer posts: the same question, acknowledged — now the cards open, as for any first question
        d.post(evidence(2, true));
        assertEquals(0, d.snapshot().startPage().noLogHeld(), "★ never held twice");
        assertTrue(d.snapshot().profiles().open(), "the first question opens the cards, as it always did");
    }

    /** ★ Open the sample / Open a log…: the person's open, then the question continues about the log that opened. */
    @Test
    @DisplayName("★ Open the sample or Open a log…: the open runs, and once a log has opened the question continues")
    void openThenContinue() {
        for (var choice : new SessionEvents.NoLogChoice[]{SessionEvents.NoLogChoice.OPEN_SAMPLE,
                SessionEvents.NoLogChoice.OPEN_LOG}) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = FirstRunGuideTest.unchosen(a);
            d.post(evidence(1, false));
            d.post(new SessionEvents.StartPageNoLogAnswered(1, choice));
            assertEquals(1, a.startFlows.size(), choice + ": the person's open");
            assertEquals(choice == SessionEvents.NoLogChoice.OPEN_SAMPLE ? StartPageNoLogHint.SAMPLE_FLOW
                    : StartPageNoLogHint.LOG_FLOW, a.startFlows.get(0).entryId());
            assertTrue(d.snapshot().startPage().noLogOpening(), "the line says it is opening");
            assertTrue(a.startContinues.isEmpty(), "★ nothing continues before a log has opened (a cancelled chooser)");
            AskDraftSessionTest.open(d, "/logs/demo.yaml");
            assertEquals(1, a.startContinues.size(), "★ " + choice + ": continued once the log opened");
            assertEquals(0, d.snapshot().startPage().noLogHeld());
        }
    }

    /** Never held: a log is open, the words are not about evidence, a stale answer. */
    @Test
    @DisplayName("not held with a log open or for a question about the tool; a stale answer changes nothing")
    void notHeld() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = FirstRunGuideTest.unchosen(a);
        d.post(new SessionEvents.StartPagePromptSent(1, "NONE", 5, StartPagePromptTest.consent(false), false, false, true,
                false, false, false));
        assertEquals(0, d.snapshot().startPage().noLogHeld(), "not about evidence");
        d.post(new SessionEvents.StartPageNoLogAnswered(9, SessionEvents.NoLogChoice.ASK_ANYWAY));
        assertTrue(a.startContinues.isEmpty(), "a stale answer");
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = FirstRunGuideTest.unchosen(b);
        AskDraftSessionTest.open(e, "/logs/demo.yaml");
        e.post(evidence(2, false));
        assertEquals(0, e.snapshot().startPage().noLogHeld(), "a log is open: the answer will be about it");
    }

    @Test
    @DisplayName("what reads as a question about evidence, and what does not")
    void evidenceWords() {
        for (String yes : new String[]{"What happened here?", "Why did the breach fire?", "explain this LOG",
                "which node failed", "What caused record 7?"}) {
            assertTrue(EvidenceQuestion.reads(yes), yes);
        }
        for (String no : new String[]{"What can this tool do?", "How do I install it?", "Teach me Fluxtion", "", "  ",
                "What does this tool do with my logs?",
                "What can the Fluxtion Audit Log Analyser do for me? Start with analyser_context."}) {
            assertFalse(EvidenceQuestion.reads(no), no);
        }
    }
}
