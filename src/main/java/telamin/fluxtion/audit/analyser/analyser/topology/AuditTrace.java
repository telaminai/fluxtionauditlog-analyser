package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
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

    /** The key node-invocation tracing adds to every entry in the TEXT record. */
    private static final String METHOD = "method";

    /**
     * True when this record traces every invocation — every logged node carries a {@code method}
     * entry, which only the TEXT runtime's tracing adds — AND the record is one that heuristic applies
     * to. Applicability comes from the record's grammar ({@link LogRecord#textEncoding()}), i.e. from
     * the reader that produced it, never from any property spelling: a binary log's business
     * {@code method} property is a business property. The binary format carries no completeness
     * declaration (FLXA §11.7, §16), so for a binary log this is false and absence stays unknown - the
     * v1 boundary, enforced here rather than promised.
     *
     * <p>Requires <em>all</em> logged nodes to carry it, not any: a node is free to log a key called
     * "method" itself, and one such node must not make a sparse record look complete.
     */
    public static boolean tracesEveryInvocation(LogRecord record) {
        if (record == null) return false;
        if (record.textEncoding() != telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader.TextEncoding.LEGACY) {
            return false;
        }
        List<NodeLog> nodeLogs = record.nodeLogs();
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

    /** True when a wire TRACE entry said this node ran: evidence for this node, and only that. */
    public static boolean ran(NodeLog node) {
        return node.traced();
    }
}
