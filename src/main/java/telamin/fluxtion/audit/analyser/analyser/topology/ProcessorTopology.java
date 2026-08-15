package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The processor's node graph, parsed from the GraphML Fluxtion emits at build time (M21.1,
 * spec-graph-replay §2). The static half of the picture the audit log animates: the log says which nodes
 * fired in a cycle, this says how they are wired.
 *
 * <p>The join key is the <b>node id</b>, which is the same {@code instanceId} that appears in a record's
 * {@code nodeLogs}. That correspondence is what makes step-through (M21.4) a lookup rather than an
 * inference — and what {@link #match(Collection)} checks, because a topology from a different build than
 * the log misleads silently.
 *
 * <p>Immutable once built. Insertion order is preserved throughout so rendering and tests are
 * deterministic.
 */
public final class ProcessorTopology {

    /** What a node is, from the GraphML {@code Style properties} attribute. */
    public enum Kind {
        /** An ordinary compute node. */
        NODE,
        /** An event class entering the graph. */
        EVENT,
        /** A node that handles an inbound event. */
        EVENT_HANDLER,
        /** A service the processor exports. */
        EXPORT_SERVICE,
        /** Present in the graph but unstyled or a style we don't know. */
        UNKNOWN;

        /** Map a GraphML {@code properties="…"} value; unknown or missing → {@link #UNKNOWN}. */
        public static Kind fromStyle(String style) {
            if (style == null) return UNKNOWN;
            return switch (style.trim().toUpperCase()) {
                case "NODE" -> NODE;
                case "EVENT" -> EVENT;
                case "EVENTHANDLER" -> EVENT_HANDLER;
                case "EXPORTSERVICE" -> EXPORT_SERVICE;
                default -> UNKNOWN;
            };
        }
    }

    /**
     * One node. {@code id} is the {@code instanceId} seen in {@code nodeLogs}; {@code className} is the
     * fully-qualified type, which is what {@code SourceNavigation} needs to open the source.
     */
    public record Node(String id, String label, String className, Kind kind) {

        /** The class name without package or outer classes — {@code Foo$Bar} → {@code Bar}. */
        public String simpleName() {
            if (className == null || className.isBlank()) return id;
            String tail = className.substring(className.lastIndexOf('.') + 1);
            int inner = tail.lastIndexOf('$');
            return inner >= 0 ? tail.substring(inner + 1) : tail;
        }
    }

    /** A directed dependency: {@code source} feeds {@code target}. */
    public record Edge(String id, String source, String target) { }

    /**
     * How well a topology matches a log. A partial match usually means the GraphML came from a different
     * build than the log — the failure mode this whole record exists to make visible.
     */
    public record Match(Set<String> matched, Set<String> unknownToTopology, Set<String> notInLog) {

        /** Fraction of the log's instanceIds this topology knows, 0..1. Vacuously 1 for an empty log. */
        public double coverage() {
            int seen = matched.size() + unknownToTopology.size();
            return seen == 0 ? 1.0 : (double) matched.size() / seen;
        }

        /** True when every instanceId in the log exists in the topology. */
        public boolean complete() {
            return unknownToTopology.isEmpty();
        }

        /** A short line for the UI — states the mismatch rather than hiding it. */
        public String describe() {
            if (complete()) {
                return notInLog.isEmpty()
                        ? "topology matches the log (" + matched.size() + " nodes)"
                        : matched.size() + " of " + (matched.size() + notInLog.size())
                          + " nodes appear in this log";
            }
            return "topology may be from a different build — " + unknownToTopology.size()
                   + " node(s) in the log are not in the graph: " + preview(unknownToTopology);
        }

        private static String preview(Set<String> ids) {
            List<String> shown = ids.stream().limit(3).toList();
            return String.join(", ", shown) + (ids.size() > shown.size() ? ", …" : "");
        }
    }

    private final Map<String, Node> nodes;              // id → node, insertion-ordered
    private final List<Edge> edges;
    private final Map<String, Set<String>> children;    // id → ids it feeds
    private final Map<String, Set<String>> parents;     // id → ids that feed it

    ProcessorTopology(Map<String, Node> nodes, List<Edge> edges) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = List.copyOf(edges);

        Map<String, Set<String>> kids = new LinkedHashMap<>();
        Map<String, Set<String>> dads = new LinkedHashMap<>();
        for (Edge e : this.edges) {
            kids.computeIfAbsent(e.source(), k -> new LinkedHashSet<>()).add(e.target());
            dads.computeIfAbsent(e.target(), k -> new LinkedHashSet<>()).add(e.source());
        }
        this.children = Collections.unmodifiableMap(kids);
        this.parents = Collections.unmodifiableMap(dads);
    }

    /** A topology with nothing in it — what a failed or absent parse yields, so callers never see null. */
    public static ProcessorTopology empty() {
        return new ProcessorTopology(Map.of(), List.of());
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    /** All nodes, in GraphML document order. */
    public Collection<Node> nodes() {
        return nodes.values();
    }

    public List<Edge> edges() {
        return edges;
    }

    public Set<String> ids() {
        return nodes.keySet();
    }

    /** The node with this {@code instanceId}, or {@code null}. */
    public Node node(String id) {
        return id == null ? null : nodes.get(id);
    }

    public boolean contains(String id) {
        return id != null && nodes.containsKey(id);
    }

    /** Ids this node feeds. */
    public Set<String> childrenOf(String id) {
        return children.getOrDefault(id, Set.of());
    }

    /** Ids that feed this node. */
    public Set<String> parentsOf(String id) {
        return parents.getOrDefault(id, Set.of());
    }

    /** Nodes nothing feeds — the graph's entry points. */
    public List<Node> roots() {
        List<Node> roots = new ArrayList<>();
        for (Node n : nodes.values()) {
            if (parentsOf(n.id()).isEmpty()) roots.add(n);
        }
        return roots;
    }

    /**
     * Compare this topology against the {@code instanceId}s a log actually exhibits (M21.1's pair-check).
     * Sets come back sorted so the UI and tests read the same way every time.
     */
    public Match match(Collection<String> logInstanceIds) {
        Set<String> matched = new TreeSet<>();
        Set<String> unknown = new TreeSet<>();
        if (logInstanceIds != null) {
            for (String id : logInstanceIds) {
                if (id == null) continue;
                (nodes.containsKey(id) ? matched : unknown).add(id);
            }
        }
        Set<String> notInLog = new TreeSet<>(nodes.keySet());
        notInLog.removeAll(matched);
        return new Match(matched, unknown, notInLog);
    }
}
