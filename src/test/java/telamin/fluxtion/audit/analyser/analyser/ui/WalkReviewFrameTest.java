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
}
