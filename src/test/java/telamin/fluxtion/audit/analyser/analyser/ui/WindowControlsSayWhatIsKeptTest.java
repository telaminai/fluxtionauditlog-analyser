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

        for (String zoom : List.of("+", "\u2212", "Fit")) {
            AbstractButton b = labelled(panel, zoom);
            assertNotNull(b, "the " + zoom + " control must be on the toolbar");
            String tip = b.getToolTipText();
            assertNotNull(tip, zoom + " had no tooltip at all — silence is what caused this");
            assertTrue(tip.contains("not saved"),
                    zoom + " must say its window is not kept: " + tip);
            // R12-4: a keyword check passed on a REVERSED tip — "the zoom is kept with the chart and
            // restored on reload, so it is not saved separately" contains "not saved" and means the
            // opposite. Reject the words that would reverse it.
            // phrases that cannot occur in a correct zoom tip; "saved with the chart" is NOT one of them,
            // because the correct tip legitimately contains "not saved with the chart"
            for (String reversal : List.of("kept with", "restored on reload", "is saved")) {
                assertFalse(tip.contains(reversal),
                        zoom + " must not also claim the window IS kept (" + reversal + "): " + tip);
            }
        }

        AbstractButton pin = labelled(panel, "\ud83d\udccc");
        assertNotNull(pin, "the pin control must be on the toolbar");
        String pinTip = pin.getToolTipText();
        assertNotNull(pinTip);
        assertTrue(pinTip.contains("SAVED with the chart"),
                "the pin must say its window IS saved, in those words: " + pinTip);
        // the reviewer's witness: "…unlike a zoom the window is UNSAVED: it is forgotten on reload"
        // passed the old check, because toUpperCase() found SAVED inside UNSAVED.
        for (String reversal : List.of("UNSAVED", "unsaved", "not saved", "forgotten")) {
            assertFalse(pinTip.contains(reversal),
                    "the pin tip must not read as the opposite (" + reversal + "): " + pinTip);
        }
    }

    @Test
    void theTwoKindsOfControlDoNotReadAlike() {
        GraphPanel panel = new GraphPanel();
        String zoom = labelled(panel, "+").getToolTipText();
        String pin = labelled(panel, "\ud83d\udccc").getToolTipText();

        assertNotEquals(zoom, pin);
        assertTrue(zoom.contains("\ud83d\udccc"),
                "zoom should point at the control that DOES keep a window, so the reader learns the pair: " + zoom);
    }
}
