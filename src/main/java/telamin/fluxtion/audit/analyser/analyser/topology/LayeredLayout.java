package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sugiyama layered layout for a {@link ProcessorTopology} (M21.2, spec-graph-replay §3/§5) — the same
 * family of algorithm as <a href="https://github.com/dagrejs/dagre">dagre</a>, which is what
 * {@code fluxtion-visualiser} and {@code svc-admin-web} get from cytoscape. Written rather than
 * depended-upon because the JS libraries have no drop-in Java equivalent that doesn't drag in a layout
 * framework, and the graphs are processor-sized: the largest emitted graph to hand is 300 nodes.
 *
 * <p>The classic four passes, plus the two the classic description omits:
 * <ol>
 *   <li><b>Break cycles</b> — a Fluxtion dispatch graph is a DAG, but never trust that: a back edge is
 *       reversed for layout and reported the right way round.</li>
 *   <li><b>Assign layers</b> — longest path, so every edge points strictly downward and a node sits
 *       below everything that feeds it. That is the property that makes the picture readable: <em>lower
 *       means later in dispatch order</em>.</li>
 *   <li><b>Insert dummies</b> for edges spanning more than one layer, so long edges route through the
 *       gaps instead of cutting across nodes.</li>
 *   <li><b>Reduce crossings</b> — median heuristic sweeps, then adjacent-exchange (transpose) passes
 *       that keep only swaps which actually lower the crossing count.</li>
 *   <li><b>Assign coordinates</b> — pull each node toward the median of its neighbours, then enforce
 *       minimum separation so the ordering from (4) survives.</li>
 *   <li><b>Route</b> — polylines through the dummy positions.</li>
 * </ol>
 *
 * <p><b>Deterministic by construction.</b> Every iteration order derives from the topology's own node
 * order, never from a hash. The same graph always lays out identically — which tests depend on, and
 * which matters more than it sounds: a graph that reshuffles between openings is unreadable.
 */
public final class LayeredLayout {
    private LayeredLayout() { }

    /** Which way the layers run. */
    public enum Orientation {
        /** Layers stack downward — dispatch order reads top to bottom. */
        TOP_DOWN,
        /** Layers march right — dispatch order reads left to right. */
        LEFT_RIGHT
    }

    /**
     * Layout tuning. Defaults suit the node box the panel draws (a name and a type on two lines).
     *
     * @param nodeWidth   box width
     * @param nodeHeight  box height
     * @param siblingGap  minimum gap between neighbours on the same layer
     * @param layerGap    gap between layers
     * @param orientation which way layers run
     * @param sweeps      crossing-reduction sweeps; more is tidier and slower, 4 is ample at this scale
     */
    public record Config(double nodeWidth, double nodeHeight, double siblingGap, double layerGap,
                         Orientation orientation, int sweeps) {

        public static Config defaults() {
            return new Config(160, 48, 28, 72, Orientation.TOP_DOWN, 4);
        }

        /**
         * Width reserved for a bend point. A dummy is a place an edge passes through, not a box, so it
         * must not occupy a whole node's width — a graph with many long edges is otherwise spread to
         * absurdity by rows that are mostly bend points.
         */
        double dummyWidth() {
            return 8;
        }

        public Config withOrientation(Orientation o) {
            return new Config(nodeWidth, nodeHeight, siblingGap, layerGap, o, sweeps);
        }
    }

    public static TopologyLayout layout(ProcessorTopology topology) {
        return layout(topology, Config.defaults());
    }

    public static TopologyLayout layout(ProcessorTopology topology, Config config) {
        if (topology == null || topology.isEmpty()) return TopologyLayout.empty();
        return new Run(topology, config).execute();
    }

    // ---- one layout run -----------------------------------------------------------------------------

    /** Mutable working state for a single layout. Vertices are ints: reals first, then dummies. */
    private static final class Run {
        private final ProcessorTopology topology;
        private final Config config;

        private final List<String> ids;             // vertex index → node id (reals only)
        private final Map<String, Integer> index;   // node id → vertex index
        private final int realCount;

        /** Layout edges after cycle-breaking: {from, to, originalEdgeIndex, reversedFlag}. */
        private final List<int[]> edges = new ArrayList<>();
        private final List<ProcessorTopology.Edge> sourceEdges = new ArrayList<>();

        private int vertexCount;
        private int[] layerOf = new int[0];
        private List<List<Integer>> layers = new ArrayList<>();
        private double[] pos = new double[0];       // along-layer coordinate, pre-orientation
        private int[] orderInLayer = new int[0];

