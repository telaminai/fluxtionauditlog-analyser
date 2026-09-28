package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mouse trace is a field diagnostic, so the two things that matter are that it is OFF unless asked, and
 * that when asked it writes something a reader with no context can act on.
 */
class MouseTraceTest {

    @Test
    @DisplayName("Off unless asked: no property, no listener, no file")
    void offUnlessAsked() {
        assertNull(System.getProperty(MouseTrace.PROPERTY), "precondition: the property is not set in this suite");
        assertNull(MouseTrace.installIfRequested(), "a diagnostic that installs itself is not a diagnostic");
    }

    @Test
    @DisplayName("Asked for, it opens its file and states what the trace is")
    void whenAskedItSaysWhatItIs(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("trace.log");
        System.setProperty(MouseTrace.PROPERTY, out.toString());
        try {
            assertNotNull(MouseTrace.installIfRequested());

            String first = Files.readString(out);
            assertTrue(first.contains("START"), first);
            assertTrue(first.contains(out.toString()), "it names where it is writing: " + first);
            assertTrue(first.contains("source window"),
                    "the header says what question the trace answers, for a reader who did not set it up: " + first);
        } finally {
            System.clearProperty(MouseTrace.PROPERTY);
        }
    }

    @Test
    @DisplayName("A bad destination disables the trace rather than stopping the app starting")
    void aBadDestinationIsNotFatal() {
        System.setProperty(MouseTrace.PROPERTY, "/this/path/does/not/exist/trace.log");
        try {
            // assertDoesNotThrow, not a bare call: a diagnostic that throws must fail at a NAMED assertion
            // saying why, not as an error nobody reads as a contract.
            MouseTrace installed = assertDoesNotThrow(MouseTrace::installIfRequested,
                    "a diagnostic that stops the app starting is worse than the bug it is hunting");
            assertNull(installed, "and it reports itself as not installed, rather than half-installed");
        } finally {
            System.clearProperty(MouseTrace.PROPERTY);
        }
    }

    /** Install a trace writing to {@code out}, watching a table we can drive. */
    private static javax.swing.JTable watchedTable(Path out) {
        System.setProperty(MouseTrace.PROPERTY, out.toString());
        try {
            MouseTrace trace = MouseTrace.installIfRequested();
            assertNotNull(trace);
            javax.swing.JTable table = new javax.swing.JTable(new javax.swing.table.DefaultTableModel(50, 2));
            trace.watch(table);
            return table;
        } finally {
            System.clearProperty(MouseTrace.PROPERTY);
        }
    }

    @Test
    @DisplayName("SUSPECT fires when the table is mid-drag with no button down — the stuck state itself")
    void theStuckStateIsFlagged(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("trace.log");
        javax.swing.JTable table = watchedTable(out);

        pressSomewhere();                                        // a real gesture happened at some point
        table.getSelectionModel().setValueIsAdjusting(true);     // the table believes a drag is in progress
        table.setRowSelectionInterval(3, 9);                     // ...and it extends, with no button down

        String log = Files.readString(out);
        assertTrue(log.contains("SUSPECT"), log);
        assertTrue(log.contains("tableIsMidDragButNoButtonIsDown"), log);
        assertTrue(log.contains("the RELEASED line above says where"),
                "the line tells a reader with no context what to look at next: " + log);
    }

    @Test
    @DisplayName("A PROGRAMMATIC selection is not suspect — a walk step or goto must not cry wolf")
    void anOrdinarySelectionIsNotFlagged(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("trace.log");
        javax.swing.JTable table = watchedTable(out);

        table.setRowSelectionInterval(3, 3);                     // exactly what selectModelRow does
        table.setRowSelectionInterval(3, 20);                    // and a grown selection, still not a drag

        assertFalse(Files.readString(out).contains("SUSPECT"),
                "every programmatic selection grows with no button down; flagging those makes the trace "
                        + "unreadable and the real signal invisible");
    }

    @Test
    @DisplayName("It actually records a real mouse event — the worst failure is a trace that stays empty")
    void itRecordsAMouseEventThroughTheToolkit(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        Path out = tmp.resolve("trace.log");
        System.setProperty(MouseTrace.PROPERTY, out.toString());
        try {
            assertNotNull(MouseTrace.installIfRequested());
            javax.swing.JPanel panel = new javax.swing.JPanel();
            panel.setSize(100, 100);
            // through the queue, so it reaches the Toolkit's AWTEventListener the way a real click does
            java.awt.EventQueue.invokeAndWait(() -> panel.dispatchEvent(new java.awt.event.MouseEvent(
                    panel, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                    java.awt.event.MouseEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false,
                    java.awt.event.MouseEvent.BUTTON1)));

            String log = Files.readString(out);
            assertTrue(log.contains("PRESSED"),
                    "a trace that records no mouse events is worse than no trace: it looks like evidence of "
                            + "nothing happening. Got:\n" + log);
            assertTrue(log.contains("src=JPanel"), log);
        } finally {
            System.clearProperty(MouseTrace.PROPERTY);
        }
    }

    /** A real press, through the queue, so the trace has seen a gesture. */
    private static void pressSomewhere() throws Exception {
        javax.swing.JPanel panel = new javax.swing.JPanel();
        panel.setSize(50, 50);
        java.awt.EventQueue.invokeAndWait(() -> panel.dispatchEvent(new java.awt.event.MouseEvent(
                panel, java.awt.event.MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                java.awt.event.MouseEvent.BUTTON1_DOWN_MASK, 5, 5, 1, false,
                java.awt.event.MouseEvent.BUTTON1)));
        java.awt.EventQueue.invokeAndWait(() -> panel.dispatchEvent(new java.awt.event.MouseEvent(
                panel, java.awt.event.MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0,
                5, 5, 1, false, java.awt.event.MouseEvent.BUTTON1)));
    }

    @Test
    @DisplayName("Opening a log churns the selection model — that is NOT suspect, and a real capture proved it")
    void modelChurnWithAnEmptySelectionIsNotSuspect(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        Path out = tmp.resolve("trace.log");
        javax.swing.JTable table = watchedTable(out);
        pressSomewhere();

        // exactly what a log open does: something was selected, then the model is swapped out under an
        // adjusting flag and the selection goes empty. clearSelection() on an ALREADY empty selection fires
        // no event at all, so the rule would never be reached and the test would pass for the wrong reason.
        table.setRowSelectionInterval(1, 1);
        table.getSelectionModel().setValueIsAdjusting(true);
        table.clearSelection();
        table.getSelectionModel().setValueIsAdjusting(false);

        assertFalse(Files.readString(out).contains("SUSPECT"),
                "the first capture produced three SUSPECT lines with size=0 during a log open; a runaway always "
                        + "has rows selected, so an empty selection is churn, not the fault");
    }
}
