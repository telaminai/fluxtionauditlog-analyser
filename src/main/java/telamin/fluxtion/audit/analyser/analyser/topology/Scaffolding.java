package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells the processor's own plumbing apart from the graph you wrote (M22.1).
 *
 * <p>Fluxtion wires a fixed set of infrastructure nodes into every processor — a clock, the context, the
 * name auditor, the callback dispatcher, the subscription manager, the service registry, the event
 * logger — plus their control events. In the demo graph that is <b>10 of 16 nodes</b>, so a user opening
 * their own processor sees roughly a third signal and two thirds machinery.
 *
 * <p><b>Ask the graph first (M68.1 / M45.4, D-E10).</b> A graph at a supported vocabulary declares
 * {@code fluxtion.framework} on each node vertex. That declaration is the answer; the list below is a
 * <em>fallback</em>, used only when the fact is absent, holds a value other than {@code true}/{@code false},
 * or the file's vocabulary is not trusted for node facts. This class used to say "there is no marker in the
 * GraphML to distinguish them" — true when written, false since the compiler began emitting the key, and the
 * reason a held-out client was told on 2026-09-24 that a declared node was absent from a graph that declares
 * it: the node's class was {@code com.telamin.fluxtion.runtime.output.SinkPublisher}, a framework class the
 * developer used as a node and named, so the prefix guess hid it while the graph said {@code framework=false}.
 *
 * <p><b>Why the version gate is not the whole argument.</b> {@link GraphVocabulary#trustedForNodeFacts()} is
 * a presence and major-version check; it validates no value and names no authority. The basis for believing
 * this particular key is the one the vocabulary spec's D-V2 amendment requires — a named producer authority —
 * which upstream supplied on 2026-09-01 and which was verified the same day against a real session graph on
 * released 1.0.65 (completed tracker, M45.4). That verification measured <em>one</em> graph; it is evidence,
 * not proof that a producer cannot be wrong, and the same adoption report records that registration windows
 * remain. So every answer carries its {@link Basis}, and nothing inferred is ever presented as declared.
 *
 * <p><b>The fact is NODE-scoped.</b> It is read for compute nodes, event handlers and unstyled vertices, and
 * deliberately ignored for {@code EVENT} and {@code EXPORT_SERVICE} vertices: applied to those it would
 * understate coverage, and {@link CoverageScope} already drops them by kind with named reasons. Their answer
 * is therefore always the fallback below — which is why {@link EntryPointResolver}, which only ever asks about
 * exported services, is unaffected by this change.
 *
 * <p>The fallback copes with the two label shapes real GraphML produces, because the compiler emits both: a
 * package-qualified {@code class:} ({@code com.telamin.fluxtion.runtime.time.Clock}) and a bare simple name
 * ({@code Clock}). Event nodes often carry <b>no class at all</b>, so those are matched by id. Deliberately a
 * list rather than a rule: wrongly showing plumbing is a much smaller harm than wrongly hiding a node someone
 * is looking for.
 */
public final class Scaffolding {
    private Scaffolding() { }

    /** Package prefixes that are always framework-owned. */
    private static final List<String> FRAMEWORK_PACKAGES = List.of(
            "com.telamin.fluxtion.runtime.",
            "com.fluxtion.runtime.",
            "com.telamin.mongoose.");

    /** Simple names the compiler emits unqualified for the standard plumbing. */
    private static final Set<String> FRAMEWORK_TYPES = Set.of(
            "MutableEventProcessorContext", "MutableDataFlowContext",
            "Clock", "NodeNameAuditor", "CallbackDispatcherImpl",
            "SubscriptionManagerNode", "ServiceRegistryNode", "EventLogManager",
            "ServiceListener", "ExportFunctionAuditEvent");

    /** Control/lifecycle events, which usually arrive with no {@code class:} at all. */
    private static final Set<String> FRAMEWORK_EVENT_IDS = Set.of(
            "ClockStrategyEvent", "EventLogControlEvent", "ServiceListener",
            "SinkRegistration", "SinkDeregister", "LifecycleEvent");

    /** The graph key a supported producer declares on each node vertex. */
    public static final String FRAMEWORK_FACT = "fluxtion.framework";

    /** How an authorship answer was established. */
    public enum Basis {
        /** The graph said so, under a vocabulary trusted for node facts. */
        DECLARED,
        /** The graph did not say (or could not be believed), so the class-name list decided. */
        INFERRED
    }

    /** Whether a node is framework plumbing, and how that was established. */
    public record Authorship(boolean framework, Basis basis) {
        public String because() {
            return basis == Basis.DECLARED
                    ? "declared by the processor's own graph (" + FRAMEWORK_FACT + ")"
                    : "inferred from the class name — the graph did not declare it";
        }
    }

    /**
     * The declared-first answer. {@code vocabulary} may be {@code null} (no file context), which is the
     * same as an untrusted one: the fallback decides and says so.
     */
    public static Authorship classify(ProcessorTopology.Node node, GraphVocabulary vocabulary) {
        if (node == null) return new Authorship(false, Basis.INFERRED);
        if (nodeScoped(node) && vocabulary != null && vocabulary.trustedForNodeFacts()) {
            String declared = node.fact(FRAMEWORK_FACT);
            if ("true".equalsIgnoreCase(declared)) return new Authorship(true, Basis.DECLARED);
            if ("false".equalsIgnoreCase(declared)) return new Authorship(false, Basis.DECLARED);
            // absent, blank or any other value: not a declaration — fall through, do not half-believe it
        }
        return new Authorship(inferred(node), Basis.INFERRED);
    }

    /** As {@link #classify}, reduced to the boolean every visibility caller needs. */
    public static boolean isScaffolding(ProcessorTopology.Node node, GraphVocabulary vocabulary) {
        return classify(node, vocabulary).framework();
    }

    /**
     * The class-name list alone — no graph context. Kept for callers that hold only a node. <b>Prefer
     * {@link #isScaffolding(ProcessorTopology.Node, GraphVocabulary)}</b>: this overload cannot see a
     * declaration and so will misread a framework class used as an authored node.
     */
    public static boolean isScaffolding(ProcessorTopology.Node node) {
        return node != null && inferred(node);
    }

    /** The declaration applies to node vertices only (M45.4: NODE-scoped). */
    private static boolean nodeScoped(ProcessorTopology.Node node) {
        return node.kind() != ProcessorTopology.Kind.EVENT
                && node.kind() != ProcessorTopology.Kind.EXPORT_SERVICE;
    }

    private static boolean inferred(ProcessorTopology.Node node) {
        if (FRAMEWORK_EVENT_IDS.contains(node.id())) return true;
        String className = node.className();
        if (className == null || className.isBlank()) return false;   // unknown → show it
        for (String prefix : FRAMEWORK_PACKAGES) {
            if (className.startsWith(prefix)) return true;
        }
        return FRAMEWORK_TYPES.contains(node.simpleName());
    }

    /**
     * The ids of everything that is <b>not</b> scaffolding — what {@code subgraph} keeps. Declared-first,
     * using the topology's own vocabulary, so every caller (the hide control, the authored subgraph, the
     * coverage population, discovery counts) gets the same answer without having to know the rule.
     */
    public static Set<String> authoredNodes(ProcessorTopology topology) {
        Set<String> keep = new LinkedHashSet<>();
        if (topology == null) return keep;
        GraphVocabulary vocabulary = topology.vocabulary();
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (!isScaffolding(node, vocabulary)) keep.add(node.id());
        }
        return keep;
    }

    /**
     * How the authored set was established, for a surface that has to justify itself: {@code declared}
     * when every node vertex's answer came from the graph, {@code inferred} when none did, and
     * {@code mixed} otherwise. Event and exported-service vertices are not counted — the fact does not
     * apply to them, so they say nothing about whether the graph was believed.
     */
    public static String authorshipBasis(ProcessorTopology topology) {
        if (topology == null) return "inferred";
        GraphVocabulary vocabulary = topology.vocabulary();
        int declared = 0;
        int inferred = 0;
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (!nodeScoped(node)) continue;
            if (classify(node, vocabulary).basis() == Basis.DECLARED) declared++;
            else inferred++;
        }
        if (declared == 0) return "inferred";
        return inferred == 0 ? "declared" : "mixed";
    }

    /** How many nodes would be hidden — for the checkbox label, so the cost is visible before clicking. */
    public static int count(ProcessorTopology topology) {
        return topology == null ? 0 : topology.nodeCount() - authoredNodes(topology).size();
    }
}
