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

    /** Why an id left the denominator. Bucketed by REASON, never by matching on the reason TEXT. */
    public enum Reason {
        EVENT("an event class — data entering the graph, not code that runs", "event class(es)"),
        EXPORT_SERVICE("an exported service interface — an entry point, not a node that logs",
                "exported service(s)"),
        SILENT_BY_CONSTRUCTION("this class cannot reach an audit logger at all — it declares no "
                + "supertype, so it has no auditLog to write with", "node(s) that cannot log at all");

        public final String why;
        final String plural;

        Reason(String why, String plural) {
            this.why = why;
            this.plural = plural;
        }
    }

    /**
     * @param loggable the ids coverage should score — nodes that could have written audit output
     * @param excluded id → why it was left out, in graph order; never empty-but-unreported
     * @param reasons  id → the same verdict as DATA, so callers bucket on the enum rather than the prose
     */
    public record Scope(Set<String> loggable, Map<String, String> excluded, Map<String, Reason> reasons) {

        public Scope {
            // NOT Set.copyOf/Map.copyOf: both return unordered immutables, so the graph order this
            // record's javadoc promises would be discarded at its own boundary — which is exactly what
            // happened, and what the live echo showed (review N2). A documented order nobody delivers
            // is worse than none, because callers rely on it.
            loggable = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(loggable));
            excluded = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(excluded));
            reasons = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(reasons));
        }

        /** The ids dropped for one reason, in graph order. */
        public java.util.List<String> excludedFor(Reason r) {
            return reasons.entrySet().stream().filter(e -> e.getValue() == r)
                    .map(Map.Entry::getKey).toList();
        }

        /**
         * The one-line statement a caller owes the reader when anything was dropped.
         *
         * <p>Counted by {@link Reason}, not by string containment on the prose (review N3 predicted the
         * exact break: the old code derived "services" as everything-not-an-event, which was true with
         * two reasons and wrong the moment M40.2b added a third).
         */
        public String note() {
            if (excluded.isEmpty()) return null;
            StringBuilder sb = new StringBuilder("excluded ").append(excluded.size())
                    .append(" declared item(s) that can never write audit output: ");
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (Reason r : Reason.values()) {
                int n = excludedFor(r).size();
                if (n > 0) parts.add(n + " " + r.plural);
            }
            sb.append(String.join(", ", parts));
            sb.append(". They appear in the graph because the processor handles them, not because they "
                    + "run — counting them as 'never logged' would report a category error as a low score.");

            // A node that CANNOT log is not the same reassurance as one that merely is not scored: its
            // execution is unobservable in ANY audit log, so the ratio is silent about it rather than
            // vouching for it. Say that, or the improved number reads as better news than it is.
            java.util.List<String> silent = excludedFor(Reason.SILENT_BY_CONSTRUCTION);
            if (!silent.isEmpty()) {
                sb.append(" Note that ").append(String.join(", ", silent))
                        .append(silent.size() == 1 ? " cannot write audit output at all, so this ratio "
                                + "says nothing about whether it ran — it is not observable in any log, "
                                + "not merely absent from this one."
                                : " cannot write audit output at all, so this ratio says nothing about "
                                + "whether they ran — they are not observable in any log, not merely "
                                + "absent from this one.");
            }
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
        return of(topology, authored, null);
    }

    /**
     * As above, plus M40.2b: a node whose class cannot reach an audit logger is silent by construction,
     * so its coverage gap is not evidence of anything.
     *
     * @param source fqn → source text ({@code SourceService::sourceForFqn}), or null when no source is
     *               configured. Without it every node stays counted — the safe direction, and the
     *               reason this parameter is optional rather than required.
     */
    public static Scope of(ProcessorTopology topology, Set<String> authored,
                           java.util.function.Function<String, java.util.Optional<String>> source) {
        Set<String> loggable = new LinkedHashSet<>();
        Map<String, String> excluded = new LinkedHashMap<>();
        Map<String, Reason> reasons = new LinkedHashMap<>();
        if (authored == null) return new Scope(loggable, excluded, reasons);

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
                drop(excluded, reasons, id, Reason.EVENT);
            } else if (kind == ProcessorTopology.Kind.EXPORT_SERVICE) {
                drop(excluded, reasons, id, Reason.EXPORT_SERVICE);
            } else if (node != null && silentByConstruction(node, topology, source)) {
                // M40.2b — proven, not assumed. Two ways to prove it now, and neither is a guess:
                // the graph DECLARED the node cannot log (M45, fluxtion.auditCapable=false), or the
                // source is in hand and the class declares no supertype so there is nowhere an
                // auditLog could come from. UNKNOWN never lands here.
                drop(excluded, reasons, id, Reason.SILENT_BY_CONSTRUCTION);
            } else {
                // NODE, EVENT_HANDLER, UNKNOWN: a node, or something we cannot rule out. Both stay —
                // dropping an UNKNOWN would be assuming silence, which flatters the score.
                loggable.add(id);
            }
        }
        return new Scope(loggable, excluded, reasons);
    }

    /**
     * Is this node provably unable to log? M45 D-V2: ask the graph first, fall back to the source.
     *
     * <p>The fallback is why this is a method rather than a swap. A graph that predates the vocabulary
     * — which is most of them, permanently, because the file is written by whichever builder its
     * author pinned — has nothing to declare, and the source check is still the only evidence
     * available. The vocabulary DEMOTES the heuristic; it does not retire it.
     *
     * <p>The gain is real even so: the source check fails closed to UNKNOWN whenever the source is
     * missing, and missing source is the normal case for someone else's log. A declared answer needs
     * no source at all.
     */
    private static boolean silentByConstruction(ProcessorTopology.Node node, ProcessorTopology topology,
                                                java.util.function.Function<String, java.util.Optional<String>> source) {
        GraphVocabulary vocabulary = topology == null ? GraphVocabulary.none() : topology.vocabulary();
        if (vocabulary.trustedForNodeFacts()) {
            NodeLogging.Answer declared = NodeLogging.of(node, vocabulary, source);
            if (declared.basis() == NodeLogging.Basis.DECLARED) {
                return declared.capability() == NodeLogging.Capability.SILENT_BY_CONSTRUCTION;
            }
        }
        return source != null
                && NodeLogging.of(node.className(), source) == NodeLogging.Capability.SILENT_BY_CONSTRUCTION;
    }

    private static void drop(Map<String, String> excluded, Map<String, Reason> reasons,
                             String id, Reason r) {
        excluded.put(id, r.why);
        reasons.put(id, r);
    }
}
