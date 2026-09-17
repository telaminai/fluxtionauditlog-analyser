package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M64 D-SP3 — "the caption sits in whichever quadrant has room", as arithmetic rather than as an opinion
 * held by a paint method. Also the canvas's world→screen transform, which is what makes
 * {@code topology:node:<id>} land on the node a person sees.
 */
class SpotlightGeometryTest {

    private static final Dimension FRAME = new Dimension(1200, 800);
    private static final Dimension CAPTION = new Dimension(240, 60);

    private static Rectangle frame() {
        return new Rectangle(0, 0, FRAME.width, FRAME.height);
    }

    @Test
    void theCutOutIsPadded_soTheTargetIsNotCroppedByItsOwnFrame_andStaysInsideTheWindow() {
        Rectangle cut = SpotlightGeometry.cutOut(new Rectangle(100, 100, 50, 20), FRAME);
        assertEquals(new Rectangle(94, 94, 62, 32), cut);

        Rectangle atEdge = SpotlightGeometry.cutOut(new Rectangle(0, 0, 50, 20), FRAME);
        assertTrue(frame().contains(atEdge), "clipped to the frame: " + atEdge);
    }

    @Test
    void withRoomBelow_theCaptionGoesBelow_centredOnTheTarget() {
        Rectangle cut = new Rectangle(500, 100, 200, 40);
        Rectangle at = SpotlightGeometry.captionBox(cut, CAPTION, FRAME);

        assertTrue(at.y >= cut.y + cut.height + SpotlightGeometry.GAP, "below, with room for the arrow: " + at);
        assertEquals(cut.x + (cut.width - CAPTION.width) / 2, at.x);
    }

    @Test
    void aTargetAtTheBottom_putsTheCaptionAbove() {
        Rectangle cut = new Rectangle(500, 740, 200, 40);            // the status line
        Rectangle at = SpotlightGeometry.captionBox(cut, CAPTION, FRAME);
        assertTrue(at.y + at.height <= cut.y - SpotlightGeometry.GAP, "above: " + at);
    }

    @Test
    void aTargetFillingTheHeight_putsTheCaptionBeside_rightFirstThenLeft() {
        Rectangle tallOnTheLeft = new Rectangle(0, 0, 300, 800);      // the Project rail
        Rectangle right = SpotlightGeometry.captionBox(tallOnTheLeft, CAPTION, FRAME);
        assertTrue(right.x >= tallOnTheLeft.x + tallOnTheLeft.width + SpotlightGeometry.GAP, "to the right: " + right);

        Rectangle tallOnTheRight = new Rectangle(900, 0, 300, 800);
        Rectangle left = SpotlightGeometry.captionBox(tallOnTheRight, CAPTION, FRAME);
        assertTrue(left.x + left.width <= tallOnTheRight.x - SpotlightGeometry.GAP, "to the left: " + left);
    }

    @Test
    void withRoomNowhere_itGoesINSIDE_ratherThanOffScreen_aCaptionNobodyCanReadIsWorse() {
        Rectangle everything = frame();
        Rectangle at = SpotlightGeometry.captionBox(everything, CAPTION, FRAME);
        assertTrue(frame().contains(at), "on screen: " + at);
        assertTrue(everything.intersects(at));
    }

    @Test
    void theCaptionIsAlwaysClampedIntoTheFrame() {
        Rectangle nearLeftEdge = new Rectangle(2, 100, 20, 20);       // centring would push it off the left
        Rectangle at = SpotlightGeometry.captionBox(nearLeftEdge, CAPTION, FRAME);
        assertTrue(frame().contains(at), at.toString());
        assertTrue(at.x >= SpotlightGeometry.MARGIN);
    }

    @Test
    void theArrowRunsFromTheCaptionsEdgeToTheCutOutsEdge_notThroughEither() {
        Rectangle cut = new Rectangle(500, 100, 200, 40);
        Rectangle caption = SpotlightGeometry.captionBox(cut, CAPTION, FRAME);

        Point[] arrow = SpotlightGeometry.arrow(caption, cut);

        assertEquals(caption.y, arrow[0].y, "leaves the caption's top edge (it sits below)");
        assertEquals(cut.y + cut.height, arrow[1].y, "arrives at the cut-out's bottom edge");
        assertFalse(arrow[0].equals(arrow[1]));
    }

    @Test
    void aCaptionInsideTheCutOutHasNoArrow() {
        Rectangle everything = frame();
        Rectangle caption = SpotlightGeometry.captionBox(everything, CAPTION, FRAME);
        Point[] arrow = SpotlightGeometry.arrow(caption, everything);
        assertEquals(arrow[0], arrow[1]);
    }

    // ---- the canvas transform: topology:node:<id> must land on the box a person sees ------------------

    @Test
    void worldToScreenIsScaleThenOffset_asTheCanvasPaints() {
        assertEquals(new Rectangle(20, 20, 160, 70),
                TopologyCanvas.toScreen(20, 20, 160, 70, 1.0, 0, 0), "identity");
        assertEquals(new Rectangle(140, 90, 320, 140),
                TopologyCanvas.toScreen(20, 20, 160, 70, 2.0, 100, 50), "zoomed 2x, panned (100, 50)");
    }

    @Test
    void aFractionalZoomRoundsOUTWARDS_soTheCutOutNeverClipsTheNodesEdge() {
        Rectangle r = TopologyCanvas.toScreen(10, 10, 100, 40, 0.75, 0.5, 0.5);
        assertTrue(r.x <= 8 && r.y <= 8, r.toString());                       // floor(8.0) = 8
        assertTrue(r.x + r.width >= 83 && r.y + r.height >= 38, r.toString()); // ceil(83.0), ceil(38.0)
    }
}
