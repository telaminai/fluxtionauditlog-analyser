package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final review of the M74 usability pass (2026-10-17) #9: the spotlight callout's hover and focus targets. They are
 * placed OUTSIDE paint (paint never adds, removes or moves a component); the expanded card always contains the collapsed
 * one, so it cannot flicker at an edge; and a focused callout that goes dark hands the focus back. Headless.
 */
class SpotlightCalloutTargetsTest {

    static void paint(SpotlightOverlay o) {
        BufferedImage img = new BufferedImage(o.getWidth(), o.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        o.paint(g);
        g.dispose();
    }

    static List<Rectangle> bounds(SpotlightOverlay o) {
        List<Rectangle> out = new ArrayList<>();
        for (Component c : o.getComponents()) out.add(c.getBounds());
        return out;
    }

    /**
     * ★ Witness: place the targets in paint again ({@code relayout()} as the first line of {@code paintSpotlight}) — the
     * paint after the resize moves the target and the "paint changes no component" assertion fails.
     */
    @Test
    @DisplayName("★ the targets are placed when what is lit changes, never by paint: a paint leaves the component tree as it was")
    void paintChangesNoComponent() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(this::paintChangesNoComponentOnEdt);
    }

    private void paintChangesNoComponentOnEdt() {
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.setSize(900, 600);
        o.light("records:row:16", new Rectangle(100, 100, 300, 24), SpotlightCaptionNeverCutTest.LONG);
        assertEquals(1, o.getComponentCount(), "the target exists before any paint");
        assertFalse(o.calloutTargets().get(0).getBounds().isEmpty(), "and is placed where the card goes");
        o.setSize(340, 220);                       // a size change nothing reported (the frame remeasures on its own)
        List<Rectangle> before = bounds(o);
        paint(o);
        assertEquals(before, bounds(o), "paint added, removed or moved no component");
        assertEquals(1, o.getComponentCount());
        o.relayout();                              // what the queued follow-up does, outside paint
        assertEquals(o.lastCallouts().get(0), o.calloutTargets().get(0).getBounds(), "then the target follows the card");
        assertNotEquals(before, bounds(o), "(the precondition: the resize did move the card)");
    }

    /** ★ Witness: drop {@code .union(at)} in {@code SpotlightOverlay.expanded} — a card at the frame's edge is not contained. */
    @Test
    @DisplayName("★ the expanded card contains the collapsed card, at every edge")
    void theExpandedCardContainsTheCollapsedOne() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(this::theExpandedCardContainsTheCollapsedOneOnEdt);
    }

    private void theExpandedCardContainsTheCollapsedOneOnEdt() {
        Dimension frame = new Dimension(400, 300);
        List<Rectangle> collapsed = List.of(new Rectangle(2, 2, 100, 40), new Rectangle(300, 250, 98, 48),
                new Rectangle(150, 120, 120, 40), new Rectangle(0, 260, 80, 40));
        for (Rectangle at : collapsed) {
            for (int[] wh : new int[][]{{300, 120}, {520, 400}, {130, 60}}) {
                Rectangle open = SpotlightOverlay.expanded(at, wh[0], wh[1], frame);
                assertTrue(open.contains(at), at + " expanded to " + open);
                assertTrue(open.width >= wh[0] && open.height >= wh[1], "and the whole caption fits: " + open);
            }
        }
        // on the overlay itself: a card near the bottom-right corner, hovered
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.setSize(520, 300);
        o.light("status", new Rectangle(380, 240, 120, 24), SpotlightCaptionNeverCutTest.LONG);
        Rectangle shut = new Rectangle(o.lastCallouts().get(0));
        var t = o.calloutTargets().get(0);
        t.dispatchEvent(new MouseEvent(t, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 5, 5, 0, false));
        Rectangle open = o.lastCallouts().get(0);
        assertTrue(open.contains(shut), shut + " inside " + open);
        assertEquals(open, t.getBounds(), "the target grew with the card, outside paint");
    }

    /**
     * ★ Witness: drop {@code focusAway()} from {@code removeTarget} — the focus is left on a component that is gone and
     * the component the person came from is never asked to take it back.
     */
    @Test
    @DisplayName("★ a focused callout that goes dark hands the focus back to where it came from")
    void aFocusedCalloutGoingDarkHandsTheFocusBack() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(this::aFocusedCalloutGoingDarkHandsTheFocusBackOnEdt);
    }

    private void aFocusedCalloutGoingDarkHandsTheFocusBackOnEdt() {
        List<String> asked = new ArrayList<>();
        javax.swing.JButton back = new javax.swing.JButton("Records") {
            @Override public boolean isShowing() { return true; }

            @Override public boolean requestFocusInWindow() {
                asked.add("back");
                return true;
            }
        };
        SpotlightOverlay o = new SpotlightOverlay(null);
        o.setSize(900, 600);
        o.light("records:row:16", new Rectangle(100, 100, 300, 24), SpotlightCaptionNeverCutTest.LONG);
        var t = o.calloutTargets().get(0);
        for (var l : t.getFocusListeners()) l.focusGained(new FocusEvent(t, FocusEvent.FOCUS_GAINED, false, back));
        assertSame(back, o.focusBeforeCallout(), "where the focus came from is remembered");
        o.clearSpotlight();
        assertEquals(List.of("back"), asked, "the focus goes back when the callout goes dark");
        assertTrue(o.calloutTargets().isEmpty());
    }
}
