package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The result of laying a {@link ProcessorTopology} out: plain geometry, no Swing (M21.2,
 * spec-graph-replay §5).
 *
 * <p>Deliberately just numbers. The panel (M21.3) paints these boxes and polylines and owns nothing about
 * <em>where</em> things go, which keeps the hard part — layout — headless-testable, and keeps repaint
 * cheap because geometry is computed once and cached rather than recomputed per frame.
 *
 * <p>Coordinates start at the origin: the layout is normalised so the top-left of the bounding box is
 * {@code (0,0)}, leaving pan/zoom entirely to the view.
 */
public final class TopologyLayout {

    public record Point(double x, double y) { }

    /** A node's placed rectangle. {@code layer} and {@code indexInLayer} are kept for debugging and tests. */
    public record NodeBox(String id, double x, double y, double width, double height,
                          int layer, int indexInLayer) {

        public double centerX() {
            return x + width / 2;
        }

        public double centerY() {
            return y + height / 2;
        }

        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean contains(double px, double py) {
            return px >= x && px <= maxX() && py >= y && py <= maxY();
        }
    }

    /**
     * A routed edge. {@code points} runs from the source node's centre to the target's, bending through
     * the layers it spans. Always in the graph's own direction: an edge reversed internally to break a
     * cycle is reported the right way round, with {@link #reversed()} set so the view can mark it.
     */
    public record EdgePath(String id, String source, String target, List<Point> points, boolean reversed) {

        public Point start() {
            return points.get(0);
        }

        public Point end() {
            return points.get(points.size() - 1);
        }

        /** True when the edge needed bend points — i.e. it spans more than one layer. */
        public boolean isRouted() {
            return points.size() > 2;
        }
    }

    private final Map<String, NodeBox> boxes;
    private final List<EdgePath> edges;
    private final int layerCount;
    private final double width;
    private final double height;

    TopologyLayout(Map<String, NodeBox> boxes, List<EdgePath> edges, int layerCount,
                   double width, double height) {
        this.boxes = Collections.unmodifiableMap(new LinkedHashMap<>(boxes));
        this.edges = List.copyOf(edges);
        this.layerCount = layerCount;
        this.width = width;
        this.height = height;
    }

    public static TopologyLayout empty() {
        return new TopologyLayout(Map.of(), List.of(), 0, 0, 0);
    }

    public boolean isEmpty() {
        return boxes.isEmpty();
    }

    /** Boxes in the topology's own node order, so painting and hit-testing are deterministic. */
    public Collection<NodeBox> boxes() {
        return boxes.values();
    }

    public NodeBox box(String id) {
        return id == null ? null : boxes.get(id);
    }

    public List<EdgePath> edges() {
        return edges;
    }

    public int layerCount() {
        return layerCount;
    }

    /** Overall size; the layout starts at {@code (0,0)}. */
    public double width() {
        return width;
    }

    public double height() {
        return height;
    }

    /** Boxes on one layer, left to right. */
    public List<NodeBox> layer(int layer) {
        List<NodeBox> out = new ArrayList<>();
        for (NodeBox b : boxes.values()) {
            if (b.layer() == layer) out.add(b);
        }
        out.sort((a, b) -> Integer.compare(a.indexInLayer(), b.indexInLayer()));
        return out;
    }

    /** The topmost box containing this point, or {@code null} — hit-testing for the view. */
    public NodeBox at(double x, double y) {
        NodeBox hit = null;
        for (NodeBox b : boxes.values()) {
            if (b.contains(x, y)) hit = b;
        }
        return hit;
    }
}
