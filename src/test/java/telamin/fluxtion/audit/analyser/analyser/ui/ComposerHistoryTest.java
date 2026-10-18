package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import javax.swing.SwingUtilities;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner, 2026-10-18 (M74 hands-on test): ↑ in the assistant composer recalls the questions sent, as a terminal does — ↑
 * on the first line walks back, ↓ on the last line walks forward, and ↓ past the newest puts back the draft that was
 * being typed. In memory only, per assistant. Shift and Alt keep their meaning, and away from the first or last line the
 * caret moves as it always did. Headless: keys by direct dispatch through the composer's own key bindings
 * ({@link SwingUtilities#processKeyBindings}) — posted key events are dropped on a Mac with no focus owner.
 */
class ComposerHistoryTest {

    /** Press {@code key} with {@code modifiers} on the composer, through its bindings; true when a binding took it. */
    static boolean press(AssistantSurface s, int key, int modifiers) {
        KeyEvent e = new KeyEvent(s.draft, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), modifiers, key,
                KeyEvent.CHAR_UNDEFINED);
        return SwingUtilities.processKeyBindings(e);
    }

    static boolean up(AssistantSurface s) {
        return press(s, KeyEvent.VK_UP, 0);
    }

    static boolean down(AssistantSurface s) {
        return press(s, KeyEvent.VK_DOWN, 0);
    }

    /** A composer laid out at a width that never wraps these lines, with {@code sent} accepted in {@code mode}, in order. */
    static AssistantSurface surface(AssistantMode mode, String... sent) {
        AssistantSurface s = new AssistantSurface();
        s.historyMode(mode);
        s.draft.setSize(900, 200);
        for (String q : sent) {
            s.draft.setText(q);
            s.accepted(mode);
        }
        assertEquals("", s.draft.getText(), "accepted clears the composer");
        return s;
    }

    static void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    /**
     * ★ The walk. Witness: drop {@code installHistoryKeys()} from the constructor — ↑ moves the caret and the composer stays
     * empty; or drop the restore of {@code historyDraft} in {@code historyNext} — ↓ past the newest leaves the newest.
     */
    @Test
    @DisplayName("★ ↑ on an empty composer recalls the last question; ↑/↓ walk the history; ↓ past the newest restores the draft")
    void upRecallsAndDownWalksBackToTheDraft() throws Exception {
        onEdt(() -> {
            AssistantSurface s = surface(AssistantMode.LOCAL_AGENT, "first question", "second question", "third question");
            assertTrue(up(s), "↑ is taken by the composer");
            assertEquals("third question", s.draft.getText(), "★ the newest first");
            up(s);
            assertEquals("second question", s.draft.getText());
            up(s);
            assertEquals("first question", s.draft.getText());
            up(s);
            assertEquals("first question", s.draft.getText(), "the oldest stays");
            down(s);
            down(s);
            assertEquals("third question", s.draft.getText());
            down(s);
            assertEquals("", s.draft.getText(), "★ past the newest: the (empty) draft is back");
            assertFalse(down(s) && !s.draft.getText().isEmpty(), "and ↓ walks no further");

            // a half-typed draft is kept while walking, and put back
            s.draft.setText("half a ques");
            s.draft.setCaretPosition(s.draft.getDocument().getLength());
            up(s);
            assertEquals("third question", s.draft.getText(), "a single-line draft: its line is the first line");
            down(s);
            assertEquals("half a ques", s.draft.getText(), "★ the draft that was being typed");
        });
    }

    /**
     * ★ Multi-line editing is untouched: ↑ recalls only from the first line and ↓ walks only from the last. Witness: drop
     * {@code !onFirstLine()} in {@code historyPrevious} — ↑ on the second line replaces the person's text.
     */
    @Test
    @DisplayName("★ in a multi-line draft ↑ moves the caret until the first line, and ↓ moves it until the last")
    void multiLineEditingIsUntouched() throws Exception {
        onEdt(() -> {
            AssistantSurface s = surface(AssistantMode.IN_APP, "an older question");
            s.draft.setText("line one\nline two");
            s.draft.setCaretPosition(s.draft.getDocument().getLength());      // the end of line two
            up(s);
            assertEquals("line one\nline two", s.draft.getText(), "★ the text is the person's");
            assertTrue(s.draft.getCaretPosition() <= "line one".length(), "the caret went up a line: "
                    + s.draft.getCaretPosition());
            up(s);
            assertEquals("an older question", s.draft.getText(), "from the first line, ↑ recalls");
            down(s);
            assertEquals("line one\nline two", s.draft.getText(), "and ↓ puts the two lines back");

            // walking into a multi-line question: ↓ first moves through its lines
            AssistantSurface t = surface(AssistantMode.IN_APP, "asked on\ntwo lines", "newest");
            up(t);
            up(t);
            assertEquals("asked on\ntwo lines", t.draft.getText());
            t.draft.setCaretPosition(0);
            down(t);
            assertEquals("asked on\ntwo lines", t.draft.getText(), "★ ↓ on the first of two lines is the caret's");
            t.draft.setCaretPosition(t.draft.getDocument().getLength());     // on the last line
            down(t);
            assertEquals("newest", t.draft.getText(), "then, on the last line, walks forward");
        });
    }

    /** ★ Shift and Alt are never hijacked. Witness: bind ↑ with any modifier — Shift+↑ recalls instead of selecting. */
    @Test
    @DisplayName("★ Shift+↑ selects and Alt+↑ is left alone: neither recalls")
    void modifiersAreNotHijacked() throws Exception {
        onEdt(() -> {
            AssistantSurface s = surface(AssistantMode.LOCAL_AGENT, "a question");
            // Shift+↑ / Shift+↓ resolve to the text area's own selection actions, never the recall (the selection action
            // itself is the look and feel's; headless, the JDK's accessibility listener cannot run it)
            for (int key : new int[]{KeyEvent.VK_UP, KeyEvent.VK_DOWN}) {
                Object bound = s.draft.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)
                        .get(javax.swing.KeyStroke.getKeyStroke(key, InputEvent.SHIFT_DOWN_MASK));
                assertNotNull(bound, "Shift+arrow keeps its binding");
                assertFalse(String.valueOf(bound).startsWith("assistant-history"), "★ Shift+arrow is never a recall: " + bound);
                assertTrue(String.valueOf(bound).startsWith("selection-"), "it selects, as it always did: " + bound);
            }
            s.draft.setText("draft");
            s.draft.setCaretPosition(5);
            press(s, KeyEvent.VK_UP, InputEvent.ALT_DOWN_MASK);
            press(s, KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK);
            assertEquals("draft", s.draft.getText(), "★ Alt+↑ / Alt+↓ recall nothing");
            // with a selection, plain ↑ is not a recall either
            s.draft.select(0, 5);
            assertFalse(s.historyPrevious(), "a selection is never replaced by a recall");
            assertEquals("draft", s.draft.getText());
        });
    }

    /** ★ Per assistant, in memory. Witness: one shared list — the in-app ↑ recalls Claude Code's question. */
    @Test
    @DisplayName("★ each assistant recalls its own questions; an edit ends the walk; a new composer remembers nothing")
    void perAssistantAndInMemoryOnly() throws Exception {
        onEdt(() -> {
            AssistantSurface s = surface(AssistantMode.LOCAL_AGENT, "to Claude Code");
            s.historyMode(AssistantMode.IN_APP);
            s.draft.setText("to the in-app assistant");
            s.accepted(AssistantMode.IN_APP);
            up(s);
            assertEquals("to the in-app assistant", s.draft.getText(), "★ the in-app assistant's own");
            down(s);
            s.historyMode(AssistantMode.LOCAL_AGENT);
            up(s);
            assertEquals("to Claude Code", s.draft.getText(), "and Claude Code's own");
            // the person edits the recalled question: it is their draft now, and ↓ no longer replaces it
            s.draft.append(" — again");
            down(s);
            assertEquals("to Claude Code — again", s.draft.getText(), "an edit ends the walk");
            // the same question twice in a row is kept once
            s.draft.setText("to Claude Code");
            s.accepted(AssistantMode.LOCAL_AGENT);
            assertEquals(java.util.List.of("to Claude Code"), s.history(AssistantMode.LOCAL_AGENT));
            assertTrue(new AssistantSurface().history(AssistantMode.LOCAL_AGENT).isEmpty(), "never saved: a new one is empty");
            // nothing recalled with no history: ↑ is the caret's
            AssistantSurface empty = surface(AssistantMode.IN_APP);
            assertFalse(up(empty) && !empty.draft.getText().isEmpty());
            assertEquals("", empty.draft.getText());
        });
    }

    /** The bound: the oldest questions go first. */
    @Test
    @DisplayName("at most " + AssistantSurface.HISTORY_LIMIT + " questions are remembered, the oldest dropped first")
    void bounded() throws Exception {
        onEdt(() -> {
            AssistantSurface s = surface(AssistantMode.IN_APP);
            for (int i = 0; i < AssistantSurface.HISTORY_LIMIT + 5; i++) {
                s.draft.setText("q" + i);
                s.accepted(AssistantMode.IN_APP);
            }
            var h = s.history(AssistantMode.IN_APP);
            assertEquals(AssistantSurface.HISTORY_LIMIT, h.size());
            assertEquals("q5", h.get(0));
        });
    }
}
