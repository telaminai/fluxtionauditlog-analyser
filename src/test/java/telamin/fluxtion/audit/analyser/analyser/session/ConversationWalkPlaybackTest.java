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