        /** originalEdgeIndex → the vertex chain (source … dummies … target) used to route it. */
        private final Map<Integer, List<Integer>> chains = new LinkedHashMap<>();

        Run(ProcessorTopology topology, Config config) {
            this.topology = topology;
            this.config = config;
            this.ids = new ArrayList<>(topology.ids());
            this.realCount = ids.size();
            this.index = new HashMap<>(realCount * 2);
            for (int i = 0; i < realCount; i++) index.put(ids.get(i), i);
            this.vertexCount = realCount;
        }

        TopologyLayout execute() {
            breakCycles();
            assignLayers();
            insertDummies();
            initialOrdering();
            reduceCrossings();
            assignCoordinates();
            return build();
        }

        // 1 — cycle breaking ------------------------------------------------------------------------

        /**
         * DFS, reversing any edge that closes a cycle. Self-loops are dropped: they carry no layering
         * information and the view can draw them as a decoration on the node.
         */
        private void breakCycles() {
            List<List<int[]>> out = adjacency();          // vertex → {target, originalEdgeIndex}
            byte[] state = new byte[realCount];           // 0 unvisited, 1 on stack, 2 done

            for (int start = 0; start < realCount; start++) {
                if (state[start] != 0) continue;
                Deque<int[]> stack = new ArrayDeque<>();  // {vertex, nextChildCursor}
                stack.push(new int[]{start, 0});
                state[start] = 1;
                while (!stack.isEmpty()) {
                    int[] frame = stack.peek();
                    int v = frame[0];
                    List<int[]> children = out.get(v);
                    if (frame[1] < children.size()) {
                        int[] child = children.get(frame[1]++);
                        int w = child[0];
                        if (state[w] == 1) {
                            edges.add(new int[]{w, v, child[1], 1});   // back edge → reverse it
                        } else {
                            edges.add(new int[]{v, w, child[1], 0});
                            if (state[w] == 0) {
                                state[w] = 1;
                                stack.push(new int[]{w, 0});
                            }
                        }
                    } else {
                        state[v] = 2;
                        stack.pop();
                    }
                }
            }
        }

        private List<List<int[]>> adjacency() {
            List<List<int[]>> out = new ArrayList<>(realCount);
            for (int i = 0; i < realCount; i++) out.add(new ArrayList<>());
            List<ProcessorTopology.Edge> all = topology.edges();
            for (int e = 0; e < all.size(); e++) {
                ProcessorTopology.Edge edge = all.get(e);
                Integer from = index.get(edge.source());
                Integer to = index.get(edge.target());
                if (from == null || to == null) continue;   // edge to a node the document never declared
                sourceEdges.add(edge);
                if (from.equals(to)) continue;              // self-loop: not a layering constraint
                out.get(from).add(new int[]{to, sourceEdges.size() - 1});
            }
            return out;
        }

        // 2 — layering ------------------------------------------------------------------------------

        /** Longest-path layering over the now-acyclic edge set, via Kahn's algorithm in vertex order. */
        private void assignLayers() {
            layerOf = new int[realCount];
            int[] indegree = new int[realCount];
            List<List<Integer>> succ = new ArrayList<>(realCount);
            for (int i = 0; i < realCount; i++) succ.add(new ArrayList<>());
            for (int[] e : edges) {
                succ.get(e[0]).add(e[1]);
                indegree[e[1]]++;
            }

            Deque<Integer> ready = new ArrayDeque<>();
            for (int v = 0; v < realCount; v++) {
                if (indegree[v] == 0) ready.addLast(v);
            }
            int settled = 0;
            while (!ready.isEmpty()) {
                int v = ready.pollFirst();
                settled++;
                for (int w : succ.get(v)) {
                    layerOf[w] = Math.max(layerOf[w], layerOf[v] + 1);
                    if (--indegree[w] == 0) ready.addLast(w);
                }
            }
            if (settled < realCount) {
                // defensive: cycle-breaking should make this impossible, but a wrong layering must not
                // become an infinite loop or a crash downstream
                for (int v = 0; v < realCount; v++) layerOf[v] = Math.max(layerOf[v], 0);
            }
        }

        // 3 — dummy vertices ------------------------------------------------------------------------

