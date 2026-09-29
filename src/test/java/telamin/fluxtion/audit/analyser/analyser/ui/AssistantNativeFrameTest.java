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

    static void type(Robot robot, int... keys) {
        for (int k : keys) {
            robot.keyPress(k);
            robot.keyRelease(k);
        }
        robot.waitForIdle();
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
            click(robot, panel(f.frame).composerArea());
            AtomicReference<Boolean> focused = new AtomicReference<>();
            onEdt(() -> focused.set(panel(f.frame).composerArea().isFocusOwner()));
            assumeTrue(focused.get(), "the OS must give the assistant window's composer focus — a SKIP, not a pass");
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
            click(robot, table);                                                       // an outside press in the analyser
            onEdt(() -> { });
            assumeTrue(f.frame.isActive() || f.frame.isFocused(), "the press must reach the analyser window — a SKIP, not a pass");
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
            assumeTrue(visible.get(), "the docked assistant must be on screen beside the walk — a SKIP, not a pass");
            click(robot, panel(f.frame).conversationView());
            assumeTrue(f.frame.isActive(), "the press must reach the analyser window — a SKIP, not a pass");
            assertTrue(walk(f).showing(), "a native click in the docked assistant did not end the walk");
            JComponent table = (JComponent) field(field(f.frame, "tablePanel"), "table");
            click(robot, table);
            await("the same click in the table ends it", () -> !walk(f).showing());
        } finally {
            robot.mouseMove(before.x, before.y);
        }
    }
}
