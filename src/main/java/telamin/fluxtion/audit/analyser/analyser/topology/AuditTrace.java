package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;

import java.util.List;

/**
 * Tells whether a record's audit covers <b>every node invocation</b>, or only the nodes that chose to log.
 *
 * <p>This is the difference between two very different logs, and it decides what silence means:
 *
 * <ul>
 *   <li><b>Node-invocation tracing on</b> — the processor was built with an audit level, so every node it
 *       invokes writes a {@code thread} + {@code method} entry whether or not it makes {@code auditLog}
 *       calls of its own. The record is then a <em>complete</em> list of what ran, and a node's absence
 *       genuinely means it did not run.</li>
 *   <li><b>Tracing off</b> — only nodes that call {@code auditLog} appear. Absence says nothing, which is
 *       the case {@link ProcessorTopology.Execution} exists to model.</li>
 * </ul>
 *
 * <p>Tracing is compiled in at build time and gated at runtime, so a given log either carries these
 * entries or cannot — which is why detecting it per record is reliable rather than a heuristic about
 * how chatty the nodes happen to be.
 */
public final class AuditTrace {
    private AuditTrace() { }

    /** The key node-invocation tracing adds to every entry. */
    private static final String METHOD = "method";

    /**
     * True when this record traces every invocation — i.e. every logged node carries a {@code method}
     * entry, which only the framework's tracing adds.
     *
     * <p>Requires <em>all</em> entries to have it, not any: a node is free to log a key called
     * "method" itself, and one such node must not make a sparse record look complete.
     */
    public static boolean tracesEveryInvocation(List<NodeLog> nodeLogs) {
        if (nodeLogs == null || nodeLogs.isEmpty()) return false;
        for (NodeLog node : nodeLogs) {
            if (!hasMethodEntry(node)) return false;
        }
        return true;
    }

    private static boolean hasMethodEntry(NodeLog node) {
        for (KV kv : node.entries()) {
            if (METHOD.equals(kv.key())) return true;
        }
        return false;
    }
}