        private void insertDummies() {
            int maxLayer = 0;
            for (int l : layerOf) maxLayer = Math.max(maxLayer, l);

            List<Integer> dummyLayers = new ArrayList<>();
            for (int[] e : edges) {
                int from = e[0];
                int to = e[1];
                int span = layerOf[to] - layerOf[from];
                List<Integer> chain = new ArrayList<>();
                chain.add(from);
                for (int l = layerOf[from] + 1; l < layerOf[to]; l++) {
                    int dummy = realCount + dummyLayers.size();
                    dummyLayers.add(l);
                    chain.add(dummy);
                }
                chain.add(to);
                if (span > 1) chains.put(e[2], chain);
                else chains.put(e[2], List.of(from, to));
            }

            vertexCount = realCount + dummyLayers.size();
            int[] grown = Arrays.copyOf(layerOf, vertexCount);
            for (int i = 0; i < dummyLayers.size(); i++) grown[realCount + i] = dummyLayers.get(i);
            layerOf = grown;

            layers = new ArrayList<>();
            for (int l = 0; l <= maxLayer; l++) layers.add(new ArrayList<>());
        }

        // 4 — ordering ------------------------------------------------------------------------------

        private void initialOrdering() {
            for (int v = 0; v < vertexCount; v++) layers.get(layerOf[v]).add(v);
            reindex();
        }

        private void reindex() {
            orderInLayer = new int[vertexCount];
            for (List<Integer> layer : layers) {
                for (int i = 0; i < layer.size(); i++) orderInLayer[layer.get(i)] = i;
            }
        }

        /** Neighbour lists over the dummy-expanded graph, used by ordering and coordinates. */
        private List<List<Integer>> up, down;

        private void buildChainAdjacency() {
            up = new ArrayList<>(vertexCount);
            down = new ArrayList<>(vertexCount);
            for (int i = 0; i < vertexCount; i++) {
                up.add(new ArrayList<>());
                down.add(new ArrayList<>());
            }
            for (List<Integer> chain : chains.values()) {
                for (int i = 0; i + 1 < chain.size(); i++) {
                    int a = chain.get(i);
                    int b = chain.get(i + 1);
                    down.get(a).add(b);
                    up.get(b).add(a);
                }
            }
        }

        private void reduceCrossings() {
            buildChainAdjacency();
            int best = totalCrossings();
            List<List<Integer>> bestLayers = copyLayers();

            for (int sweep = 0; sweep < config.sweeps(); sweep++) {
                boolean downward = sweep % 2 == 0;
                medianSweep(downward);
                transpose();
                int crossings = totalCrossings();
                if (crossings < best) {
                    best = crossings;
                    bestLayers = copyLayers();
                }
            }
            layers = bestLayers;
            reindex();
        }

        private List<List<Integer>> copyLayers() {
            List<List<Integer>> copy = new ArrayList<>(layers.size());
            for (List<Integer> l : layers) copy.add(new ArrayList<>(l));
            return copy;
        }

        /** Order each layer by the median position of its neighbours in the layer just crossed. */
        private void medianSweep(boolean downward) {
            if (downward) {
                for (int l = 1; l < layers.size(); l++) orderByMedian(l, up);
            } else {
                for (int l = layers.size() - 2; l >= 0; l--) orderByMedian(l, down);
            }
        }

        /**
         * Reorder a layer by the median of each vertex's neighbours in the adjacent layer.
         *
         * <p>Vertices with no neighbours there have no median to sort by, and are <b>pinned to the slots
         * they already occupy</b> while the rest are sorted around them. Note the shape of this: it is
         * not a comparator that returns 0 for them. Doing that looks equivalent and is not — it makes the
         * ordering intransitive (a &lt; b, b == u, u == a) and {@code TimSort} throws
         * "Comparison method violates its general contract" on a graph with enough unanchored vertices.
         * A real 300-node graph does; small hand-written ones don't, which is exactly how such a bug
         * reaches production.
         */
        private void orderByMedian(int layerIndex, List<List<Integer>> neighbours) {
            List<Integer> layer = layers.get(layerIndex);
            Map<Integer, Double> median = new HashMap<>();
            List<Integer> anchored = new ArrayList<>();
            Map<Integer, Integer> pinned = new LinkedHashMap<>();   // slot → vertex

            for (int i = 0; i < layer.size(); i++) {
                int v = layer.get(i);
                List<Integer> ns = neighbours.get(v);
                if (ns.isEmpty()) {
                    pinned.put(i, v);
                    continue;
                }
                double[] positions = ns.stream().mapToDouble(n -> orderInLayer[n]).sorted().toArray();
                int mid = positions.length / 2;
                median.put(v, positions.length % 2 == 1
                        ? positions[mid]
                        : (positions[mid - 1] + positions[mid]) / 2);
                anchored.add(v);
            }

            anchored.sort((a, b) -> Double.compare(median.get(a), median.get(b)));   // total order

            List<Integer> reordered = new ArrayList<>(layer.size());
            int next = 0;
            for (int slot = 0; slot < layer.size(); slot++) {
                Integer pin = pinned.get(slot);
                reordered.add(pin != null ? pin : anchored.get(next++));
            }
            layers.set(layerIndex, reordered);
            reindex();
        }

