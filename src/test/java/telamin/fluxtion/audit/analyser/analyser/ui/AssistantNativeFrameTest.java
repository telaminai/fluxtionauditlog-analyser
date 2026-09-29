package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.configure;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.panel;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.await;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.opened;
import static telamin.fluxtion.audit.analyser.analyser.ui.WalkPlaybackFrameTest.walk;

/**
 * OA-A6 / OA-A11 with NATIVE input (spec §3, §9: "native cross-window focus and mouse routing; direct component dispatch
 * is insufficient proof"). The OS routes every press and key here, through the Robot. Each precondition the desktop may
 * refuse — focus, a press reaching its target — is an explicit SKIP, never a pass: a focus skip is not a visual pass.
 */
class AssistantNativeFrameTest {

    static void click(Robot robot, JComponent c) throws Exception {
        AtomicReference<Point> at = new AtomicReference<>();
        onEdt(() -> {
            Point p = new Point(c.getWidth() / 2, Math.min(c.getHeight() / 2, 20));
            SwingUtilities.convertPointToScreen(p, c);
            at.set(p);
        });
        robot.mouseMove(at.get().x, at.get().y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        Thread.sleep(250);
    }

    /**
     * Click a point of {@code c} that no OTHER showing window covers (CI's screen is small enough for the assistant's
     * window to overlap the analyser's table — found by the stderr diagnostic: a centre press landed on that window).
     */
    static void clickUncovered(Robot robot, JComponent c) throws Exception {
        AtomicReference<Point> at = new AtomicReference<>();
        onEdt(() -> {
            java.awt.Window own = SwingUtilities.getWindowAncestor(c);
            for (int y = 10; y < c.getHeight() && at.get() == null; y += 20) {
                for (int x = 10; x < c.getWidth() && at.get() == null; x += 20) {
                    Point p = new Point(x, y);
                    SwingUtilities.convertPointToScreen(p, c);
                    boolean covered = false;
                    for (java.awt.Window w : java.awt.Window.getWindows()) {
                        if (w != own && w.isShowing() && w.getBounds().contains(p)) covered = true;
                    }
                    if (!covered) at.set(p);
                }
            }
        });
        assumeTrue(diagnose(at.get() != null, "an uncovered point of " + c.getClass().getSimpleName()),
                "part of the target must be uncovered on this screen — a SKIP, not a pass");
        robot.mouseMove(at.get().x, at.get().y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
        Thread.sleep(250);
    }

    static void type(Robot robot, int... keys) {
        for (int k : keys) {
            robot.keyPress(k);
            robot.keyRelease(k);
        }
        robot.waitForIdle();
    }

    /**
     * Records every native mouse press that reaches {@code target} (or a child), as the toolkit delivers it. A press that
     * reached its component is the precondition; whether the window also became ACTIVE is a window manager's business
     * (Xvfb has none), and the walk's rule is about the press, not the activation.
     */
    static final class PressesReaching implements AutoCloseable {
        final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        private final java.awt.event.AWTEventListener listener;

        PressesReaching(java.awt.Component target) {
            listener = e -> {
                if (e.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED && e.getSource() instanceof java.awt.Component c
                        && (c == target || SwingUtilities.isDescendingFrom(c, target))) count.incrementAndGet();
            };
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_EVENT_MASK);
        }

        @Override public void close() {
            java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
        }
    }

    /** CI does not upload frame reports: a skipped precondition says here, on stderr, which it was and what held focus. */
    static boolean diagnose(boolean ok, String precondition) throws Exception {
        if (ok) return true;
        AtomicReference<String> state = new AtomicReference<>();
        onEdt(() -> {
            var kfm = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager();
            StringBuilder b = new StringBuilder();
            b.append("focusOwner=").append(kfm.getFocusOwner() == null ? null : kfm.getFocusOwner().getClass().getSimpleName())
                    .append(" activeWindow=").append(kfm.getActiveWindow() == null ? null : kfm.getActiveWindow().getClass().getSimpleName()
                            + ":" + (kfm.getActiveWindow() instanceof java.awt.Frame fr ? fr.getTitle() : ""));
            for (java.awt.Window w : java.awt.Window.getWindows()) {
                if (w.isShowing()) b.append(" | showing ").append(w.getClass().getSimpleName()).append(" active=")
                        .append(w.isActive()).append(" focused=").append(w.isFocused()).append(" bounds=").append(w.getBounds());
            }
            state.set(b.toString());
        });
        System.err.println("[AssistantNativeFrameTest] precondition not met: " + precondition + " :: " + state.get());
        return false;
    }

    /** Give the composer keyboard focus: a native click first, then an explicit request (fixture set-up, as PR #62's). */
    static boolean focusComposer(Robot robot, JComponent composer) throws Exception {
        click(robot, composer);
        onEdt(() -> {
            java.awt.Window w = SwingUtilities.getWindowAncestor(composer);
            w.toFront();
            w.requestFocus();
            composer.requestFocusInWindow();
        });
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
        AtomicReference<Boolean> focused = new AtomicReference<>(false);
        while (System.nanoTime() < until) {
            onEdt(() -> focused.set(composer.isFocusOwner()));
            if (focused.get()) return true;
            Thread.sleep(50);
        }
        return false;
    }

    /** A two-step walk over record rows (the centre table), saved through the walk verb and shown from step 1. */
    static void showWalk(AsyncOpenInterleavingFrameTest.Frame f) throws Exception {
        onEdt(() -> render(f.ex, "walk", Map.of("name", "tour", "steps", List.of(
                Map.of("caption", "one", "targets", List.of(Map.of("target", "records:row:3", "caption", "DEMO row 3"))),
                Map.of("caption", "two", "targets", List.of(Map.of("target", "records:row:5", "caption", "DEMO row 5")))))));
        onEdt(() -> assertNull(f.frame.playWalk("tour", 0, "test")));
        await("step 1 shown", () -> walk(f).showing() && walk(f).step() == 0 && !"PREPARING".equals(walk(f).phase()));
    }

    @Test
    @DisplayName("OA-A6/A11: native typing and ← in the popped-out composer edit text; clicks there keep the walk; a click in the analyser ends it")
    void theWindowsInputIsTheAssistants(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Robot robot = new Robot();
        robot.setAutoDelay(30);
        Point before = MouseInfo.getPointerInfo().getLocation();
        try (FakeProvider provider = new FakeProvider(); var f = opened(tmp)) {
            configure(f.frame, provider);
            showWalk(f);
            onEdt(() -> panel(f.frame).hostButton().doClick());                      // pop out
            onEdt(() -> panel(f.frame).composerArea().setText(""));
            assumeTrue(diagnose(focusComposer(robot, panel(f.frame).composerArea()), "composer focus in the window"),
                    "the assistant window's composer must hold keyboard focus — a SKIP, not a pass");
            assertTrue(walk(f).showing(), "a native click in the assistant window did not end the walk");
            type(robot, KeyEvent.VK_A, KeyEvent.VK_B, KeyEvent.VK_LEFT, KeyEvent.VK_C);
            AtomicReference<String> text = new AtomicReference<>();
            onEdt(() -> text.set(panel(f.frame).composerArea().getText()));
            assertEquals("acb", text.get(), "← moved the caret in the composer");
            assertEquals(0, walk(f).step(), "← in the composer did not step the walk");
            assertTrue(walk(f).showing());
            click(robot, panel(f.frame).conversationView());                          // select/scroll the conversation
            assertTrue(walk(f).showing(), "selecting in the assistant's conversation keeps the walk");
            JComponent table = (JComponent) field(field(f.frame, "tablePanel"), "table");
            try (PressesReaching reached = new PressesReaching(f.frame.getRootPane())) {
                clickUncovered(robot, table);                                          // an outside press in the analyser
                assumeTrue(diagnose(reached.count.get() > 0, "a native press reached the analyser (" + reached.count.get() + ")"),
                        "the native press must reach the analyser window — a SKIP, not a pass");
            }
            await("an unrelated press in the analyser ended the walk", () -> !walk(f).showing());
        } finally {
            robot.mouseMove(before.x, before.y);
        }
    }

    @Test
    @DisplayName("OA-A11: a native click in the DOCKED assistant keeps the walk; the same click in the table ends it")
    void theDockedAssistantsPressesAreItsOwn(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Robot robot = new Robot();
        robot.setAutoDelay(30);
        Point before = MouseInfo.getPointerInfo().getLocation();
        try (FakeProvider provider = new FakeProvider(); var f = opened(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> {
                JTabbedPane tabs = (JTabbedPane) field(f.frame, "sideTabs");
                tabs.setSelectedIndex(tabs.indexOfTab("Analyser assistant"));
            });
            showWalk(f);
            AtomicReference<Boolean> visible = new AtomicReference<>();
            onEdt(() -> visible.set(panel(f.frame).conversationView().isShowing()));
            assumeTrue(diagnose(visible.get(), "the docked assistant is on screen"),
                    "the docked assistant must be on screen beside the walk — a SKIP, not a pass");
            try (PressesReaching reached = new PressesReaching(f.frame.getRootPane())) {
                click(robot, panel(f.frame).conversationView());
                assumeTrue(diagnose(reached.count.get() > 0, "a native press reached the analyser (" + reached.count.get() + ")"),
                        "the native press must reach the analyser window — a SKIP, not a pass");
            }
            assertTrue(walk(f).showing(), "a native click in the docked assistant did not end the walk");
            JComponent table = (JComponent) field(field(f.frame, "tablePanel"), "table");
            click(robot, table);
            await("the same click in the table ends it", () -> !walk(f).showing());
        } finally {
            robot.mouseMove(before.x, before.y);
        }
    }
}
