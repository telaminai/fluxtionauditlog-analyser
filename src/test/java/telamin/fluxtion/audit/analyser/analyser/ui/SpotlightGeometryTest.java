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

    // ---- several callouts (M64.6) ------------------------------------------------------------------------

    @Test
    void withOneSpotlight_theLayoutIsExactlyTheSingleCaptionRule() {
        for (Rectangle cut : new Rectangle[]{new Rectangle(500, 100, 200, 40), new Rectangle(500, 740, 200, 40),
                new Rectangle(0, 0, 300, 800), new Rectangle(900, 0, 300, 800), frame(), new Rectangle(2, 100, 20, 20)}) {
            assertEquals(SpotlightGeometry.captionBox(cut, CAPTION, FRAME),
                    SpotlightGeometry.layout(java.util.List.of(cut), java.util.List.of(CAPTION), FRAME).get(0), cut.toString());
        }
    }

    @Test
    void twoNeighbours_getCalloutsThatCoverNeitherCutOutNorEachOther() {
        Rectangle a = new Rectangle(400, 300, 160, 60), b = new Rectangle(580, 300, 160, 60);   // side by side
        java.util.List<Rectangle> at = SpotlightGeometry.layout(java.util.List.of(a, b), java.util.List.of(CAPTION, CAPTION), FRAME);

        assertFalse(at.get(0).intersects(at.get(1)), "two callouts on top of each other can be read by nobody: " + at);
        for (Rectangle callout : at) {
            assertTrue(frame().contains(callout), callout.toString());
            assertFalse(callout.intersects(a) || callout.intersects(b),
                    "a callout hiding the OTHER thing being pointed at defeats the pointing: " + callout);
        }
    }

    @Test
    void aStackOfTargets_putsTheMiddleCalloutBeside_becauseAboveAndBelowAreTaken() {
        Rectangle top = new Rectangle(500, 200, 200, 40), mid = new Rectangle(500, 270, 200, 40), low = new Rectangle(500, 340, 200, 40);
        java.util.List<Rectangle> at = SpotlightGeometry.layout(java.util.List.of(top, mid, low),
                java.util.List.of(CAPTION, CAPTION, CAPTION), FRAME);
        for (int i = 0; i < 3; i++) {
            for (Rectangle cut : new Rectangle[]{top, mid, low}) assertFalse(at.get(i).intersects(cut), i + " covers " + cut);
            for (int j = 0; j < i; j++) assertFalse(at.get(i).intersects(at.get(j)), i + " covers callout " + j);
        }
    }

    @Test
    void aSpotlightWithNoCaptionHasNoCallout_andTakesNoRoomFromTheOthers() {
        Rectangle a = new Rectangle(400, 300, 160, 60), b = new Rectangle(580, 300, 160, 60);
        java.util.List<Dimension> sizes = new java.util.ArrayList<>();
        sizes.add(null);
        sizes.add(CAPTION);
        java.util.List<Rectangle> at = SpotlightGeometry.layout(java.util.List.of(a, b), sizes, FRAME);
        assertEquals(null, at.get(0));
        assertFalse(at.get(1).intersects(a));
    }

    @Test
    void whenEverySideCoversSomething_theLeastCoveredSideWins_neverOffScreen() {
        Dimension small = new Dimension(400, 300);
        Rectangle a = new Rectangle(20, 20, 360, 100), b = new Rectangle(20, 160, 360, 100);
        java.util.List<Rectangle> at = SpotlightGeometry.layout(java.util.List.of(a, b),
                java.util.List.of(new Dimension(300, 120), new Dimension(300, 120)), small);
        for (Rectangle callout : at) {
            assertTrue(new Rectangle(0, 0, small.width, small.height).contains(callout), "on screen, even crowded: " + callout);
        }
    }

    @Test
    void theNumberSitsOnTheCutOutsCorner_andStaysInsideTheFrame() {
        assertEquals(new Rectangle(89, 89, 22, 22), SpotlightGeometry.badge(new Rectangle(100, 100, 50, 20), 22, FRAME));
        assertTrue(frame().contains(SpotlightGeometry.badge(new Rectangle(0, 0, 50, 20), 22, FRAME)));
        assertTrue(frame().contains(SpotlightGeometry.badge(new Rectangle(1195, 795, 5, 5), 22, FRAME)));
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

    // ---- neighbouring targets share one separator (owner report, 2026-09-26) ----------------------------------

    private static java.util.List<Rectangle> cuts(Rectangle... targets) {
        return SpotlightGeometry.cutOuts(java.util.List.of(targets), FRAME);
    }

    /** An outline edge at {@code y} crosses a target when it lies strictly inside the target's rows. */
    private static boolean crosses(int y, Rectangle target) {
        return y > target.y && y < target.y + target.height;
    }

    @Test
    void neighbouringLinesMeetAtOneSeparatorAndNeitherOutlineCrossesTheOtherLine() {
        Rectangle first = new Rectangle(100, 100, 400, 17), second = new Rectangle(100, 117, 400, 17);
        var c = cuts(first, second);
        Rectangle up = c.get(0), low = c.get(1);
        assertEquals(up.y + up.height, low.y, "one shared edge: " + c);
        assertEquals(117, low.y, "halfway between the facing edges, which touch");
        assertFalse(crosses(up.y + up.height, second), "the first outline's bottom stays off the second line");
        assertFalse(crosses(low.y, first), "the second outline's top stays off the first line");
        assertEquals(SpotlightGeometry.cutOut(first, FRAME).y, up.y, "the outer edges keep their padding");
        assertEquals(SpotlightGeometry.cutOut(second, FRAME).y + SpotlightGeometry.cutOut(second, FRAME).height,
                low.y + low.height);
    }

    @Test
    void aSmallGapPutsTheSeparatorHalfwayAndTheOrderOfLightingDoesNotMatter() {
        Rectangle first = new Rectangle(100, 100, 400, 17), third = new Rectangle(100, 124, 400, 17);
        var c = cuts(third, first);                               // lit bottom-first
        assertEquals((117 + 124) / 2, c.get(1).y + c.get(1).height, "upper ends halfway: " + c);
        assertEquals((117 + 124) / 2, c.get(0).y, "lower starts halfway: " + c);
    }

    @Test
    void threeStackedLinesEachShareOnlyTheirOwnSeparators() {
        Rectangle a = new Rectangle(0, 200, 300, 17), b = new Rectangle(0, 217, 300, 17), d = new Rectangle(0, 234, 300, 17);
        var c = cuts(a, b, d);
        assertEquals(c.get(0).y + c.get(0).height, c.get(1).y);
        assertEquals(c.get(1).y + c.get(1).height, c.get(2).y);
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) if (i != j) {
            Rectangle other = java.util.List.of(a, b, d).get(j);
            assertFalse(crosses(c.get(i).y, other) || crosses(c.get(i).y + c.get(i).height, other),
                    "outline " + i + " crosses line " + j + ": " + c);
        }
    }

    @Test
    void farApartSideBySideAndOverlappingTargetsKeepTheirCutOuts() {
        Rectangle top = new Rectangle(0, 100, 300, 17), far = new Rectangle(0, 300, 300, 17);
        assertEquals(java.util.List.of(SpotlightGeometry.cutOut(top, FRAME), SpotlightGeometry.cutOut(far, FRAME)), cuts(top, far));
        Rectangle left = new Rectangle(0, 100, 100, 17), right = new Rectangle(104, 100, 100, 17);
        assertEquals(java.util.List.of(SpotlightGeometry.cutOut(left, FRAME), SpotlightGeometry.cutOut(right, FRAME)), cuts(left, right),
                "side by side on one line: no stacking to separate");
        Rectangle big = new Rectangle(0, 100, 300, 60), inner = new Rectangle(10, 110, 50, 17);
        assertEquals(java.util.List.of(SpotlightGeometry.cutOut(big, FRAME), SpotlightGeometry.cutOut(inner, FRAME)), cuts(big, inner),
                "overlapping targets merge into one hole as before");
    }
}
