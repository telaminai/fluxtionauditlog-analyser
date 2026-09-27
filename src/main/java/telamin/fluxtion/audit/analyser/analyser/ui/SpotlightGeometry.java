package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

/**
 * M64 — where the caption goes and where its arrow points. Pure arithmetic, so the one judgement the
 * overlay makes ("the caption sits in whichever quadrant has room", D-SP3) is a test and not an opinion
 * held by a paint method.
 */
public final class SpotlightGeometry {

    private SpotlightGeometry() {
    }

    /** Room left between the cut-out and the caption, so the arrow has somewhere to be. */
    static final int GAP = 28;
    /** The cut-out is drawn slightly larger than its target, so the thing pointed at is not cropped by its own frame. */
    static final int PAD = 6;
    static final int MARGIN = 12;

    /** The target rectangle as it is cut out: padded, and kept inside the frame. */
    public static Rectangle cutOut(Rectangle target, Dimension frame) {
        Rectangle r = new Rectangle(target.x - PAD, target.y - PAD, target.width + 2 * PAD, target.height + 2 * PAD);
        return r.intersection(new Rectangle(0, 0, frame.width, frame.height));
    }

    /**
     * Every target's cut-out, in order. Two targets stacked one above the other — neighbouring code lines — have pads
     * that reach into each other: the upper outline's bottom edge was drawn through the lower line's text and the lower
     * outline's top edge through the upper line's. Where two such cut-outs overlap, both are trimmed to meet halfway
     * between the targets' facing edges, so their outlines become one separator there and no line of text is crossed.
     * Targets that overlap each other, or sit side by side, keep their cut-outs as they are.
     */
    public static java.util.List<Rectangle> cutOuts(java.util.List<Rectangle> targets, Dimension frame) {
        java.util.List<Rectangle> cuts = new java.util.ArrayList<>();
        for (Rectangle t : targets) cuts.add(cutOut(t, frame));
        for (int i = 0; i < targets.size(); i++) {
            for (int j = i + 1; j < targets.size(); j++) {
                Rectangle a = targets.get(i), b = targets.get(j);
                if (!cuts.get(i).intersects(cuts.get(j))) continue;
                boolean sideBySide = a.x >= b.x + b.width || b.x >= a.x + a.width;
                int upper, lower;
                if (a.y + a.height <= b.y) { upper = i; lower = j; }
                else if (b.y + b.height <= a.y) { upper = j; lower = i; }
                else continue;                                  // the targets overlap: one merged hole, as before
                if (sideBySide) continue;
                Rectangle up = targets.get(upper), low = targets.get(lower);
                int mid = (up.y + up.height + low.y) / 2;
                Rectangle cu = cuts.get(upper), cl = cuts.get(lower);
                if (cu.y + cu.height > mid) cu.height = mid - cu.y;
                if (cl.y < mid) { cl.height -= mid - cl.y; cl.y = mid; }
            }
        }
        return cuts;
    }

    /**
     * Place a caption of {@code size} beside {@code cutOut}. Tried in order — below, above, right, left —
     * and the first side with room wins; with room nowhere (a target filling the frame) it goes INSIDE
     * the cut-out's bottom edge rather than off screen, because a caption nobody can read is worse than
     * one that overlaps. Always clamped into the frame.
     */
    public static Rectangle captionBox(Rectangle cutOut, Dimension size, Dimension frame) {
        int centredX = cutOut.x + (cutOut.width - size.width) / 2;
        int centredY = cutOut.y + (cutOut.height - size.height) / 2;
        Rectangle below = new Rectangle(centredX, cutOut.y + cutOut.height + GAP, size.width, size.height);
        Rectangle above = new Rectangle(centredX, cutOut.y - GAP - size.height, size.width, size.height);
        Rectangle right = new Rectangle(cutOut.x + cutOut.width + GAP, centredY, size.width, size.height);
        Rectangle left = new Rectangle(cutOut.x - GAP - size.width, centredY, size.width, size.height);
        if (below.y + below.height + MARGIN <= frame.height) return clampX(below, frame);
        if (above.y >= MARGIN) return clampX(above, frame);
        if (right.x + right.width + MARGIN <= frame.width) return clampY(right, frame);
        if (left.x >= MARGIN) return clampY(left, frame);
        Rectangle inside = new Rectangle(centredX, cutOut.y + cutOut.height - size.height - MARGIN, size.width, size.height);
        return clampY(clampX(inside, frame), frame);
    }

