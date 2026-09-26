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
     * still be read, one off screen cannot. With one spotlight this is exactly {@link #captionBox}.
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

    /** The sides with room, in {@link #captionBox}'s order, each clamped into the frame; INSIDE always last. */
    private static java.util.List<Rectangle> candidates(Rectangle cutOut, Dimension size, Dimension frame) {
        int centredX = cutOut.x + (cutOut.width - size.width) / 2;
        int centredY = cutOut.y + (cutOut.height - size.height) / 2;
        Rectangle below = new Rectangle(centredX, cutOut.y + cutOut.height + GAP, size.width, size.height);
        Rectangle above = new Rectangle(centredX, cutOut.y - GAP - size.height, size.width, size.height);
        Rectangle right = new Rectangle(cutOut.x + cutOut.width + GAP, centredY, size.width, size.height);
        Rectangle left = new Rectangle(cutOut.x - GAP - size.width, centredY, size.width, size.height);
        java.util.List<Rectangle> out = new java.util.ArrayList<>();
        if (below.y + below.height + MARGIN <= frame.height) out.add(clampX(below, frame));
        if (above.y >= MARGIN) out.add(clampX(above, frame));
        if (right.x + right.width + MARGIN <= frame.width) out.add(clampY(right, frame));
        if (left.x >= MARGIN) out.add(clampY(left, frame));
        Rectangle inside = new Rectangle(centredX, cutOut.y + cutOut.height - size.height - MARGIN, size.width, size.height);
        out.add(clampY(clampX(inside, frame), frame));
        return out;
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
