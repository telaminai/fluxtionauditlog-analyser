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
 *
 * <p><b>Values are compared by KIND and value, not by their characters.</b> A number {@code 42.0} and
 * the text {@code "42.0"} spell the same characters and mean different things: one is a figure the
 * chart plots and the scorer compares, the other is a string. Flattening both to {@code rawValue()}
 * called them SAME while a graphable obligation had disappeared - the diff disagreeing with every other
 * consumer of the same record. The kind comes from {@link KV#kind()}, the model's own interpretation,
 * so the diff cannot drift from the chart or the scorer by re-deriving it here.
 */
public final class DiffBuilder {

    public enum Change { CHANGED, ONLY_A, ONLY_B, SAME }

    /**
     * One compared key. {@code a}/{@code b} are the values as text for display; {@code kindA}/{@code
     * kindB} say what each value IS, and are null on a side the key is absent from.
     */
    public record DiffRow(String key, String a, String b, Change change, KV.Kind kindA, KV.Kind kindB) {
        /** A row whose kinds are not stated - export fixtures and the like. */
        public DiffRow(String key, String a, String b, Change change) {
            this(key, a, b, change, null, null);
        }

        public boolean isDifference() {
            return change != Change.SAME;
        }

        /** True when the two sides hold values of different kinds - the case a character diff misses. */
        public boolean kindDiffers() {
            return kindA != null && kindB != null && kindA != kindB;
        }

        /** Side A for display: the value, and its kind when the kind is what differs. */
        public String displayA() {
            return display(a, kindA);
        }

        public String displayB() {
            return display(b, kindB);
        }

        private String display(String value, KV.Kind kind) {
            if (value == null) return null;
            return kindDiffers() ? value + " (" + kind.name().toLowerCase() + ")" : value;
        }
    }

    private DiffBuilder() {
    }

    public static List<DiffRow> diff(LogRecord a, LogRecord b) {
        Map<String, KV> ma = flatten(a);
        Map<String, KV> mb = flatten(b);
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(ma.keySet());
        keys.addAll(mb.keySet());

        List<DiffRow> rows = new ArrayList<>(keys.size());
        for (String k : keys) {
            KV va = ma.get(k);
            KV vb = mb.get(k);
            Change c;
            if (va != null && vb == null) c = Change.ONLY_A;
            else if (va == null && vb != null) c = Change.ONLY_B;
            else if (!same(va, vb)) c = Change.CHANGED;
            else c = Change.SAME;
            rows.add(new DiffRow(k, va == null ? null : va.rawValue(), vb == null ? null : vb.rawValue(), c,
                    va == null ? null : va.kind(), vb == null ? null : vb.kind()));
        }
        // differences first (stable within groups)
        rows.sort((x, y) -> Boolean.compare(y.isDifference(), x.isDifference()));
        return rows;
    }

    /**
     * Same kind AND same value. Numbers compare EXACTLY, through {@link KV#exact()}: {@code 1} and
     * {@code 1.0} are the same figure, and {@code 9007199254740992} and {@code 9007199254740993} are
     * not - a review found the first version narrowing both to a double and calling them SAME, which
     * lost a distinction the log carries. The plotting approximation is not an equality. A number with
     * no exact decimal ({@code NaN}, {@code Infinity}) compares as the text it is. Everything else
     * compares as text; quoted {@code "hello"} and bare {@code hello} are both TEXT and both spell
     * hello, so they are the same - the quoted flag is how the kind was established, not the kind.
     */
    static boolean same(KV a, KV b) {
        KV.Kind ka = a.kind(), kb = b.kind();
        if (ka != kb) return false;
        switch (ka) {
            case NULL: return true;
            case NUMBER: {
                var ea = a.exact();
                var eb = b.exact();
                if (ea.isPresent() && eb.isPresent()) return ea.get().compareTo(eb.get()) == 0;
                return Objects.equals(a.rawValue().trim(), b.rawValue().trim());
            }
            case BOOLEAN: return a.asBoolean().equals(b.asBoolean());
            default: return Objects.equals(a.rawValue(), b.rawValue());
        }
    }

    static Map<String, KV> flatten(LogRecord r) {
        Map<String, KV> m = new LinkedHashMap<>();
        for (NodeLog nl : r.nodeLogs()) {
            for (KV kv : nl.entries()) {
                if (kv.key() != null) m.put(nl.instanceId() + "." + kv.key(), kv);
            }
        }
        return m;
    }
}