        /**
         * Swap adjacent pairs while that strictly reduces crossings.
         *
         * <p>Only the two swapped vertices' own edges can change the count, so each candidate is scored
         * locally in {@code O(deg(v)·deg(w))} rather than by recounting the layer. Recounting is the
         * obvious implementation and is what made a 300-node graph take seconds.
         */
        private void transpose() {
            boolean improved = true;
            int guard = 0;
            while (improved && guard++ < 4) {
                improved = false;
                for (List<Integer> layer : layers) {
                    for (int i = 0; i + 1 < layer.size(); i++) {
                        int v = layer.get(i);
                        int w = layer.get(i + 1);
                        int asIs = pairCrossings(v, w, up) + pairCrossings(v, w, down);
                        int swapped = pairCrossings(w, v, up) + pairCrossings(w, v, down);
                        if (swapped < asIs) {
                            layer.set(i, w);
                            layer.set(i + 1, v);
                            orderInLayer[w] = i;
                            orderInLayer[v] = i + 1;
                            improved = true;
                        }
                    }
                }
            }
        }

        /** Crossings contributed by {@code left}'s and {@code right}'s edges when left precedes right. */
        private int pairCrossings(int left, int right, List<List<Integer>> neighbours) {
            List<Integer> ln = neighbours.get(left);
            List<Integer> rn = neighbours.get(right);
            int crossings = 0;
            for (int a : ln) {
                for (int b : rn) {
                    if (orderInLayer[a] > orderInLayer[b]) crossings++;
                }
            }
            return crossings;
        }

        private int totalCrossings() {
            int total = 0;
            for (int l = 0; l + 1 < layers.size(); l++) total += crossingsBetween(l);
            return total;
        }

        /**
         * Crossings between layer {@code l} and {@code l+1}, counted as inversions: walk the edges in
         * source order and count how many already-seen targets sit to the right of each new one. A
         * Fenwick tree makes that {@code O(E log n)} instead of the naive pairwise {@code O(E²)}.
         */
        private int crossingsBetween(int l) {
            int belowSize = layers.get(l + 1).size();
            if (belowSize == 0) return 0;
            List<Integer> targets = new ArrayList<>();
            for (int v : layers.get(l)) {
                List<Integer> ends = new ArrayList<>();
                for (int w : down.get(v)) {
                    if (layerOf[w] == l + 1) ends.add(orderInLayer[w]);
                }
                ends.sort(null);
                targets.addAll(ends);
            }
            int[] tree = new int[belowSize + 1];
            int crossings = 0;
            int seen = 0;
            for (int t : targets) {
                crossings += seen - prefixSum(tree, t + 1);   // already-seen targets strictly to the right
                add(tree, t + 1, belowSize);
                seen++;
            }
            return crossings;
        }

        private static void add(int[] tree, int i, int size) {
            for (; i <= size; i += i & -i) tree[i]++;
        }

        private static int prefixSum(int[] tree, int i) {
            int sum = 0;
            for (; i > 0; i -= i & -i) sum += tree[i];
            return sum;
        }

        // 5 — coordinates ---------------------------------------------------------------------------

        /**
         * Straighten by pulling each vertex toward the median of its neighbours, then push apart to
         * restore minimum separation. Alternating direction converges quickly and, unlike a full
         * Brandes–Köpf, is short enough to read.
         */
        private void assignCoordinates() {
            pos = new double[vertexCount];      // pos[v] is the vertex CENTRE along its layer
            for (List<Integer> layer : layers) {
                double cursor = 0;
                for (int i = 0; i < layer.size(); i++) {
                    int v = layer.get(i);
                    if (i > 0) cursor += separation(layer.get(i - 1), v);
                    pos[v] = cursor;
                }
            }

            for (int pass = 0; pass < 4; pass++) {
                boolean downward = pass % 2 == 0;
                if (downward) {
                    for (int l = 1; l < layers.size(); l++) alignLayer(l, up);
                } else {
                    for (int l = layers.size() - 2; l >= 0; l--) alignLayer(l, down);
                }
            }
            normalise();
        }

