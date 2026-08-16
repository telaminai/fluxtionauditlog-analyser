package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which vertical axis each series is drawn against.
 *
 * <p>One shared axis is right until two series differ in magnitude, and then it is actively misleading:
 * a revenue line rising to 2,000 and a stock level oscillating around 20 share a chart where the stock
 * line is a flat smear along the bottom. Both facts are on screen and neither is readable, which is
 * worse than plotting them separately — it looks like an answer.
 *
 * <p>Two axes, left and right, deliberately. A third is possible and almost never legible: past two
 * scales a reader has to consult a legend to know what a height means, at which point the chart has
 * stopped being a picture.
 *
 * <p>Pure: series labels in, an axis per label out. The chart owns the drawing and the scaling.
 */
public final class AxisAssignment {

    /** Which side of the plot a series is measured against. */
    public enum Axis { LEFT, RIGHT }

    private final Set<String> right = new LinkedHashSet<>();

    /** Everything on the left — the default, and correct whenever the series are commensurable. */
    public AxisAssignment() {
    }

    public AxisAssignment(Collection<String> rightSeries) {
        if (rightSeries != null) {
            for (String label : rightSeries) {
                if (label != null && !label.isBlank()) {
                    right.add(label);
                }
            }
        }
    }

    public Axis axisOf(String seriesLabel) {
        return right.contains(seriesLabel) ? Axis.RIGHT : Axis.LEFT;
    }

    public boolean isRight(String seriesLabel) {
        return right.contains(seriesLabel);
    }

    /** True when anything is on the right, i.e. the chart must draw and label a second scale. */
    public boolean hasRightAxis() {
        return !right.isEmpty();
    }

    public Set<String> rightSeries() {
        return Set.copyOf(right);
    }

    /**
     * Move a series to the right axis. Returns a new assignment; the chart replaces its copy rather than
     * mutating one shared with a saved graph.
     */
    public AxisAssignment plusRight(String seriesLabel) {
        Set<String> next = new LinkedHashSet<>(right);
        next.add(seriesLabel);
        return new AxisAssignment(next);
    }

    public AxisAssignment minusRight(String seriesLabel) {
        Set<String> next = new LinkedHashSet<>(right);
        next.remove(seriesLabel);
        return new AxisAssignment(next);
    }

    /**
     * A suggestion: put a series on the right when its range is an order of magnitude away from the
     * largest, so a caller can offer the split rather than make the user discover the need for it.
     *
     * <p>Only ever a suggestion. Two series that genuinely belong on one scale — a price and its moving
     * average — can differ in range transiently, and silently splitting them would break the comparison
     * the reader came for.
     */
    public static AxisAssignment suggestFor(Collection<Series> series) {
        double largest = 0;
        for (Series s : series) {
            largest = Math.max(largest, spanOf(s));
        }
        if (largest <= 0) {
            return new AxisAssignment();
        }
        Set<String> right = new LinkedHashSet<>();
        for (Series s : series) {
            double span = spanOf(s);
            if (span > 0 && largest / span >= 10) {
                right.add(s.label());
            }
        }
        // everything suggested for the right means nothing is left to compare against
        return right.size() == series.size() ? new AxisAssignment() : new AxisAssignment(right);
    }

    private static double spanOf(Series s) {
        double min = s.minFiniteY();
        double max = s.maxFiniteY();
        if (Double.isNaN(min) || Double.isNaN(max) || Double.isInfinite(min) || Double.isInfinite(max)) {
            return 0;
        }
        // a flat series has no span of its own; judge it by its magnitude instead of calling it zero
        double span = max - min;
        return span > 0 ? span : Math.abs(max);
    }
}
