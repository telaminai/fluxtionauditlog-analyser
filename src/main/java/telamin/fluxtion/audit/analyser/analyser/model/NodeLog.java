package telamin.fluxtion.audit.analyser.analyser.model;

import java.util.ArrayList;
import java.util.List;

/**
 * One node's contribution to a propagation cycle: the node's {@code instanceId} (its field name in
 * the generated EventProcessor) and the ordered list of {@code key: value} pairs it logged.
 *
 * <p>Order and duplicates are preserved. The same {@code instanceId} can appear multiple times
 * within a single {@link LogRecord} (a node may log at several points in one cycle); each occurrence
 * is a separate {@code NodeLog}.
 */
/**
 * One node's contribution to a cycle: its business entries, and - separately - whether a wire TRACE
 * entry said it ran. {@code traced} is METADATA, not an entry: a review showed a trace marker carried as
 * an entry sharing the last-value slot with a business key of the same spelling, so the diff and the
 * MCP field read returned {@code true} where the log said {@code 99}. Provenance and business keys are
 * separate identities in every reduction, lookup, diff and projection because they are separate here.
 * Only the binary reader's reserved bare {@code @invoked} marker, emitted for a wire TRACE entry with
 * key 0, sets it; a business key spelled {@code @invoked} is quoted on the way out and stays an entry.
 */
public record NodeLog(String instanceId, List<KV> entries, boolean traced) {

    /** A node-log with no trace metadata - every text log, and a binary node that logged values only. */
    public NodeLog(String instanceId, List<KV> entries) {
        this(instanceId, entries, false);
    }

    /** All values logged under {@code key} in this node-log (usually 0 or 1). */
    public List<KV> all(String key) {
        List<KV> out = new ArrayList<>();
        for (KV kv : entries) {
            if (key.equals(kv.key())) out.add(kv);
        }
        return out;
    }

    /** The last value logged under {@code key}, or {@code null}. */
    public KV last(String key) {
        KV found = null;
        for (KV kv : entries) {
            if (key.equals(kv.key())) found = kv;
        }
        return found;
    }
}
