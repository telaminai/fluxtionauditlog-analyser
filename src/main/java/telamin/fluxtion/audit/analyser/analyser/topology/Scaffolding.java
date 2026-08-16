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
 * <p>Detection has to cope with the two label shapes real GraphML produces, because the compiler emits
 * both: a package-qualified {@code class:} ({@code com.telamin.fluxtion.runtime.time.Clock}) and a bare
 * simple name ({@code Clock}). Event nodes often carry <b>no class at all</b>, so those are matched by id.
 *
 * <p>Deliberately a list rather than a rule. "Anything in a framework package" would also hide a user
 * node that happens to extend one, and there is no marker in the GraphML to distinguish them — so this
 * names what it knows and lets everything else through. Wrongly showing plumbing is a much smaller harm
 * than wrongly hiding a node someone is looking for.
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

    /** True when this node is framework plumbing rather than part of the authored graph. */
    public static boolean isScaffolding(ProcessorTopology.Node node) {
        if (node == null) return false;
        if (FRAMEWORK_EVENT_IDS.contains(node.id())) return true;
        String className = node.className();
        if (className == null || className.isBlank()) return false;   // unknown → show it
        for (String prefix : FRAMEWORK_PACKAGES) {
            if (className.startsWith(prefix)) return true;
        }
        return FRAMEWORK_TYPES.contains(node.simpleName());
    }

    /** The ids of everything that is <b>not</b> scaffolding — what {@code subgraph} keeps. */
    public static Set<String> authoredNodes(ProcessorTopology topology) {
        Set<String> keep = new LinkedHashSet<>();
        if (topology == null) return keep;
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (!isScaffolding(node)) keep.add(node.id());
        }
        return keep;
    }

    /** How many nodes would be hidden — for the checkbox label, so the cost is visible before clicking. */
    public static int count(ProcessorTopology topology) {
        return topology == null ? 0 : topology.nodeCount() - authoredNodes(topology).size();
    }
}
