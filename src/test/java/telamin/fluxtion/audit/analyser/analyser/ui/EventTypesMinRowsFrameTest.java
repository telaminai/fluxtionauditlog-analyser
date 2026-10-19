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
 * of them, when there are fewer), and so does the records table under it; the Record detail pane is what
 * gives way. Each floor is its panel's own minimum size, computed from its real rows, and every route that
 * sizes the Facts column honours them: dragging the checklist's divider, dragging the records/detail
 * divider, a divider saved too small by an earlier version, and a window made shorter.
 *
 * <p>Mutation witnesses: {@link EventFilterPanel#MIN_VISIBLE_ROWS} set to 0 (no checklist floor) fails every
 * test here on the checklist's rows; {@link LogTablePanel#MIN_VISIBLE_ROWS} set to 0 (no records floor)
 * fails every test here on the records table's rows.
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

    @Test
    void aShorterWindowShrinksTheDetailPaneNotTheLists(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, true);
            AtomicReference<Integer> before = new AtomicReference<>();
            onEdt(() -> before.set(((DetailPanel) field(f.frame, "detailPanel")).getHeight()));
            onEdt(() -> { f.frame.setSize(1300, 790); f.frame.validate(); });
            settle();
            snapshot(f, "window-1300x790.png");
            assertAtLeastThreeRowsVisible(f, "in a 1300x790 window");
            onEdt(() -> {
                DetailPanel detail = (DetailPanel) field(f.frame, "detailPanel");
                assertTrue(detail.getHeight() < before.get(), "the detail pane gave way: " + before.get() + " -> " + detail.getHeight());
                assertTrue(detail.getHeight() >= detail.getMinimumSize().height,
                        "the detail pane keeps its own floor: " + detail.getHeight() + " < " + detail.getMinimumSize().height);
            });
        }
    }

    /**
     * Robot walk of P3 (2026-10-18): at 1200x680 the checklist's viewport was 61px, under two rows. The Record detail
     * pane gives way first, down to its own floor, and the checklist and the records table each keep three rows.
     */
    @Test
    void a1200x680WindowKeepsThreeRowsInBothLists(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, true);
            onEdt(() -> { f.frame.setSize(1200, 680); f.frame.validate(); });
            settle();
            snapshot(f, "window-1200x680.png");
            assertAtLeastThreeRowsVisible(f, "in a 1200x680 window");
            onEdt(() -> {
                DetailPanel detail = (DetailPanel) field(f.frame, "detailPanel");
                assertTrue(detail.getHeight() >= detail.getMinimumSize().height,
                        "the detail pane keeps its own floor: " + detail.getHeight() + " < " + detail.getMinimumSize().height);
            });
        }
    }

    /**
     * Robot walk of P3, 900x680 dark: the Event types button was laid over "Search:" and the search box vanished. In a
     * narrow Facts column the controls move to a line of their own; the label, the box and the controls never overlap.
     *
     * <p>INTEG-R11 (owner, 2026-10-19): at 680 px the wrapped search row leaves the column too short for BOTH floors
     * on Linux's font metrics (2 checklist rows on the CI runner, 3 on macOS), and then the documented give-way order
     * applies — the records table keeps its three rows and the checklist yields. So at 680 this asserts that order, on
     * every platform; the three-row checklist floor is asserted at {@value #FITS_BOTH_HEIGHT} px, a height that holds both
     * floors with the search row wrapped. Witnesses: {@link LogTablePanel#MIN_VISIBLE_ROWS} = 0 fails the order at 680;
     * {@link EventFilterPanel#MIN_VISIBLE_ROWS} = 0 fails the checklist's floor — at 680 where the column
     * still holds both floors (macOS), and at {@value #FITS_BOTH_HEIGHT} everywhere (both run, 2026-10-19).
     */
    @Test
    void aNarrowWindowWrapsTheSearchRowInsteadOfHidingTheBox(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            onEdt(() -> {
                try {
                    var apply = MainFrame.class.getDeclaredMethod("applyTheme", String.class);
                    apply.setAccessible(true);
                    apply.invoke(f.frame, "Dark");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            showChecklist(f, true);
            onEdt(() -> { f.frame.setSize(900, 680); f.frame.validate(); });
            settle();
            snapshot(f, "window-900x680-dark.png");
            assertSearchRowWrapped(f);
            assertGiveWayOrder(f, "in a 900x680 window");
            onEdt(() -> { f.frame.setSize(900, FITS_BOTH_HEIGHT); f.frame.validate(); });
            settle();
            snapshot(f, "window-900x" + FITS_BOTH_HEIGHT + "-dark.png");
            assertSearchRowWrapped(f);
            assertAtLeastThreeRowsVisible(f, "in a 900x" + FITS_BOTH_HEIGHT + " window");
        }
    }

    /** A narrow window tall enough for both floors with the search row wrapped, on macOS and on the Linux runner. */
    static final int FITS_BOTH_HEIGHT = 760;

    private static void assertSearchRowWrapped(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        onEdt(() -> {
            Component search = (Component) field(f.frame, "searchField");
            JToggleButton toggle = (JToggleButton) field(f.frame, "eventTypesToggle");
            Container row = search.getParent();
            Component label = row.getComponent(0);
            Rectangle s = SwingUtilities.convertRectangle(row, search.getBounds(), f.frame.getRootPane());
            Rectangle t = SwingUtilities.convertRectangle(toggle.getParent(), toggle.getBounds(), f.frame.getRootPane());
            Rectangle l = SwingUtilities.convertRectangle(row, label.getBounds(), f.frame.getRootPane());
            assertTrue(search.isShowing() && s.width >= SearchRowLayout.MIN_FIELD, "the search box keeps its width: " + s);
            assertFalse(s.intersects(t), "the toggle is not laid over the box: " + s + " / " + t);
            assertFalse(l.intersects(t), "nor over the label: " + l + " / " + t);
            assertTrue(toggle.isShowing() && t.width >= toggle.getPreferredSize().width, "the toggle is whole: " + t);
        });
    }

    /**
     * The owner's give-way order (2026-10-18) when the column may be too short for both floors: the records table
     * always keeps its three rows; the checklist keeps its three too unless it is the one that yielded — it is never
     * cut below its floor while it still has the floor's height.
     */
    private static void assertGiveWayOrder(AsyncOpenInterleavingFrameTest.Frame f, String when) throws Exception {
        assertRecordsRowsVisible(f, when);
        AtomicReference<Boolean> yielded = new AtomicReference<>();
        onEdt(() -> {
            EventFilterPanel panel = (EventFilterPanel) field(f.frame, "eventFilterPanel");
            yielded.set(panel.getHeight() < panel.rowsFloor());
        });
        if (!yielded.get()) assertAtLeastThreeRowsVisible(f, when + " (the column held both floors)");
    }

    /** Too short for every floor: the checklist gives way, the records table never does (owner, 2026-10-18). */
    @Test
    void aWindowTooShortForEveryFloorKeepsTheRecordsTable(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = open(tmp)) {
            showChecklist(f, true);
            onEdt(() -> { f.frame.setSize(1300, 560); f.frame.validate(); });
            settle();
            snapshot(f, "window-1300x560.png");
            assertRecordsRowsVisible(f, "in a 1300x560 window");
            onEdt(() -> {
                EventFilterPanel panel = (EventFilterPanel) field(f.frame, "eventFilterPanel");
                assertTrue(panel.getHeight() < panel.rowsFloor(), "this window is too short for both lists' floors");
            });
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

    /** Both lists: the checklist and the records table each show at least min(3, rows) whole rows. */
    private static void assertAtLeastThreeRowsVisible(AsyncOpenInterleavingFrameTest.Frame f, String when) throws Exception {
        assertRecordsRowsVisible(f, when);
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
                    + " checklist rows fully visible, expected at least " + expected + " (checklist height " + panel.getHeight()
                    + ", viewport visible " + onScreen + ")");
        });
    }

    private static void assertRecordsRowsVisible(AsyncOpenInterleavingFrameTest.Frame f, String when) throws Exception {
        onEdt(() -> {
            LogTablePanel records = (LogTablePanel) field(f.frame, "tablePanel");
            javax.swing.JTable table = records.table();
            JViewport tableView = (JViewport) table.getParent();
            Rectangle tableOnScreen = tableView.getVisibleRect();
            int tableVisible = 0;
            for (int r = 0; r < table.getRowCount(); r++) {
                Rectangle cell = SwingUtilities.convertRectangle(table, table.getCellRect(r, 0, true), tableView);
                cell.width = 1;                                    // vertical extent only: columns scroll sideways
                if (tableOnScreen.contains(cell)) tableVisible++;
            }
            int expectedRecords = Math.min(3, table.getRowCount());
            assertTrue(table.getRowCount() > 0, "the sample log has records");
            assertTrue(tableVisible >= expectedRecords, when + ": " + tableVisible + " of " + table.getRowCount()
                    + " records-table rows fully visible, expected at least " + expectedRecords
                    + " (records panel height " + records.getHeight() + ", viewport visible " + tableOnScreen + ")");
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
