package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OA-3/OA-4 (spec §6.1–§6.2) — a walk's dialogue under walkPlayback on the REAL generated processor: the conversation is
 * part of the frozen definition, so an edit to it ends a showing walk rather than mixing revisions.
 */
class ConversationWalkPlaybackTest {

    static WalkSpec journey(String lastWords) {
        List<WalkSpec.Step> steps = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            steps.add(new WalkSpec.Step("step " + (i + 1), WalkSpec.View.NONE,
                    List.of(new WalkSpec.Target("status", "", null)), "s" + (i + 1), i == 0 ? null : "t" + (i + 1)));
        }
        var c = new WalkSpec.Conversation(1, WalkSpec.SCRIPTED, "", List.of(
                new WalkSpec.Turn("t1", "user", "What does the DEMO log contain?"),
                new WalkSpec.Turn("t2", "assistant", "Quote records."),
                new WalkSpec.Turn("t3", "assistant", lastWords)));
        return new WalkSpec("journey", "", "person", "", "", null, List.of(), steps, Map.of(), c);
    }

    static SessionDriver opened(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/logs/demo.yaml");
        return d;
    }

    @Test
    @DisplayName("an edit to the SHOWING walk's dialogue ends the showing, saying why — no prefix from another revision")
    void aDialogueEditEndsTheShowing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, journey("The breach is at record 16."), 1, "test"));
        assertTrue(d.snapshot().walkPlayback().showing());
        d.post(new SessionEvents.WalkDefinitionChanged("journey", journey("Edited: the breach is at record 16."), null));
        assertFalse(d.snapshot().walkPlayback().showing(), "a changed conversation is a changed walk");
        assertTrue(d.snapshot().walkPlayback().reason().contains("changed while it was showing"),
                d.snapshot().walkPlayback().reason());
    }

    private static SessionEvents.WalkTargetState target(boolean available) {
        return new SessionEvents.WalkTargetState(1, "status", "", "CURRENT", available, available ? "" : "not on screen");
    }

    private static void prepared(SessionDriver d, boolean available) {
        var w = d.snapshot().walkPlayback();
        d.post(new SessionEvents.WalkStepPrepared(w.ticket(), d.snapshot().logGeneration(), List.of(target(available)), ""));
    }

    @Test
    @DisplayName("OA-A9: a refused step keeps the ACCEPTED step, so its answer is never shown as if its evidence were")
    void aRefusedStepKeepsTheAcceptedPrefix() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, journey("The breach is at record 16."), 0, "test"));
        assertEquals(-1, d.snapshot().walkPlayback().accepted(), "nothing is shown until the first step settles");
        prepared(d, true);
        assertEquals(0, d.snapshot().walkPlayback().accepted());
        d.post(new SessionEvents.WalkNavigated(1));
        prepared(d, false);                                    // step 2's evidence is not on screen
        assertEquals("NOT_SHOWN", d.snapshot().walkPlayback().phase());
        assertEquals(1, d.snapshot().walkPlayback().step());
        assertEquals(0, d.snapshot().walkPlayback().accepted(), "the dialogue stays at step 1's prefix");
        d.post(new SessionEvents.WalkNavigated(1));
        prepared(d, true);
        assertEquals(2, d.snapshot().walkPlayback().accepted());
    }

    @Test
    @DisplayName("OA-4: a dialogue walk does not start over a pending live turn; an ordinary walk still does")
    void aPendingLiveTurnBlocksADialogueWalk() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertTrue(d.snapshot().assistant().busy());
        d.post(new SessionEvents.WalkPlayRequested(7, journey("x"), 0, "test"));
        assertFalse(d.snapshot().walkPlayback().showing());
        assertFalse(d.snapshot().walkPlayback().answer().accepted());
        assertTrue(d.snapshot().walkPlayback().answer().reason().contains("live assistant turn is in progress"));
        WalkSpec plain = new WalkSpec("plain", "", "person", "", "", null, List.of(),
                List.of(new WalkSpec.Step("s", WalkSpec.View.NONE, List.of(new WalkSpec.Target("status", "", null)))), Map.of());
        d.post(new SessionEvents.WalkPlayRequested(8, plain, 0, "test"));
        assertTrue(d.snapshot().walkPlayback().showing(), "a walk without dialogue plays beside a live turn, as before");
    }

    @Test
    @DisplayName("OA-A15: Ask about this evidence ends the demonstration and opens an EMPTY live conversation")
    void theHandoffStartsAFreshThread() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long before = d.snapshot().assistant().conversation();
        d.post(new SessionEvents.WalkPlayRequested(0, journey("x"), 2, "test"));
        d.post(new SessionEvents.AssistantHandoffRequested("test"));
        assertFalse(d.snapshot().walkPlayback().showing());
        assertTrue(d.snapshot().walkPlayback().reason().contains("Ask about this evidence"));
        assertEquals(before + 1, d.snapshot().assistant().conversation());
        assertEquals(List.of(), d.snapshot().assistant().entries(), "no simulated turn enters the live thread");
    }

    @Test
    @DisplayName("OA-4: a live question ends a dialogue walk through the session")
    void aLiveQuestionEndsTheDemonstration() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, journey("x"), 0, "test"));
        d.post(new SessionEvents.AssistantSendRequested(1, 1, AssistantLoopTest.route(true, 3, 20)));
        assertFalse(d.snapshot().walkPlayback().showing());
        assertTrue(d.snapshot().walkPlayback().reason().contains("live question"));
    }

    @Test
    @DisplayName("saving the same steps and the same dialogue changes nothing on screen")
    void anUnchangedSaveKeepsTheShowing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        d.post(new SessionEvents.WalkPlayRequested(0, journey("The breach is at record 16."), 1, "test"));
        d.post(new SessionEvents.WalkDefinitionChanged("journey", journey("The breach is at record 16."), null));
        assertTrue(d.snapshot().walkPlayback().showing());
    }
}
