package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.TranscriptViewTest.onEdt;

/**
 * ★ Owner, 2026-10-18: an answer's Copy (shown on hover or focus, M78 OD-Q.11) flickered. The cause: Copy, the chips and
 * the marks are components INSIDE the transcript, so a pointer crossing onto one of them made the transcript report a
 * mouse exit — and the exit hid the answer's Copy while the pointer was still over the answer (on the Copy button itself,
 * so it vanished as it was reached). Its space was always reserved; the toggling was the hover state.
 *
 * <p>On a shown window, the pointer is moved the way AWT delivers it: across the answer's words (moves on the transcript),
 * onto a chip and the Copy button (an exit on the transcript, then an enter and moves on the component), back, and finally
 * off the window. Copy stays shown at every step over the answer — the button included — and hides only off it, and its
 * bounds never move. Witness (run 2026-10-18): restore the old hover handling ({@code mouseExited} → {@code hover(null)}
 * unconditionally, no listener on the components) — Copy hides on the first crossing and this fails.
 */
class TranscriptCopyHoverFrameTest {

    @Test
    @DisplayName("★ an answer's Copy stays shown over the whole answer, the button and its chips included; its bounds never move")
    void copyDoesNotFlicker() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        JFrame[] frame = new JFrame[1];
        TranscriptView[] view = new TranscriptView[1];
        try {
            onEdt(() -> {
                TranscriptView v = new TranscriptView();
                v.setChips(TranscriptViewTest.chips(Set.of("riskMonitor"), Set.of(), 0, 1));
                v.render(List.of(TranscriptViewTest.answer("a1", "The first answer names `riskMonitor` and says more "
                                + "about it, long enough to cross."),
                        TranscriptViewTest.answer("a2", "The second answer.")));
                JFrame f = new JFrame("transcript hover");
                f.add(new JScrollPane(v));
                f.setBounds(60, 60, 700, 500);
                f.setVisible(true);
                frame[0] = f;
                view[0] = v;
                return null;
            });
            TranscriptView v = view[0];
            onEdt(() -> { frame[0].validate(); v.doLayout(); return null; });
            JButton copy = onEdt(() -> copyOf(v, 0));
            JButton chip = onEdt(() -> v.chipButtons().get(0));
            Rectangle copyAt = onEdt(copy::getBounds);
            assertTrue(copyAt.width > 0 && copyAt.height > 0, "Copy has its place: " + copyAt);
            List<String> seen = new ArrayList<>();

            // across the answer's words
            for (int pos : onEdt(() -> wordPositions(v))) {
                Point p = onEdt(() -> centre(v.modelToView2D(pos)));
                step(seen, v, "move over the words", () -> moved(v, p));
            }
            // onto the chip: the transcript reports an exit, the chip an enter and moves
            step(seen, v, "cross onto the chip", () -> exitInto(v, chip));
            step(seen, v, "move over the chip", () -> movedOn(chip));
            // back onto the words, then down to the Copy button and over it
            Point back = onEdt(() -> centre(v.modelToView2D(1)));
            step(seen, v, "back onto the words", () -> { enteredFrom(chip, v, back); moved(v, back); });
            step(seen, v, "cross onto Copy", () -> exitInto(v, copy));
            for (int i = 0; i < 4; i++) step(seen, v, "move over Copy", () -> movedOn(copy));
            assertEquals(List.of(), seen, "Copy toggled while the pointer was over the answer");
            assertEquals(copyAt, onEdt(copy::getBounds), "Copy's bounds are stable: it never moves or resizes");

            // off the window: now it hides
            onEdt(() -> {
                Point outside = SwingUtilities.convertPoint(v, new Point(-40, -40), copy);
                copy.dispatchEvent(new MouseEvent(copy, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0,
                        outside.x, outside.y, 0, false));
                return null;
            });
            assertFalse(onEdt(() -> v.copyShown("a1")), "off the transcript, Copy hides");
            assertEquals(copyAt, onEdt(copy::getBounds), "…and keeps its place");
        } finally {
            onEdt(() -> { if (frame[0] != null) frame[0].dispose(); return null; });
        }
    }

    interface Step { void run() throws Exception; }

    /** Run one pointer step on the event thread and note it if the first answer's Copy is not shown after it. */
    private static void step(List<String> seen, TranscriptView v, String what, Step s) throws Exception {
        onEdt(() -> { s.run(); return null; });
        if (!onEdt(() -> v.copyShown("a1"))) seen.add(what);
    }

    private static JButton copyOf(TranscriptView v, int answer) {
        return v.buttons("Copy").get(answer);
    }

    /** Positions inside the first answer's words. */
    private static List<Integer> wordPositions(TranscriptView v) throws Exception {
        String all = v.getDocument().getText(0, v.getDocument().getLength());
        int from = all.indexOf("The first answer");
        int to = all.indexOf("long enough");
        List<Integer> out = new ArrayList<>();
        for (int i = from; i < to; i += 5) out.add(i);
        return out;
    }

    private static Point centre(Rectangle2D r) {
        return new Point((int) r.getCenterX(), (int) r.getCenterY());
    }

    private static void moved(Component c, Point p) {
        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, p.x, p.y, 0, false));
    }

    private static void movedOn(JComponent c) {
        moved(c, new Point(c.getWidth() / 2, c.getHeight() / 2));
    }

    /** What AWT delivers as the pointer crosses from the transcript onto one of its components. */
    private static void exitInto(TranscriptView v, JComponent child) {
        Point inView = SwingUtilities.convertPoint(child, new Point(child.getWidth() / 2, child.getHeight() / 2), v);
        v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, inView.x, inView.y, 0,
                false));
        child.dispatchEvent(new MouseEvent(child, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0,
                child.getWidth() / 2, child.getHeight() / 2, 0, false));
    }

    /** …and back from the component onto the transcript at {@code p}. */
    private static void enteredFrom(JComponent child, TranscriptView v, Point p) {
        Point inChild = SwingUtilities.convertPoint(v, p, child);
        child.dispatchEvent(new MouseEvent(child, MouseEvent.MOUSE_EXITED, System.currentTimeMillis(), 0, inChild.x,
                inChild.y, 0, false));
        v.dispatchEvent(new MouseEvent(v, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, p.x, p.y, 0, false));
    }
}
