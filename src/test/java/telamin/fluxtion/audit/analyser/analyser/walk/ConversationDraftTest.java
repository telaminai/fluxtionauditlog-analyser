package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** OA-3 (spec §7): what a composed dialogue IS — its kind follows what happened, and its preview is exactly what leaves. */
class ConversationDraftTest {

    static WalkSpec twoSteps() {
        var s = new WalkSpec.Step("c", WalkSpec.View.NONE, List.of(new WalkSpec.Target("status", "", null)));
        return new WalkSpec("w", "", "person", "", "", null, List.of(), List.of(s, s), Map.of());
    }

    @Test
    @DisplayName("written turns only: scripted; captured and untouched: recorded; captured then edited: edited-recording")
    void theKindFollowsWhatHappened() {
        ConversationDraft d = new ConversationDraft(twoSteps());
        assertNull(d.conversation(), "nothing is preselected: an untouched draft is no dialogue");
        d.addWritten("user", "q");
        assertEquals(WalkSpec.SCRIPTED, d.kind());
        ConversationDraft r = new ConversationDraft(twoSteps());
        r.addCaptured("user", "q");
        r.addCaptured("assistant", "a");
        assertEquals(WalkSpec.RECORDED, r.kind());
        r.edit(1, "a, improved");
        assertEquals(WalkSpec.EDITED_RECORDING, r.kind(), "editing captured words changes the kind");
        ConversationDraft mixed = new ConversationDraft(twoSteps());
        mixed.addCaptured("user", "q");
        mixed.addWritten("assistant", "made up");
        assertEquals(WalkSpec.EDITED_RECORDING, mixed.kind(), "captured and written mixed is not a recording");
    }

    @Test
    @DisplayName("a fresh draft for a walk without dialogue reveals nothing at every step (and can say so)")
    void aFreshDraftRevealsNothing() {
        ConversationDraft d = new ConversationDraft(twoSteps());
        assertEquals(java.util.Arrays.asList(null, null), d.through(), "found by the editor frame test: nulls are 'nothing yet'");
    }

    @Test
    @DisplayName("removing a turn moves any reveal that pointed at it back to the turn before")
    void removalKeepsRevealsCoherent() {
        ConversationDraft d = new ConversationDraft(twoSteps());
        d.addWritten("user", "q1");
        d.addWritten("assistant", "a1");
        d.addWritten("user", "q2");
        d.reveal(0, 1);
        d.reveal(1, 2);
        d.remove(1);
        assertEquals(List.of("t1", "t2"), d.through(), "step 1 now reveals t1; step 2's t3 is now t2");
        assertNull(WalkConversation.bindingProblem(d.conversation(),
                WalkConversation.withIds(List.of(twoSteps().steps().get(0).withBinding(null, d.through().get(0)),
                        twoSteps().steps().get(1).withBinding(null, d.through().get(1))))));
    }

    @Test
    @DisplayName("the export preview is exactly the label, the declared author and each turn's words — nothing else")
    void thePreviewIsWhatLeaves() {
        ConversationDraft d = new ConversationDraft(twoSteps());
        d.addCaptured("user", "Where is the first breach?");
        d.setAuthor("DEMO author");
        String preview = d.exportPreview();
        assertTrue(preview.startsWith("Recorded conversation — declared author: DEMO author"), preview);
        assertTrue(preview.contains("t1  user: Where is the first breach?"), preview);
        assertTrue(preview.contains("no hidden context, no system prompt, no key"), preview);
    }
}
