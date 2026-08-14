package telamin.fluxtion.audit.analyser.analyser.summary;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates the records that pass the active {@link FilterState}, grouped by event dimension,
 * into {@link SummaryRow}s (count + log-time span). Works entirely off the {@link LogIndex} — no
 * node-log parsing (spec §8.6).
 */
public final class SummaryBuilder {

    private SummaryBuilder() {
    }

    public static List<SummaryRow> build(LogIndex idx, FilterState filter) {
        Map<String, long[]> agg = new LinkedHashMap<>();   // key -> {count, min, max}
        for (int row = 0; row < idx.size(); row++) {
            if (!filter.test(idx, row)) continue;
            String key = filter.groupKey(idx, row);
            long[] a = agg.computeIfAbsent(key, k -> new long[]{0, Long.MAX_VALUE, Long.MIN_VALUE});
            a[0]++;
            Long lt = idx.logTime(row);
            if (lt != null) {
                if (lt < a[1]) a[1] = lt;
                if (lt > a[2]) a[2] = lt;
            }
        }
        List<SummaryRow> out = new ArrayList<>(agg.size());
        for (Map.Entry<String, long[]> e : agg.entrySet()) {
            long[] a = e.getValue();
            Long first = a[1] == Long.MAX_VALUE ? null : a[1];
            Long last = a[2] == Long.MIN_VALUE ? null : a[2];
            out.add(new SummaryRow(e.getKey(), a[0], first, last));
        }
        out.sort(Comparator.comparingLong(SummaryRow::count).reversed());
        return out;
    }
}
