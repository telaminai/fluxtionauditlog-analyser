package telamin.fluxtion.audit.analyser.analyser.ui.render;

import telamin.fluxtion.audit.analyser.analyser.graph.Series;

import java.util.function.LongFunction;

/**
 * The drawing that carried defects, moved onto {@link Surface} so it can be asserted.
 *
 * <p>A deliberate slice, not a rewrite: the series line and the x-axis labels, because those are the
 * two paths whose defects needed a display list to see. The rest of the chart still paints straight
 * onto {@code Graphics2D}. Converting it is worthwhile and is not what this proves.
 */
public final class PlotPainter {

    private PlotPainter() {
    }

    /** How a series line is joined between samples. */
    public enum Style { STEP, LINE, POINTS }

    /**
     * Baseline for the x-axis labels: below the plot frame by {@code drop}.
     *
     * <p>Relative to the PLOT, never to the component. They were positioned at the component's
     * bottom edge, which is inside the explanation footer whenever a chart has one — and the footer
     * fills its background, so pinning a single note silently erased the time axis. A note is pinned
     * to a moment in time; losing the axis to it defeats the feature.
     */
    public static int axisLabelBaseline(PlotGeometry geom, int drop) {
        return geom.bottom() + drop;
    }

    /**
     * The window's start and end times, at the bottom corners of the plot.
     *
     * <p>{@code format} turns an epoch into the printed text, supplied rather than reached for, so a
     * test is not asserting against the machine's locale or zone.
     */
    public static void axisLabels(Surface surface, PlotGeometry geom, int drop, LongFunction<String> format) {
        int baseline = axisLabelBaseline(geom, drop);
        String low = format.apply((long) geom.minX());
        String high = format.apply((long) geom.maxX());
        surface.drawString(low, geom.x(), baseline);
        surface.drawString(high, geom.right() - surface.fontMetrics().stringWidth(high), baseline);
    }

    /**
     * One series, in {@code style}, including the closing hold.
     *
     * <p>NaN and infinity break the line rather than being plotted — a gap in the data is a gap on
     * the chart, not a spike through zero.
     *
     * @param markers draw a dot at each sample; the caller decides, since it depends on density
     */
    public static void series(Surface surface, PlotGeometry geom, Series s, Style style, boolean markers) {
        int prevX = 0, prevY = 0;
        boolean have = false;
        for (int i = 0; i < s.size(); i++) {
            double v = s.y(i);
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                have = false;
                continue;
            }
            int px = geom.xToPx(s.x(i));
            int py = geom.yToPx(v);
            if (have && style != Style.POINTS) {
                if (style == Style.STEP) {
                    surface.drawLine(prevX, prevY, px, prevY);   // hold the value…
                    surface.drawLine(px, prevY, px, py);         // …then step to the new one
                } else {
                    surface.drawLine(prevX, prevY, px, py);
                }
            }
            if (markers || style == Style.POINTS) surface.fillOval(px - 2, py - 2, 4, 4);
            prevX = px;
            prevY = py;
            have = true;
        }
        holdToWindowEdge(surface, geom, style, prevX, prevY, have);
    }

    /**
     * Carry the LAST value to the right edge of the window, as a step.
     *
     * <p>Every other step in a series asserts "this value holds until the next one"; without this the
     * final one asserted nothing and drew as a bare vertical stroke with no horizontal run. The value
     * that ends a run — the closing position, the final spread — is the one most often wanted, and it
     * was the least visible thing on the chart. Pinning the window past the last record did not help:
     * the axis grew and the line still stopped dead, leaving the remainder blank, which reads exactly
     * like "no data".
     *
     * <p>STEP only. A LINE interpolates between points it actually has, so running one flat past the
     * last point would assert a hold the style does not claim.
     */
    public static void holdToWindowEdge(Surface surface, PlotGeometry geom, Style style,
                                        int lastX, int lastY, boolean have) {
        Integer end = holdEnd(geom, style, lastX, have);
        if (end != null) surface.drawLine(lastX, lastY, end, lastY);
    }

    /** Where the closing hold ends, or null when none should be drawn. */
    public static Integer holdEnd(PlotGeometry geom, Style style, int lastX, boolean have) {
        if (!have || style != Style.STEP) return null;
        return lastX < geom.right() ? geom.right() : null;
    }
}
