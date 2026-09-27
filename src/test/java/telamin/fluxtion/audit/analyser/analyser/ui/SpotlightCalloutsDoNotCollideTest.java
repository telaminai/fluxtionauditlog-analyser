package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Callouts pointing at CONSECUTIVE source lines must not be drawn over each other.
 *
 * <p>Walking someone through code is the case this is for — "1 declares it, 2 depends on it, 3 is what
 * it computes" — and those lines are adjacent by definition. Each callout had five possible positions
 * (four sides and inside), and four callouts round four adjacent lines share almost the same five, so
 * the greedy placer ran out and fell back to "least bad": a box drawn across another box's text, with
 * the lower caption unreadable. A callout that hides another callout defeats the pointing.
 */
class SpotlightCalloutsDoNotCollideTest {

    private static final Dimension FRAME = new Dimension(1200, 900);
    /** A caption is wide and short, which is what makes them so easy to collide vertically. */
    private static final Dimension CAPTION = new Dimension(360, 56);

    /** {@code n} targets on consecutive 17px lines, like a source viewer's rows. */
    private static List<Rectangle> consecutiveLines(int n, int top) {
        List<Rectangle> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Rectangle(300, top + i * 17, 560, 17));
        return out;
    }

    private static List<Dimension> sizes(int n) {
        List<Dimension> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(CAPTION);
        return out;
    }

    /** Every unordered pair that overlaps, named so a failure says WHICH two collided. */
    private static List<String> collisions(List<Rectangle> placed) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) {
            for (int j = i + 1; j < placed.size(); j++) {
                if (placed.get(i) == null || placed.get(j) == null) continue;
                Rectangle both = placed.get(i).intersection(placed.get(j));
                if (!both.isEmpty()) {
                    out.add((i + 1) + "×" + (j + 1) + " overlap " + both.width + "×" + both.height
                            + "px (" + placed.get(i) + " vs " + placed.get(j) + ")");
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("Four callouts on four consecutive lines are all readable")
    void fourAdjacentLinesDoNotCollide() {
        var cuts = SpotlightGeometry.cutOuts(consecutiveLines(4, 400), FRAME);

        var placed = SpotlightGeometry.layout(cuts, sizes(4), FRAME);

        assertEquals(List.of(), collisions(placed),
                "this is the reported defect: box 4 was drawn over box 2's caption");
    }

    @Test
    @DisplayName("…and so are six, the most the verb allows")
    void sixAdjacentLinesDoNotCollide() {
        var cuts = SpotlightGeometry.cutOuts(consecutiveLines(6, 350), FRAME);

        var placed = SpotlightGeometry.layout(cuts, sizes(6), FRAME);

        assertEquals(List.of(), collisions(placed));
    }

    @Test
    @DisplayName("A callout never covers a cut-out either — that is the thing being pointed at")
    void noCalloutCoversATarget() {
        var targets = consecutiveLines(4, 400);
        var cuts = SpotlightGeometry.cutOuts(targets, FRAME);

        var placed = SpotlightGeometry.layout(cuts, sizes(4), FRAME);

        for (int i = 0; i < placed.size(); i++) {
            for (Rectangle cut : cuts) {
                assertTrue(placed.get(i).intersection(cut).isEmpty(),
                        "callout " + (i + 1) + " at " + placed.get(i) + " covers a cut-out " + cut);
            }
        }
    }

    @Test
    @DisplayName("Every callout stays wholly on screen")
    void allOnScreen() {
        var cuts = SpotlightGeometry.cutOuts(consecutiveLines(5, 380), FRAME);

        for (Rectangle r : SpotlightGeometry.layout(cuts, sizes(5), FRAME)) {
            assertTrue(r.x >= 0 && r.y >= 0 && r.x + r.width <= FRAME.width && r.y + r.height <= FRAME.height,
                    "off screen: " + r + " in " + FRAME.width + "×" + FRAME.height);
        }
    }

    /**
     * The promise in {@code layout}'s javadoc: with ONE spotlight it is exactly {@code captionBox}.
     * Sliding must be a fallback for contention, never a change to the uncontended placement.
     */
    @Test
    @DisplayName("One spotlight still lands exactly where captionBox puts it")
    void oneSpotlightIsUnchanged() {
        for (Rectangle target : List.of(
                new Rectangle(300, 400, 560, 17),      // middle of the frame
                new Rectangle(300, 20, 560, 17),       // hard against the top
                new Rectangle(300, 860, 560, 17),      // hard against the bottom
                new Rectangle(20, 400, 120, 17))) {    // hard against the left
            var cuts = SpotlightGeometry.cutOuts(List.of(target), FRAME);
            assertEquals(SpotlightGeometry.captionBox(cuts.getFirst(), CAPTION, FRAME),
                    SpotlightGeometry.layout(cuts, List.of(CAPTION), FRAME).getFirst(),
                    "target " + target);
        }
    }

    @Test
    @DisplayName("A spotlight with no caption still gets a null slot, in place")
    void captionlessKeepsItsSlot() {
        var cuts = SpotlightGeometry.cutOuts(consecutiveLines(3, 400), FRAME);

        var placed = SpotlightGeometry.layout(cuts, java.util.Arrays.asList(CAPTION, null, CAPTION), FRAME);

        assertEquals(3, placed.size());
        assertNull(placed.get(1));
        assertEquals(List.of(), collisions(placed));
    }
}
