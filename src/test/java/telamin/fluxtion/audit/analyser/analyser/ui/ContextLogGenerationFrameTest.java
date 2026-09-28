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
 *
 * <p>With it, {@code context.project.unsavedEdits} (spec r3 §4.1): a capture copies the project profile FILE, and
 * project writes are debounced, so the file lags the session by one window. Found by driving the demo: a walk saved
 * a moment before capture was not in the bundle. The capture waits for this to clear instead of guessing a delay.
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

    @SuppressWarnings("unchecked")
    private static Object unsaved(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Object> out = new AtomicReference<>();
        onEdt(() -> {
            var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", java.util.List.of("project"))).get("context");
            out.set(((Map<String, Object>) ctx.get("project")).get("unsavedEdits"));
        });
        return out.get();
    }

    @Test
    @DisplayName("context.project.unsavedEdits is true while a project edit is waiting to be written, and false once the file has it")
    void theProjectSaysWhenItsFileLagsTheSession(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path profile = java.nio.file.Files.createDirectories(tmp.resolve("proj/.analyser")).resolve("project.fluxtion-settings");
        java.nio.file.Files.writeString(profile, "share.version=1\n");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            onEdt(() -> render(f.ex, "open", Map.of("log",
                    Path.of("src/main/resources/demo/demo-quote-audit.yaml").toAbsolutePath().toString())));
            awaitLoaded(f.ex);
            for (int i = 0; i < 40 && !Boolean.FALSE.equals(unsaved(f)); i++) Thread.sleep(100);
            assertEquals(Boolean.FALSE, unsaved(f), "a settled project has nothing waiting");

            AtomicReference<Object> during = new AtomicReference<>();
            onEdt(() -> {
                render(f.ex, "report", Map.of("name", "demo-lag", "sections",
                        java.util.List.of(Map.of("kind", "narrative", "text", "DEMO"))));
                var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", java.util.List.of("project"))).get("context");
                during.set(((Map<String, Object>) ctx.get("project")).get("unsavedEdits"));
            });
            assertEquals(Boolean.TRUE, during.get(), "straight after a save the FILE does not yet hold it, and context says so");
            assertFalse(java.nio.file.Files.readString(profile).contains("demo-lag"), "which is the lag a capture would copy");

            for (int i = 0; i < 50 && !Boolean.FALSE.equals(unsaved(f)); i++) Thread.sleep(100);
            assertEquals(Boolean.FALSE, unsaved(f), "the debounced write lands and the flag clears");
            assertTrue(java.nio.file.Files.readString(profile).contains("demo-lag"), "and only then does the file hold the edit");
        }
    }
}
