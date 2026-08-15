package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
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
        /**
         * A service interface the processor exports. <b>An entry point, not an output</b>: an external
         * caller invokes the interface and dispatch flows from here into the graph, the same way an event
         * does. Fluxtion emits these with no inbound edges, like event nodes.
         */
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

    /**
     * What the log lets you say about a node in one cycle.
     *
     * <p>The distinction this exists to protect: <b>an absent audit entry does not mean the node did not
     * run.</b> A node only appears in {@code nodeLogs} if it actually writes audit output, and whether it
     * does depends on the node and on the audit level in force. Colouring "no entry" as "did not execute"
     * would invent a fact the log never stated — and would do it in the one place a user is trying to
     * work out what ran.
     */
    public enum Execution {
        /** Wrote audit output this cycle. The only state the log gives directly. */
        LOGGED,
        /**
         * Silent, but something downstream of it logged — so dispatch must have passed through here.
         * Executed; simply produced no audit output.
         */
        RAN_SILENTLY,
        /**
         * Silent, and downstream of something that logged. Dispatch may have reached it, or may have
         * stopped short — the log does not say. <b>Unknown, not "no".</b>
         */
        MAY_HAVE_RUN,
        /** Not connected to anything that logged: no reason to think this event's dispatch came near it. */
        OFF_PATH
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
     * Classify every node by what this cycle's audit entries let you claim about it.
     *
     * <p>Only {@link Execution#LOGGED} is observed. The rest are inferences from the wiring, kept
     * deliberately separate so the UI can show them as different claims:
     * <ul>
     *   <li>{@link Execution#RAN_SILENTLY} — <b>forced</b>: the node is the <em>only</em> parent of
     *       something that ran, so dispatch had no other way in;</li>
     *   <li>{@link Execution#MAY_HAVE_RUN} — connected to something that logged, upstream or down, but
     *       not forced. A genuine unknown;</li>
     *   <li>{@link Execution#OFF_PATH} — not connected to anything that logged.</li>
     * </ul>
     *
     * <p><b>Why "only parent" and not "any ancestor".</b> A node with several parents needs just one of
     * them to have triggered it, so its other ancestors may never have run. Marking every ancestor as
     * certain would manufacture evidence — the same over-claiming this classification exists to prevent,
     * pointed the other way.
     */
    public Map<String, Execution> classifyCycle(Collection<String> loggedIds) {
        return classifyCycle(loggedIds, null);
    }

    /**
     * As {@link #classifyCycle(Collection)}, but told where the cycle <b>entered</b> the graph
     * (see {@link EntryPointResolver}).
     *
     * <p>This matters for the case the log is worst at. A branch that executed but logged nothing at all
     * is invisible to reasoning that starts from logged nodes — it comes out {@link Execution#OFF_PATH},
     * which reads as "the event never went near it". Given the entry point, everything reachable from it
     * is on the path dispatch <em>could</em> have taken, so those nodes are reported as
     * {@link Execution#MAY_HAVE_RUN}: unknown, which is the truth, rather than excluded.
     */
    public Map<String, Execution> classifyCycle(Collection<String> loggedIds, Collection<String> entryIds) {
        Map<String, Execution> out = new LinkedHashMap<>();
        Set<String> logged = new LinkedHashSet<>();
        if (loggedIds != null) {
            for (String id : loggedIds) {
                if (id != null && nodes.containsKey(id)) logged.add(id);
            }
        }
        Set<String> entries = new LinkedHashSet<>();
        if (entryIds != null) {
            for (String id : entryIds) {
                if (id != null && nodes.containsKey(id)) entries.add(id);
            }
        }

        // fixpoint: anything that is the sole parent of a node known to have run, also ran
        Set<String> ran = new LinkedHashSet<>(logged);
        Deque<String> queue = new ArrayDeque<>(logged);
        while (!queue.isEmpty()) {
            Set<String> feeders = parentsOf(queue.poll());
            if (feeders.size() != 1) continue;             // more than one way in → nothing is forced
            String only = feeders.iterator().next();
            if (ran.add(only)) queue.add(only);
        }

        // The predicted path: everything dispatch could have reached from where the cycle came in.
        // When it is known and consistent with the evidence it is AUTHORITATIVE — a node the event
        // could not reach did not run, however it happens to be wired to something that logged.
        Set<String> predicted = new LinkedHashSet<>(entries);
        predicted.addAll(reach(entries, true));
        boolean trustPredicted = !entries.isEmpty() && predicted.containsAll(logged);

        Set<String> connected;
        if (trustPredicted) {
            connected = predicted;
        } else {
            // no entry point, or one that contradicts the log (a resolution miss, or a build mismatch) —
            // fall back to reasoning outward from what actually logged
            connected = reach(logged, false);
            connected.addAll(reach(logged, true));
            connected.addAll(predicted);
        }

        for (String id : nodes.keySet()) {
            Execution state = logged.contains(id) ? Execution.LOGGED
                    : ran.contains(id) ? Execution.RAN_SILENTLY
                    : connected.contains(id) ? Execution.MAY_HAVE_RUN
                    : Execution.OFF_PATH;
            out.put(id, state);
        }
        return out;
    }

    /** Everything reachable from {@code seeds} following edges forward ({@code down}) or backward. */
    private Set<String> reach(Set<String> seeds, boolean down) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            for (String next : down ? childrenOf(id) : parentsOf(id)) {
                if (seen.add(next)) queue.add(next);
            }
        }
        seen.removeAll(seeds);
        return seen;
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
