package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.Frame;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/** Native mouse routing regression: no component-dispatched synthetic release is used to end a drag. */
class TableDragCancellationFrameTest {
    private static <T> T edt(Supplier<T> read) throws Exception {
        AtomicReference<T> value = new AtomicReference<>();
        onEdt(() -> value.set(read.get()));
        return value.get();
    }

    private static boolean until(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        do {
            if (edt(condition::getAsBoolean)) return true;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private static Point screen(Component component, int x, int y) throws Exception {
        return edt(() -> { Point p = new Point(x, y); SwingUtilities.convertPointToScreen(p, component); return p; });
    }

    private static void move(Robot robot, Point from, Point to) {
        for (int n = 1; n <= 12; n++) robot.mouseMove(from.x + (to.x - from.x) * n / 12, from.y + (to.y - from.y) * n / 12);
    }

    private static void requireNativeInput(boolean available, String reason) {
        if (!available) throw new IllegalStateException("native input unavailable: " + reason);
    }

    /** Retry only a press the OS never delivered, before any gesture or cancellation assertion starts. */
    private static Point press(Robot robot, Component component, Supplier<Point> localPoint,
                               BooleanSupplier started) throws Exception {
        AtomicInteger delivered = new AtomicInteger();
        MouseAdapter observed = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) delivered.incrementAndGet();
            }
        };
        onEdt(() -> component.addMouseListener(observed));
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                onEdt(() -> {
                    // A window focus request cannot activate an inactive application on macOS.
                    if (Desktop.isDesktopSupported()) {
                        Desktop desktop = Desktop.getDesktop();
                        if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) desktop.requestForeground(true);
                    }
                    Window window = SwingUtilities.getWindowAncestor(component);
                    window.toFront(); window.requestFocus(); component.requestFocusInWindow();
                });
                Point point = edt(() -> {
                    Point p = localPoint.get();
                    requireNativeInput(component.isShowing() && component.contains(p), "native press target is visible");
                    SwingUtilities.convertPointToScreen(p, component);
                    return p;
                });
                int before = delivered.get();
                robot.mouseMove(point.x, point.y); robot.waitForIdle();
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
                if (until(() -> delivered.get() > before)) {
                    requireNativeInput(until(() -> SwingUtilities.getWindowAncestor(component).isFocused()),
                            "a delivered native press must focus its window");
                    requireNativeInput(until(started), "a delivered native press must start the gesture");
                    return point; // keep the button held for the actual interruption test
                }
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
            }
            throw new IllegalStateException("nativePressDeliveredBeforeGestureChecks: the desktop dropped all three presses");
        } finally {
            onEdt(() -> component.removeMouseListener(observed));
        }
    }

    private static Point tablePressPoint(JTable table) {
        Rectangle visible = table.getVisibleRect();
        return new Point(visible.x + 180, visible.y + visible.height / 2);
    }

    private static void open(Frame f) throws Exception {
        f.dialogs.stop(); // This test must keep the real modal open; it owns dialog cleanup.
        onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); f.frame.toFront(); f.frame.requestFocus(); });
        onEdt(() -> render(f.ex, "open", Map.of("log", Path.of("src/main/resources/demo/demo-quote-series.yaml").toAbsolutePath().toString())));
        awaitLoaded(f.ex);
        onEdt(() -> { f.frame.toFront(); f.frame.requestFocus(); });
        // A native press can activate a window whose programmatic focus request the desktop refused.
        // press() verifies focus and the started gesture before the interruption checks begin.
    }

    private static Window settings(Frame f) throws Exception {
        AtomicReference<JMenuItem> item = new AtomicReference<>();
        onEdt(() -> {
            for (int m = 0; m < f.frame.getJMenuBar().getMenuCount(); m++) {
                for (Component c : f.frame.getJMenuBar().getMenu(m).getMenuComponents()) {
                    if (c instanceof JMenuItem i && "Settings…".equals(i.getText())) item.set(i);
                }
            }
        });
        assertNotNull(item.get(), "control: the real Settings menu action exists");
        // This deliberately schedules a real modal during the native drag. It proves cancellation at that
        // boundary, not that this was the cause of the owner's original demo incident.
        SwingUtilities.invokeLater(item.get()::doClick);
        assertTrue(until(() -> Arrays.stream(Window.getWindows()).anyMatch(w -> w instanceof ConfigPanel && w.isShowing())),
                "control: the real Settings dialog became visible");
        assertTrue(until(() -> !f.frame.isFocused()), "control: the main window lost focus to the modal");
        return edt(() -> Arrays.stream(Window.getWindows()).filter(w -> w instanceof ConfigPanel && w.isShowing()).findFirst().orElseThrow());
    }

    private static void timerTurns() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        onEdt(() -> {
            int[] ticks = {0};
            Timer timer = new Timer(100, null);
            timer.addActionListener(e -> { if (++ticks[0] == 4) { timer.stop(); done.countDown(); } });
            timer.start();
        });
        assertTrue(done.await(4, TimeUnit.SECONDS), "the EDT must run the observation timer");
    }

    private static void disposeDialogs() throws Exception {
        onEdt(() -> { for (Window w : Window.getWindows()) if (w instanceof ConfigPanel) w.dispose(); });
    }

    @Test
    void aModalEndsTheNativeTableDragAndTheNextDragStillWorks(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Robot robot = new Robot(); robot.setAutoDelay(25);
        Point previous = MouseInfo.getPointerInfo().getLocation();
        try (Frame f = new Frame(tmp)) {
            try {
                open(f);
                JTable table = ((LogTablePanel) field(f.frame, "tablePanel")).table();
                onEdt(() -> { render(f.ex, "goto", Map.of("recordIndex", 50)); table.scrollRectToVisible(table.getCellRect(65, 0, true)); });
                robot.waitForIdle();
                Rectangle visible = edt(table::getVisibleRect);
                int beforeDragY = visible.y;
                Point start = press(robot, table, () -> tablePressPoint(table),
                        () -> table.getSelectionModel().getValueIsAdjusting());
                Point end = screen(table, visible.x + 180, visible.y - 8);
                assertTrue(edt(() -> table.getColumnModel().getSelectionModel().getValueIsAdjusting()),
                        "control: the native table press starts column adjustment too");
                move(robot, start, end);
                assertTrue(until(() -> table.getVisibleRect().y < beforeDragY), "control: native drag starts actual table autoscrolling");
                Window dialog = settings(f);
                // The button is STILL HELD: release delivery must not be what makes these checks pass.
                onEdt(() -> {
                    assertFalse(table.getSelectionModel().getValueIsAdjusting(), "before release, losing window focus cancels the table's adjusting gesture");
                    assertFalse(table.getColumnModel().getSelectionModel().getValueIsAdjusting(), "column adjustment ends with the cancelled gesture");
                    assertTrue(table.getAutoscrolls(), "cancellation preserves the configured autoscroll behaviour");
                });
                int[] selection = edt(table::getSelectedRows);
                Rectangle after = edt(table::getVisibleRect);
                timerTurns();
                onEdt(() -> {
                    assertArrayEquals(selection, table.getSelectedRows(), "no table selection growth after window loses drag");
                    assertEquals(after, table.getVisibleRect(), "no table scrolling after window loses drag");
                });
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
                timerTurns();
                onEdt(() -> {
                    assertFalse(table.getSelectionModel().getValueIsAdjusting(), "release does not restart row adjustment");
                    assertFalse(table.getColumnModel().getSelectionModel().getValueIsAdjusting(), "release does not restart column adjustment");
                    assertTrue(table.getAutoscrolls(), "release preserves autoscroll for the next normal drag");
                    assertArrayEquals(selection, table.getSelectedRows(), "release does not restart selection growth");
                    assertEquals(after, table.getVisibleRect(), "release does not restart table scrolling");
                    dialog.dispose(); f.frame.toFront(); f.frame.requestFocus(); table.requestFocusInWindow();
                });
                assertTrue(until(f.frame::isFocused), "the main window regains focus for the next gesture");
                onEdt(() -> table.scrollRectToVisible(table.getCellRect(65, 0, true)));
                visible = edt(table::getVisibleRect);
                start = press(robot, table, () -> tablePressPoint(table),
                        () -> table.getSelectionModel().getValueIsAdjusting());
                end = screen(table, visible.x + 180, visible.y - 8);
                int initialY = visible.y;
                move(robot, start, end);
                assertTrue(until(() -> table.getVisibleRect().y < initialY), "the next normal drag can still autoscroll");
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
                assertTrue(until(() -> !table.getSelectionModel().getValueIsAdjusting()), "normal release still ends the next gesture");
            } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); disposeDialogs(); }
        } finally { robot.mouseMove(previous.x, previous.y); }
    }

    @Test
    void aModalEndsTheNativeSliderEdgePanWithoutChangingItsRange(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Robot robot = new Robot(); robot.setAutoDelay(25);
        Point previous = MouseInfo.getPointerInfo().getLocation();
        try (Frame f = new Frame(tmp)) {
            try {
                open(f);
                TimeRangeSlider slider = (TimeRangeSlider) field(f.frame, "timeSlider");
                Timer edge = (Timer) field(slider, "edgeScroll");
                onEdt(() -> {
                    long span = (long) field(slider, "absMax") - (long) field(slider, "absMin");
                    slider.setWindowMillis(span / 4);
                    long min = (long) field(slider, "min"), max = (long) field(slider, "max");
                    // Acquire the thumb inside the window: a press at the edge itself starts the timer,
                    // which can pan to the absolute boundary while native delivery is synchronised.
                    long from = min + (max - min) / 4, to = max - (max - min) / 4;
                    render(f.ex, "filter", Map.of("from", from, "to", to));
                    assertEquals(from, field(slider, "lo"), "control: prepare an interior thumb through the filter action");
                });
                Point start = press(robot, slider, () -> new Point(12 + (slider.getWidth() - 24) / 4, 34),
                        () -> (int) field(slider, "dragMode") >= 0), end = screen(slider, 0, 34);
                move(robot, start, end);
                assertTrue(until(edge::isRunning), "control: the native edge drag starts the slider timer");
                AtomicReference<long[]> atFocusLoss = new AtomicReference<>();
                // Toolkit observers run before the frame's focus listeners. Edge ticks before this moment
                // are legitimate; cancellation must preserve THIS range, not the range before the drag.
                AWTEventListener captureRange = event -> {
                    if (event.getSource() == f.frame && event.getID() == WindowEvent.WINDOW_LOST_FOCUS)
                        atFocusLoss.set(new long[]{(long) field(slider, "lo"), (long) field(slider, "hi")});
                };
                Toolkit.getDefaultToolkit().addAWTEventListener(captureRange, AWTEvent.WINDOW_FOCUS_EVENT_MASK);
                try { settings(f); }
                finally { Toolkit.getDefaultToolkit().removeAWTEventListener(captureRange); }
                assertNotNull(atFocusLoss.get(), "control: observed the slider range at the moment focus was lost");
                onEdt(() -> {
                    assertEquals(-1, field(slider, "dragMode"), "before release, losing window focus cancels the slider gesture");
                    assertFalse(edge.isRunning(), "before release, losing window focus stops the slider edge timer");
                    assertRangeAtFocusLoss(slider, atFocusLoss.get());
                });
                timerTurns();
                onEdt(() -> assertRangeAtFocusLoss(slider, atFocusLoss.get()));
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
                timerTurns();
                onEdt(() -> {
                    assertEquals(-1, field(slider, "dragMode"), "release does not restart the slider gesture");
                    assertFalse(edge.isRunning(), "release does not restart the slider edge timer");
                    assertRangeAtFocusLoss(slider, atFocusLoss.get());
                });
            } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); disposeDialogs(); }
        } finally { robot.mouseMove(previous.x, previous.y); }
    }
    private static void assertRangeAtFocusLoss(TimeRangeSlider slider, long[] range) {
        assertEquals(range[0], field(slider, "lo"), "lower boundary remains the range at the moment focus was lost");
        assertEquals(range[1], field(slider, "hi"), "upper boundary remains the range at the moment focus was lost");
    }

    @Test
    void aNonModalFocusLossKeepsSelectionDeferredUntilRelease(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Robot robot = new Robot(); robot.setAutoDelay(25);
        Point previous = MouseInfo.getPointerInfo().getLocation();
        try (Frame f = new Frame(tmp)) {
            JDialog other = edt(() -> new JDialog(f.frame, "DEMO non-modal", false));
            try {
                open(f);
                JTable table = ((LogTablePanel) field(f.frame, "tablePanel")).table();
                Rectangle visible = edt(table::getVisibleRect);
                Point start = press(robot, table, () -> {
                    Rectangle v = table.getVisibleRect(); return new Point(v.x + 180, v.y + 40);
                }, () -> table.getSelectionModel().getValueIsAdjusting());
                Point end = screen(table, visible.x + 180, visible.y + 160);
                AtomicInteger finalized = new AtomicInteger();
                onEdt(() -> {
                    table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) finalized.incrementAndGet(); });
                    other.setBounds(f.frame.getX() + 700, f.frame.getY() + 80, 240, 120);
                    other.setVisible(true); other.toFront();
                });
                assertTrue(until(other::isFocused), "control: the non-modal window takes focus");
                onEdt(() -> {
                    assertTrue(table.getSelectionModel().getValueIsAdjusting(), "non-modal focus loss keeps selection adjusting");
                    assertEquals(0, finalized.get(), "non-modal focus loss does not publish a finalized selection");
                    other.dispose(); f.frame.toFront(); f.frame.requestFocus(); table.requestFocusInWindow();
                });
                assertTrue(until(f.frame::isFocused), "control: return focus to continue the held drag");
                int[] before = edt(table::getSelectedRows);
                move(robot, start, end);
                assertTrue(until(() -> !Arrays.equals(before, table.getSelectedRows())), "control: the native drag continues after non-modal focus loss");
                assertEquals(0, finalized.get(), "continued drag steps remain deferred until release");
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
                assertTrue(until(() -> !table.getSelectionModel().getValueIsAdjusting()), "release finalizes the continued drag");
                assertTrue(finalized.get() > 0, "release publishes the final selection");
            } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); onEdt(other::dispose); disposeDialogs(); }
        } finally { robot.mouseMove(previous.x, previous.y); }
    }

    @Test
    void aDroppedNativePressIsRetriedBeforeTheGestureStarts() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        AtomicInteger presses = new AtomicInteger();
        Robot robot = new Robot() {
            @Override public synchronized void mousePress(int buttons) {
                if (presses.incrementAndGet() > 1) super.mousePress(buttons); // deterministic dropped first press
            }
        };
        robot.setAutoDelay(25);
        Point previous = MouseInfo.getPointerInfo().getLocation();
        // This tests acquisition itself. The three interruption witnesses above still use MainFrame.
        JTable table = edt(() -> new JTable(100, 1));
        JFrame frame = edt(() -> {
            JFrame window = new JFrame("DEMO native press acquisition");
            window.setContentPane(new JScrollPane(table));
            window.setSize(500, 350); window.setLocationRelativeTo(null);
            window.setVisible(true); window.toFront(); window.requestFocus();
            return window;
        });
        try {
            try {
                try {
                    press(robot, table, () -> tablePressPoint(table),
                            () -> table.getSelectionModel().getValueIsAdjusting());
                } catch (IllegalStateException unavailable) {
                    if (unavailable.getMessage().startsWith("nativePressDeliveredBeforeGestureChecks:"))
                        assertTrue(presses.get() > 1, "aDroppedNativePressMustBeRetried");
                    throw unavailable; // all three failed: environment error, never a cancellation witness
                }
                assertTrue(presses.get() >= 2, "the first press was deliberately dropped");
                assertTrue(edt(() -> table.getSelectionModel().getValueIsAdjusting()),
                        "recovery established a real held gesture before testing cancellation");
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); robot.waitForIdle();
                assertTrue(until(() -> !table.getSelectionModel().getValueIsAdjusting()),
                        "normal release still ends the acquired gesture");
                Robot noInput = new Robot() {
                    @Override public synchronized void mousePress(int buttons) { } // deterministic unavailable input
                };
                var unavailable = assertThrows(IllegalStateException.class, () ->
                                press(noInput, table, () -> tablePressPoint(table),
                                        () -> table.getSelectionModel().getValueIsAdjusting()),
                        "unavailableNativeInputMustNotCountAsAnAssertionFailure");
                assertTrue(unavailable.getMessage().startsWith("nativePressDeliveredBeforeGestureChecks:"),
                        "the exhausted acquisition boundary was reached");
            } finally { robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK); onEdt(frame::dispose); }
        } finally { robot.mouseMove(previous.x, previous.y); }
    }

}
