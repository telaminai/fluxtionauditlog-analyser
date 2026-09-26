package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * M68.7 (owner, Q4 2026-09-26), in a real, SHOWN frame on a MAPPED log — the store whose charts and detail pane read the
 * file as it is now, while the table's rows are the log as it was indexed. After an in-place rewrite, a real
 * record-reading request is refused and the session's verdict must reach, ON SCREEN, every surface that can show a
 * value: the table (M68.5), the charts (M68.7, gating G14) and the detail pane (M68.7). Reopening clears them.
 *
 * <p>Review R1: the first version never showed the frame and read only the label's own visible flag, so a banner with
 * no parent — never painted — passed. The assertions here are about reachability: the banner is showing, is inside the
 * surface it marks, and has visible, non-empty bounds within it.
 */
class IdentityMarkFrameTest {

    @TempDir Path tmp;

    private static final String REC = "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - node: { value: %d}\n---\n";

    private Path writeLog() throws Exception {
        return Files.writeString(tmp.resolve("mapped.yml"), "---\n" + REC.formatted(1000, 1) + REC.formatted(2000, 2));
    }

    /** Poll a Swing condition ON THE EDT until it holds or the deadline passes; true if it held. */
    private static boolean awaitOnEdt(BooleanSupplier condition, long millis) throws Exception {
        long deadline = System.nanoTime() + millis * 1_000_000;
        AtomicBoolean held = new AtomicBoolean();
        while (true) {
            onEdt(() -> held.set(condition.getAsBoolean()));
            if (held.get() || System.nanoTime() > deadline) return held.get();
            Thread.sleep(20);                                     // between polls only; the condition decides
        }
    }

    private static JLabel banner(JComponent surface) {
        return (JLabel) field(surface, "identityBanner");
    }

    /** Review R1: the mark is ON SCREEN — showing, inside the surface it marks, with visible bounds within it. Call on the EDT. */
    private static void assertOnScreen(JComponent surface, String what) {
        JLabel label = banner(surface);
        assertTrue(surface.isShowing(), "control: the " + what + " itself is on screen");
        assertTrue(label.isShowing(), "the " + what + " banner must be showing on the " + what + ", not merely flagged visible");
        assertTrue(SwingUtilities.isDescendingFrom(label, surface), "the " + what + " banner must be inside the " + what);
        Rectangle visible = label.getVisibleRect();
        assertTrue(visible.width > 0 && visible.height > 0, "the " + what + " banner must have visible bounds: " + visible);
        Rectangle inSurface = SwingUtilities.convertRectangle(label, visible, surface);
        Rectangle area = new Rectangle(0, 0, surface.getWidth(), surface.getHeight());
        Rectangle overlap = area.intersection(inSurface);
        assertTrue(overlap.width > 0 && overlap.height > 0,
                "the " + what + " banner's visible bounds must lie within the " + what + ": " + inSurface + " in " + area);
    }

    /** What the label actually paints at its current size, clipping included. */
    private static String drawn(JLabel label) {
        Rectangle view = new Rectangle(0, 0, label.getWidth(), label.getHeight());
        var insets = label.getInsets();
        view.x += insets.left; view.y += insets.top;
        view.width -= insets.left + insets.right; view.height -= insets.top + insets.bottom;
        return SwingUtilities.layoutCompoundLabel(label, label.getFontMetrics(label.getFont()), label.getText(), null,
                label.getVerticalAlignment(), label.getHorizontalAlignment(), label.getVerticalTextPosition(),
                label.getHorizontalTextPosition(), view, new Rectangle(), new Rectangle(), 0);
    }

