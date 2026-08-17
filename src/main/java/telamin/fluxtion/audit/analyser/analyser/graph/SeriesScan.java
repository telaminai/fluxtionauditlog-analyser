package telamin.fluxtion.audit.analyser.analyser.graph;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.model.KV;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The {@code series} verb (M26.1): stats and threshold crossings over any key or formula, computed
 * <b>in the analyser</b> so a token-metered agent never pages raw records to do arithmetic. The rule
 * this verb exists for: any question answerable by an index/series scan should be a verb, not a read.
 *
 * <p>Crossings are <b>edge events</b> — the sample where the value ENTERS the region — each carrying a
 * {@code recordIndex}/{@code byteOffset} anchor so the agent's next call is a targeted {@code read},
 * not an estimate. Capped, with an explicit {@code truncated} flag: no silent bounds.
 *
 * <p>Same evaluation semantics as graphing ({@link SeriesExtractor}): last occurrence per record,
 * STRICT = all refs co-occur in one record, LOCF = carry each ref's last finite value.
 */
public final class SeriesScan {

    /** Edge-event cap unless the caller narrows it further — bounded output, never a silent subset. */
    public static final int MAX_CROSSINGS = 200;

    private SeriesScan() {
    }

    public static Map<String, Object> scan(LogStore store, Map<String, Object> params) {
        Object exprText = params.get("expr");
        if (exprText == null || exprText.toString().isBlank()) {
            throw new IllegalArgumentException("'expr' is required — a key (\"node.key\") or a formula");
        }
        Expr expr = Expr.parse(exprText.toString());
        SeriesExtractor.Resolve resolve = "LOCF".equalsIgnoreCase(String.valueOf(params.get("resolve")))
                ? SeriesExtractor.Resolve.LOCF : SeriesExtractor.Resolve.STRICT;

        FilterState filter = new FilterState();
        Object f = params.get("filter");
        if (f instanceof Map<?, ?> fm) {
            if (fm.get("text") != null) {
                throw new IllegalArgumentException("'filter.text' is not supported on 'series' — a raw "
                        + "text scan would defeat the point of an index-speed verb; narrow with the "
                        + "'filter' verb first, or use from/to/dimensions here");
            }
            Long from = asLong(fm.get("from"));
            Long to = asLong(fm.get("to"));
            filter.setTimeRange(from, to);
            if (fm.get("dimensions") instanceof List<?> dims && !dims.isEmpty()) {
                java.util.Set<String> set = new java.util.LinkedHashSet<>();
                for (Object d : dims) if (d != null) set.add(d.toString());
                filter.setDimensions(set);
            }
        }

        Double above = asDouble(params.get("crossings") instanceof Map<?, ?> c ? c.get("above") : null);
        Double below = asDouble(params.get("crossings") instanceof Map<?, ?> c ? c.get("below") : null);
        int cap = Math.min(MAX_CROSSINGS, Math.max(1, asLong(params.get("limit")) == null
                ? MAX_CROSSINGS : asLong(params.get("limit")).intValue()));
        String bucket = params.get("buckets") == null ? null
                : params.get("buckets").toString().toLowerCase(java.util.Locale.ROOT);

        var index = store.index();
        Set<GraphKey> refs = expr.refs();
        Evaluator eval = expr.newEvaluator();   // ONE per scan — rolling windows reset with the scan (W0)
        Map<GraphKey, Double> carry = new HashMap<>();

        long count = 0;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum = 0;
        Long minAt = null, maxAt = null, firstAt = null, lastAt = null;
        double first = Double.NaN, last = Double.NaN;
        Double prev = null;
        List<Map<String, Object>> aboveEvents = new ArrayList<>();
        List<Map<String, Object>> belowEvents = new ArrayList<>();
        boolean truncated = false;
        TreeMap<String, double[]> buckets = bucket == null ? null : new TreeMap<>();   // key → [count,min,max,sum]

        for (int row = 0; row < store.size(); row++) {
            if (!filter.test(index, row)) continue;
            Long logTime = index.logTime(row);
            if (logTime == null) continue;
            List<NodeLog> nodeLogs = store.record(row).nodeLogs();

            Double v = null;
            if (resolve == SeriesExtractor.Resolve.STRICT) {
                Map<GraphKey, Double> vals = new HashMap<>();
                boolean allFinite = true;
                for (GraphKey k : refs) {
                    KV kv = SeriesExtractor.lastMatching(nodeLogs, k);
                    var d = kv == null ? java.util.OptionalDouble.empty() : kv.graphValue();
                    if (d.isPresent() && Double.isFinite(d.getAsDouble())) vals.put(k, d.getAsDouble());
                    else { allFinite = false; break; }
                }
                if (allFinite) {
                    double e = eval.eval(logTime, vals);
                    if (Double.isFinite(e)) v = e;
                }
            } else {
                boolean touched = false;
                for (GraphKey k : refs) {
                    KV kv = SeriesExtractor.lastMatching(nodeLogs, k);
                    if (kv == null) continue;
                    touched = true;
                    var d = kv.graphValue();
                    if (d.isPresent() && Double.isFinite(d.getAsDouble())) carry.put(k, d.getAsDouble());
                    else carry.remove(k);
                }
                if (touched) {
                    double e = eval.eval(logTime, carry);
                    if (Double.isFinite(e)) v = e;
                }
            }
            if (v == null) continue;

            count++;
            sum += v;
            if (v < min) { min = v; minAt = logTime; }
            if (v > max) { max = v; maxAt = logTime; }
            if (firstAt == null) { firstAt = logTime; first = v; }
            lastAt = logTime;
            last = v;

            if (above != null && v > above && (prev == null || prev <= above)) {
                if (aboveEvents.size() < cap) aboveEvents.add(event(row, index.offset(row), logTime, v));
                else truncated = true;
            }
            if (below != null && v < below && (prev == null || prev >= below)) {
                if (belowEvents.size() < cap) belowEvents.add(event(row, index.offset(row), logTime, v));
                else truncated = true;
            }
            prev = v;

            if (buckets != null) {
                String key = bucketKey(logTime, bucket);
                double[] b = buckets.computeIfAbsent(key, k -> new double[]{0, Double.POSITIVE_INFINITY,
                        Double.NEGATIVE_INFINITY, 0});
                b[0]++;
                if (v < b[1]) b[1] = v;
                if (v > b[2]) b[2] = v;
                b[3] += v;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("expr", exprText.toString());
        out.put("resolve", resolve.name());
        out.put("points", count);
        if (count > 0) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("min", min);
            stats.put("minAt", minAt);
            stats.put("max", max);
            stats.put("maxAt", maxAt);
            stats.put("mean", sum / count);
            stats.put("first", first);
            stats.put("firstAt", firstAt);
            stats.put("last", last);
            stats.put("lastAt", lastAt);
            out.put("stats", stats);
        }
        if (above != null || below != null) {
            Map<String, Object> crossings = new LinkedHashMap<>();
            if (above != null) {
                crossings.put("above", above);
                crossings.put("aboveEvents", aboveEvents);
            }
            if (below != null) {
                crossings.put("below", below);
                crossings.put("belowEvents", belowEvents);
            }
            crossings.put("truncated", truncated);
            if (truncated) {
                crossings.put("note", "more crossings exist than the cap (" + cap + ") — narrow the "
                        + "filter window or raise 'limit' (max " + MAX_CROSSINGS + ")");
            }
            out.put("crossings", crossings);
        }
        if (buckets != null) {
            List<Map<String, Object>> bs = new ArrayList<>(buckets.size());
            for (var e : buckets.entrySet()) {
                double[] b = e.getValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", e.getKey());
                m.put("count", (long) b[0]);
                m.put("min", b[1]);
                m.put("max", b[2]);
                m.put("mean", b[3] / b[0]);
                bs.add(m);
            }
            out.put("buckets", bs);
        }
        return out;
    }

    private static Map<String, Object> event(int row, long offset, long logTime, double value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordIndex", row);
        m.put("byteOffset", offset);
        m.put("logTime", logTime);
        m.put("value", value);
        return m;
    }

    private static String bucketKey(long millis, String bucket) {
        var t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC);
        return "hour".equals(bucket)
                ? String.format("%04d-%02d-%02dT%02d:00Z", t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour())
                : String.format("%04d-%02d-%02dT%02d:%02dZ", t.getYear(), t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    private static Double asDouble(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }
}
