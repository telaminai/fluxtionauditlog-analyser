package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * Owner, 2026-10-18: when the Event types toggle shows the checklist, it shows at least three rows (or all
 * of them, when there are fewer). The floor is the checklist's own minimum size, computed from its real
 * rows, and every route that sizes it honours that floor: dragging the checklist's divider, dragging the
 * records/detail divider above it, and a divider saved too small by an earlier version.
 *
 * <p>Mutation witness: {@link EventFilterPanel#MIN_VISIBLE_ROWS} set to 0 (no row floor) fails every test
 * here — the checklist is squeezed to its Select all / none row and no row is visible.
 */
class EventTypesMinRowsFrameTest {

    @Test
    void draggingTheChecklistDividerToTheTopLeavesThreeRowsVisible(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, true);
            JSplitPane split = (JSplitPane) field(f.frame, "eventTypesSplit");
            dragDivider(split, 0);
            snapshot(f, "checklist-divider-dragged-to-top.png");
            assertAtLeastThreeRowsVisible(f, "after dragging the checklist divider to the top");
        }
    }

    @Test
    void draggingTheRecordsDetailDividerUpCannotSqueezeTheChecklist(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, true);
            AtomicReference<JSplitPane> main = new AtomicReference<>();
            onEdt(() -> main.set((JSplitPane) ((JPanel) field(f.frame, "tableArea")).getParent()));
            dragDivider(main.get(), 0);
            snapshot(f, "records-detail-divider-dragged-to-top.png");
            assertAtLeastThreeRowsVisible(f, "after dragging the records/detail divider to the top");
        }
    }

    @Test
    void aDividerSavedTooSmallIsMovedOutWhenTheChecklistIsShown(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, false);
            onEdt(() -> ((AppConfig) field(f.frame, "config")).eventTypesDivider = 12);
            showChecklist(f, true);
            snapshot(f, "shown-with-divider-saved-at-12px.png");
            assertAtLeastThreeRowsVisible(f, "when shown with a divider saved at 12px");
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static AsyncOpenInterleavingFrameTest.Frame open(Path tmp) throws Exception {
        var f = new AsyncOpenInterleavingFrameTest.Frame(tmp);
        f.dialogs.stop();
        Path log = Files.copy(Path.of("src/test/resources/sample.yml"), tmp.resolve("run.yml"));
        onEdt(() -> { f.frame.setSize(1300, 850); f.frame.setVisible(true); });
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
        settle();
        return f;
    }

    private static void showChecklist(AsyncOpenInterleavingFrameTest.Frame f, boolean show) throws Exception {
        onEdt(() -> {
            JToggleButton t = (JToggleButton) field(f.frame, "eventTypesToggle");
            if (t.isSelected() != show) t.doClick();
        });
        settle();
    }

    /** A real divider drag: press on the divider, drag it to {@code targetY} in the split's space, release. */
    private static void dragDivider(JSplitPane split, int targetY) throws Exception {
        onEdt(() -> {
            Component divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
            Point start = new Point(divider.getWidth() / 2, divider.getHeight() / 2);
            long t = System.currentTimeMillis();
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, t, InputEvent.BUTTON1_DOWN_MASK,
                    start.x, start.y, 1, false, MouseEvent.BUTTON1));
            int dy = targetY - divider.getY();
            for (int step = 1; step <= 10; step++) {
                divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, t + step,
                        InputEvent.BUTTON1_DOWN_MASK, start.x, start.y + dy * step / 10, 0, false, MouseEvent.NOBUTTON));
            }
            divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, t + 20, 0,
                    start.x, start.y + dy, 1, false, MouseEvent.BUTTON1));
        });
        settle();
    }

    private static void assertAtLeastThreeRowsVisible(AsyncOpenInterleavingFrameTest.Frame f, String when) throws Exception {
        onEdt(() -> {
            EventFilterPanel panel = (EventFilterPanel) field(f.frame, "eventFilterPanel");
            assertTrue(panel.isShowing(), "the checklist is showing " + when);
            List<JCheckBox> rows = new ArrayList<>();
            collect(panel, rows);
            assertFalse(rows.isEmpty(), "the sample log has event types");
            JViewport viewport = ((JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, rows.get(0))).getViewport();
            // what of the viewport is on screen: clipped by every ancestor up to the frame
            Rectangle onScreen = viewport.getVisibleRect();
            int visible = 0;
            for (JCheckBox row : rows) {
                Rectangle r = SwingUtilities.convertRectangle(row.getParent(), row.getBounds(), viewport);
                if (onScreen.contains(r)) visible++;
            }
            int expected = Math.min(3, rows.size());
            assertTrue(visible >= expected, when + ": " + visible + " of " + rows.size()
                    + " rows fully visible, expected at least " + expected + " (checklist height " + panel.getHeight()
                    + ", viewport visible " + onScreen + ")");
        });
    }

    private static void collect(Container c, List<JCheckBox> out) {
        for (Component k : c.getComponents()) {
            if (k instanceof JCheckBox cb) out.add(cb);
            else if (k instanceof Container kc) collect(kc, out);
        }
    }

    /** Optional before/after image, under the test's isolated home: -Devtypes.shots=<dir>. */
    private static void snapshot(AsyncOpenInterleavingFrameTest.Frame f, String name) throws Exception {
        String dir = System.getProperty("evtypes.shots");
        if (dir == null) return;
        AtomicReference<BufferedImage> img = new AtomicReference<>();
        onEdt(() -> {
            var root = f.frame.getRootPane();
            BufferedImage b = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            var g = b.createGraphics();
            root.paint(g);
            g.dispose();
            img.set(b);
        });
        Files.createDirectories(Path.of(dir));
        ImageIO.write(img.get(), "png", Path.of(dir, name).toFile());
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(150);
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
