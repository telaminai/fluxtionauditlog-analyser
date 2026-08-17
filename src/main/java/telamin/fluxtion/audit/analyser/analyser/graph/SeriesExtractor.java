package telamin.fluxtion.audit.analyser.analyser.graph;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts a {@link Series} for a {@link GraphKey} over the records passing the active filter
 * (spec §8.7). Uses the <b>last occurrence</b> of the instanceId within each record (final state
 * that cycle); {@code NaN}/non-finite values are kept as gaps. Streams over the store so it scales.
 */
public final class SeriesExtractor {

    private SeriesExtractor() {
    }

    public static Series extract(LogStore store, FilterState filter, GraphKey key) {
        return extract(store, filter, key, false);
    }

    /**
     * Extract one series. When {@code acrossAllTime} is true the time range is ignored (dimension/text
     * constraints still apply) — the graph extracts once across time and windows via the chart view, so a
     * time-slider drag never re-parses the log (spec: graph redraw throttle / smoothness).
     */
    public static Series extract(LogStore store, FilterState filter, GraphKey key, boolean acrossAllTime) {
        Series series = new Series(key);
        var index = store.index();
        for (int row = 0; row < store.size(); row++) {
            if (acrossAllTime ? !filter.testExceptTime(index, row) : !filter.test(index, row)) continue;
            Long logTime = index.logTime(row);
            if (logTime == null) continue;
            LogRecord rec = store.record(row);
            KV chosen = lastMatching(rec.nodeLogs(), key);
            if (chosen == null) continue;
            var d = chosen.graphValue();   // numeric, or boolean mapped to +1.0/-1.0
            // NaN/Infinity means "no data point" for this record (not a parse failure) — omit it
            if (d.isPresent() && Double.isFinite(d.getAsDouble())) series.add(logTime, d.getAsDouble());
        }
        return series;
    }

    /** Reference-resolution policy for a derived series (spec-graph-artifacts §B.2). */
    public enum Resolve { LOCF, STRICT }

    /**
     * Extract a <b>derived</b> series — the value of {@code expr} over node-log references per record
     * (spec-graph-artifacts §B). Scans in <b>row (event-sequence) order</b> — exact for a single-logger
     * file. Under <b>LOCF</b> (default), each ref carries its last-known value across records so cross-node
     * formulas plot; the carry rule is: a <b>finite</b> observation <b>updates</b> the carry, an explicit
     * <b>NaN / non-numeric</b> observation <b>clears</b> it (subsequent points omitted until a finite value
     * returns — no fabricated continuity through a no-quote period), an <b>absent</b> key leaves it
     * unchanged; a point is produced only on a record that touches ≥1 ref. Under <b>STRICT</b>, every ref
     * must be present-and-finite in the same record. Non-finite results (missing carry, div-by-zero) are
     * omitted, the existing NaN-as-no-point semantics.
     */
    public static Series extractExpr(LogStore store, FilterState filter, Expr expr, String label,
                                     boolean acrossAllTime, Resolve policy) {
        Series series = new Series(label);
        var index = store.index();
        Set<GraphKey> refs = expr.refs();
        Evaluator eval = expr.newEvaluator();   // ONE per scan — rolling windows reset with the scan (W0)
        java.util.Map<GraphKey, Double> carry = new java.util.HashMap<>();   // LOCF last-known finite value

        for (int row = 0; row < store.size(); row++) {
            if (acrossAllTime ? !filter.testExceptTime(index, row) : !filter.test(index, row)) continue;
            Long logTime = index.logTime(row);
            List<NodeLog> nodeLogs = store.record(row).nodeLogs();

            if (policy == Resolve.STRICT) {
                java.util.Map<GraphKey, Double> vals = new java.util.HashMap<>();
                boolean allFinite = true;
                for (GraphKey k : refs) {
                    KV kv = lastMatching(nodeLogs, k);
                    var d = kv == null ? java.util.OptionalDouble.empty() : kv.graphValue();
                    if (d.isPresent() && Double.isFinite(d.getAsDouble())) vals.put(k, d.getAsDouble());
                    else { allFinite = false; break; }   // absent or NaN → not a co-occurring numeric record
                }
                if (!allFinite || logTime == null) continue;
                double v = eval.eval(logTime, vals);
                if (Double.isFinite(v)) series.add(logTime, v);
            } else {   // LOCF
                boolean touched = false;
                for (GraphKey k : refs) {
                    KV kv = lastMatching(nodeLogs, k);
                    if (kv == null) continue;             // absent → carry unchanged, no point from this ref
                    touched = true;
                    var d = kv.graphValue();
                    if (d.isPresent() && Double.isFinite(d.getAsDouble())) carry.put(k, d.getAsDouble());  // update
                    else carry.remove(k);                 // explicit NaN / non-numeric → clear the carry
                }
                if (!touched || logTime == null) continue;
                double v = eval.eval(logTime, carry);
                if (Double.isFinite(v)) series.add(logTime, v);
            }
        }
        return series;
    }

    /**
     * Which of {@code wantedDisplays} ({@code "instanceId.key"}) exist as graphable (numeric/boolean) keys
     * anywhere in the log. Unlike {@link #discover} this is <b>targeted</b> and scans the whole log with an
     * <b>early exit</b> once all are found — so a key that first fires late (a rare locked-book flag) still
     * resolves. Only a genuinely-absent key (a typo) forces a full scan. Used for accurate action feedback.
     */
    public static Set<String> resolveExisting(LogStore store, Set<String> wantedDisplays) {
        Set<String> found = new LinkedHashSet<>();
        if (wantedDisplays == null || wantedDisplays.isEmpty()) return found;
        Set<String> pending = new java.util.HashSet<>(wantedDisplays);
        for (int row = 0; row < store.size() && !pending.isEmpty(); row++) {
            for (NodeLog nl : store.record(row).nodeLogs()) {
                for (KV kv : nl.entries()) {
                    if (kv.key() == null || kv.graphValue().isEmpty()) continue;
                    String display = nl.instanceId() + "." + kv.key();
                    if (pending.remove(display)) {
                        found.add(display);
                        if (pending.isEmpty()) return found;
                    }
                }
            }
        }
        return found;
    }

    /** Discovers numeric graph keys by scanning up to {@code limit} filtered records. */
    public static List<GraphKey> discover(LogStore store, FilterState filter, int limit) {
        Set<GraphKey> keys = new LinkedHashSet<>();
        var index = store.index();
        int scanned = 0;
        for (int row = 0; row < store.size() && scanned < limit; row++) {
            if (!filter.test(index, row)) continue;
            scanned++;
            for (NodeLog nl : store.record(row).nodeLogs()) {
                for (KV kv : nl.entries()) {
                    if (kv.key() != null && kv.graphValue().isPresent()) {   // numeric or boolean
                        keys.add(new GraphKey(nl.instanceId(), kv.key()));
                    }
                }
            }
        }
        return keys.stream()
                .sorted((a, b) -> a.display().compareToIgnoreCase(b.display()))
                .toList();
    }

    /** Package-private: {@link SeriesScan} (M26.1) shares the exact last-occurrence match rule. */
    static KV lastMatching(List<NodeLog> nodeLogs, GraphKey key) {
        KV chosen = null;
        for (NodeLog nl : nodeLogs) {
            if (nl.instanceId().equals(key.instanceId())) {
                KV kv = nl.last(key.key());
                if (kv != null) chosen = kv;   // keep the last occurrence in the record
            }
        }
        return chosen;
    }
}
