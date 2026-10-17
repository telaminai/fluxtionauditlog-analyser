package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (spec §6): conversation schema v2 — a recording from the local agent. Written ONLY with a source; v1 stays
 * byte-for-byte; no session id, path, account, cost or budget can be carried; limits truncate and keep the most recent.
 */
class ConversationV2Test {

    static WalkSpec.Source source() {
        return new WalkSpec.Source(WalkSpec.CLAUDE_CODE_SOURCE, "claude-sonnet-5", "2.1.282");
    }

    /** ★ Witness: write v2 for every walk (ConversationDraft.conversation always CONVERSATION_V2) — the v1 assertion fails. */
    @Test
    @DisplayName("★ v2 is written only with a source; a draft without one is v1")
    void v2OnlyWithASource() {
        WalkSpec walk = telamin.fluxtion.audit.analyser.analyser.config.WalkPersistenceTestAccess.sample("w");
        ConversationDraft plain = new ConversationDraft(walk);
        plain.addCaptured("user", "q");
        assertEquals(WalkSpec.CONVERSATION_VERSION, plain.conversation().version());
        assertNull(plain.conversation().source());
        ConversationDraft recorded = new ConversationDraft(walk);
        recorded.setSource(source());
        recorded.addCaptured("user", "q", false);
        recorded.addCaptured("assistant", "a", true);
        var c = recorded.conversation();
        assertEquals(WalkSpec.CONVERSATION_V2, c.version());
        assertEquals(source(), c.source());
        assertTrue(c.turns().get(1).truncated());
        assertNull(WalkConversation.problem(c));
        assertNotNull(WalkConversation.problem(new WalkSpec.Conversation(2, WalkSpec.RECORDED, "", c.turns(), null)),
                "a v2 without a source is refused");
        assertNotNull(WalkConversation.problem(new WalkSpec.Conversation(1, WalkSpec.RECORDED, "", c.turns(), source())),
                "a v1 with a source is refused");
    }

    @Test
    @DisplayName("the walk verb's input path accepts v2 with the same validation, and refuses a source field outside it")
    void parseV2() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 2);
        m.put("kind", "recorded");
        m.put("source", Map.of("kind", "claude-code", "model", "claude-sonnet-5", "cliVersion", "2.1.282"));
        m.put("turns", List.of(Map.of("id", "t1", "role", "user", "text", "q"),
                Map.of("id", "t2", "role", "assistant", "text", "a", "truncated", true)));
        var p = WalkConversation.parse(m);
        assertTrue(p.ok(), p.error());
        assertTrue(p.conversation().turns().get(1).truncated());
        Map<String, Object> leak = new LinkedHashMap<>(m);
        leak.put("source", Map.of("kind", "claude-code", "sessionId", "1234"));
        assertTrue(WalkConversation.parse(leak).error().contains("sessionId"), "no session id can be carried");
        Map<String, Object> v1 = new LinkedHashMap<>(m);
        v1.put("version", 1);
        assertTrue(WalkConversation.parse(v1).error().contains("'source' is not a conversation field"));
    }

    /** ★ Witness: no truncation (offer the text whole) — the 16 KB assertion fails. */
    @Test
    @DisplayName("★ a turn over 16 KB is truncated at a character boundary, marked; over 200 turns the most recent are offered")
    void limits() {
        String big = "é".repeat(10_000);                       // 20,000 bytes of UTF-8
        var offer = WalkConversation.limit(List.of(new WalkConversation.Candidate("assistant", big, null)));
        var t = offer.turns().get(0);
        assertTrue(t.truncated());
        assertTrue(t.text().endsWith(WalkConversation.TRUNCATED_MARK));
        assertTrue(t.text().getBytes(StandardCharsets.UTF_8).length <= WalkConversation.MAX_TURN_BYTES);
        assertFalse(t.text().contains("�"), "cut at a character boundary");
        List<WalkConversation.Candidate> many = new ArrayList<>();
        for (int i = 0; i < 250; i++) many.add(new WalkConversation.Candidate(i % 2 == 0 ? "user" : "assistant", "turn " + i, null));
        var kept = WalkConversation.limit(many);
        assertEquals(200, kept.turns().size());
        assertEquals(50, kept.leftOut(), "the oldest are left out and counted");
        assertEquals("turn 249", kept.turns().get(199).text(), "the most recent are kept");
        assertEquals("t1", kept.turns().get(0).id(), "ids renumbered from the first kept");
    }

    /** ★ Witness: skip revalidate on load — the dangling binding is played. */
    @Test
    @DisplayName("★ a binding that no longer names a turn is dropped on load and reported")
    void danglingBindingDropped() {
        WalkSpec base = telamin.fluxtion.audit.analyser.analyser.config.WalkPersistenceTestAccess.sample("w");
        var c = new WalkSpec.Conversation(2, WalkSpec.RECORDED, "", List.of(new WalkSpec.Turn("t1", "user", "q")), source());
        var steps = List.of(base.steps().get(0).withBinding("s1", "t1"), base.steps().get(1).withBinding("s2", "t9"));
        var r = WalkConversation.revalidate(base.withConversation(c, steps, base.updatedAt()));
        assertEquals(List.of("step 2's conversation binding named a turn that no longer exists; it was removed"), r.notes());
        assertNull(r.walk().steps().get(1).through());
        assertEquals("t1", r.walk().steps().get(0).through());
    }

    @Test
    @DisplayName("the model of every turn when they agree, else the distinct models in order")
    void models() {
        assertEquals("claude-sonnet-5", WalkConversation.models(List.of("claude-sonnet-5", "claude-sonnet-5")));
        assertEquals("claude-sonnet-5, claude-opus-5", WalkConversation.models(List.of("claude-sonnet-5", "claude-opus-5",
                "claude-sonnet-5")));
    }
}
