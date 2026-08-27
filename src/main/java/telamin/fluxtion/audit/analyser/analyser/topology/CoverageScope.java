package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which nodes belong in coverage's DENOMINATOR — the things that could have written audit output, as
 * against the things that appear in a graph.
 *
 * <p><b>Measured before it was written</b> (2026-08-27). `coverage` on the shipped demo — the fixture on
 * the docs site — reported {@code declared 10 · covered 5 · uncovered 5 · ratio 0.5}, and named its five
 * uncovered nodes:
 *
 * <pre>
 *   QuoteControl        com.acme.demo.api.QuoteControl     an EXPORTED SERVICE interface
 *   spreadCalculator    Nodes$SpreadCalculator             a node deliberately not an EventLogNode
 *   RiskBreachEvent     Events$RiskBreachEvent             an EVENT class
 *   OrderUpdateEvent    Events$OrderUpdateEvent            an EVENT class
 *   MarketDataEvent     Events$MarketDataEvent             an EVENT class
 * </pre>
 *
 * Every one of them can never write audit output. Three are event CLASSES — data entering the graph, not
 * code that runs — and one is a service interface. So "50% coverage" was really "100% of what can log,
 * logged", and the tool was wrong in the ALARMING direction about its own showcase fixture, on the number
 * a support engineer reads first.
 *
 * <p><b>An event class in a "which nodes never ran" answer is a category error, not a low score.</b> That
 * is what this class fixes, and it fixes it from the graph alone — no source, no new evidence.
 *
 * <p><b>What it deliberately does NOT do.</b> A node that IS a node and still cannot log — the demo's
 * {@code spreadCalculator}, an ordinary class rather than an {@code EventLogNode} — stays in the
 * denominator here. Deciding that needs the class, which needs source resolution, which fails in its own
 * way when source is missing (M40.2 slice 2). Counting it is the honest answer meanwhile: it IS a node
 * and it DID never log.
 *
 * <p><b>Nothing leaves the denominator silently.</b> {@link Scope#excluded} carries every id dropped and
 * why, and the caller is expected to report it. A denominator that quietly shrinks is the same dishonesty
 * as one that quietly includes — pointing the other way, and harder to notice because the number improves.
 */
public final class CoverageScope {

    private CoverageScope() {
    }

    /**
     * @param loggable the ids coverage should score — nodes that could have written audit output
     * @param excluded id → why it was left out, in graph order; never empty-but-unreported
     */
    public record Scope(Set<String> loggable, Map<String, String> excluded) {

        public Scope {
            // NOT Set.copyOf/Map.copyOf: both return unordered immutables, so the graph order this
            // record's javadoc promises would be discarded at its own boundary — which is exactly what
            // happened, and what the live echo showed (review N2). A documented order nobody delivers
            // is worse than none, because callers rely on it.
            loggable = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(loggable));
            excluded = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(excluded));
        }

        /**
         * The one-line statement a caller owes the reader when anything was dropped.
         *
         * <p>M40.2b WILL BREAK THIS COUNT (review N3): it derives "services" as everything that is not
         * an event class, which is true while there are exactly two reasons and false the moment a
         * third — "silent by construction" — arrives. Bucket by REASON then, not by string containment
         * on the reason text.
         */
        public String note() {
            if (excluded.isEmpty()) return null;
            long events = excluded.values().stream().filter(v -> v.contains("event class")).count();
            long services = excluded.size() - events;
            StringBuilder sb = new StringBuilder("excluded ").append(excluded.size())
                    .append(" declared item(s) that can never write audit output: ");
            if (events > 0) sb.append(events).append(" event class(es)");
            if (events > 0 && services > 0) sb.append(", ");
            if (services > 0) sb.append(services).append(" exported service(s)");
            sb.append(". They appear in the graph because the processor handles them, not because they "
                    + "run — counting them as 'never logged' would report a category error as a low score.");
            return sb.toString();
        }
    }

    /**
     * Narrow {@code authored} to what could log, using the node KIND the graph already declares.
     *
     * @param topology the full graph
     * @param authored the ids already filtered of framework scaffolding ({@link Scaffolding#authoredNodes})
     */
    public static Scope of(ProcessorTopology topology, Set<String> authored) {
        Set<String> loggable = new LinkedHashSet<>();
        Map<String, String> excluded = new LinkedHashMap<>();
        if (authored == null) return new Scope(loggable, excluded);

        // Walk the GRAPH's own order, keeping only ids in `authored` — `authored` itself arrives as an
        // unordered set, so iterating it would produce whatever order the hash gave us. The graph's node
        // map is insertion-ordered, and that is the order a reader can follow in the Topology tab.
        // Anything authored but absent from the graph still has to be scored, so it follows, in its own
        // stable order rather than being dropped.
        java.util.List<String> inGraphOrder = new java.util.ArrayList<>();
        java.util.Set<String> seen = new LinkedHashSet<>();
        if (topology != null) {
            for (ProcessorTopology.Node n : topology.nodes()) {
                if (authored.contains(n.id()) && seen.add(n.id())) inGraphOrder.add(n.id());
            }
        }
        for (String id : new java.util.TreeSet<>(authored)) if (seen.add(id)) inGraphOrder.add(id);

        for (String id : inGraphOrder) {
            ProcessorTopology.Node node = topology == null ? null : topology.node(id);
            ProcessorTopology.Kind kind = node == null ? null : node.kind();
            if (kind == ProcessorTopology.Kind.EVENT) {
                excluded.put(id, "an event class — data entering the graph, not code that runs");
            } else if (kind == ProcessorTopology.Kind.EXPORT_SERVICE) {
                excluded.put(id, "an exported service interface — an entry point, not a node that logs");
            } else {
                // NODE, EVENT_HANDLER, UNKNOWN: a node, or something we cannot rule out. Both stay —
                // dropping an UNKNOWN would be assuming silence, which flatters the score.
                loggable.add(id);
            }
        }
        return new Scope(loggable, excluded);
    }
}
