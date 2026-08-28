package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * The {@code aggregate} query verb (spec-assistant-actions §4.1): typed counts / rates over a
 * {@link LogIndex.Snapshot} — the model's aggregation backend, doing what file {@code grep} can't
 * (rates, spans, per-dimension and time-bucketed counts). Pure and headless; no Swing, no I/O.
 *
 * <p>All metrics and the {@code dimension}/{@code thread}/time filters are index-resident (O(index)).
 * A {@code text} filter is the one exception — it reads record bytes via {@code rawText} and the result
 * reports {@code scan:"raw"} so speed is never implied where a raw pass is happening.
 */
public final class AggregateService {

    private static final DateTimeFormatter HOUR = fmt("yyyy-MM-dd'T'HH':00Z'");
    private static final DateTimeFormatter MIN = fmt("yyyy-MM-dd'T'HH:mm'Z'");
    private static final DateTimeFormatter DAY = fmt("yyyy-MM-dd");

    private static final List<String> METRICS = List.of("count", "rate_per_min", "nan_count", "breach_count");
    private static final List<String> GROUP_BYS = List.of("dimension", "thread", "hour", "minute", "day", "none");

    private AggregateService() {
    }

    /**
     * @param snap     immutable index view (see {@link LogIndex#snapshot()})
     * @param params   the action's {@code params} object (metric / groupBy / filter / limit)
     * @param rawText  row → raw record text, for a {@code text} filter; may be null
     * @return the {@code result} object (JSON-ready map) described in §4.1
     */
    public static Map<String, Object> aggregate(LogIndex.Snapshot snap, Map<String, Object> params,
                                                IntFunction<String> rawText) {
        String metric = str(params.get("metric"), "count");
        String groupBy = str(params.get("groupBy"), "none");
        int limit = asInt(params.get("limit"), 500);
        ActionFilter filter = ActionFilter.from(params.get("filter"));

        // validate up front so a typo becomes a structured ok:false (the model can self-correct), not a
        // silently-coerced answer; and never claim a raw-text result when there is no source to scan
        if (!METRICS.contains(metric)) {
            throw new IllegalArgumentException("unknown metric '" + metric + "' (expected one of " + METRICS + ")");
        }
        if (!GROUP_BYS.contains(groupBy)) {
            throw new IllegalArgumentException("unknown groupBy '" + groupBy + "' (expected one of " + GROUP_BYS + ")");
        }
        if (filter.isRawScan() && rawText == null) {
            throw new IllegalArgumentException("filter.text needs to read record bytes, but no raw-text "
                    + "source is available for this query");
        }

        boolean timeBucket = groupBy.equals("hour") || groupBy.equals("minute") || groupBy.equals("day");
        Map<String, long[]> counts = new LinkedHashMap<>();   // key -> [count]
        int population = 0;
        long minT = Long.MAX_VALUE, maxT = Long.MIN_VALUE;

        for (int i = 0; i < snap.size(); i++) {
            if (!filter.matches(snap, i, rawText)) continue;
            population++;
            Long t = snap.logTime(i);
            if (t != null) {
                if (t < minT) minT = t;
                if (t > maxT) maxT = t;
            }
            if (!metricMatches(metric, snap, i)) continue;    // nan_count / breach_count narrow the tally
            String key = groupKey(groupBy, snap, i, timeBucket);
            if (key == null) continue;                        // time grouping skips untimed rows
            counts.computeIfAbsent(key, k -> new long[1])[0]++;
        }

        long total = counts.values().stream().mapToLong(a -> a[0]).sum();
        double spanMin = (maxT >= minT) ? Math.max(0, (maxT - minT)) / 60_000.0 : 0;

        List<Map.Entry<String, long[]>> ordered = new ArrayList<>(counts.entrySet());
        if (timeBucket) ordered.sort(Map.Entry.comparingByKey());
        else ordered.sort(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> e.getValue()[0]).reversed());

        boolean rate = metric.equals("rate_per_min");
        double bucketMin = groupBy.equals("hour") ? 60 : groupBy.equals("day") ? 1440 : 1;   // minute → 1
        List<Map<String, Object>> buckets = new ArrayList<>();
        for (int k = 0; k < ordered.size() && k < limit; k++) {
            var e = ordered.get(k);
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("key", e.getKey());
            b.put("count", e.getValue()[0]);
            if (rate) {
                double denom = timeBucket ? bucketMin : (spanMin > 0 ? spanMin : 1);
                b.put("rate_per_min", round(e.getValue()[0] / denom));
            }
            buckets.add(b);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", metric);
        result.put("groupBy", groupBy);
        result.put("total", total);
        if (rate && spanMin > 0) result.put("rate_per_min", round(total / spanMin));
        if (ordered.size() > limit) result.put("truncated", ordered.size() - limit);
        result.put("buckets", buckets);

        Map<String, Object> pop = new LinkedHashMap<>();
        pop.put("records", population);
        pop.put("filter", filter.toMap());
        pop.put("scan", filter.isRawScan() ? "raw" : "index");
        result.put("population", pop);
        return result;
    }

    private static boolean metricMatches(String metric, LogIndex.Snapshot snap, int i) {
        return switch (metric) {
            case "nan_count" -> snap.hasNaN(i);
            case "breach_count" -> snap.hasBreach(i);
            default -> true;                                  // count / rate_per_min count every row
        };
    }

    private static String groupKey(String groupBy, LogIndex.Snapshot snap, int i, boolean timeBucket) {
        if (!timeBucket) {
            return switch (groupBy) {
                case "dimension" -> String.valueOf(snap.dimension(i));
                case "thread" -> String.valueOf(snap.thread(i));
                default -> "all";                             // none
            };
        }
        Long t = snap.logTime(i);
        if (t == null) return null;
        Instant at = Instant.ofEpochMilli(t);
        return switch (groupBy) {
            case "hour" -> HOUR.format(at.truncatedTo(ChronoUnit.HOURS));
            case "minute" -> MIN.format(at.truncatedTo(ChronoUnit.MINUTES));
            default -> DAY.format(at.truncatedTo(ChronoUnit.DAYS));   // day
        };
    }

    private static DateTimeFormatter fmt(String p) {
        return DateTimeFormatter.ofPattern(p).withZone(ZoneOffset.UTC);
    }

    private static String str(Object o, String dflt) {
        return o == null || o.toString().isBlank() ? dflt : o.toString();
    }

    private static int asInt(Object value, int dflt) {
        if (value instanceof Number n) return Math.max(1, n.intValue());
        if (value instanceof String s) {
            try {
                return Math.max(1, Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
                // Invalid limits have always fallen back to the documented default; keep that tolerant
                // scalar contract when a persisted report reissues its top-level value as text.
            }
        }
        return dflt;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
