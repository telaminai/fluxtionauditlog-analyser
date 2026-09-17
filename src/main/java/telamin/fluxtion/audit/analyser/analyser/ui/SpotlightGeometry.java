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
