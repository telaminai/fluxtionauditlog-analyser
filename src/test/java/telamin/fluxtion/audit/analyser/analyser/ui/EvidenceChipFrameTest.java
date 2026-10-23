package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.core.FileObservation;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * ★ M79-3 (spec-first-minute §8.1) in the REAL frame: the evidence chip heads the Context column and states the
 * session's published observation — {@code Evidence · as read} for an ordinary log, {@code Evidence ✓ checked} for a
 * verified bundle's log, {@code ⚠ changed} once the file moves on disk — while {@code context} keeps
 * {@code log.freshness} exactly as the pre-M79-3 computation produced it (A3.3) and the Project panel loses its
 * "File observation" row (the rows collapse into the chip). With {@code -Dm79.shots=<dir>} it also paints the column
 * in each state there (review scratch, never committed).
 */
class EvidenceChipFrameTest {

    private static EvidenceChipLines chip(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<EvidenceChipLines> out = new AtomicReference<>();
        onEdt(() -> out.set(((EvidenceChipView) field(f.frame, "evidenceChip")).lines()));
        return out.get();
    }

    private static EvidenceChipLines awaitChip(AsyncOpenInterleavingFrameTest.Frame f, EvidenceChipLines.State want)
            throws Exception {
        for (int i = 0; i < 100; i++) {
            var c = chip(f);
            if (c != null && c.state() == want) return c;
            Thread.sleep(50);
        }
        return chip(f);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> logContext(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Map<String, Object>> out = new AtomicReference<>();
        onEdt(() -> out.set((Map<String, Object>) ((Map<String, Object>) render(f.ex, "context", Map.of()).get("context")).get("log")));
        return out.get();
    }

    /** What MainFrame.logFreshness() computed before M79-3, from the frame's own read-time observation. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> preM79Freshness(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<Map<String, Object>> out = new AtomicReference<>();
        onEdt(() -> out.set(FileObservation.compare(field(f.frame, "observedLogStore") == field(f.frame, "store")
                ? (List<Map<String, Object>>) field(f.frame, "logObservations") : List.of())));
        return out.get();
    }

    private static List<String> logRows(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        AtomicReference<List<String>> out = new AtomicReference<>();
        onEdt(() -> {
            var panel = (ProjectPanel) field(f.frame, "projectPanel");
            out.set(panel.model().sections().stream().filter(s -> s.title().equals(ProjectModel.LOG))
                    .flatMap(s -> s.rows().stream()).map(ProjectModel.Row::primary).toList());
        });
        return out.get();
    }

    private static void shot(AsyncOpenInterleavingFrameTest.Frame f, String name) throws Exception {
        String dir = System.getProperty("m79.shots");
        if (dir == null) return;
        onEdt(() -> {
            try {
                JComponent chip = (JComponent) field(f.frame, "evidenceChip");
                java.awt.Container column = chip.getParent();
                int w = Math.max(1, column.getWidth()), h = Math.max(1, Math.min(column.getHeight(), 260));
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                var g = img.createGraphics();
                column.paint(g);
                g.dispose();
                ImageIO.write(img, "png", Files.createDirectories(Path.of(dir)).resolve(name + ".png").toFile());
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    @DisplayName("★ M79-3: as read, then ✓ for a verified bundle's log, then ⚠ changed when the file moves; context keeps its freshness")
    void theChipStatesThePublishedObservation(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path log = Files.copy(EvidenceCaptureFrameTest.DEMO_LOG, tmp.resolve("audit.yaml"));
        try (var f = shown(tmp)) {
            Path dir = exchange(f, tmp);
            openLog(f, log);
            var asRead = awaitChip(f, EvidenceChipLines.State.AS_READ);
            assertEquals("Evidence · as read", asRead.chip(), "an ordinary log, unchanged metadata: as read, never ✓");
            AtomicReference<Boolean> showing = new AtomicReference<>();
            onEdt(() -> showing.set(((EvidenceChipView) field(f.frame, "evidenceChip")).isShowing()));
            assertTrue(showing.get(), "the chip heads the Context column");
            assertFalse(logRows(f).contains("File observation"), "the row collapsed into the chip: " + logRows(f));
            // A3.3: context's log.freshness is what the pre-M79-3 computation gave, key for key
            Map<String, Object> ctx = logContext(f);
            assertEquals(preM79Freshness(f), ctx.get("freshness"));
            assertEquals("unchanged-metadata", ((Map<?, ?>) ctx.get("freshness")).get("state"));
            // ★ A3.2 in the frame: the details hold the basis, verbatim
            AtomicReference<String> details = new AtomicReference<>();
            onEdt(() -> {
                var view = (EvidenceChipView) field(f.frame, "evidenceChip");
                view.showDetails();
                var popup = view.details();
                details.set(popup == null ? "" : TermDescriptionsTest.labels(popup).toString()
                        + texts(popup));
                if (popup != null) popup.setVisible(false);
            });
            assertTrue(details.get().contains("size, modification time and file identity; unchanged metadata does not prove identical bytes"),
                    details.get());
            shot(f, "chip-as-read");

            // a verified bundle's own log: ✓ checked
            onEdt(() -> render(f.ex, "report", Map.of("bundle", Map.of("path", "evidence.fexp"))));
            assertEquals("WRITTEN", awaitDecided(f).get("phase"));
            onEdt(() -> {
                try {
                    var m = MainFrame.class.getDeclaredMethod("loadExperiment", Path.class);
                    m.setAccessible(true);
                    m.invoke(f.frame, dir.resolve("evidence.fexp"));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            var checked = awaitChip(f, EvidenceChipLines.State.CHECKED);
            assertEquals("Evidence ✓ checked", checked.chip(), "a verified bundle's log is a digest check");
            assertTrue(EvidenceChipLinesTest.text(checked).contains("verified on open; unsigned, so it is not authenticated to a sender"));
            shot(f, "chip-checked");

            // an ordinary log again, then its file changes on disk: ⚠ changed, and context says so
            openLog(f, log);
            awaitChip(f, EvidenceChipLines.State.AS_READ);
            Files.writeString(log, Files.readString(log) + "\n# appended after the read\n");
            Map<String, Object> changedCtx = logContext(f);
            assertEquals("changed-on-disk", ((Map<?, ?>) changedCtx.get("freshness")).get("state"));
            assertEquals(preM79Freshness(f), changedCtx.get("freshness"), "A3.3: the same observation the old code made");
            var changed = awaitChip(f, EvidenceChipLines.State.CHANGED);
            assertEquals("⚠ changed", changed.chip(), "the chip states the session's published observation");
            shot(f, "chip-changed");
        }
    }

    private static String texts(java.awt.Container c) {
        StringBuilder b = new StringBuilder();
        for (var k : c.getComponents()) {
            if (k instanceof javax.swing.text.JTextComponent t) b.append(t.getText()).append('\n');
            if (k instanceof java.awt.Container inner) b.append(texts(inner));
        }
        return b.toString();
    }

    @Test
    @DisplayName("M79-3: the chip in each of its states, light and dark, at the head of the real Context column")
    void paintsEveryState(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        String dir = System.getProperty("m79.shots");
        javax.swing.LookAndFeel before = javax.swing.UIManager.getLookAndFeel();
        try (var f = shown(tmp)) {
            openLog(f, EvidenceCaptureFrameTest.DEMO_LOG);
            awaitChip(f, EvidenceChipLines.State.AS_READ);
            for (String theme : new String[]{"Light", "Dark"}) {
                onEdt(() -> {
                    ThemeManager.apply(theme);
                    SwingUtilities.updateComponentTreeUI(f.frame);
                });
                for (var row : EvidenceChipLinesTest.rows()) {
                    var lines = EvidenceChipLines.of(EvidenceChipLinesTest.snapshot(row.with()));
                    AtomicReference<BufferedImage> img = new AtomicReference<>();
                    onEdt(() -> {
                        var view = (EvidenceChipView) field(f.frame, "evidenceChip");
                        view.render(lines);                      // review evidence only: a synthetic snapshot's lines
                        f.frame.validate();
                        java.awt.Container column = view.getParent();
                        int w = Math.max(1, column.getWidth());
                        BufferedImage i = new BufferedImage(w, 44, BufferedImage.TYPE_INT_RGB);
                        var g = i.createGraphics();
                        column.paint(g);
                        g.dispose();
                        img.set(i);
                        assertEquals(lines.chip(), view.chip().getText(), row.what());
                    });
                    if (dir != null && Boolean.getBoolean("m79.robot")) {     // what the screen shows, pixel for pixel
                        Thread.sleep(150);
                        AtomicReference<java.awt.Rectangle> at = new AtomicReference<>();
                        onEdt(() -> {
                            var column = ((JComponent) field(f.frame, "evidenceChip")).getParent();
                            var p0 = column.getLocationOnScreen();
                            at.set(new java.awt.Rectangle(p0.x, p0.y, Math.max(1, column.getWidth()), 44));
                        });
                        ImageIO.write(new java.awt.Robot().createScreenCapture(at.get()), "png", Files.createDirectories(Path.of(dir))
                                .resolve("screen-" + lines.state() + "-" + theme + ".png").toFile());
                    }
                    if (dir != null) {
                        ImageIO.write(img.get(), "png", Files.createDirectories(Path.of(dir))
                                .resolve("state-" + lines.state() + "-" + theme + "-"
                                        + row.what().replaceAll("[^A-Za-z]+", "_") + ".png").toFile());
                    }
                }
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(before);
                } catch (javax.swing.UnsupportedLookAndFeelException e) {
                    throw new AssertionError(e);
                }
            });
        }
    }
}
