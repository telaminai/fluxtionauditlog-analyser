package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/**
 * Review PR57 (implementation findings) — the cross-component checks the node tests could not make, on a REAL frame:
 * the real session, the real presenter, the real overlay and the real selection. Each test is a reviewer probe
 * scenario, turned into a regression.
 */
class WalkReviewFrameTest {

    static Map<String, Object> target(String name, String caption) {
        return Map.of("target", name, "caption", caption);
    }

    static void call(AsyncOpenInterleavingFrameTest.Frame f, String verb, Map<String, Object> params) throws Exception {
        onEdt(() -> render(f.ex, verb, params));
    }

    @Test
    @DisplayName("R3: an unavailable target 1 and an available target 2 — the overlay draws 2, not 1")
    void theOverlayKeepsTheSessionsNumbers(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var partial = Map.of("view", Map.of("tab", "topology"), "targets", List.of(
                    target("topology:node:DEMO_missing", "unavailable first"), target("topology:node:priceListener", "second")));
            call(f, "walk", Map.of("name", "DEMO_partial", "steps", List.of(partial)));
            call(f, "walk", Map.of("name", "DEMO_partial", "play", true));
            await("partly shown and lit", () -> "PARTLY_SHOWN".equals(walk(f).phase()) && overlay.isLit());
            onEdt(() -> {
                assertEquals(List.of(1, 2), walk(f).targets().stream().map(t -> t.n()).toList(), "control: the session numbers 1 and 2");
                assertFalse(walk(f).targets().get(0).available(), "control: target 1 is not available");
                List<SpotlightOverlay.Lit> lit = overlay.lit();
                assertEquals(1, lit.size(), "only the available target is lit");
                assertEquals("topology:node:priceListener", lit.get(0).target());
                assertEquals(2, lit.get(0).n(), "the overlay must draw the number the strip and context state: " + lit);
            });
        }
    }

    @Test
    @DisplayName("R5: a record the step's filter hides is not SHOWN, and a detail target that depends on it is not available")
    void aHiddenRecordIsNotClaimedAsShown(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var hidden = Map.of("view", Map.of("tab", "summary", "record", 1, "filter", Map.of("dimensions", List.of())),
                    "targets", List.of(target("detail", "record one claim")));
            call(f, "walk", Map.of("name", "DEMO_hidden", "steps", List.of(hidden)));
            call(f, "walk", Map.of("name", "DEMO_hidden", "play", true));
            await("the step decided", () -> walk(f).showing() && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                int[] selected = ((LogTablePanel) field(f.frame, "tablePanel")).selectedModelRows();
                assertEquals(0, selected.length, "control: the real selection is empty — record 1 is hidden");
                assertNotEquals("SHOWN", walk(f).phase(), "a hidden record must not be reported as shown");
                assertFalse(walk(f).targets().get(0).available(),
                        "the detail target depends on the requested record, which is not shown: " + walk(f).targets());
                assertTrue(walk(f).targets().get(0).reason().contains("record 1"), walk(f).targets().get(0).reason());
                assertFalse(overlay.isLit(), "and nothing is lit that would point at the wrong record");
            });
        }
    }

    @Test
    @DisplayName("R5 / §3.4: a step whose record is not in the log is refused whole — the previous step stays on screen")
    void aRefusedViewLeavesThePreviousStep(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            var first = Map.of("view", Map.of("tab", "topology"), "targets", List.of(target("topology:node:priceListener", "first")));
            var beyond = Map.of("view", Map.of("tab", "summary", "record", 999_999, "filter", Map.of("text", "DEMO_changed")),
                    "targets", List.of(target("status", "beyond")));
            call(f, "walk", Map.of("name", "DEMO_refused", "steps", List.of(first, beyond)));
            call(f, "walk", Map.of("name", "DEMO_refused", "play", true));
            await("step 1 lit", () -> "SHOWN".equals(walk(f).phase()) && overlay.isLit());
            var filter = (telamin.fluxtion.audit.analyser.analyser.filter.FilterState) field(f.frame, "filter");
            String textBefore = filter.text();

            onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session"))
                    .post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkNavigated(1)));
            await("step 2 decided", () -> walk(f).step() == 1 && !"PREPARING".equals(walk(f).phase()));
            onEdt(() -> {
                assertEquals("NOT_SHOWN", walk(f).phase(), "the step is refused");
                assertTrue(walk(f).reason().contains("999999") || walk(f).reason().contains("999,999"), walk(f).reason());
                assertEquals(textBefore, filter.text(), "nothing of the refused view was applied");
                assertTrue(overlay.lit().stream().anyMatch(l -> l.target().equals("topology:node:priceListener")),
                        "the previous step stays on screen: " + overlay.lit());
            });
        }
    }
}
