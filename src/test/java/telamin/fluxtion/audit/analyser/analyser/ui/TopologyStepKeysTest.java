package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The step keys are bound. Headless-safe: the panel is constructed, never shown, and only its input and
 * action maps are inspected — no painting, no peers.
 *
 * <p>Worth a test because a key binding fails silently. Nothing throws when the stroke is wrong; the key
 * simply does nothing, and the only way to notice is to press it.
 */
class TopologyStepKeysTest {

    private static final int MAP = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

    @Test
    void downAndUpStep() {
        TopologyPanel panel = new TopologyPanel();
        assertEquals("step-next",
                panel.getInputMap(MAP).get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
        assertEquals("step-prev",
                panel.getInputMap(MAP).get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)));
    }

    @Test
    void bracketsRemainAsAliases() {
        TopologyPanel panel = new TopologyPanel();
        assertEquals("step-next", panel.getInputMap(MAP).get(KeyStroke.getKeyStroke(']')));
        assertEquals("step-prev", panel.getInputMap(MAP).get(KeyStroke.getKeyStroke('[')));
    }

    @Test
    void theActionsExist() {
        TopologyPanel panel = new TopologyPanel();
        assertNotNull(panel.getActionMap().get("step-next"));
        assertNotNull(panel.getActionMap().get("step-prev"));
    }

    @Test
    void steppingWithNoRecordDoesNothingRatherThanThrowing() {
        TopologyPanel panel = new TopologyPanel();
        assertDoesNotThrow(() -> panel.getActionMap().get("step-next")
                .actionPerformed(new java.awt.event.ActionEvent(panel, 0, "step-next")));
    }
}
