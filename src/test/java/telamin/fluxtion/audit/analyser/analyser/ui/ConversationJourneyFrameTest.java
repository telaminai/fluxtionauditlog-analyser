package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;

import javax.swing.JLabel;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.configure;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.panel;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/**
 * OA-4 in the real frame (spec §6, OA-A7/A8/A9/A10/A15): a conversation journey played over the DEMO log. The dialogue is
 * labelled, stepped in lock-step with the walk's REAL effects, never runs anything or calls a provider, never writes a
 * definition, shows a refused step as refused, and hands off to a fresh live thread with none of its words.
 */
class ConversationJourneyFrameTest {

    static final String T1 = "What does this DEMO log contain?";
    static final String T2 = "DEMO quote records, one per processor cycle.";
    static final String T3 = "Where is the record this step points at?";

    /** Save a four-step journey: steps 1–3 reveal t1..t3; step 4 points at a record that does not exist. */
    static void saveJourney(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        Map<String, Object> conversation = Map.of("version", 1, "kind", "scripted", "author", "DEMO", "turns", List.of(
                Map.of("id", "t1", "role", "user", "text", T1),
                Map.of("id", "t2", "role", "assistant", "text", T2),
                Map.of("id", "t3", "role", "user", "text", T3),
                Map.of("id", "t4", "role", "assistant", "text", "SHOULD-NEVER-SHOW-FOR-A-REFUSED-STEP")));
        // a saved chart, drawn now and CLOSED before playback: step 2 points at it, and a walk never opens one
        onEdt(() -> assertEquals(true, render(f.ex, "graph", Map.of("name", "DEMO spread",
                "series", List.of("quotePublisher.spread"))).get("ok")));
        Map<String, Object> chartStep = step("records:row:2", "t2");
        chartStep.put("view", Map.of("graph", "DEMO spread"));
        chartStep.put("targets", List.of(Map.of("target", "records:row:2", "caption", "DEMO"),
                Map.of("target", "graph:DEMO spread", "caption", "DEMO chart")));
        List<Object> steps = List.of(
                step("records:row:1", "t1"), chartStep, step("records:row:3", "t3"),
                step("records:row:99999", "t4"));
        onEdt(() -> {
            var r = render(f.ex, "walk", Map.of("name", "journey", "steps", steps, "conversation", conversation));
            assertEquals(true, r.get("ok"), String.valueOf(r));
        });
        onEdt(() -> assertEquals(true, render(f.ex, "graph", Map.of("name", "DEMO spread", "close", true)).get("ok"),
                "the chart is closed: only its definition remains"));
    }

    static Map<String, Object> step(String target, String through) {
        Map<String, Object> m = new HashMap<>();
        m.put("caption", "look");
        m.put("targets", List.of(Map.of("target", target, "caption", "DEMO")));
        m.put("conversationThrough", through);
        return m;
    }

    static String shown(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<String> t = new AtomicReference<>();
        onEdt(() -> t.set(panel(f.frame).conversationView().getText()));
        return t.get();
    }

    static String mode(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<String> t = new AtomicReference<>();
        onEdt(() -> t.set(((JLabel) field(panel(f.frame), "mode")).getText()));
        return t.get();
    }

    static void settled(AsyncOpenInterleavingFrameTest.Frame f, int step) throws Exception {
        await("step " + (step + 1) + " settled", () -> walk(f).showing() && walk(f).step() == step
                && !"PREPARING".equals(walk(f).phase()));
    }

    /** Every file under the isolated home, by path, with its bytes — to prove playback wrote none of them. */
    static Map<String, String> homeBytes(Path home) throws Exception {
        Map<String, String> out = new TreeMap<>();
        try (Stream<Path> files = Files.walk(home)) {
            for (Path p : files.filter(Files::isRegularFile).toList()) out.put(home.relativize(p).toString(), Files.readString(p));
        }
        return out;
    }

