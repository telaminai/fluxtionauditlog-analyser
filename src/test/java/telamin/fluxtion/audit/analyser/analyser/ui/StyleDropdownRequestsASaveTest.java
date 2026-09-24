package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.4 — the style DROPDOWN must request a save, not just the verb.
 *
 * <p>M68.2 persisted a chart's plot style and was reported as verified. It was not: the verification was
 * driven entirely through {@code setStyleByName}, the action-socket path, which calls {@code mutated()}
 * itself. The {@code styleCombo} listener — the only way a PERSON changes the style — called
 * {@code chart.setStyle(...)} and nothing else, so the choice repainted the plot and was then lost on the
 * next load. The feature worked on the one path that was tested and on no path a user has.
 *
 * <p>So this test drives the real combo found in the panel's component tree, exactly as a click does, and
 * never calls {@code setStyleByName}. A test written against the verb would have passed throughout.
 */
class StyleDropdownRequestsASaveTest {

    /** The style combo as a person meets it: found in the rendered panel, not reached through an API. */
    private static JComboBox<?> styleCombo(Container parent) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JComboBox<?> box && box.getItemCount() == 3
                    && "Stairs".equals(box.getItemAt(0))) {
                return box;
            }
            if (c instanceof Container child) {
                JComboBox<?> found = styleCombo(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void choosingAStyleFromTheDropdownAsksToBeSaved() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GraphPanel panel = new GraphPanel();
            JComboBox<?> combo = styleCombo(panel);
            assertNotNull(combo, "the style dropdown must be on the panel — this test is about that control");

            int[] mutations = {0};
            panel.setOnMutation(() -> mutations[0]++);

            combo.setSelectedIndex(1);   // "Line" — what a click does
            assertEquals("line", panel.styleName(), "the plot follows the dropdown");
            assertEquals(1, mutations[0],
                    "B-M20-3: choosing a style edits the saved chart, so it must request a save — without "
                            + "this the choice is lost on the next load and only the verb path persisted");

            combo.setSelectedIndex(2);   // "Points"
            assertEquals("points", panel.styleName());
            assertEquals(2, mutations[0], "every change asks, not just the first");
        });
    }

    @Test
    void theVerbPathStillAsksExactlyOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GraphPanel panel = new GraphPanel();
            int[] mutations = {0};
            panel.setOnMutation(() -> mutations[0]++);

            panel.setStyleByName("line");
            assertEquals(1, mutations[0],
                    "ONCE: the combo listener now asks, so setStyleByName must not ask again on top of it");

            // JComboBox fires its action event even when the selection does not change, so the listener
            // runs here too — one call, one report, which is what matters for a save request
            panel.setStyleByName("line");
            assertEquals(2, mutations[0], "a repeated restyle reports one edit, not two");
        });
    }

    @Test
    void settingsShareKeepsStyleAndClosedStateWhenItRewritesAnExternalPath() {
        GraphSpec original = new GraphSpec("chart", List.of(), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new GraphSpec.ExternalSpec("rel/venue.csv", "venue mid", "ts", "epochMillis",
                        "UTC", "mid", 0)),
                List.of(), "line", false);

        GraphSpec rewritten = original.withExternal(
                List.of(new GraphSpec.ExternalSpec("/abs/venue.csv", "venue mid", "ts", "epochMillis",
                        "UTC", "mid", 0)),
                original.markers());

        assertEquals("/abs/venue.csv", rewritten.external().get(0).path(), "the path is what changes");
        assertEquals("line", rewritten.style(),
                "and NOTHING else: rebuilding through a shorter constructor reset this to stairs");
        assertFalse(rewritten.open(),
                "a closed chart must not be revived by a settings import");
        assertEquals(original, rewritten.withExternal(original.external(), original.markers()),
                "the rewrite is a round trip — anything else is a component quietly taking its default");
    }
}
