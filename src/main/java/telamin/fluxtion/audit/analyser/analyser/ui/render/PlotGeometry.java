package telamin.fluxtion.audit.analyser.analyser.ui.render;

/**
 * The plot rectangle and the data window it shows — everything needed to turn a data point into a
 * pixel, as an explicit value rather than as six fields on a Swing component.
 *
 * <p>That is the whole reason it exists. The mapping used to read {@code plotX}, {@code plotW},
 * {@code vx0}, {@code vx1} and friends off the panel, so no drawing code could be exercised without
 * constructing a panel and running a paint. Passed as a value, the same mapping serves the screen
 * and a test.
 *
 * @param x      left edge of the plot area, in pixels
 * @param y      top edge
 * @param width  plot width in pixels; the right edge is {@code x + width}
 * @param height plot height
 * @param minX   data value at the left edge (epoch millis)
 * @param maxX   data value at the right edge
 * @param minY   data value at the BOTTOM edge
 * @param maxY   data value at the top edge
 */
public record PlotGeometry(int x, int y, int width, int height,
                           double minX, double maxX, double minY, double maxY) {

    /** The right-hand edge in pixels — where a series that runs to the end of the window stops. */
    public int right() {
        return x + width;
    }

    /** The bottom edge in pixels, which is where the x-axis line is drawn. */
    public int bottom() {
        return y + height;
    }

    public int xToPx(long dataX) {
        return x + (int) Math.round((dataX - minX) / (maxX - minX) * width);
    }

    public int yToPx(double dataY) {
        return y + height - (int) Math.round((dataY - minY) / (maxY - minY) * height);
    }
}
