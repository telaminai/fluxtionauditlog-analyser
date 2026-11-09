package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.report.Finding;

import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.awaitDecided;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.exchange;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.openLog;
import static telamin.fluxtion.audit.analyser.analyser.ui.EvidenceCaptureFrameTest.shown;

/**
 * #178 on REAL frames, by round trip between two homes: flag records with a note, a fix and a kind, write a report
 * whose finding sections render them, capture the bundle — then, in a FRESH analyser under another home, open it.
 * Before #178 the flags stayed behind, so every finding section arrived unresolved ("record N has no flag"); now each
 * flag goes back on its own record, matched by digest, and the report's findings carry their text.
 */
class BundleFlagsFrameTest {

    @SuppressWarnings("unchecked")
    static Map<String, Object> bundleContext(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Map<String, Object>> out = new AtomicReference<>();
        onEdt(() -> {
            var ctx = (Map<String, Object>) render(f.ex, "context", Map.of("sections", List.of("project"))).get("context");
            var proj = (Map<String, Object>) ctx.get("project");
            out.set(proj == null ? null : (Map<String, Object>) proj.get("bundle"));
        });
        return out.get();
    }

    static void openBundle(AsyncOpenInterleavingFrameTest.Frame f, Path fexp) throws Exception {
        onEdt(() -> {
            try {
                var m = MainFrame.class.getDeclaredMethod("loadExperiment", Path.class);
                m.setAccessible(true);
                m.invoke(f.frame, fexp);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    static Map<Integer, Finding> findings(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Map<Integer, Finding>> out = new AtomicReference<>();
        onEdt(() -> out.set(new LinkedHashMap<>((Map<Integer, Finding>) field(f.frame, "findings"))));
        return out.get();
    }

    @Test
    @DisplayName("★ #178: a bundle opened fresh restores every flagged finding's text, on its own record (red before: no flags, findings unresolved)")
    @SuppressWarnings("unchecked")
    void flagsTravelWithTheBundle(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path fexp;
        try (var sender = shown(Files.createDirectories(tmp.resolve("sender")))) {
            Path dir = exchange(sender, tmp.resolve("sender"));
            openLog(sender, EvidenceCaptureFrameTest.DEMO_LOG);
            onEdt(() -> assertEquals(Boolean.TRUE, render(sender.ex, "flag", Map.of("recordIndexes", List.of(2),
                    "note", "DEMO the quote crossed its band", "fix", "DEMO widen the band", "kind", "fault")).get("ok")));
            onEdt(() -> assertEquals(Boolean.TRUE, render(sender.ex, "flag", Map.of("recordIndexes", List.of(5),
                    "note", "DEMO the hedge fired as designed", "kind", "confirmation")).get("ok")));
            onEdt(() -> {
                var echo = render(sender.ex, "report", Map.of("name", "inv", "title", "DEMO investigation", "sections",
                        List.of(Map.of("kind", "finding", "recordIndex", 2), Map.of("kind", "finding", "recordIndex", 5))));
                assertEquals(Boolean.TRUE, echo.get("ok"), String.valueOf(echo));
            });
            onEdt(() -> render(sender.ex, "report", Map.of("bundle", Map.of("path", "flags.fexp"))));
            Map<String, Object> written = awaitDecided(sender);
            assertEquals("WRITTEN", written.get("phase"), "the fixture bundle was written: " + written);
            fexp = Files.copy(dir.resolve("flags.fexp"), tmp.resolve("flags.fexp"));
        }

        try (var recipient = shown(Files.createDirectories(tmp.resolve("recipient")))) {   // a fresh home: nothing local
            openBundle(recipient, fexp);
            Map<String, Object> flags = null;
            for (int i = 0; i < 200 && flags == null; i++) {
                var bundle = bundleContext(recipient);
                flags = bundle == null ? null : (Map<String, Object>) bundle.get("flags");
                if (flags == null) Thread.sleep(50);
            }
            assertNotNull(flags, "★ the recipient's context states the bundle's flags (red before #178: none travel)");
            assertEquals(2, flags.get("carried"));
            assertEquals(2, flags.get("restored"), "both went back on their records: " + flags);
            assertNull(flags.get("withheld"), "none withheld: " + flags);

            Map<Integer, Finding> restored = findings(recipient);
            assertEquals("DEMO the quote crossed its band", restored.get(2).note(), "★ the finding's text, on record 2");
            assertEquals("DEMO widen the band", restored.get(2).fix());
            assertEquals("fault", restored.get(2).kind());
            assertEquals("DEMO the hedge fired as designed", restored.get(5).note());
            assertEquals("confirmation", restored.get(5).kind(), "the kind travels too");

            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set((Map<String, Object>) render(recipient.ex, "report", Map.of("name", "inv")).values().stream()
                    .filter(Map.class::isInstance).findFirst().orElseThrow()));
            assertNull(echo.get().get("unresolved"),
                    "★ every finding section resolves on the recipient's side: " + echo.get());
        }
    }
}
