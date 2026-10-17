package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.StartPageState;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.8, r8 F9, F10, F15, F18, F19; §10): the start page's prompt — choosing is local and posts nothing, the
 * chosen entry and the text are separate, the box shows the whole text, the keyboard and the names, the no-AI fallbacks
 * as buttons, and the full-posture cue on the box. Headless; keys by DIRECT dispatch through the component's own
 * bindings (posted KeyEvents are dropped with no focus owner on this machine).
 */
class StartPromptViewTest {

    /** A recording host: the button and the fallbacks are the only things the view may ask for. */
    static final class Host implements StartPromptView.Host {
        final List<String> presses = new ArrayList<>();
        final List<String> fallbacks = new ArrayList<>();

        @Override public void press(String entryId, String text) { presses.add(entryId + "|" + text); }
        @Override public void fallback(AskCatalogue.Fallback f, String entryId, String text) { fallbacks.add(f + "|" + entryId); }
        @Override public String claudeSearched() { return "/a/bin, /b/bin"; }
    }

    static StartPageState route(StartPageState.Route r) {
        return new StartPageState(r, false, null, "", 0);
    }

    static <T> T onEdt(java.util.concurrent.Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                out.set(c.call());
            } catch (Exception e) {
                err.set(e);
            }
        });
        if (err.get() != null) throw err.get();
        return out.get();
    }

    static void bind(JComponent c, KeyStroke ks, int condition) {
        Object name = c.getInputMap(condition).get(ks);
        assertNotNull(name, "a binding for " + ks);
        KeyEvent e = new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), ks.getModifiers(), ks.getKeyCode(),
                KeyEvent.CHAR_UNDEFINED);
        assertTrue(SwingUtilities.notifyAction(c.getActionMap().get(name), ks, e, c, e.getModifiersEx()));
    }

    // ---- ★ choosing posts nothing; the chosen entry and the text are separate (r8 F9, F10) ---------------------------

    /** ★ Witness: press the host from {@code choose} (a fact on choose) — the zero-presses assertion fails. */
    @Test
    @DisplayName("★ choosing posts nothing; choose S5 then S2: one chip (S2), the box untouched; the button runs S2 with the text")
    void choosingIsLocal() throws Exception {
        Host host = new Host();
        onEdt(() -> {
            StartPromptView v = new StartPromptView(host);
            v.render(route(StartPageState.Route.IN_APP), LocalAgentState.IDLE);
            v.choose(4);                                         // S5
            assertEquals("S5", v.chosen().id());
            assertEquals(AskCatalogue.text("S5.verb"), v.actionButton().getText(), "the button takes the entry's verb");
            v.choose(1);                                         // S2 replaces it
            assertEquals("S2", v.chosen().id());
            assertEquals(AskCatalogue.text("S2.label"), v.chipLabel().getText(), "one chip");
            assertEquals("", v.box().getText(), "a flow entry never touches the box");
            assertEquals(List.of(), host.presses, "choosing posted nothing");
            v.box().setText("my notes");
            v.box().setText("");                                 // deleting the box text: the button still runs S2
            v.actionButton().doClick();
            assertEquals(List.of("S2|"), host.presses);
            v.unchoose();
            assertEquals(AskCatalogue.text("start.send"), v.actionButton().getText(), "removing the chip returns Send");
            return null;
        });
    }

    @Test
    @DisplayName("a prompt entry APPENDS its text to the box, never replacing what the person typed")
    void promptAppends() throws Exception {
        onEdt(() -> {
            StartPromptView v = new StartPromptView(new Host());
            v.render(route(StartPageState.Route.IN_APP), LocalAgentState.IDLE);
            v.box().setText("Before:");
            v.choose(5);                                         // S6
            assertTrue(v.box().getText().startsWith("Before:\n\nWhat can the Fluxtion Audit Log Analyser do for me?"),
                    v.box().getText());
            assertNull(v.chosen(), "a prompt entry is no chip");
            return null;
        });
    }

    // ---- ★ keyboard and names (r8 F19) -------------------------------------------------------------------------------

    /** ★ Witness: bind the plain digit keys to choose — a typed "3" in the box then chooses S3 and this fails. */
    @Test
    @DisplayName("★ Up/Down/Enter choose; Alt+1–Alt+9 choose the first nine; a typed digit is a digit; each row is named")
    void keyboardAndNames() throws Exception {
        onEdt(() -> {
            StartPromptView v = new StartPromptView(new Host());
            v.render(route(StartPageState.Route.IN_APP), LocalAgentState.IDLE);
            JList<StartPromptView.Row> list = v.promptList();
            // the view's own ancestor binding: it fires for a key in the box or the list (wherever the focus is in it)
            bind(v, KeyStroke.getKeyStroke(KeyEvent.VK_3, InputEvent.ALT_DOWN_MASK),
                    JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            assertEquals("S3", v.chosen().id(), "Alt+3");
            assertNull(v.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0)), "a plain digit is not bound");
            assertNull(v.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_0 + 10 % 10, InputEvent.ALT_DOWN_MASK)), "no mnemonic past 9");
            v.box().replaceSelection("1");
            assertEquals("1", v.box().getText(), "typed into the box");
            assertEquals("S3", v.chosen().id(), "and chose nothing");
            list.setSelectedIndex(4);
            bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);
            assertEquals("S5", v.chosen().id(), "Enter chooses the selected row");
            var renderer = list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(2), 2,
                    false, false);
            assertEquals("Prompt 3 of 6: Open an evidence bundle someone sent me",
                    renderer.getAccessibleContext().getAccessibleName());
            assertEquals(Boolean.TRUE, ((JComponent) renderer).getClientProperty(UiText.HTML_DISABLE), "plain (r8 F15)");
            return null;
        });
    }

    /** ★ r8 F18. Witness: a one-line JTextField for the box — the 500-character text is cut and this fails. */
    @Test
    @DisplayName("★ the box shows the whole text: it wraps, grows to six lines, then scrolls; nothing hidden by an ellipsis")
    void theWholeText() throws Exception {
        onEdt(() -> {
            StartPromptView v = new StartPromptView(new Host());
            String text = "word ".repeat(100).strip();
            v.box().setText(text);
            assertTrue(v.box().getLineWrap() && v.box().getWrapStyleWord());
            assertEquals(text, v.box().getText());
            v.boxScroll().setSize(400, 400);
            v.box().setSize(380, 10_000);
            int line = v.box().getFontMetrics(v.box().getFont()).getHeight();
            int h = v.box().getPreferredScrollableViewportSize().height;
            assertTrue(h > 2 * line && h <= 6 * line + 4, "grows, at most six lines: " + h + " (line " + line + ")");
            return null;
        });
    }

    // ---- no dead end: the no-AI fallbacks are buttons (r8 F7) --------------------------------------------------------

    @Test
    @DisplayName("with no AI, the chosen entry's fallbacks are buttons that run existing actions; the menu path is a tooltip")
    void fallbacks() throws Exception {
        Host host = new Host();
        onEdt(() -> {
            StartPromptView v = new StartPromptView(host);
            v.render(route(StartPageState.Route.NO_AI), LocalAgentState.IDLE);
            List<JButton> buttons = buttons(v.fallbacksPanel());
            assertEquals(List.of("User guide"), buttons.stream().map(JButton::getText).toList(), "S6 by default");
            assertEquals("Help ▸ User guide", buttons.get(0).getToolTipText());
            v.choose(0);
            buttons = buttons(v.fallbacksPanel());
            assertEquals(List.of("Assistant settings", "Connect an AI client…", "Copy prompt"),
                    buttons.stream().map(JButton::getText).toList());
            buttons.get(2).doClick();
            assertEquals(List.of("COPY_PROMPT|S1"), host.fallbacks);
            return null;
        });
    }

    static List<JButton> buttons(java.awt.Container c) {
        List<JButton> out = new ArrayList<>();
        for (var x : c.getComponents()) if (x instanceof JButton b) out.add(b);
        return out;
    }

    @Test
    @DisplayName("the button reads Ask with Claude Code when the other assistant answers; NO_AI with claude not found names it")
    void routeWords() throws Exception {
        onEdt(() -> {
            StartPromptView v = new StartPromptView(new Host());
            v.box().setText("Q");
            v.render(route(StartPageState.Route.SWITCH_TO_CLAUDE_CODE), LocalAgentState.IDLE);
            assertEquals(AskCatalogue.text("start.askClaude"), v.actionButton().getText());
            v.render(new StartPageState(StartPageState.Route.NO_AI, true, null, "", 0), LocalAgentState.IDLE);
            assertTrue(buttons(v.fallbacksPanel()).stream().anyMatch(b -> b.getText().equals("Install Claude Code")));
            return null;
        });
    }

    // ---- the full-posture cue on the box (owner request, 2026-10-17) -----------------------------------------------

    static LocalAgentState fullOn(LocalAgentPosture.FullMode mode) {
        LocalAgentState i = LocalAgentState.IDLE;
        return new LocalAgentState(AssistantMode.LOCAL_AGENT, 1, 0, "OFF", "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false,
                false, 0, List.of(), i.answer(), LocalAgentPosture.ANALYSER_ASSISTANT, 0, i.billing(), null,
                LocalAgentPosture.full(mode));
    }

    /** ★ Witness: key the box's cue on the route alone (always on toward Claude Code) — the default-posture row fails. */
    @Test
    @DisplayName("★ the start box carries the full-posture badge and border iff the question goes to a full Claude Code")
    void fullCueOnTheBox() throws Exception {
        onEdt(() -> {
            StartPromptView v = new StartPromptView(new Host());
            v.render(route(StartPageState.Route.CLAUDE_CODE), LocalAgentState.IDLE);
            assertFalse(v.fullBadge().isVisible(), "the default posture: no cue");
            v.render(route(StartPageState.Route.CLAUDE_CODE), fullOn(LocalAgentPosture.FullMode.ACCEPT_EDITS));
            assertTrue(v.fullBadge().isVisible());
            assertTrue(v.fullBadge().getText().contains("Full Claude Code (preview) · acceptEdits: your tools and permissions"),
                    v.fullBadge().getText());
            assertTrue(v.fullBadge().getText().startsWith("◆◆"), "acceptEdits is the strong cue: not colour alone");
            assertNotNull(v.fullBadge().getToolTipText());
            v.render(route(StartPageState.Route.IN_APP), fullOn(LocalAgentPosture.FullMode.ACCEPT_EDITS));
            assertFalse(v.fullBadge().isVisible(), "a question for the in-app assistant: no cue");
            return null;
        });
    }
}
