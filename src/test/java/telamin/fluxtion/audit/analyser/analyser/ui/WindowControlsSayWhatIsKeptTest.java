package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zoom and pin both set the visible window, sit on the same toolbar, and behave oppositely — a zoom is a
 * lens that is never written, a pin is {@code graph.N.from}/{@code to} in the profile and comes back on
 * reload. Nothing on screen said which was which, and the owner lost a zoom expecting it to return.
 *
 * <p>These assert on the TOOLTIP TEXT, which is the whole fix: the behaviour was already correct and
 * already tested, and the defect was that a person could not tell the two apart. A test of the behaviour
 * would have passed throughout.
 */
class WindowControlsSayWhatIsKeptTest {

    private static List<AbstractButton> buttons(Container parent) {
        List<AbstractButton> found = new ArrayList<>();
        for (Component c : parent.getComponents()) {
            if (c instanceof AbstractButton b) found.add(b);
            if (c instanceof Container child) found.addAll(buttons(child));
        }
        return found;
    }

    private static AbstractButton labelled(Container root, String text) {
        for (AbstractButton b : buttons(root)) if (text.equals(b.getText())) return b;
        return null;
    }

    @Test
    void everyWindowControlSaysWhetherItsWindowIsKept() {
        GraphPanel panel = new GraphPanel();

        for (String zoom : List.of("+", "−", "Fit")) {
            AbstractButton b = labelled(panel, zoom);
            assertNotNull(b, "the " + zoom + " control must be on the toolbar");
            String tip = b.getToolTipText();
            assertNotNull(tip, zoom + " had no tooltip at all — silence is what caused this");
            assertTrue(tip.toLowerCase().contains("not saved"),
                    zoom + " must say its window is not kept, or it reads like the pin beside it: " + tip);
        }

        AbstractButton pin = labelled(panel, "📌");
        assertNotNull(pin, "the pin control must be on the toolbar");
        String pinTip = pin.getToolTipText();
        assertNotNull(pinTip);
        assertTrue(pinTip.toUpperCase().contains("SAVED"),
                "the pin must say its window IS saved — that is the distinction: " + pinTip);
        assertFalse(pinTip.toLowerCase().contains("not saved"),
                "and must not read as the opposite: " + pinTip);
    }

    @Test
    void theTwoKindsOfControlDoNotReadAlike() {
        GraphPanel panel = new GraphPanel();
        String zoom = labelled(panel, "+").getToolTipText();
        String pin = labelled(panel, "📌").getToolTipText();

        assertNotEquals(zoom, pin);
        assertTrue(zoom.contains("📌"),
                "zoom should point at the control that DOES keep a window, so the reader learns the pair: " + zoom);
    }
}
