package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/**
 * The 1.14.0 tracker carried a list of checks "only a person at the screen can do". Three of them are not
 * about a person's hand at all — they are about which listener fires and what state it leaves — so a real
 * frame on a display can hold them, and the CI {@code ui-frame} job runs them under xvfb:
 * <ul>
 *   <li>Escape with keyboard focus in a component that has its own Escape (the time-range combo's popup)
 *       while SEVERAL spotlights are lit: the key event is POSTED through the AWT queue, so it walks the same
 *       focus-manager and key-binding path a keyboard does — the one thing a Robot would add is the OS.</li>
 *   <li>AI ▸ Posture and AI ▸ Clear mode-selector record: the real {@link JMenuItem}s' listeners, found by
 *       walking the menu bar, and the shared canvas read back through {@code context.handoff}.</li>
 *   <li>The Project-panel row for a log RESTORED at startup: the same {@code openFile(path, atStartup(true))}
 *       the launcher makes, and the attribution the row and {@code context} then carry.</li>
 * </ul>
 * What is left for a person: Audit log ▸ Close log during a slow first load as a physical click (its listener is
 * held by {@code AsyncOpenInterleavingFrameTest}), AI ▸ Place mode-selector record… (a file chooser), and
 * the two checks that need a context-free LLM client.
 */
class PersonAtTheScreenFrameTest {

    private static final String SERIES_LOG = "src/main/resources/demo/demo-quote-series.yaml";

