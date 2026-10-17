package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.assistant.FakeProvider;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.configure;
import static telamin.fluxtion.audit.analyser.analyser.ui.AssistantLiveFrameTest.panel;

/**
 * M74 P2 (spec §5.8, OD-74.21, r8 F1 and F19; §10) on a REAL frame: with nothing open the start page's button shows the
 * conversation — the assistant popped out into its own window, the composer holding the draft, and (Claude Code) Start
 * armed and visible — and the page's keyboard works in light and dark. Keys by direct dispatch through the components'
 * own bindings (posted KeyEvents are dropped with no focus owner on this machine).
 */
class StartPromptFrameTest {

    static StartPanel startPanel(MainFrame f) {
        return (StartPanel) field(f, "startPanel");
    }

    static void showStart(MainFrame f) throws Exception {
        onEdt(() -> {
            try {
                var show = MainFrame.class.getDeclaredMethod("showWorkspace", boolean.class);
                show.setAccessible(true);
                show.invoke(f, true);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    static SessionDriver session(MainFrame f) throws Exception {
        AtomicReference<SessionDriver> d = new AtomicReference<>();
        onEdt(() -> {
            try {
                var m = MainFrame.class.getDeclaredMethod("session");
                m.setAccessible(true);
                d.set((SessionDriver) m.invoke(f));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        return d.get();
    }

    static Window hostOf(JComponent c) throws Exception {
        AtomicReference<Window> w = new AtomicReference<>();
        onEdt(() -> w.set(SwingUtilities.getWindowAncestor(c)));
        return w.get();
    }

    /**
     * ★ r8 F1 (in-app). Witness: compose with {@code popOut = false} whatever the workspace (the P1 {@code
     * revealAssistant()} alone) — the panel stays in the hidden investigation card, its window is the analyser's, and
     * the "popped out and showing" assertion fails.
     */
    @Test
    @DisplayName("★ with nothing open, S6 then the button leaves the popped-out assistant showing the draft; nothing sent")
    void nothingOpenShowsTheConversation(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (FakeProvider provider = new FakeProvider();
             var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            configure(f.frame, provider);
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            showStart(f.frame);
            session(f.frame);
            onEdt(() -> {
                StartPromptView prompt = startPanel(f.frame).prompt();
                prompt.choose(5);                                 // S6
                prompt.actionButton().doClick();
            });
            Thread.sleep(300);
            Window host = hostOf(panel(f.frame));
            assertNotSame(f.frame, host, "the assistant popped out into its own window (OD-74.21)");
            AtomicReference<Boolean> showing = new AtomicReference<>();
            AtomicReference<String> draft = new AtomicReference<>();
            onEdt(() -> {
                showing.set(host.isShowing());
                draft.set(panel(f.frame).draftText());
            });
            assertTrue(showing.get(), "and it is showing");
            String expected = AskCatalogue.composeStart("S6", AskCatalogue.StartFacts.none("")).text();
            assertEquals(expected, draft.get(), "the composer holds the draft");
            assertEquals(0, provider.bodies.size(), "nothing is sent until Send");
        }
    }

    /**
     * ★ r8 F1 + F2 (Claude Code): the draft is placed and Start ARMED, visible, in the popped-out window; no process
     * starts. Witness: arm whatever the consent (REST off) — the armed Start would then be one the node refuses.
     */
    @Test
    @DisplayName("★ Claude Code with nothing open: the draft placed, Start armed and visible, and no process started")
    void claudeCodeArmsStart(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                AppConfig cfg = (AppConfig) field(f.frame, "config");
                cfg.claudeCodeAllowed = true;
                cfg.assistantActionsRest = true;
                f.frame.claudeFoundForTest = true;
                try {
                    var rest = MainFrame.class.getDeclaredMethod("reportRestSwitch");   // review B5: the switch is a fact; actionListener decides
                    rest.setAccessible(true);
                    rest.invoke(f.frame);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
                f.frame.setSize(1200, 800);
                f.frame.setVisible(true);
            });
            showStart(f.frame);
            SessionDriver d = session(f.frame);
            onEdt(() -> d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test")));
            onEdt(() -> {
                StartPromptView prompt = startPanel(f.frame).prompt();
                prompt.box().setText("Which node writes the spread?");
                prompt.actionButton().doClick();
            });
            Thread.sleep(300);
            LocalAgentPanel agent = (LocalAgentPanel) field(f.frame, "localAgentPanel");
            Window host = hostOf(agent);
            assertNotSame(f.frame, host, "popped out");
            AtomicReference<String> start = new AtomicReference<>();
            AtomicReference<String> composer = new AtomicReference<>();
            AtomicReference<Boolean> visible = new AtomicReference<>();
            onEdt(() -> {
                start.set(agent.startButton().getText());
                visible.set(agent.startButton().isShowing());
                composer.set(agent.composerArea().getText());
            });
            assertEquals(AskCatalogue.text("armed.START_PAGE"), start.get(), "Start is armed");
            assertTrue(visible.get(), "and visible");
            assertEquals("Which node writes the spread?", composer.get());
            assertEquals(0, d.snapshot().localAgent().generation(), "no process was started: an armed Start starts nothing");
        }
    }

    /** ★ r8 F19: the page's keyboard on a real frame, in both themes; a typed digit in the box is a digit. */
    @Test
    @DisplayName("★ Alt+2 chooses S2 and the button follows it, in light and in dark")
    void keyboardInBothThemes(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        javax.swing.LookAndFeel original = javax.swing.UIManager.getLookAndFeel();
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            showStart(f.frame);
            session(f.frame);
            for (String theme : new String[]{"Light", "Dark"}) {
                onEdt(() -> {
                    ThemeManager.apply(theme);
                    SwingUtilities.updateComponentTreeUI(f.frame);
                    StartPromptView prompt = startPanel(f.frame).prompt();
                    prompt.unchoose();
                    KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK);
                    Object name = prompt.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(ks);
                    KeyEvent e = new KeyEvent(prompt.box(), KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                            InputEvent.ALT_DOWN_MASK, KeyEvent.VK_2, KeyEvent.CHAR_UNDEFINED);
                    assertTrue(SwingUtilities.notifyAction(prompt.getActionMap().get(name), ks, e, prompt.box(),
                            e.getModifiersEx()));
                    assertEquals("S2", prompt.chosen().id(), theme);
                    assertEquals(AskCatalogue.text("S2.verb"), prompt.actionButton().getText(), theme);
                    assertTrue(prompt.isShowing(), theme + ": the prompt is on screen");
                });
            }
        } finally {
            // the next suite in this JVM must meet the look and feel it would have met (SpotlightFrameTest samples pixels)
            onEdt(() -> {
                try {
                    javax.swing.UIManager.setLookAndFeel(original);
                } catch (javax.swing.UnsupportedLookAndFeelException e) {
                    throw new AssertionError(e);
                }
            });
        }
    }
}
