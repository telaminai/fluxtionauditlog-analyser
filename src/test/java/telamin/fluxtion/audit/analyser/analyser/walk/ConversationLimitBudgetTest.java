package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review PR #150 R4: {@link WalkConversation#limit} offers the most recent turns that FIT, but it estimated a turn's
 * serialized size as twice its UTF-8 bytes. A control character serializes as a six-byte escape, so a recording of
 * such turns was offered whole and then refused by {@link WalkConversation#problem} at save.
 */
class ConversationLimitBudgetTest {

    /** ★ Witness: restore the "UTF-8 \u00D7 2" estimate — the offer is over 512 KB serialized and problem() refuses it. */
    @Test
    @DisplayName("★ what limit offers always passes the serialized-size check")
    void theOfferFitsWhenSerialized() {
        List<WalkConversation.Candidate> turns = new ArrayList<>();
        String controls = "\u0001".repeat(8_000);                        // 8 KB raw, 48 KB serialized
        for (int i = 0; i < 40; i++) turns.add(new WalkConversation.Candidate(i % 2 == 0 ? "user" : "assistant", controls, null));
        var offer = WalkConversation.limit(turns);
        assertTrue(offer.leftOut() > 0, "the recording is over the budget, so the oldest are left out");
        var c = new WalkSpec.Conversation(WalkSpec.CONVERSATION_V2, WalkSpec.RECORDED, "",
                offer.turns(), new WalkSpec.Source(WalkSpec.CLAUDE_CODE_SOURCE, "a-model", "1.0.0"));
        assertNull(WalkConversation.problem(c), "the offered turns are within every limit");
    }
}
