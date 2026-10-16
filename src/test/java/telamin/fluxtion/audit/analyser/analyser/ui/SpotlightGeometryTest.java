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

    /**
     * #42, from a cold-assistant test (2026-10-16): a node's callout was drawn over the sibling node its caption was
     * contrasting it with. A keep-clear rectangle (an UNLIT thing being compared) is avoided when a free side exists.
     */
    @Test
    void aCalloutKeepsClearOfAnUnlitNeighbour_whenAFreeSideExists() {
        Rectangle node = new Rectangle(500, 300, 145, 53);
        Rectangle sibling = new Rectangle(500, 380, 145, 53);               // right below: where "below" would land
        java.util.List<Rectangle> cuts = java.util.List.of(SpotlightGeometry.cutOut(node, FRAME));
        java.util.List<Dimension> sizes = java.util.List.of(CAPTION);
        Rectangle usual = SpotlightGeometry.layout(cuts, sizes, FRAME).get(0);
        assertTrue(usual.intersects(sibling), "precondition: the usual placement covers the sibling: " + usual);
        Rectangle kept = SpotlightGeometry.layout(cuts, sizes, FRAME, java.util.List.of(sibling)).get(0);
        assertFalse(kept.intersects(sibling), "the callout moved off the sibling: " + kept);
        assertFalse(kept.intersects(cuts.get(0)), "and still covers nothing lit: " + kept);
    }

    /**
     * Keep-clear is strictly secondary: with an empty list every placement is exactly what MAIN placed before keep-clear
     * existed. The codes are {@code String.hashCode()} of each trial's {@code layout(...).toString()}, produced by
     * {@code SpotlightGeometry} as it was at {@code 5cbab2e5} (the base of PR #144). Comparing the three-argument
     * overload with the four-argument one would compare the new code with itself, since the first delegates to the
     * second (PR #144 review, B3). The CROWDED trials are the ones that matter: only there does no candidate cover
     * nothing, so only there does the tie-break run with an empty list — in five of these trials a tie going to the
     * LAST equal candidate instead of the first moves a callout.
     */
    @Test
    void withNothingToKeepClearOf_everyPlacementIsWhatMainPlaced() {
        assertPlacementsMatch("random", false, RANDOM_GOLDEN);
        assertPlacementsMatch("crowded", true, CROWDED_GOLDEN);
    }

    private static void assertPlacementsMatch(String label, boolean crowded, String golden) {
        String[] codes = golden.trim().split("\\s+");
        assertEquals(200, codes.length, label + ": golden code count");
        java.util.Random random = new java.util.Random(42);
        for (int trial = 0; trial < codes.length; trial++) {
            java.util.List<Rectangle> cuts = new java.util.ArrayList<>();
            java.util.List<Dimension> sizes = new java.util.ArrayList<>();
            int n = crowded ? 6 + random.nextInt(5) : 1 + random.nextInt(5);
            for (int i = 0; i < n; i++) {
                Rectangle target = crowded
                        ? new Rectangle(random.nextInt(900), random.nextInt(600), 150 + random.nextInt(300), 80 + random.nextInt(200))
                        : new Rectangle(random.nextInt(1100), random.nextInt(760), 20 + random.nextInt(300), 14 + random.nextInt(60));
                cuts.add(SpotlightGeometry.cutOut(target, FRAME));
                sizes.add(random.nextInt(4) == 0 ? null : new Dimension(120 + random.nextInt(260), 40 + random.nextInt(40)));
            }
            java.util.List<Rectangle> placed = SpotlightGeometry.layout(cuts, sizes, FRAME, java.util.List.of());
            assertEquals(codes[trial], String.format("%08x", placed.toString().hashCode()),
                    label + " trial " + trial + " placed differently from main: " + placed);
        }
    }

    /**
     * And it never trades a lit thing for an unlit one. Built so the two orders disagree: the usual "below" place
     * covers a small lit cut-out and NO keep-clear, while every place clear of lit things lies wholly inside the
     * keep-clear area. A keep-clear counted with the primary cost would choose "below", over the lit cut-out
     * (PR #144 review, B3: a keep-clear covering the whole frame cannot tell the two orders apart).
     */
    @Test
    void keepClearNeverPushesACalloutOntoSomethingLit() {
        Rectangle node = new Rectangle(500, 300, 145, 53);
        Rectangle nodeCut = SpotlightGeometry.cutOut(node, FRAME);
        Rectangle below = SpotlightGeometry.layout(java.util.List.of(nodeCut), java.util.List.of(CAPTION), FRAME).get(0);
        Rectangle small = new Rectangle(below.x + below.width / 2 - 10, below.y + below.height / 2 - 5, 20, 10);
        Rectangle smallCut = SpotlightGeometry.cutOut(small, FRAME);
        assertTrue(below.contains(smallCut), "precondition: the small lit thing sits inside the usual place: " + smallCut);
        java.util.List<Rectangle> everywhereButBelow = java.util.List.of(
                new Rectangle(0, 0, FRAME.width, below.y),
                new Rectangle(0, below.y + below.height, FRAME.width, FRAME.height - below.y - below.height),
                new Rectangle(0, below.y, below.x, below.height),
                new Rectangle(below.x + below.width, below.y, FRAME.width - below.x - below.width, below.height));
        java.util.List<Rectangle> cuts = java.util.List.of(nodeCut, smallCut);
        java.util.List<Dimension> sizes = java.util.Arrays.asList(CAPTION, null);    // only the node has a caption
        Rectangle callout = SpotlightGeometry.layout(cuts, sizes, FRAME, everywhereButBelow).get(0);
        assertFalse(callout.intersects(smallCut), "keep-clear outranked a lit cut-out: " + callout + " covers " + smallCut);
        assertFalse(callout.intersects(nodeCut), "the callout covers its own target: " + callout);
    }

    private static final String RANDOM_GOLDEN = ""
            + "daef325d c3bd1ac7 7fdcfb39 2f8b9f49 186bec4b 3b2ec41a bc126a0d 7588e988 "
            + "3c64c2c1 a18dec3b 529fe21c 9672f99f 74bf7a61 43adad52 4fa079e3 35aef550 "
            + "a18dec3b 40d00d83 e232c5d6 2a9f9126 5eaf237c 4104cca3 b66877b9 ecf944f3 "
            + "5fc9c0be 6becb0b8 b07d8fed ef0be0f5 083e5891 e26e7290 546a611c 0939a897 "
            + "753f0930 84f51dba d8ee9ed8 0f14e727 a6bd79d7 f8a88233 2f2450f8 2c650b56 "
            + "3e3636f8 902219d2 984f203b 4528c822 24752c61 b2cd6a8e 0fffe139 8e1167ba "
            + "3e488d85 21c9eccc a30d7556 cc2a6a43 7db65b06 5a679347 0426c11a 4a2f007c "
            + "71091e68 94d30ce0 c826611d 33a0473a 8fd6d616 7bc26d53 0abeed89 f82e7c63 "
            + "60e4a092 d3f9cf20 e5df523c 04122475 33145658 62e4f0b4 09413605 fc6e068a "
            + "08c46053 f4746ceb cba0aa3c ec4d5d29 7cf86c8c c09ebedc efea49e7 c8f02ac7 "
            + "aa09b2ef 4ab69991 b962a1a1 aed869bc 9a22665c 19a3921b cf33d99e 0881e1e7 "
            + "cf67ede5 35e44135 a18dec3b c6929234 ea743306 a5b5b7fd 50da115f d448e769 "
            + "458de338 bcf7eca8 c1a54343 3bd30855 c33fb96c 54496139 c5da9407 a7920fa9 "
            + "0ccc1e15 d2d45e42 f2da0ce8 a18dec3b 1db3d12c 45fe431c 7f755afa ee640caa "
            + "f63f2871 80c741ff b6eaf233 4a0f3f72 08e2569c 3bede5b3 ff0b376f 2e03edea "
            + "b095407c fef2e13f a18dec3b 1ad74447 446e89d7 2bc4ffe0 818bdb28 11dd1911 "
            + "029240da 10e87d24 376251a3 8a43dd6d ad25975d 8b0f0027 ce3935ea ee297173 "
            + "80d6cb30 718675c3 2bb07717 e9ae153d dc30dbfa 7077d7ed 372b2117 1a7320b8 "
            + "45344d6f bceb2cba f74a89aa 6b7f0506 945d1b75 00c52b48 7fe357f5 f531a537 "
            + "de93d55c bb0a648e cb840939 8878a57f 88722fab fb439b84 23451141 1213b2ac "
            + "d24e2986 ded5e757 e6d1a531 b5a6849a dcdaf189 8b7416b3 f4c178a7 7c74b106 "
            + "85df08ef ccfbdfcf efd9c900 5c179c44 b52526df ab854f04 43f510e4 0f174e16 "
            + "84cebe46 209dcb5a 87681b8e f575c0d2 74461e15 ae92df8a 6f1a5c6b 2a565411 "
            + "ca8fdb1d 1990f4aa 694e9b67 e4fc872e c0174592 befbf42f ab793a09 1ee5df55 "
            + "d430a7ce 12d8190a b10e4fd1 ad3f7267 d44a859b 3d2c7922 a4fc94f9 cef0edc4 ";

    private static final String CROWDED_GOLDEN = ""
            + "384b7806 b5f88301 42022e5f 0a985462 0ecc9667 b00e6d81 c34b2265 26a26f48 "
            + "09dc3db4 035e3d1e a41a6e20 3da5c616 11ccf562 a2e6d4bc 76877c9b 3c185408 "
            + "ac00cf82 19caf26b eea0fdd3 a017cb76 9d208ba5 80eaf065 61676139 6c8d6541 "
            + "3907062f 3bfc1f1a c7279e16 fc173e36 0716a6f4 b6404093 51e8a892 e79cac4e "
            + "9b00e801 5f417257 63aa38f7 f95a7cff 2e70e72e be0dec01 23e9a202 3559153d "
            + "99d8aa0c 4eddb775 5b97e154 f1fe5902 0ca7b83f 9c754746 6b5170a4 b6cec917 "
            + "e0226bb0 703707bb 89881c49 324e4aa7 293efe8f 0c1cec90 5f3576fe b3062fac "
            + "35a1a3ca 43049765 c137be15 b0de276b 75727170 9e2d53b0 5b787b1e b4fbd211 "
            + "ea7bb161 af90073f dc0a591d 0db67b25 0bb36d3c 50d5f35c 6eaa290e 94e20c74 "
            + "24018cb6 6b5501d6 8b762321 c7bdbd77 526122e1 1cab9346 e5515cb1 795688f8 "
            + "80957fe5 344295fb ab236e1f 49495fbe 1b0d441b 0fd942f2 a7ec102a dd727431 "
            + "8e25dc69 260fbeaa a83c4367 ad302ed7 3e05ea69 25525fc1 5b65fb8c f0c8c663 "
            + "e1db2149 c6142e60 aa6141b2 747ee12f 558d061a 82b910a8 7e004129 1065f8f7 "
            + "ff9b812f 16a23c57 07cb035f fadeaef9 b57206ab 768456ef f873bd61 ee9b6759 "
            + "4ee78c0b 3719e519 056e90fe 3d0ce4bb c7734c36 407b57b3 ca0c51e0 55c7502b "
            + "5ac08170 f6a60cd7 8fbb1ced b589b735 f78b1565 9ebee775 11ab4659 fde7b619 "
            + "bb29d18e c5303ab4 56b22e2c 2f792500 24947d94 4fc18caf cf957955 e7a8634f "
            + "b899bfa0 8377e650 4ae488fd 98979634 9b2058ad 18cfa435 e445a75f 8a687cdd "
            + "44b81720 0b9cbd78 bd267617 6bed5997 0fb3f548 7b392be4 92a22e6f aba27f01 "
            + "9e75e95b 739b9df6 bc8115a9 88ba8ea7 64f4d69e be180669 eb4515c1 1834871a "
            + "eebd970f c0b5cd56 cf71555b 6ae0b6a0 e0afe7cf 39c4fcca b7b0b253 18e09157 "
            + "e224f315 ba6ebd99 84ebfb72 8f45f8c9 5f895ee3 5e78b267 e51eb501 e0b6098e "
            + "7c31b4f5 082957ae a1645b59 9841c252 57d2c398 f49a8bd4 b93d470f a15ef9ac "
            + "d8b9b055 c68aeee6 a5dab1d7 ae03c1ef 6bbfa360 a923c5ca 089e5256 698ab69e "
            + "82bf24d5 66a206f0 94c7f763 83efb62f 6ab5b371 59c5a307 92eef59c eb8ff7a9 ";
}
