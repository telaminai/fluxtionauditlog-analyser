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

    // ---- nothing between the canvas and the panel may swallow the key -----------------------------

    /**
     * Mirrors what {@code JComponent.processKeyBinding} does for a WHEN_ANCESTOR binding: walk up from
     * the focused component and take the first ancestor that both binds the stroke <b>and</b> has an
     * action for it. An ancestor that binds it without an action does not consume the key.
     */
    private static Object resolve(JComponent from, KeyStroke stroke) {
        for (java.awt.Component c = from; c != null; c = c.getParent()) {
            if (!(c instanceof JComponent jc)) continue;
            Object binding = jc.getInputMap(MAP).get(stroke);
            if (binding != null && jc.getActionMap().get(binding) != null) return binding;
        }
        return null;
    }

    @Test
    void theSplitPaneDoesNotStealTheStepKeys() {
        // A JSplitPane's look-and-feel binds Up/Down to move its divider in the ancestor map, and the
        // split sits between the canvas and the panel — so it is consulted FIRST. Adding the source pane
        // silently broke stepping exactly this way, and nothing threw.
        TopologyPanel panel = new TopologyPanel();
        assertEquals("step-next", resolve(panel.canvas(), KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)),
                "something between the canvas and the panel is eating Down");
        assertEquals("step-prev", resolve(panel.canvas(), KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0)),
                "something between the canvas and the panel is eating Up");
    }

    @Test
    void theStepKeysStillResolveWithTheSourcePaneOpen() {
        // the split only gets a second child once source navigation is used; re-check with it present
        TopologyPanel panel = new TopologyPanel();
        panel.bindSource(new telamin.fluxtion.audit.analyser.analyser.source.SourceService());
        panel.openSourceFor("anything");
        assertEquals("step-next", resolve(panel.canvas(), KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0)));
    }

    @Test
    void enterOpensSource() {
        TopologyPanel panel = new TopologyPanel();
        assertEquals("open-source",
                panel.getInputMap(MAP).get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
        assertNotNull(panel.getActionMap().get("open-source"));
    }
}
