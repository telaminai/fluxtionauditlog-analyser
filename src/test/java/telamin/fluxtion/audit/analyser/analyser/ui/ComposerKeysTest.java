package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.StartPageState;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.FocusEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.text.AttributedString;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final review of the M74 usability pass (2026-10-17) #7 and #8: the keys of the start box and the composer. A held
 * Enter that placed a draft never sends it; with an entry chosen, the hint says what Enter does; the page's window
 * shortcuts leave another text field's keys alone; and an input method's Enter is the input method's. Headless: keys by
 * direct dispatch through the components' own bindings, focus by the listeners' own events.
 */
class ComposerKeysTest {

    static boolean fire(JComponent c, KeyStroke ks, int condition) {
        Object name = c.getInputMap(condition).get(ks);
        assertNotNull(name, "a binding for " + ks);
        KeyEvent e = new KeyEvent(c, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), ks.getModifiers(), ks.getKeyCode(),
                KeyEvent.CHAR_UNDEFINED);
        return SwingUtilities.notifyAction(c.getActionMap().get(name), ks, e, c, e.getModifiersEx());
    }

    static KeyEvent enter(java.awt.Component on, int id) {
        return new KeyEvent(on, id, System.currentTimeMillis(), 0, KeyEvent.VK_ENTER,
                id == KeyEvent.KEY_TYPED ? '\n' : KeyEvent.CHAR_UNDEFINED);
    }

    static final KeyStroke ENTER = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);

    /**
     * ★ #7. Witness: drop {@code if (enterHeldSinceFocus) return;} from the composer's send — the start page's held Enter,
     * auto-repeating into the composer it moved the focus to, sends the draft it only placed.
     */
    @Test
    @DisplayName("★ an Enter held from the start page never sends in the composer; the composer's own next Enter does")
    void aHeldEnterFromTheStartPageNeverSends() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HeldEnter.reset();
            AtomicInteger sent = new AtomicInteger();
            AssistantSurface s = new AssistantSurface();
            s.onSend = sent::incrementAndGet;
            JTextArea startBox = new JTextArea();
            HeldEnter.observe(enter(startBox, KeyEvent.KEY_PRESSED));      // Enter pressed in the start box, held
            s.appendDraft(telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT, 31,
                    "what does this log show?", null);
            for (var l : s.draft.getFocusListeners()) l.focusGained(new FocusEvent(s.draft, FocusEvent.FOCUS_GAINED));
            assertTrue(fire(s.draft, ENTER, JComponent.WHEN_FOCUSED), "the binding is the composer's");
            assertEquals(0, sent.get(), "the held Enter's auto-repeat sent nothing");

            KeyEvent up = enter(s.draft, KeyEvent.KEY_RELEASED);
            HeldEnter.observe(up);
            for (var l : s.draft.getKeyListeners()) l.keyReleased(up);
            HeldEnter.observe(enter(s.draft, KeyEvent.KEY_PRESSED));        // a fresh press, in the composer
            fire(s.draft, ENTER, JComponent.WHEN_FOCUSED);
            assertEquals(1, sent.get(), "the composer's own Enter sends");
            HeldEnter.reset();
        });
    }

    /** A press older than {@link HeldEnter#HELD_MS} without its release (a release lost to a window switch) is not held. */
    @Test
    @DisplayName("a lost release cannot disable Enter: a press counts as held for at most a second")
    void aLostReleaseExpires() {
        HeldEnter.reset();
        KeyEvent old = new KeyEvent(new JTextArea(), KeyEvent.KEY_PRESSED, 1_000, 0, KeyEvent.VK_ENTER,
                KeyEvent.CHAR_UNDEFINED);
        HeldEnter.observe(old);
        assertTrue(HeldEnter.down(1_500));
        assertFalse(HeldEnter.down(1_000 + HeldEnter.HELD_MS + 1));
        HeldEnter.reset();
    }

    /**
     * ★ #7: with a flow entry chosen, Enter runs that entry — the hint under the box says so, by the button's words, and
     * a screen reader hears the same for the box. Witness: leave the hint "Enter sends …" ({@code renderKeyHint} a no-op).
     */
    @Test
    @DisplayName("★ with an entry chosen the hint says what Enter does: 'Enter: <the button's words>'")
    void theHintSaysWhatEnterDoes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StartPromptView v = new StartPromptView(new StartPromptViewTest.Host());
            v.render(StartPromptViewTest.route(StartPageState.Route.IN_APP), LocalAgentState.IDLE);
            assertEquals(StartPromptView.KEY_HINT, v.keyHintLabel().getText());
            v.choose(4);                                         // S5, a flow entry: a chip
            String verb = AskCatalogue.text("S5.verb");
            assertEquals("Enter: " + verb + " · Shift+Enter starts a new line", v.keyHintLabel().getText());
            assertEquals(v.keyHintLabel().getText(), v.box().getAccessibleContext().getAccessibleDescription());
            v.unchoose();
            assertEquals(StartPromptView.KEY_HINT, v.keyHintLabel().getText(), "the chip removed: Enter sends again");
        });
    }

    /**
     * ★ #8: ⌘/Alt+1…9 and ⌘/Alt+R are window bindings; with the focus in ANOTHER text field they do nothing and the key
     * goes on to that field. Witness: drop the actions' {@code isEnabled} override — ⌘3 typed in another field chooses
     * the third entry.
     */
    @Test
    @DisplayName("★ the page's shortcuts are ignored while another text field has the focus; they work from the box")
    void shortcutsLeaveOtherTextFieldsAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StartPromptView v = new StartPromptView(new StartPromptViewTest.Host());
            v.render(StartPromptViewTest.route(StartPageState.Route.IN_APP), LocalAgentState.IDLE);
            KeyStroke three = KeyStroke.getKeyStroke(KeyEvent.VK_3, StartPromptView.chooserMask());
            KeyStroke r = KeyStroke.getKeyStroke(KeyEvent.VK_R, StartPromptView.chooserMask());
            JTextField elsewhere = new JTextField();
            v.focusOwner = () -> elsewhere;
            assertFalse(fire(v, three, JComponent.WHEN_IN_FOCUSED_WINDOW), "not consumed: the key is the field's");
            assertNull(v.chosen(), "and nothing was chosen");
            assertFalse(fire(v, r, JComponent.WHEN_IN_FOCUSED_WINDOW), "⌘/Alt+R too");
            v.focusOwner = v::box;
            assertTrue(fire(v, three, JComponent.WHEN_IN_FOCUSED_WINDOW), "from the start box it applies");
            assertEquals("S3", v.chosen().id());
            v.focusOwner = () -> v.promptList();
            assertTrue(fire(v, KeyStroke.getKeyStroke(KeyEvent.VK_2, StartPromptView.chooserMask()),
                    JComponent.WHEN_IN_FOCUSED_WINDOW), "and from the page's list");
        });
    }

    /** Composed (uncommitted) input-method text in {@code area}, as an input method sends it. */
    static void composeInto(JTextArea area, String text) {
        area.dispatchEvent(new InputMethodEvent(area, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                new AttributedString(text).getIterator(), 0, null, null));
    }

    /**
     * ★ #8 (the review's M8b survived): while an input method is composing, Enter belongs to it — the start box does
     * not send. Witness: drop {@code if (composing()) return;} from the box's start-send — the press is reported.
     */
    @Test
    @DisplayName("★ the start box: Enter while an input method composes is the input method's, not a send")
    void theStartBoxLeavesAComposingEnterAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            StartPromptViewTest.Host h = new StartPromptViewTest.Host();
            StartPromptView v = new StartPromptView(h);
            v.render(StartPromptViewTest.route(StartPageState.Route.IN_APP), LocalAgentState.IDLE);
            v.box().setText("ask about ");
            composeInto(v.box(), "kanji");
            assertTrue(v.box().getText().contains("kanji"), "the composed text is in the box: " + v.box().getText());
            fire(v.box(), ENTER, JComponent.WHEN_FOCUSED);
            assertEquals(List.of(), h.presses, "Enter committed the composition, it did not send");
        });
    }

    /** ★ The composer: the same. Witness: drop {@code if (composing()) return;} from assistant-send. */
    @Test
    @DisplayName("★ the composer: Enter while an input method composes is the input method's, not a send")
    void theComposerLeavesAComposingEnterAlone() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HeldEnter.reset();
            AtomicInteger sent = new AtomicInteger();
            AssistantSurface s = new AssistantSurface();
            s.onSend = sent::incrementAndGet;
            s.draft.setText("ask about ");
            composeInto(s.draft, "kanji");
            assertTrue(s.composing(), "an input method is composing");
            fire(s.draft, ENTER, JComponent.WHEN_FOCUSED);
            assertEquals(0, sent.get());
        });
    }
}
