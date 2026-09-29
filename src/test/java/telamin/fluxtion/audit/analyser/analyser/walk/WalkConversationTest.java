package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** OA-3 (spec §6.1): the dialogue schema and its step binding, refused by name before anything is installed. */
class WalkConversationTest {

    static Map<String, Object> turn(String id, String role, String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("role", role);
        m.put("text", text);
        return m;
    }

    static Map<String, Object> conversation(List<Map<String, Object>> turns) {
        Map<String, Object> m = new HashMap<>();
        m.put("version", 1);
        m.put("kind", "scripted");
        m.put("turns", turns);
        return m;
    }

    static WalkSpec.Conversation valid() {
        return WalkConversation.parse(conversation(List.of(turn("t1", "user", "Where did the application first log a breach?"),
                turn("t2", "assistant", "At the DEMO record shown here.")))).conversation();
    }

    /** The refusal, asserted to exist first, so a missing check fails HERE by name rather than by a null dereference. */
    static String refused(Map<String, Object> raw) {
        WalkConversation.Parsed p = WalkConversation.parse(raw);
        assertFalse(p.ok(), "this conversation must be refused, and was accepted");
        return p.error();
    }

    static WalkSpec.Step step(String id, String through) {
        return new WalkSpec.Step("c", WalkSpec.View.NONE, List.of(new WalkSpec.Target("status", "", null)), id, through);
    }

    @Test
    @DisplayName("a valid version-1 conversation parses, typed")
    void aValidConversationParses() {
        WalkSpec.Conversation c = valid();
        assertNotNull(c);
        assertEquals(2, c.turns().size());
        assertEquals("Simulated conversation", c.label());
    }

    @Test
    @DisplayName("refused by name: unknown fields, a newer version, a bad kind, duplicate ids, a bad role, empty text")
    void malformedDialogueIsRefused() {
        Map<String, Object> extra = conversation(List.of(turn("t1", "user", "q")));
        extra.put("script", "run me");
        assertTrue(refused(extra).contains("'script' is not a conversation field"));
        Map<String, Object> v2 = conversation(List.of(turn("t1", "user", "q")));
        v2.put("version", 2);
        assertTrue(refused(v2).contains("version 2 is not supported"));
        Map<String, Object> kind = conversation(List.of(turn("t1", "user", "q")));
        kind.put("kind", "live");
        assertTrue(refused(kind).contains("kind"));
        assertTrue(refused(conversation(List.of(turn("t1", "user", "a"), turn("t1", "assistant", "b")))).contains("used twice"));
        assertTrue(refused(conversation(List.of(turn("t1", "system", "a")))).contains("role"));
        assertTrue(refused(conversation(List.of(turn("t1", "user", " ")))).contains("no text"));
        Map<String, Object> tool = turn("t1", "assistant", "done");
        tool.put("action", "{\"action\":\"open\"}");
        assertTrue(refused(conversation(List.of(tool))).contains("nothing that runs"));
    }

    @Test
    @DisplayName("bounds: a turn over 16 KiB, more than 200 turns, and a conversation over 512 KiB are refused")
    void boundsAreEnforced() {
        String big = "x".repeat(WalkConversation.MAX_TURN_BYTES + 1);
        assertTrue(refused(conversation(List.of(turn("t1", "user", big)))).contains("bytes"));
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i <= WalkConversation.MAX_TURNS; i++) many.add(turn("t" + i, "user", "q"));
        assertTrue(refused(conversation(many)).contains("at most " + WalkConversation.MAX_TURNS));
        List<Map<String, Object>> heavy = new ArrayList<>();
        String part = "y".repeat(WalkConversation.MAX_TURN_BYTES - 100);
        for (int i = 0; i < 40; i++) heavy.add(turn("t" + i, "user", part));
        assertTrue(refused(conversation(heavy)).contains("at most " + WalkConversation.MAX_TOTAL_BYTES));
    }

    @Test
    @DisplayName("binding: a missing turn, a reveal that goes backwards, duplicate step ids and a missing id are refused")
    void bindingsAreValidated() {
        WalkSpec.Conversation c = valid();
        assertNull(WalkConversation.bindingProblem(c, List.of(step("s1", "t1"), step("s2", "t2"))));
        assertNull(WalkConversation.bindingProblem(c, List.of(step("s1", "t2"), step("s2", "t2"))), "two steps may reveal the same prefix");
        assertTrue(WalkConversation.bindingProblem(c, List.of(step("s1", "t9"))).contains("does not have"));
        assertTrue(WalkConversation.bindingProblem(c, List.of(step("s1", "t2"), step("s2", "t1"))).contains("only grows"));
        assertTrue(WalkConversation.bindingProblem(c, List.of(step("s1", "t1"), step("s1", "t2"))).contains("used twice"));
        assertTrue(WalkConversation.bindingProblem(c, List.of(step(null, "t1"))).contains("every step an id"));
        assertTrue(WalkConversation.bindingProblem(null, List.of(step("s1", "t1"))).contains("no conversation"));
    }

    @Test
    @DisplayName("ids are assigned to steps without one, never renumbering a kept id")
    void idsAreStable() {
        List<WalkSpec.Step> steps = WalkConversation.withIds(List.of(step(null, null), step("s1", null), step(null, null)));
        assertEquals(List.of("s2", "s1", "s3"), steps.stream().map(WalkSpec.Step::id).toList());
    }

    @Test
    @DisplayName("the visible prefix at a step is every turn up to the furthest reveal so far")
    void prefixIsTheFurthestRevealSoFar() {
        WalkSpec.Conversation c = valid();
        WalkSpec w = new WalkSpec("w", "", "person", "", "", null, List.of(),
                List.of(step("s1", null), step("s2", "t1"), step("s3", null), step("s4", "t2")), Map.of(), c);
        assertEquals(0, WalkConversation.prefix(w, 0).size());
        assertEquals(1, WalkConversation.prefix(w, 1).size());
        assertEquals(1, WalkConversation.prefix(w, 2).size(), "a step with no reveal keeps what was shown");
        assertEquals(2, WalkConversation.prefix(w, 3).size());
    }
}