    @Test
    @DisplayName("OA-A7/A8/A9/A10: labelled, prefix-exact through Next/Back/resume, refusals shown as refused, zero requests, nothing written")
    void aJourneyPlays(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider(); var f = opened(tmp)) {
            configure(f.frame, provider);
            saveJourney(f);
            Thread.sleep(600);                                                       // let the save's debounce settle
            Map<String, String> before = homeBytes(tmp.resolve("home"));
            onEdt(() -> assertNull(f.frame.playWalk("journey", 0, "test")));
            settled(f, 0);
            assertEquals("Simulated conversation", mode(f), "the mode is stated in words");
            // the composer's OWN visibility, not isShowing(): a step's view may select another side tab, which hides the
            // docked assistant whether or not the composer is hidden — found when this control first survived
            onEdt(() -> {
                assertFalse(((javax.swing.JPanel) field(panel(f.frame), "composer")).isVisible(), "no composer in a demonstration");
                assertFalse(panel(f.frame).sendButton().isEnabled(), "and Send is off: nothing can be sent from a demo");
            });
            String s1 = shown(f);
            assertTrue(s1.contains(T1) && !s1.contains(T2), "step 1 reveals t1 only: " + s1);
            assertTrue(s1.contains("Question (scripted)"), "attributed as scripted: " + s1);
            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session")).post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(1)));
            settled(f, 1);
            String s2 = shown(f);
            assertTrue(s2.contains(T2) && !s2.contains(T3), "step 2 reveals t2: " + s2);
            assertEquals("PARTLY_SHOWN", walk(f).phase(), "the record is lit; the closed chart is not");
            assertTrue(s2.contains("graph:DEMO spread — not available"), "OA-A9: a closed chart is never shown as evidence: " + s2);
            assertTrue(s2.contains("not a live model"), s2);
            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session")).post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(-1)));
            settled(f, 0);
            String back = shown(f);
            assertTrue(back.contains(T1) && !back.contains(T2), "Back restores step 1's prefix, not a replay: " + back);
            assertEquals(back.indexOf(T1), back.lastIndexOf(T1), "no duplicated turn after Back");
            onEdt(() -> assertNull(f.frame.playWalk("journey", 2, "test")));        // resume at step 3 directly
            settled(f, 2);
            assertTrue(shown(f).contains(T3), "Play-from-3 reveals the exact prefix through t3");
            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session")).post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(1)));
            settled(f, 3);
            String refused = shown(f);
            assertEquals("NOT_SHOWN", walk(f).phase(), "a record that does not exist is not shown");
            assertTrue(refused.contains("NOT SHOWN"), "the refusal is visible: " + refused);
            assertFalse(refused.contains("SHOULD-NEVER-SHOW-FOR-A-REFUSED-STEP"),
                    "a refused step's answer is never shown as if its evidence were");
            assertTrue(refused.contains(T3), "the last accepted prefix stays");
            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session")).post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkEndRequested("test")));
            Thread.sleep(600);
            assertEquals(0, provider.bodies.size(), "zero provider requests across play, Next, Back, resume and a refusal");
            assertEquals(before, homeBytes(tmp.resolve("home")), "playback wrote no project, settings or chart definition bytes");
        }
    }

    @Test
    @DisplayName("OA-A15: Ask about this evidence ends the demo; the next live request carries none of the demo's words")
    void theHandoffIsAFreshThread(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider(); var f = opened(tmp)) {
            configure(f.frame, provider);
            saveJourney(f);
            onEdt(() -> assertNull(f.frame.playWalk("journey", 1, "test")));
            settled(f, 1);
            onEdt(() -> panel(f.frame).askAboutEvidenceButton().doClick());
            await("the demonstration ended", () -> !walk(f).showing());
            assertEquals("Live assistant", mode(f));
            onEdt(() -> assertEquals("", panel(f.frame).draftText(), "the fresh thread starts empty: nothing of the demo is carried"));
            provider.replies.add("Your own answer.");
            onEdt(() -> {
                panel(f.frame).composerArea().setText("My own question about record 2");
                panel(f.frame).sendButton().doClick();
            });
            AssistantLiveFrameTest.awaitIdle(f.frame);
            assertEquals(1, provider.bodies.size());
            String body = provider.bodies.get(0);
            assertTrue(body.contains("My own question about record 2"));
            for (String demo : new String[]{T1, T2, T3}) {
                assertFalse(body.contains(demo), "no simulated turn entered the provider call: " + demo);
            }
        }
    }
}