    /**
     * Place SEVERAL callouts (M64.6). {@code sizes.get(i)} is callout {@code i}'s size, or null when that
     * spotlight has no caption (its slot in the answer is null too).
     *
     * <p>Each is placed in turn beside its own cut-out — the same four sides, in the same order, as
     * {@link #captionBox} — and the first side that covers NO cut-out and NO callout already placed wins. A
     * callout that hides another thing being pointed at defeats the pointing, so that is what is avoided
     * first. When every side covers something, the side covering the LEAST wins: an overlapped callout can
     * still be read, one off screen cannot.
     *
     * <p><b>Each side is also offered SLID along itself</b>, by whole caption sizes ({@link #SLIDE_STEPS}),
     * after all four centred positions have been tried. Four sides alone gave each callout five places to
     * be, and callouts pointing at consecutive source lines share almost the same five — so the fourth was
     * drawn across the second's text. Sliding costs nothing when nothing contends, because the centred
     * positions come first and a zero-cost placement wins immediately.
     *
     * <p>With one spotlight this is exactly {@link #captionBox}: the centred sides are offered in the
     * documented order, so an uncontended callout lands where it always did.
     */
    public static java.util.List<Rectangle> layout(java.util.List<Rectangle> cutOuts, java.util.List<Dimension> sizes,
                                                   Dimension frame) {
        java.util.List<Rectangle> placed = new java.util.ArrayList<>();
        for (int i = 0; i < cutOuts.size(); i++) {
            Dimension size = sizes.get(i);
            if (size == null) {
                placed.add(null);
                continue;
            }
            Rectangle best = null;
            long bestCost = Long.MAX_VALUE;
            for (Rectangle candidate : candidates(cutOuts.get(i), size, frame)) {
                long cost = 0;
                for (Rectangle cut : cutOuts) cost += overlap(candidate, cut);
                for (Rectangle other : placed) if (other != null) cost += overlap(candidate, other);
                if (cost < bestCost) {
                    best = candidate;
                    bestCost = cost;
                }
                if (cost == 0) break;
            }
            placed.add(best);
        }
        return placed;
    }

    /**
     * How far a callout may SLIDE along its side to find a free slot, in steps of its own size.
     *
     * <p>Four positions and a centre: enough to clear three or four neighbours stacked on consecutive
     * lines, which is the case this exists for. More would not help — with six callouts round one
     * point the honest answer is that they do not fit, and the least-cost fallback still applies.
     */
    private static final int[] SLIDE_STEPS = {1, -1, 2, -2};