    @Test
    void escapeWithTheSearchHistoryPopupFocused_putsSeveralSpotlightsOut(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (Frame f = new Frame(tmp)) {
            onEdt(() -> {                                        // a spotlight needs a laid-out, visible window
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
            });
            onEdt(() -> render(f.ex, "open", Map.of("log", Path.of(SERIES_LOG).toAbsolutePath().toString())));
            awaitLoaded(f);
            onEdt(() -> {
                Map<String, Object> r = render(f.ex, "spotlight", Map.of("targets", List.of(
                        Map.of("target", "status", "caption", "one"),
                        Map.of("target", "toolbar:flag", "caption", "two"))));
                assertEquals(true, r.get("ok"), r::toString);
                assertEquals(2, lit(f).size(), "two spotlights lit");
            });
            // focus the time-range Window combo and open its popup — a component with its OWN Escape
            // (the search box's history popup is empty on a fresh home, so it has nothing to show)
            javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) field(f.frame, "windowCombo");
            onEdt(() -> {
                f.frame.toFront();
                f.frame.requestFocus();
                combo.requestFocusInWindow();
                combo.showPopup();
            });
            pump();
            // A POSTED key event is discarded by the focus manager unless the window owns keyboard focus, so
            // this check is only real where the display gives the frame focus (xvfb in CI does; a Mac whose
            // foreground app is the terminal does not — there it is skipped, and CI's skip guard would say so).
            final boolean[] focused = {false};
            onEdt(() -> focused[0] = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow() == f.frame);
            System.out.println("focus attempt 1: " + (focused[0] ? "acquired" : "retry needed"));
            if (!focused[0]) {
                var acquired = new java.util.concurrent.CountDownLatch(1);
                var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
                java.beans.PropertyChangeListener listener = event -> {
                    if (event.getNewValue() == f.frame) acquired.countDown();
                };
                try {
                    onEdt(() -> {
                        manager.addPropertyChangeListener("focusedWindow", listener);
                        f.frame.toFront();
                        f.frame.requestFocus();
                        combo.requestFocusInWindow();
                        if (manager.getFocusedWindow() == f.frame) acquired.countDown();
                    });
                    acquired.await(2, java.util.concurrent.TimeUnit.SECONDS); // condition, not a settling sleep
                    onEdt(() -> focused[0] = manager.getFocusedWindow() == f.frame);
                } finally {
                    onEdt(() -> manager.removePropertyChangeListener("focusedWindow", listener));
                }
                System.out.println("focus attempt 2: " + (focused[0] ? "acquired" : "skip: still unavailable"));
            }
            assumeTrue(focused[0], "the display did not give the frame keyboard focus — a posted Escape would be dropped, not tested");
            onEdt(() -> assertTrue(combo.isPopupVisible(), "control: the combo's popup is open"));

            // With the popup OPEN the combo's own Escape (hidePopup) consumes the first press; with it closed
            // that action is disabled and the overlay's WHEN_IN_FOCUSED_WINDOW binding takes the key. So one
            // or two presses, never more — a person is never stuck.
            int presses = 0;
            while (!lit(f).isEmpty() && presses < 2) {
                postEscape(combo);
                presses++;
                pump();
            }
            int pressesNeeded = presses;
            onEdt(() -> {
                assertEquals(List.of(), lit(f), "Escape put every spotlight out (presses needed: " + pressesNeeded + ")");
                assertFalse(combo.isPopupVisible(), "and the popup is closed too");
            });
            assertTrue(pressesNeeded >= 1 && pressesNeeded <= 2, "one or two Escapes, not more");
        }
    }

    @Test
    void theAiMenusPostureItems_andClearRecord_writeTheSharedCanvas(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (Frame f = new Frame(tmp)) {
            JMenu ai = menu(f.frame.getJMenuBar().getComponents(), "AI");
            JMenu posture = (JMenu) item(ai.getMenuComponents(), "Posture");
            JMenuItem research = item(posture.getMenuComponents(), "Research / support");
            JMenuItem authoring = item(posture.getMenuComponents(), "Authoring / deploy");
            JMenuItem derived = item(posture.getMenuComponents(), "Derived");
            JMenuItem clearRecord = item(ai.getMenuComponents(), "Clear mode-selector record");

            onEdt(() -> research.doClick(0));
            onEdt(() -> assertTrue(String.valueOf(handoff(f)).contains("research"), "AI ▸ Posture ▸ Research: " + handoff(f)));
            onEdt(() -> authoring.doClick(0));
            onEdt(() -> assertTrue(String.valueOf(handoff(f)).contains("authoring"), "AI ▸ Posture ▸ Authoring: " + handoff(f)));
            onEdt(() -> derived.doClick(0));
            onEdt(() -> assertTrue(String.valueOf(handoff(f)).contains("derived"), "AI ▸ Posture ▸ Derived: " + handoff(f)));
            // Clear record with no record placed: a no-op that must neither throw nor open a dialog
            onEdt(() -> clearRecord.doClick(0));
            pump();
            onEdt(() -> assertTrue(String.valueOf(handoff(f)).contains("derived"), "clearing the record left the posture alone"));
            assertEquals(0, f.dialogs.seen(), "no dialog fired from the menu path");
        }
    }

    @Test
    void aLogRestoredAtStartup_isAttributedToThePreviousSession_onContextAndTheProjectRow(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (Frame f = new Frame(tmp)) {
            Path log = Path.of(SERIES_LOG).toAbsolutePath();
            onEdt(() -> f.frame.openFile(log, OpenRequest.atStartup(true)));   // exactly what Main does with no file argument
            awaitLoaded(f);
            onEdt(() -> {
                Map<String, Object> ctx = render(f.ex, "context", Map.of());
                Object openedBy = find(find(ctx, "log"), "openedBy");
                assertNotNull(openedBy, "context.log.openedBy");
                assertTrue(String.valueOf(openedBy).contains("restored"), "restored at startup, not 'you': " + openedBy);
                ProjectModel model = ProjectModel.from(asMap(find(ctx, "context")));
                ProjectModel.Section logSection = model.section(ProjectModel.LOG);
                assertNotNull(logSection, "the Project panel has an Audit log section");
                String rows = String.valueOf(logSection.rows());
                assertTrue(rows.contains("opened by") && rows.contains("restored"),
                        "the Project row says who opened it: " + rows);
            });
        }
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) { return (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    private static List<Object> lit(Frame f) {
        Object lit = find(find(render(f.ex, "context", Map.of()), "spotlight"), "lit");
        return lit == null ? List.of() : (List<Object>) lit;
    }

    private static Object handoff(Frame f) {
        return find(render(f.ex, "context", Map.of()), "handoff");
    }

    private static void awaitLoaded(Frame f) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            final Object[] records = new Object[1];
            onEdt(() -> records[0] = find(find(render(f.ex, "context", Map.of()), "log"), "records"));
            if (records[0] != null) return;
            Thread.sleep(100);
        }
        fail("the log did not finish loading in 30 s");
    }

    private static void postEscape(Component target) {
        long when = System.currentTimeMillis();
        EventQueue q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
        q.postEvent(new KeyEvent(target, KeyEvent.KEY_PRESSED, when, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
        q.postEvent(new KeyEvent(target, KeyEvent.KEY_RELEASED, when, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
    }

    /** Let everything already queued on the EDT run (posted events included). */
    private static void pump() throws Exception {
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    private static JMenu menu(Component[] bar, String text) {
        for (Component c : bar) if (c instanceof JMenu m && text.equals(m.getText())) return m;
        throw new AssertionError("no menu '" + text + "'");
    }

    private static JMenuItem item(Component[] items, String text) {
        for (Component c : items) if (c instanceof JMenuItem m && text.equals(m.getText())) return m;
        throw new AssertionError("no item '" + text + "'");
    }
}
