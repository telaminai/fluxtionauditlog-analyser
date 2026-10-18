package telamin.fluxtion.audit.analyser.analyser.filter;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * What the Records view shows and what each active filter hides, measured once over the open log — the value the
 * {@code recordsView} session node holds and the snapshot publishes (rule 9). Every surface that says how many records
 * are shown, how many a filter hides, or how many of an event type remain renders THIS; none of them counts rows.
 *
 * <p>A record can fail more than one filter, so the per-filter hidden counts are each "records this filter excludes",
 * and they can add up to more than {@code total - shown}.
 *
 * @param total         records in the open log
 * @param shown         records passing every filter (what the table lists)
 * @param hiddenByTypes records the event-type selection excludes (0 when every type is selected)
 * @param hiddenByText  records the search text excludes (0 when there is no search)
 * @param hiddenByTime  records outside the time range (0 when it is unbounded)
 * @param hiddenByFlags records "flagged only" excludes (0 when it is off)
 * @param typesFiltered whether an event-type selection is in force (not "all")
 * @param typesSelected event types (checklist rows) the selection keeps
 * @param typesTotal    event types (checklist rows) in the log
 * @param searching     whether a search text is in force
 * @param textRef       the search text, by a {@code FactRefs} reference (0 for none): an assistant may have set it, and a
 *                      fact never carries a model's words (spec §4.1) — the frame resolves it to render the Search box
 * @param timeBounded   whether a time range is in force
 * @param flaggedOnly   whether "flagged only" is in force
 * @param types         one row per event type, highest total first then by name — the checklist's order
 */
public record RecordsViewCounts(int total, int shown, int hiddenByTypes, int hiddenByText, int hiddenByTime,
                                int hiddenByFlags, boolean typesFiltered, int typesSelected, int typesTotal,
                                boolean searching, long textRef, boolean timeBounded, boolean flaggedOnly,
                                List<TypeCount> types) {

    /**
     * One event type (a checklist row).
     *
     * @param type     the dimension key: the callback when the record had one, otherwise its event
     * @param callback whether the type came from a callback — the checklist's section
     * @param total    records of this type
     * @param shown    records of this type that the OTHER filters (search, time, flagged) keep: what checking it shows
     * @param selected whether the event-type selection keeps this type
     */
    public record TypeCount(String type, boolean callback, int total, int shown, boolean selected) { }

    public RecordsViewCounts {
        types = types == null ? List.of() : List.copyOf(types);
    }

    /** Whether any filter hides at least one record. */
    public boolean anyHidden() {
        return shown < total;
    }

    /** Whether the log has records and every one of them is hidden. */
    public boolean allHidden() {
        return total > 0 && shown == 0;
    }

    /**
     * Measure {@code filter} over {@code idx}, with the flagged-only constraint the frame owns; {@code textRef} names the
     * filter's search text. The four predicates are {@link FilterState}'s own, so {@code shown} is exactly what the
     * table's row filter keeps.
     */
    public static RecordsViewCounts measure(LogIndex idx, FilterState filter, boolean flaggedOnly, IntPredicate flagged,
                                            long textRef) {
        int n = idx.size();
        int shown = 0, byTypes = 0, byText = 0, byTime = 0, byFlags = 0;
        Map<String, int[]> perType = new LinkedHashMap<>();       // type -> {total, shownUnderOthers, selectedRows}
        Set<String> callbacks = new HashSet<>();
        for (int row = 0; row < n; row++) {
            boolean types = filter.passesTypes(idx, row);
            boolean text = filter.passesText(idx, row);
            boolean time = filter.passesTime(idx, row);
            boolean flags = !flaggedOnly || flagged.test(row);
            if (!types) byTypes++;
            if (!text) byText++;
            if (!time) byTime++;
            if (!flags) byFlags++;
            if (types && text && time && flags) shown++;
            String key = FilterState.groupKey(idx, row, FilterState.GroupMode.DIMENSION);
            int[] c = perType.computeIfAbsent(key, k -> new int[3]);
            c[0]++;
            if (text && time && flags) c[1]++;
            if (types) c[2]++;
            if (idx.callback(row) != null) callbacks.add(key);
        }
        boolean typesFiltered = filter.dimensions() != null;
        List<TypeCount> rows = new ArrayList<>(perType.size());
        int selected = 0;
        for (Map.Entry<String, int[]> e : perType.entrySet()) {
            int[] c = e.getValue();
            boolean on = !typesFiltered || c[2] > 0;
            if (on) selected++;
            rows.add(new TypeCount(e.getKey(), callbacks.contains(e.getKey()), c[0], c[1], on));
        }
        rows.sort(Comparator.comparingInt(TypeCount::total).reversed().thenComparing(TypeCount::type));
        return new RecordsViewCounts(n, shown, byTypes, byText, byTime, byFlags, typesFiltered, selected, rows.size(),
                !filter.text().isEmpty(), textRef, filter.fromMillis() != null || filter.toMillis() != null, flaggedOnly, rows);
    }
}
