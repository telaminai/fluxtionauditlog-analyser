package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import java.awt.event.ActionEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M74 OD-74.1 amendment, §10: warm start fires on the person's direct selection of the Assistant tab only. Headless:
 * the predicate is judged on the events Swing would be dispatching when the selection changes.
 */
class WarmStartTest {

    /**
     * ★ Witness: make {@code isDirectSelection} return true for any event (a plain tab-selection listener, which fires for
     * programmatic reveals too) — the reveal and restore assertions fail.
     */
    @Test
    @DisplayName("★ a click or key on the tab strip is the person's act; revealAssistant, a handoff and restore are not")
    void onlyTheDirectSelectionCounts() {
        JTabbedPane tabs = new JTabbedPane();
        JButton elsewhere = new JButton("Explain with LLM");
        long now = System.currentTimeMillis();
        assertTrue(WarmStart.isDirectSelection(new MouseEvent(tabs, MouseEvent.MOUSE_PRESSED, now, 0, 10, 10, 1, false),
                tabs), "a click on the tab");
        assertTrue(WarmStart.isDirectSelection(new KeyEvent(tabs, KeyEvent.KEY_PRESSED, now, 0, KeyEvent.VK_RIGHT,
                KeyEvent.CHAR_UNDEFINED), tabs), "a key on the focused tab strip");
        assertFalse(WarmStart.isDirectSelection(new InvocationEvent(tabs, () -> { }), tabs),
                "revealAssistant() from invokeLater, a journey handoff, the tab restored at launch");
        assertFalse(WarmStart.isDirectSelection(new ActionEvent(elsewhere, ActionEvent.ACTION_PERFORMED, "explain"), tabs),
                "a menu item or a button that reveals the tab");
        assertFalse(WarmStart.isDirectSelection(new MouseEvent(elsewhere, MouseEvent.MOUSE_PRESSED, now, 0, 1, 1, 1, false),
                tabs), "a click on another component that then reveals the tab");
        assertFalse(WarmStart.isDirectSelection(null, tabs), "no event at all (a programmatic change outside dispatch)");
    }
}