    /**
     * The sides with room, in {@link #captionBox}'s order, each clamped into the frame; INSIDE always last.
     *
     * <p>Every side is offered CENTRED first, then slid along itself by whole caption-heights (beside) or
     * caption-widths (above/below). Without the slid variants each callout had exactly five places to be,
     * and four callouts pointing at four consecutive lines of source share almost the same five: by the
     * fourth, every candidate collided with something and {@link #layout} could only choose the least-bad
     * — which is a callout drawn across another callout's text, with the lower one's caption unreadable.
     * Sliding costs nothing when there is no contention, because the centred position is tried first and
     * a zero-cost placement wins immediately.
     */
    private static java.util.List<Rectangle> candidates(Rectangle cutOut, Dimension size, Dimension frame) {
        int centredX = cutOut.x + (cutOut.width - size.width) / 2;
        int centredY = cutOut.y + (cutOut.height - size.height) / 2;
        Rectangle below = new Rectangle(centredX, cutOut.y + cutOut.height + GAP, size.width, size.height);
        Rectangle above = new Rectangle(centredX, cutOut.y - GAP - size.height, size.width, size.height);
        Rectangle right = new Rectangle(cutOut.x + cutOut.width + GAP, centredY, size.width, size.height);
        Rectangle left = new Rectangle(cutOut.x - GAP - size.width, centredY, size.width, size.height);

        boolean belowFits = below.y + below.height + MARGIN <= frame.height;
        boolean aboveFits = above.y >= MARGIN;
        boolean rightFits = right.x + right.width + MARGIN <= frame.width;
        boolean leftFits = left.x >= MARGIN;

        java.util.List<Rectangle> out = new java.util.ArrayList<>();
        // the centred positions first, in the documented order, so ONE spotlight lands exactly where
        // captionBox would put it — the equivalence the layout javadoc promises
        if (belowFits) out.add(clampX(below, frame));
        if (aboveFits) out.add(clampX(above, frame));
        if (rightFits) out.add(clampY(right, frame));
        if (leftFits) out.add(clampY(left, frame));
        // then the same sides, slid along themselves
        int dx = size.width + GAP / 2, dy = size.height + GAP / 2;
        for (int step : SLIDE_STEPS) {
            if (belowFits) addIfInFrame(out, shift(below, step * dx, 0), frame);
            if (aboveFits) addIfInFrame(out, shift(above, step * dx, 0), frame);
            if (rightFits) addIfInFrame(out, shift(right, 0, step * dy), frame);
            if (leftFits) addIfInFrame(out, shift(left, 0, step * dy), frame);
        }
        Rectangle inside = new Rectangle(centredX, cutOut.y + cutOut.height - size.height - MARGIN, size.width, size.height);
        out.add(clampY(clampX(inside, frame), frame));
        return out;
    }

    private static Rectangle shift(Rectangle r, int dx, int dy) {
        return new Rectangle(r.x + dx, r.y + dy, r.width, r.height);
    }

    /**
     * Keep a slid candidate only if it is WHOLLY on screen. Clamping it back into the frame instead would
     * quietly undo the slide and re-propose a position already offered.
     */
    private static void addIfInFrame(java.util.List<Rectangle> out, Rectangle r, Dimension frame) {
        if (r.x >= MARGIN && r.y >= MARGIN
                && r.x + r.width + MARGIN <= frame.width
                && r.y + r.height + MARGIN <= frame.height) {
            out.add(r);
        }
    }

    private static long overlap(Rectangle a, Rectangle b) {
        Rectangle both = a.intersection(b);
        return both.isEmpty() ? 0 : (long) both.width * both.height;
    }

    /**
     * Where a spotlight's number goes: centred on its cut-out's top-left corner, kept inside the frame. A
     * number is how the tutor's sentence ("② is the node that never logged") finds its cut-out.
     */
    public static Rectangle badge(Rectangle cutOut, int diameter, Dimension frame) {
        int x = clamp(cutOut.x - diameter / 2, 2, Math.max(2, frame.width - diameter - 2));
        int y = clamp(cutOut.y - diameter / 2, 2, Math.max(2, frame.height - diameter - 2));
        return new Rectangle(x, y, diameter, diameter);
    }

    /**
     * The arrow: from the caption's edge nearest the cut-out to the cut-out's edge nearest the caption.
     * Returns {from, to}; both are the same point when the caption sits inside the cut-out (no arrow).
     */
    public static Point[] arrow(Rectangle caption, Rectangle cutOut) {
        if (cutOut.intersects(caption)) {
            Point p = new Point(caption.x + caption.width / 2, caption.y);
            return new Point[]{p, p};
        }
        int cx = clamp(caption.x + caption.width / 2, cutOut.x, cutOut.x + cutOut.width);
        int cy = clamp(caption.y + caption.height / 2, cutOut.y, cutOut.y + cutOut.height);
        int fx = clamp(cx, caption.x, caption.x + caption.width);
        int fy = clamp(cy, caption.y, caption.y + caption.height);
        return new Point[]{new Point(fx, fy), new Point(cx, cy)};
    }

    private static Rectangle clampX(Rectangle r, Dimension frame) {
        int x = Math.max(MARGIN, Math.min(r.x, frame.width - r.width - MARGIN));
        return new Rectangle(x, r.y, r.width, r.height);
    }

    private static Rectangle clampY(Rectangle r, Dimension frame) {
        int y = Math.max(MARGIN, Math.min(r.y, frame.height - r.height - MARGIN));
        return new Rectangle(r.x, y, r.width, r.height);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
