package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;

import java.util.List;
import java.util.Set;

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
     * The key SPELLINGS the TEXT runtime's invocation tracing writes into a node's entry: {@code thread}
     * (when thread names are printed), {@code method} — or {@code forkedExecution} and {@code asyncMethod}
     * for a forked node — and, from Fluxtion runtime 1.1.1, {@code annotation}, the simple name of the
     * annotation that dispatched the callback. A spelling alone decides nothing: a node may log any of these
     * keys itself. Which entries ARE trace is positional — {@link #traceEntryCount}.
     */
    private static final Set<String> TRACE_KEYS = Set.of("thread", METHOD, "annotation", "forkedExecution", "asyncMethod");
    /** An annotation's simple name, the only value tracing writes for {@code annotation}. */
    private static final java.util.regex.Pattern ANNOTATION_NAME = java.util.regex.Pattern.compile("[A-Z][A-Za-z0-9_]*");

    /** True when invocation tracing can write this key in the TEXT record. Spelling only — see {@link #traceEntryCount}. */
    public static boolean isTraceKey(String key) {
        return key != null && TRACE_KEYS.contains(key);
    }

    /**
     * How many of this entry's leading values invocation tracing wrote. Tracing writes first, in a fixed
     * order ({@code EventLogManager.nodeInvoked}, and each invocation starts its own entry): an optional
     * {@code thread}, then {@code method} — or {@code forkedExecution} then {@code asyncMethod} — then an
     * optional {@code annotation} whose value is an annotation's simple name. Only that leading run is trace;
     * everything after it is the node's own, even when spelled like a trace key, and an entry that does not
     * start with the run has no trace values at all (a lone {@code thread} or {@code annotation} is business).
     *
     * <p>One ambiguity remains, and the format specification states it: a node of a runtime before 1.1.1
     * that logged its own {@code annotation} first, with a capitalised simple-name value, reads as trace.
     */
    public static int traceEntryCount(List<KV> entries) {
        int n = entries == null ? 0 : entries.size(), i = 0;
        if (i < n && "thread".equals(entries.get(i).key())) i++;
        if (i < n && METHOD.equals(entries.get(i).key())) i++;
        else if (i + 1 < n && "forkedExecution".equals(entries.get(i).key())
                && "asyncMethod".equals(entries.get(i + 1).key())) i += 2;
        else return 0;
        if (i < n && "annotation".equals(entries.get(i).key()) && entries.get(i).rawValue() != null
                && ANNOTATION_NAME.matcher(entries.get(i).rawValue()).matches()) i++;
        return i;
    }

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
        if (traceEntryCount(node.entries()) > 0) return true;   // includes a forked node's asyncMethod
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
