package telamin.fluxtion.audit.analyser.analyser.diff;

import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compares two records by flattening each to {@code instanceId.key → value} (last occurrence wins) and
 * reporting per-key differences (spec §13). Differences are listed first. Pure/testable.
 */
public final class DiffBuilder {

    public enum Change { CHANGED, ONLY_A, ONLY_B, SAME }

    public record DiffRow(String key, String a, String b, Change change) {
        public boolean isDifference() {
            return change != Change.SAME;
        }
    }

    private DiffBuilder() {
    }

    public static List<DiffRow> diff(LogRecord a, LogRecord b) {
        Map<String, String> ma = flatten(a);
        Map<String, String> mb = flatten(b);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(ma.keySet());
        keys.addAll(mb.keySet());

        List<DiffRow> rows = new ArrayList<>(keys.size());
        for (String k : keys) {
            boolean inA = ma.containsKey(k);
            boolean inB = mb.containsKey(k);
            String va = ma.get(k);
            String vb = mb.get(k);
            Change c;
            if (inA && !inB) c = Change.ONLY_A;
            else if (!inA && inB) c = Change.ONLY_B;
            else if (!Objects.equals(va, vb)) c = Change.CHANGED;
            else c = Change.SAME;
            rows.add(new DiffRow(k, va, vb, c));
        }
        // differences first (stable within groups)
        rows.sort((x, y) -> Boolean.compare(y.isDifference(), x.isDifference()));
        return rows;
    }

    static Map<String, String> flatten(LogRecord r) {
        Map<String, String> m = new LinkedHashMap<>();
        for (NodeLog nl : r.nodeLogs()) {
            for (KV kv : nl.entries()) {
                if (kv.key() != null) m.put(nl.instanceId() + "." + kv.key(), kv.rawValue());
            }
        }
        return m;
    }
}
