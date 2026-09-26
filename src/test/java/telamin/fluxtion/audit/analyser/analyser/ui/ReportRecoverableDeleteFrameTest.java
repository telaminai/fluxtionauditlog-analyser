package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * PR #33 review (owner, 2026-09-27): the assistant's {@code report {name, delete: true}} is recoverable, through the
 * real frame's verb — the reply says how to undo it, {@code restore: true} lists it, and {@code restore: "<name>"}
 * brings it back exactly; a restore onto a taken name is refused.
 */
class ReportRecoverableDeleteFrameTest {

    @TempDir Path tmp;

    private static final Map<String, Object> REPORT = Map.of("name", "finding",
            "sections", List.of(Map.of("kind", "narrative", "text", "What we found.")));

    @Test
    void theAssistantsDeleteCanBeUndone() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = java.nio.file.Files.writeString(tmp.resolve("sample.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "control: a log opens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);             // the report verb needs a log
            var first = f.ex.render("report", REPORT);
            assertTrue(first.ok(), "control: the report is built: " + first.toMap());
            AppConfig config = (AppConfig) onEdtGet(() -> field(f.frame, "config"));
            var built = onEdtGet(() -> config.reports.stream().filter(r -> r.name().equals("finding")).findFirst().orElseThrow());

            var deleted = f.ex.render("report", Map.of("name", "finding", "delete", true));
            assertTrue(deleted.ok(), "the delete succeeds: " + deleted);
            assertTrue(String.valueOf(deleted.toMap()).contains("restore"), "the reply says how to undo it: " + deleted.toMap());
            assertTrue(onEdtGet(() -> config.reports.isEmpty()), "the report left the live list");

            var listed = f.ex.render("report", Map.of("restore", true));
            assertTrue(String.valueOf(listed.toMap()).contains("finding"), "restore: true lists it: " + listed.toMap());

            var restored = f.ex.render("report", Map.of("restore", "finding"));
            assertTrue(restored.ok(), "restore succeeds: " + restored);
            assertEquals(built, onEdtGet(() -> config.reports.get(0)), "the restored report is exactly the one deleted");

            assertTrue(f.ex.render("report", Map.of("name", "finding", "delete", true)).ok());
            assertTrue(f.ex.render("report", REPORT).ok(), "a new report takes the name meanwhile");
            var refused = f.ex.render("report", Map.of("restore", "finding"));
            assertFalse(refused.ok(), "restoring onto a taken name is refused: " + refused);
            assertEquals(1, (int) onEdtGet(() -> config.reports.size()), "the live report was not replaced");
        }
    }

    private static <T> T onEdtGet(java.util.concurrent.Callable<T> c) throws Exception {
        var ref = new java.util.concurrent.atomic.AtomicReference<T>();
        var err = new java.util.concurrent.atomic.AtomicReference<Exception>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try { ref.set(c.call()); } catch (Exception e) { err.set(e); }
        });
        if (err.get() != null) throw err.get();
        return ref.get();
    }
}
