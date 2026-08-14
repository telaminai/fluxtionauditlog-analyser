package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.Arrays;

/**
 * A time series of {@code (logTime, value)} points for one {@link GraphKey}. Values may be
 * {@code NaN} (rendered as a gap). Backed by growable primitive arrays.
 */
public final class Series {

    private final GraphKey key;    // null for a derived (formula) series — see the label constructor
    private final String label;
    private long[] xs = new long[16];
    private double[] ys = new double[16];
    private int n;

    public Series(GraphKey key) {
        this.key = key;
        this.label = key == null ? "" : key.display();
    }

    /** A derived (formula) series identified by a display label rather than a single {@link GraphKey}. */
    public Series(String label) {
        this.key = null;
        this.label = label == null ? "" : label;
    }

    /** The graph key, or {@code null} for a derived series. */
    public GraphKey key() {
        return key;
    }

    /** The legend/CSV display name — the key's display for a raw series, the formula label for a derived one. */
    public String label() {
        return label;
    }

    public void add(long x, double y) {
        if (n == xs.length) {
            xs = Arrays.copyOf(xs, n * 2);
            ys = Arrays.copyOf(ys, n * 2);
        }
        xs[n] = x;
        ys[n] = y;
        n++;
    }

    public int size() { return n; }
    public long x(int i) { return xs[i]; }
    public double y(int i) { return ys[i]; }

    public long minX() {
        long m = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) m = Math.min(m, xs[i]);
        return m;
    }

    public long maxX() {
        long m = Long.MIN_VALUE;
        for (int i = 0; i < n; i++) m = Math.max(m, xs[i]);
        return m;
    }

    public double minFiniteY() {
        double m = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) if (Double.isFinite(ys[i])) m = Math.min(m, ys[i]);
        return m;
    }

    public double maxFiniteY() {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) if (Double.isFinite(ys[i])) m = Math.max(m, ys[i]);
        return m;
    }
}
