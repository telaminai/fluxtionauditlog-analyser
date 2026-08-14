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
public record NodeLog(String instanceId, List<KV> entries) {

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
