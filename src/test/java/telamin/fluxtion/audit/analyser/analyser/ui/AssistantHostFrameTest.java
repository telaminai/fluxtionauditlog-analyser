package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.assistant;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.configure;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.panel;

/**
 * OA-2 in the real frame (spec §3, OA-A1): one assistant, two hosts. Moving between them never duplicates the composer,
 * never loses the draft, never sends twice, and never cancels a request; closing the window docks it. Presses here are
 * component-level — the cross-window NATIVE claims (focus, typing, a walk kept by a click in the window) are
 * {@code AssistantNativeFrameTest}'s.
 */
class AssistantHostFrameTest {

    static Window hostOf(MainFrame f) throws Exception {
        AtomicReference<Window> w = new AtomicReference<>();
        onEdt(() -> w.set(SwingUtilities.getWindowAncestor(panel(f))));
        return w.get();
    }

    static void selectTab(MainFrame f, String title) throws Exception {
        onEdt(() -> {
            JTabbedPane tabs = (JTabbedPane) field(f, "sideTabs");
            tabs.setSelectedIndex(tabs.indexOfTab(title));
        });
    }

    @Test
    @DisplayName("OA-A1: pop out, switch Summary→Graph→Topology, dock mid-request: one draft, one request, one conversation")
    void oneConversationAcrossHosts(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            onEdt(() -> panel(f.frame).composerArea().setText("keep this draft"));
            onEdt(() -> panel(f.frame).hostButton().doClick());                       // Pop out
            Window popped = hostOf(f.frame);
            assertNotSame(f.frame, popped, "the ONE panel is now in its own window");
            assertInstanceOf(JFrame.class, popped);
            assertNull(((JFrame) popped).getOwner(), "unowned: it does not float above the analyser");
            AtomicReference<String> draft = new AtomicReference<>();
            onEdt(() -> draft.set(panel(f.frame).draftText()));
            assertEquals("keep this draft", draft.get(), "the draft moved with the panel");
            for (String tab : new String[]{"Summary", "Graph", "Topology"}) {
                selectTab(f.frame, tab);
                assertSame(popped, hostOf(f.frame), "switching to " + tab + " does not move, hide or reset the assistant");
                assertTrue(popped.isShowing(), tab);
            }
            provider.gate = new CountDownLatch(1);
            provider.replies.add("One answer.");
            onEdt(() -> panel(f.frame).sendButton().doClick());
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (provider.bodies.isEmpty() && System.nanoTime() < until) Thread.sleep(20);
            onEdt(() -> panel(f.frame).hostButton().doClick());                       // Dock, mid-request
            assertSame(f.frame, hostOf(f.frame), "docked back into the side tab");
            AtomicReference<AssistantState> s = new AtomicReference<>();
            onEdt(() -> s.set(assistant(f.frame)));
            assertTrue(s.get().busy(), "docking did not cancel the request: " + s.get().phase());
            long conversation = s.get().conversation();
            provider.gate.countDown();
            AssistantLiveFrameTest.awaitIdle(f.frame);
            onEdt(() -> s.set(assistant(f.frame)));
            assertEquals("COMPLETE", s.get().phase());
            assertEquals(conversation, s.get().conversation(), "the same conversation throughout");
            assertEquals(1, provider.bodies.size(), "ONE Send across pop out, three tabs and dock made ONE request");
            AtomicReference<String> shown = new AtomicReference<>();
            onEdt(() -> shown.set(panel(f.frame).conversationView().getText()));
            assertTrue(shown.get().contains("keep this draft") && shown.get().contains("One answer."), shown.get());
        }
    }

    @Test
    @DisplayName("OA-A1: closing the assistant window docks it, keeping the conversation and the request in flight")
    void closingTheWindowDocks(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            onEdt(() -> panel(f.frame).hostButton().doClick());
            Window popped = hostOf(f.frame);
            provider.gate = new CountDownLatch(1);
            onEdt(() -> {
                panel(f.frame).composerArea().setText("still here?");
                panel(f.frame).sendButton().doClick();
            });
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (provider.bodies.isEmpty() && System.nanoTime() < until) Thread.sleep(20);
            onEdt(() -> popped.dispatchEvent(new WindowEvent(popped, WindowEvent.WINDOW_CLOSING)));
            assertSame(f.frame, hostOf(f.frame), "closing the window docked the assistant");
            assertFalse(popped.isShowing());
            AtomicReference<AssistantState> s = new AtomicReference<>();
            onEdt(() -> s.set(assistant(f.frame)));
            assertTrue(s.get().docked());
            assertTrue(s.get().busy(), "closing the window did not cancel the request");
            provider.gate.countDown();
            AssistantLiveFrameTest.awaitIdle(f.frame);
            onEdt(() -> s.set(assistant(f.frame)));
            assertEquals("COMPLETE", s.get().phase());
            assertTrue(f.frame.isDisplayable(), "the analyser did not exit");
        }
    }

    @Test
    @DisplayName("OA-2: disposing the analyser disposes its assistant window — an unowned window is not disposed with it by Swing")
    void theWindowGoesWithTheAnalyser(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Window popped;
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            onEdt(() -> panel(f.frame).hostButton().doClick());
            popped = hostOf(f.frame);
            assertTrue(popped.isShowing());
        }                                                          // the fixture disposes the analyser
        AtomicReference<Boolean> gone = new AtomicReference<>();
        onEdt(() -> gone.set(!popped.isDisplayable()));
        assertTrue(gone.get(), "found on CI: the assistant window outlived its analyser");
    }

    @Test
    @DisplayName("OA-2: at 1200×800, docked and popped out, Send and Cancel are on screen and a theme switch reaches the window")
    void reachableAtTheDefaultSizeAndThemed(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            selectTab(f.frame, "Analyser assistant");
            onEdt(() -> {
                var send = panel(f.frame).sendButton();
                assertTrue(send.isShowing(), "Send is showing while docked");
                var at = SwingUtilities.convertRectangle(send.getParent(), send.getBounds(), f.frame.getRootPane());
                assertTrue(f.frame.getRootPane().getBounds().contains(at), "Send is inside the window: " + at);
            });
            onEdt(() -> panel(f.frame).hostButton().doClick());
            Window popped = hostOf(f.frame);
            onEdt(() -> {
                assertTrue(panel(f.frame).sendButton().isShowing());
                assertTrue(panel(f.frame).cancelButton().isShowing());
            });
            var apply = MainFrame.class.getDeclaredMethod("applyTheme", String.class);
            apply.setAccessible(true);
            onEdt(() -> {
                try {
                    apply.invoke(f.frame, "Dark");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                assertEquals(javax.swing.UIManager.getColor("Panel.background"), panel(f.frame).getBackground(),
                        "the popped-out panel follows the theme");
            });
            assertTrue(popped.isShowing());
        }
    }
}