    @Test
    void anInPlaceRewriteIsStatedOnTheTableTheChartsAndTheDetailPane() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = writeLog();
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                ((AppConfig) field(f.frame, "config")).memoryThresholdMb = 0;   // 0 → always memory-mapped
                f.frame.setSize(1200, 800);
                f.frame.setVisible(true);                                      // review R1: a person's window
            });
            assertTrue(awaitOnEdt(f.frame::isShowing, 5_000), "control: the frame is on screen");
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "the fixture opens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            onEdt(() -> assertEquals("MappedLogStore", field(f.frame, "store").getClass().getSimpleName(),
                    "control: the log is memory-mapped, the store that reads through"));
            assertTrue(f.ex.render("graph", Map.of("name", "Before", "series", List.of("node.value"))).ok(), "a chart opens");
            assertTrue(f.ex.render("goto", Map.of("recordIndex", 1)).ok(), "a record is selected");
            GraphTabs charts = (GraphTabs) field(f.frame, "graphTabs");
            DetailPanel detail = (DetailPanel) field(f.frame, "detailPanel");
            LogTablePanel table = (LogTablePanel) field(f.frame, "tablePanel");
            onEdt(() -> {
                ((javax.swing.JTabbedPane) field(f.frame, "sideTabs")).setSelectedComponent(charts);
                assertTrue(charts.selectGraph("Before"), "control: the chart is selected");
                f.frame.validate();
                assertNull(table.identityNote(), "control: nothing is stated before the file changes");
                assertFalse(banner(charts).isShowing(), "control: no chart banner before the file changes");
                assertFalse(banner(detail).isShowing(), "control: no detail banner before the file changes");
            });

            // same inode, same length, later modification time: the mapped channel now reads different bytes
            Files.writeString(log, Files.readString(log).replace("value: 2", "value: 7"));
            Files.setLastModifiedTime(log, FileTime.fromMillis(System.currentTimeMillis() + 60_000));
            // the REAL record-reading route: the dispatcher observes identity before it serves any record
            var dispatcher = new ActionDispatcher(false, null,
                    () -> ((LogStore) field(f.frame, "store")).index().snapshot(),
                    row -> ((LogStore) field(f.frame, "store")).rawText(row), f.ex);
            var read = dispatcher.dispatch(Map.of("action", "read", "params", Map.of("limit", 1)));
            assertFalse(read.ok(), "a real record read is refused on the rewritten mapped file: " + read);
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(f.frame, "session");
            AtomicReference<String> verdict = new AtomicReference<>();
            assertTrue(awaitOnEdt(() -> {                         // the observation is posted with invokeLater
                verdict.set(session.snapshot().logIdentity());
                return "UNVERIFIED".equals(verdict.get()) || "REPLACEMENT".equals(verdict.get());
            }, 5_000), "control: the session observed the rewrite, got " + verdict.get());
            String reason = session.snapshot().logIdentityReason();

            onEdt(() -> {
                f.frame.validate();
                assertAll("every surface that can show a value states the verdict",
                        () -> assertNotNull(table.identityNote(), "the table states the verdict (M68.5)"),
                        () -> assertNotNull(charts.identityNote(), "the charts must state the verdict before G14 can pass on them"),
                        () -> assertNotNull(detail.identityNote(), "the detail pane must state the verdict"),
                        () -> assertTrue(String.valueOf(charts.identityNote()).contains(reason), "the charts carry the session's reason"),
                        () -> assertTrue(String.valueOf(detail.identityNote()).contains(reason), "the detail pane carries the session's reason"));
            });
            // review R1: on screen, not merely set
            onEdt(() -> {
                assertOnScreen(charts, "chart area");
                assertOnScreen(detail, "detail pane");
                // review O1: what a narrow pane paints still carries the verdict, not only the start of the reason
                assertTrue(drawn(banner(charts)).contains("not verified"), "the painted chart banner states the verdict: " + drawn(banner(charts)));
                assertTrue(drawn(banner(detail)).contains("not verified"), "the painted detail banner states the verdict: " + drawn(banner(detail)));
            });

            // a chart opened after the verdict is under the same banner, on screen
            assertTrue(f.ex.render("graph", Map.of("name", "After", "series", List.of("node.value"))).ok(), "a second chart opens");
            onEdt(() -> {
                ((javax.swing.JTabbedPane) field(f.frame, "sideTabs")).setSelectedComponent(charts);
                assertTrue(charts.selectGraph("After"), "control: the later chart is selected");
                f.frame.validate();
                assertOnScreen(charts, "chart area");
            });

            // reopening reads the file again, so nothing is superseded and nothing is said
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok(), "the log reopens");
            AsyncOpenInterleavingFrameTest.awaitLoaded(f.ex);
            assertTrue(awaitOnEdt(() -> charts.identityNote() == null && detail.identityNote() == null
                    && table.identityNote() == null, 5_000), "the reopened log clears every mark");
            onEdt(() -> {
                f.frame.validate();
                assertAll("a reopened log is current on every surface",
                        () -> assertFalse(banner(charts).isShowing(), "the chart banner is gone from the screen"),
                        () -> assertFalse(banner(detail).isShowing(), "the detail banner is gone from the screen"),
                        () -> assertNull(table.identityNote(), "the table is current again"));
            });
        }
    }
}
