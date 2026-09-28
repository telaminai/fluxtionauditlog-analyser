package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.lightAndSave;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/**
 * M69 S3 (W-A2, review R7): the strip's ← and → with real key events, once the strip holds keyboard focus. Kept in
 * its own class because it needs a focus owner: posted keys are dropped on a display without one (a known limit of
 * the owner's Mac), so here it SKIPS, and it must not share a class with the mutation witnesses, which the gate
 * requires to run with no skips. CI's Xvfb gives it focus, and runs it.
 */
class WalkArrowKeysFrameTest {

    @Test
    @DisplayName("W-A2: ← and → move through the walk when the strip holds focus")
    void arrowKeysMove(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            lightAndSave(f, overlay, "topology:node:priceListener", "one", false);
            lightAndSave(f, overlay, "topology:node:quotePublisher", "two", true);
            onEdt(() -> render(f.ex, "spotlight", Map.of("clear", true)));
            onEdt(() -> assertNull(f.frame.playWalk("tour", 0, "test")));
            await("shown", () -> walk(f).showing() && "SHOWN".equals(walk(f).phase()));
            AtomicReference<Boolean> focused = new AtomicReference<>(false);
            Thread.sleep(200);
            onEdt(() -> focused.set(overlay.isFocusOwner()));
            // posted keys are dropped on a display with no focus owner (a known limit of this Mac); CI's Xvfb has one
            assumeTrue(focused.get(), "the strip must hold keyboard focus for this check — reported as a skip, not a pass");
            onEdt(() -> overlay.dispatchEvent(new KeyEvent(overlay, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                    KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED)));
            await("→ moved to step 2", () -> walk(f).step() == 1);
            onEdt(() -> overlay.dispatchEvent(new KeyEvent(overlay, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                    KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED)));
            await("← moved back", () -> walk(f).step() == 0);
        }
    }
}