        /** Half-widths plus the gap — so a bend point costs 8px of room, not a whole node. */
        private double separation(int a, int b) {
            return (extentOf(a) + extentOf(b)) / 2 + config.siblingGap();
        }

        private double extentOf(int v) {
            return v < realCount ? config.nodeWidth() : config.dummyWidth();
        }

        private void alignLayer(int layerIndex, List<List<Integer>> neighbours) {
            List<Integer> layer = layers.get(layerIndex);
            double[] desired = new double[layer.size()];
            for (int i = 0; i < layer.size(); i++) {
                int v = layer.get(i);
                List<Integer> ns = neighbours.get(v);
                if (ns.isEmpty()) {
                    desired[i] = pos[v];
                } else {
                    double[] xs = ns.stream().mapToDouble(n -> pos[n]).sorted().toArray();
                    int mid = xs.length / 2;
                    desired[i] = xs.length % 2 == 1 ? xs[mid] : (xs[mid - 1] + xs[mid]) / 2;
                }
            }
            // left to right, then right to left, so the layer is not dragged toward one end
            for (int i = 0; i < layer.size(); i++) {
                double min = i == 0
                        ? Double.NEGATIVE_INFINITY
                        : pos[layer.get(i - 1)] + separation(layer.get(i - 1), layer.get(i));
                pos[layer.get(i)] = Math.max(desired[i], min);
            }
            for (int i = layer.size() - 2; i >= 0; i--) {
                double max = pos[layer.get(i + 1)] - separation(layer.get(i), layer.get(i + 1));
                pos[layer.get(i)] = Math.min(pos[layer.get(i)], max);
            }
        }

        /** Shift so the leftmost node's left edge sits at 0 — the view owns pan, not the layout. */
        private void normalise() {
            double min = Double.POSITIVE_INFINITY;
            for (int v = 0; v < vertexCount; v++) min = Math.min(min, pos[v] - extentOf(v) / 2);
            if (min == Double.POSITIVE_INFINITY) min = 0;
            for (int v = 0; v < vertexCount; v++) pos[v] -= min;
        }

        // 6 — build ---------------------------------------------------------------------------------

        private TopologyLayout build() {
            boolean topDown = config.orientation() == Orientation.TOP_DOWN;
            double layerStep = (topDown ? config.nodeHeight() : config.nodeWidth()) + config.layerGap();

            Map<String, TopologyLayout.NodeBox> boxes = new LinkedHashMap<>();
            double maxX = 0;
            double maxY = 0;
            for (int v = 0; v < realCount; v++) {
                double along = pos[v] - config.nodeWidth() / 2;      // centre → left edge
                double across = layerOf[v] * layerStep;
                double x = topDown ? along : across;
                double y = topDown ? across : along;
                boxes.put(ids.get(v), new TopologyLayout.NodeBox(
                        ids.get(v), x, y, config.nodeWidth(), config.nodeHeight(),
                        layerOf[v], orderInLayer[v]));
                maxX = Math.max(maxX, x + config.nodeWidth());
                maxY = Math.max(maxY, y + config.nodeHeight());
            }

            List<TopologyLayout.EdgePath> paths = new ArrayList<>();
            for (Map.Entry<Integer, List<Integer>> entry : chains.entrySet()) {
                ProcessorTopology.Edge edge = sourceEdges.get(entry.getKey());
                boolean reversed = isReversed(entry.getKey());
                List<Integer> chain = entry.getValue();
                List<TopologyLayout.Point> points = new ArrayList<>(chain.size());
                for (int v : chain) points.add(centreOf(v, topDown, layerStep));
                // the chain runs source→target in layout space; flip it back for a reversed edge so the
                // path always reads in the graph's own direction
                if (reversed) java.util.Collections.reverse(points);
                paths.add(new TopologyLayout.EdgePath(
                        edge.id(), edge.source(), edge.target(), List.copyOf(points), reversed));
            }
            return new TopologyLayout(boxes, paths, layers.size(), maxX, maxY);
        }

        private boolean isReversed(int originalEdgeIndex) {
            for (int[] e : edges) {
                if (e[2] == originalEdgeIndex) return e[3] == 1;
            }
            return false;
        }

        /** Centre of a vertex — real or bend point — so an edge meets a box at its middle. */
        private TopologyLayout.Point centreOf(int v, boolean topDown, double layerStep) {
            double along = pos[v];
            double across = layerOf[v] * layerStep
                            + (topDown ? config.nodeHeight() : config.nodeWidth()) / 2;
            return topDown
                    ? new TopologyLayout.Point(along, across)
                    : new TopologyLayout.Point(across, along);
        }
    }
}
