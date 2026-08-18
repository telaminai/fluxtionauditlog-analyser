package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.List;
import java.util.function.Predicate;

/**
 * The point-snapped hover's search (spec-marker-series M32.1) — pure math, headless-tested even
 * though the tooltip that uses it is not. Given the chart's view transforms, find the nearest actual
 * sample to the cursor within a pixel radius; the caller renders {@code series · time · value} (and,
 * for markers, the payload). No candidate in radius → the caller falls back to the coordinate
 * readout, exactly today's behaviour.
 */
public final class SnapSearch {

    /** A snapped sample: which series, the sample's data coords, and its pixel distance. */
    public record Hit(String label, long x, double y, int distancePx, boolean decimated) {
    }

    private SnapSearch() {
    }

    /**
     * Nearest finite sample across {@code series} within {@code radiusPx} of {@code (mx, my)}.
     * {@code isRight} says which series read the right-hand scale. A series denser than
     * {@code 3 × plotW} is DECIMATED on screen; snapping to one sample of it would pretend one sample
     * is the truth, so the hit is flagged and the caller reports the cursor column's min/max instead.
     */
    public static Hit nearest(List<Series> series, Predicate<String> isRight,
                              double vx0, double vx1, double vy0, double vy1, double ry0, double ry1,
                              int plotX, int plotY, int plotW, int plotH,
                              int mx, int my, int radiusPx) {
        Hit best = null;
        for (Series s : series) {
            boolean right = isRight.test(s.label());
            boolean decimated = s.size() > 3L * plotW;
            for (int i = 0; i < s.size(); i++) {
                double v = s.y(i);
                if (!Double.isFinite(v)) continue;
                int px = xToPx(s.x(i), vx0, vx1, plotX, plotW);
                if (px < mx - radiusPx || px > mx + radiusPx) continue;   // cheap x-band rejection
                int py = yToPx(v, right ? ry0 : vy0, right ? ry1 : vy1, plotY, plotH);
                int dx = px - mx;
                int dy = py - my;
                int d2 = dx * dx + dy * dy;
                if (d2 > radiusPx * radiusPx) continue;
                if (best == null || d2 < (long) best.distancePx() * best.distancePx()) {
                    best = new Hit(s.label(), s.x(i), v, (int) Math.round(Math.sqrt(d2)), decimated);
                }
            }
        }
        return best;
    }

    /** The cursor column's min/max for a decimated series — honesty over false precision (M32.1). */
    public static double[] columnMinMax(Series s, long colXFrom, long colXTo) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < s.size(); i++) {
            long x = s.x(i);
            if (x < colXFrom || x > colXTo) continue;
            double v = s.y(i);
            if (!Double.isFinite(v)) continue;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        return min <= max ? new double[]{min, max} : null;
    }

    static int xToPx(long x, double vx0, double vx1, int plotX, int plotW) {
        return plotX + (int) Math.round((x - vx0) / (vx1 - vx0) * plotW);
    }

    static int yToPx(double y, double lo, double hi, int plotY, int plotH) {
        return plotY + plotH - (int) Math.round((y - lo) / (hi - lo) * plotH);
    }
}
