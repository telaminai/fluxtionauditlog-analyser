package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JRootPane;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The overlay is the frame's GLASS PANE, so while it is visible every mouse event lands on it and nothing
 * beneath sees one. That is right for a press — a press on the spotlight dismisses it — but wrong for a
 * RELEASE whose press the overlay never saw.
 *
 * <p>Found in a 1.26.0 demo: the records table was left scrolling and extending its selection with nothing
 * touching it, and only a restart stopped it. A press had gone to the table, a walk step had then raised the
 * overlay while the button was still down, and the release landed on the glass pane. The table never closed
 * its drag, so every later selection extended from the new anchor and auto-scrolled — measured at one row
 * growing to a hundred in two seconds, from an anchor set programmatically with no mouse in play at all.
 *
 * <p>M69 makes this far more likely than it was: a walk raises the overlay by itself, repeatedly, while the
 * person is still clicking around.
 */
class OverlayDoesNotSwallowAReleaseTest {

    /** A content pane that records the mouse events it is given. */
    private static final class Recorder extends JPanel {
        final List<String> got = new ArrayList<>();

        Recorder() {
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { got.add("pressed"); }
                @Override public void mouseReleased(MouseEvent e) { got.add("released"); }
            });
        }
    }

    /** The overlay as a glass pane over {@code beneath}, laid out so a point at (20,20) is over both. */
    private static SpotlightOverlay glassPaneOver(Recorder beneath) {
        JRootPane root = new JRootPane();
        SpotlightOverlay overlay = new SpotlightOverlay(null);
        root.setContentPane(beneath);
        root.setGlassPane(overlay);
        root.setSize(400, 300);
        beneath.setSize(400, 300);
        overlay.setSize(400, 300);
        root.doLayout();
        overlay.setVisible(true);
        return overlay;
    }

    private static MouseEvent at(SpotlightOverlay o, int id, int x, int y) {
        return new MouseEvent(o, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1);
    }

    @Test
    @DisplayName("A release whose press the overlay never saw is passed to the component beneath")
    void aReleaseWithoutItsPressReachesTheComponentBeneath() {
        Recorder beneath = new Recorder();
        SpotlightOverlay overlay = glassPaneOver(beneath);

        // the press went to the table BEFORE the overlay appeared, so the overlay never saw it
        overlay.dispatchEvent(at(overlay, MouseEvent.MOUSE_RELEASED, 20, 20));

        assertEquals(List.of("released"), beneath.got,
                "the component that owns the drag must get its release, or it stays in drag mode for the rest "
                        + "of the session — a table that scrolls and selects on its own until the app restarts");
    }

    @Test
    @DisplayName("A release the overlay DID press for is the overlay's own, and is not passed on")
    void theOverlaysOwnReleaseIsNotPassedOn() {
        Recorder beneath = new Recorder();
        SpotlightOverlay overlay = glassPaneOver(beneath);

        overlay.dispatchEvent(at(overlay, MouseEvent.MOUSE_PRESSED, 20, 20));
        overlay.dispatchEvent(at(overlay, MouseEvent.MOUSE_RELEASED, 20, 20));

        assertEquals(List.of(), beneath.got,
                "a press on the spotlight dismisses it and belongs to the overlay; passing its release through "
                        + "would deliver a click to whatever the spotlight was covering");
    }

    @Test
    @DisplayName("Each release is judged on its own press, not on one long past")
    void theFlagDoesNotLeakBetweenGestures() {
        Recorder beneath = new Recorder();
        SpotlightOverlay overlay = glassPaneOver(beneath);

        overlay.dispatchEvent(at(overlay, MouseEvent.MOUSE_PRESSED, 20, 20));
        overlay.dispatchEvent(at(overlay, MouseEvent.MOUSE_RELEASED, 20, 20));
        beneath.got.clear();

        overlay.dispatchEvent(at(overlay, MouseEvent.MOUSE_RELEASED, 20, 20));   // a second, unpaired release

        assertEquals(List.of("released"), beneath.got,
                "the previous gesture's press must not make this release look like the overlay's");
    }
}
