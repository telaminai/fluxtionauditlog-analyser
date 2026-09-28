package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.awaitLoaded;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * Evidence bundle v1, B1 (spec §4.1): {@code context.log.generation} lets a capture skill detect that another log was
 * opened while it was copying — the same rule the analyser applies internally to a walk save, exposed rather than
 * duplicated. On a real frame: it is the session's own generation, and it moves when another log is opened.
 */
class ContextLogGenerationFrameTest {

    @SuppressWarnings("unchecked")
    private static Object generation(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Object> out = new AtomicReference<>();
        onEdt(() -> {
            var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", java.util.List.of("log"))).get("context");
            out.set(((Map<String, Object>) ctx.get("log")).get("generation"));
        });
        return out.get();
    }

    @Test
    @DisplayName("context.log.generation is the session's generation, and it moves when another log is opened")
    void theGenerationIsPublishedAndMoves(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            SessionDriver session = (SessionDriver) field(f.frame, "session");
            onEdt(() -> render(f.ex, "open", Map.of("log",
                    Path.of("src/main/resources/demo/demo-quote-audit.yaml").toAbsolutePath().toString())));
            awaitLoaded(f.ex);
            Object first = generation(f);
            assertNotNull(first, "context.log carries the generation a capture must compare");
            AtomicReference<Long> snap = new AtomicReference<>();
            onEdt(() -> snap.set(session.snapshot().logGeneration()));
            assertEquals(snap.get(), ((Number) first).longValue(), "it is the session's own, projected — not a second count");

            onEdt(() -> render(f.ex, "open", Map.of("log",
                    Path.of("src/main/resources/demo/demo-quote-series.yaml").toAbsolutePath().toString())));
            awaitLoaded(f.ex);
            Object second = generation(f);
            assertTrue(((Number) second).longValue() > ((Number) first).longValue(),
                    "opening another log moves it, which is how a capture knows its copy is incoherent: " + first + " -> " + second);
        }
    }
}
