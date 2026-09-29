package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** OA-3 (spec §6.1, §10): dialogue through the {@code walk} verb — saved with steps, attached alone, validated first. */
class ConversationWalkVerbTest {

    static Map<String, Object> dialogue() {
        return Map.of("version", 1, "kind", "scripted", "turns", List.of(
                Map.of("id", "t1", "role", "user", "text", "What does the DEMO log contain?"),
                Map.of("id", "t2", "role", "assistant", "text", "Quote records; see the status line.")));
    }

    static List<Object> steps(String... through) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < through.length; i++) {
            Map<String, Object> s = new HashMap<>();
            s.put("caption", "look");
            s.put("targets", List.of(Map.of("target", "status", "caption", "here")));
            if (through[i] != null) s.put("conversationThrough", through[i]);
            out.add(s);
        }
        return out;
    }

    private static Map<String, Object> call(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("a walk saved with dialogue: ids are given to its steps, reveals kept, and the echo names it without its words")
    void saveWithDialogue() {
        WalkVerbTest.Rig rig = new WalkVerbTest.Rig();
        ActionResult r = new WalkVerb(rig).run(call("name", "j", "steps", steps(null, "t1", "t2"), "conversation", dialogue()));
        assertTrue(r.ok(), r.toJson());
        WalkSpec saved = WalkBin.find(rig.config.walks, "j");
        assertEquals(List.of("s1", "s2", "s3"), saved.steps().stream().map(WalkSpec.Step::id).toList());
        assertEquals(Arrays.asList(null, "t1", "t2"), saved.steps().stream().map(WalkSpec.Step::through).toList());
        assertEquals("scripted", saved.conversation().kind());
        String echo = r.toJson();
        assertTrue(echo.contains("Simulated conversation"), echo);
        assertFalse(echo.contains("What does the DEMO log contain"), "the echo never repeats the dialogue's words");
        var ctx = WalkVerb.context(rig.config, WalkPlaybackState.IDLE, true, rig.run, "project");
        assertFalse(String.valueOf(ctx).contains("Quote records"), "context.walks names the dialogue, not its words");
        assertTrue(String.valueOf(ctx).contains("turns=2"), String.valueOf(ctx));
    }

    @Test
    @DisplayName("a binding to a missing turn is refused before anything is stored")
    void aBadBindingStoresNothing() {
        WalkVerbTest.Rig rig = new WalkVerbTest.Rig();
        int before = rig.persisted;
        ActionResult r = new WalkVerb(rig).run(call("name", "j", "steps", steps("t9"), "conversation", dialogue()));
        assertFalse(r.ok());
        assertTrue(r.error().contains("does not have"), r.error());
        assertNull(WalkBin.find(rig.config.walks, "j"));
        assertEquals(before, rig.persisted);
    }

    @Test
    @DisplayName("dialogue attached to a saved walk leaves its evidence bases untouched, and is reported to the session")
    void attachKeepsTheEvidence() {
        WalkVerbTest.Rig rig = new WalkVerbTest.Rig();
        assertTrue(new WalkVerb(rig).run(call("name", "j", "steps", steps(null, null))).ok());
        WalkSpec before = WalkBin.find(rig.config.walks, "j");
        rig.posted.clear();
        ActionResult r = new WalkVerb(rig).run(call("name", "j", "conversation", dialogue(), "through", Arrays.asList("t1", "t2")));
        assertTrue(r.ok(), r.toJson());
        WalkSpec after = WalkBin.find(rig.config.walks, "j");
        assertEquals(before.runBasis(), after.runBasis());
        for (int i = 0; i < before.steps().size(); i++) {
            assertEquals(before.steps().get(i).targets(), after.steps().get(i).targets(), "no basis was rebound");
            assertEquals(before.steps().get(i).view(), after.steps().get(i).view());
        }
        assertTrue(rig.posted.stream().anyMatch(f -> f instanceof SessionEvents.WalkDefinitionChanged d && d.now() == after),
                "the change is reported, so a showing walk ends rather than mixing revisions");
    }

    @Test
    @DisplayName("saving new steps without 'conversation' keeps the dialogue; conversation: null removes it")
    void keepAndRemove() {
        WalkVerbTest.Rig rig = new WalkVerbTest.Rig();
        assertTrue(new WalkVerb(rig).run(call("name", "j", "steps", steps("t1", "t2"), "conversation", dialogue())).ok());
        List<Object> resteps = steps("t1", "t2");
        ((Map<String, Object>) resteps.get(0)).put("id", "s1");
        ((Map<String, Object>) resteps.get(1)).put("id", "s2");
        assertTrue(new WalkVerb(rig).run(call("name", "j", "steps", resteps)).ok());
        assertNotNull(WalkBin.find(rig.config.walks, "j").conversation(), "a steps-only save keeps the dialogue");
        Map<String, Object> remove = new HashMap<>();
        remove.put("name", "j");
        remove.put("conversation", null);
        remove.put("through", null);
        assertTrue(new WalkVerb(rig).run(remove).ok());
        WalkSpec plain = WalkBin.find(rig.config.walks, "j");
        assertNull(plain.conversation());
        assertTrue(plain.steps().stream().allMatch(s -> s.through() == null), "no step reveals a removed turn");
    }

    @Test
    @DisplayName("a newer dialogue version cannot be authored here")
    void aNewerVersionIsRefused() {
        WalkVerbTest.Rig rig = new WalkVerbTest.Rig();
        Map<String, Object> v2 = new HashMap<>(dialogue());
        v2.put("version", 2);
        ActionResult r = new WalkVerb(rig).run(call("name", "j", "steps", steps("t1"), "conversation", v2));
        assertFalse(r.ok());
        assertTrue(r.error().contains("version 2 is not supported"), r.error());
    }
}
