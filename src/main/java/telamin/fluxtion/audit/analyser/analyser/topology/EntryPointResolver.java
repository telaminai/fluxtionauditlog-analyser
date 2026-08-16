package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Works out where a cycle <b>entered</b> the graph, from what the record says about its event.
 *
 * <p>This is what makes the predicted path real. Without an entry point you can only reason outward from
 * nodes that logged — so a whole branch that executed but logged nothing looks unrelated to the event.
 * With one, the forward closure from the entry is the path dispatch could have taken, whether or not
 * anything on it wrote audit output.
 *
 * <p>Dispatch enters two ways, and both appear in the record:
 * <ul>
 *   <li>an <b>event</b> — {@code event} names its class, which matches an {@code EVENT} node;</li>
 *   <li>an <b>exported service callback</b> — {@code event} is {@code ExportFunctionAuditEvent} and
 *       {@code eventToString} is the method signature, whose declaring class names the node invoked.</li>
 * </ul>
 */
public final class EntryPointResolver {
    private EntryPointResolver() { }

    /** The audit event type used when an exported service interface method is called. */
    private static final String EXPORTED_CALL = "ExportFunctionAuditEvent";

    /**
     * Node ids where this cycle plausibly entered the graph — empty when the record doesn't say.
     *
     * <p>Empty is a normal answer, not a failure: callers fall back to reasoning from logged nodes only.
     * Guessing an entry point would put a whole subtree on the predicted path on no evidence.
     */
    public static Set<String> resolve(ProcessorTopology topology, String event, String eventToString) {
        Set<String> found = new LinkedHashSet<>();
        if (topology == null || topology.isEmpty()) return found;

        if (EXPORTED_CALL.equals(event)) {
            String declaring = declaringClassOf(eventToString);
            if (declaring != null) {
                // a class IS named: if the graph hasn't got it, that is a contradiction (graphml from
                // another build), not a gap to fill in — resolving to something else would paper over it
                addByClassName(topology, declaring, found);
            } else {
                addSoleExportedService(topology, found);
            }
            if (!found.isEmpty()) return found;
        }
        if (event != null && !event.isBlank()) {
            addByEventName(topology, event.trim(), found);
        }
        return found;
    }

    /**
     * The class that declares the called method, from a signature like
     * {@code public boolean com.acme.Foo.onThing(com.acme.ThingEvent)} — the qualified name before the
     * final dot of the part preceding the argument list.
     */
    static String declaringClassOf(String signature) {
        if (signature == null || signature.isBlank()) return null;
        int paren = signature.indexOf('(');
        String head = (paren >= 0 ? signature.substring(0, paren) : signature).trim();
        int lastSpace = head.lastIndexOf(' ');
        String qualified = lastSpace >= 0 ? head.substring(lastSpace + 1) : head;   // drop modifiers/return
        int lastDot = qualified.lastIndexOf('.');
        if (lastDot <= 0) return null;
        String declaring = qualified.substring(0, lastDot);
        return declaring.indexOf('.') < 0 ? null : declaring;   // needs to look like a package-qualified name
    }

    /**
     * Fallback for the <b>unqualified</b> signature form. Two spellings of the same fact are in the wild:
     *
     * <pre>
     * public boolean com.acme.VenueMonitor.onConnected(com.acme.ConnectedEvent)   ← declaring class present
     * &#64;Override
     * public void suspendQuoting(String arg0)                                       ← method name only
     * </pre>
     *
     * <p>The second names no class, so there is nothing to match a node against. What the record does
     * establish is that the cycle entered through <em>some</em> exported service, and the graph lists them.
     * When the author declared exactly one, that is the entry — no guess involved.
     *
     * <p>With two or more the method name cannot pick between them (graphml carries classes, not method
     * signatures), so this resolves nothing rather than putting every service's subtree on the predicted
     * path. Framework services are excluded first: a call the user made is not a
     * {@code ServiceListener} registration.
     */
    private static void addSoleExportedService(ProcessorTopology topology, Set<String> into) {
        String only = null;
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (node.kind() != ProcessorTopology.Kind.EXPORT_SERVICE) continue;
            if (Scaffolding.isScaffolding(node)) continue;
            if (only != null) return;                 // ambiguous — say nothing
            only = node.id();
        }
        if (only != null) into.add(only);
    }

    private static void addByClassName(ProcessorTopology topology, String className, Set<String> into) {
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (className.equals(node.className())) into.add(node.id());
        }
    }

    /**
     * Match an event class by name. Prefers {@code EVENT} nodes — an event class and a node that handles
     * it can share a simple name, and the event is the entry.
     */
    private static void addByEventName(ProcessorTopology topology, String event, Set<String> into) {
        Set<String> events = new LinkedHashSet<>();
        Set<String> others = new LinkedHashSet<>();
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (!namesMatch(node, event)) continue;
            (node.kind() == ProcessorTopology.Kind.EVENT ? events : others).add(node.id());
        }
        into.addAll(events.isEmpty() ? others : events);
    }

    private static boolean namesMatch(ProcessorTopology.Node node, String event) {
        return event.equals(node.id())
               || event.equals(node.className())
               || event.equals(node.simpleName());
    }
}
