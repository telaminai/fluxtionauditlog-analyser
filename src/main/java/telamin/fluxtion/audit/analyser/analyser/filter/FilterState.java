package telamin.fluxtion.audit.analyser.analyser.filter;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * The one shared, observable filter used by the table, summary and (later) graph (spec §8, F3.1).
 * Combines a log-time range, a selected set of event dimensions, a free-text query and a grouping
 * mode. Mutating a field notifies listeners so all views refresh together.
 */
public final class FilterState {

    public enum GroupMode { DIMENSION, RAW_EVENT }

    private Long fromMillis;                 // null = unbounded
    private Long toMillis;                   // null = unbounded
    private Set<String> dimensions;          // null = all
    private String text = "";
    private GroupMode groupMode = GroupMode.DIMENSION;
    private IntFunction<String> textSource;   // row -> full searchable text (raw record incl. nodeLogs)

    private final List<Runnable> listeners = new ArrayList<>();

    public void addListener(Runnable r) {
        if (r != null) listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    public void fireChanged() {
        for (Runnable r : listeners) r.run();
    }

    public void setTimeRange(Long from, Long to) {
        this.fromMillis = from;
        this.toMillis = to;
        fireChanged();
    }

    /**
     * Set the selected dimensions. {@code null} means <b>all</b> (no constraint); an <b>empty</b> set
     * means <b>none</b> (every row is filtered out) — so "Select none" clears the view rather than
     * showing everything. A non-empty set keeps only rows whose dimension is in it (an OR).
     */
    public void setDimensions(Set<String> d) {
        this.dimensions = (d == null) ? null : new HashSet<>(d);
        fireChanged();
    }

    public void setText(String t) {
        this.text = (t == null) ? "" : t.trim();
        fireChanged();
    }

    public void setGroupMode(GroupMode m) {
        this.groupMode = m;
        this.dimensions = null;   // grouping changed → reset the selection to "all"
        fireChanged();
    }

    /**
     * Supplies the full searchable text for a row (the raw record, which includes eventToString,
     * thread and nodeLogs). When set, the text query matches against it so search also finds
     * node-log content. Does not fire a change.
     */
    public void setTextSource(IntFunction<String> textSource) {
        this.textSource = textSource;
    }

    public Long fromMillis() { return fromMillis; }
    public Long toMillis() { return toMillis; }
    public Set<String> dimensions() { return dimensions; }
    public String text() { return text; }
    public GroupMode groupMode() { return groupMode; }

    /** The group/filter key for a row under the current grouping mode. */
    public String groupKey(LogIndex idx, int row) {
        return groupKey(idx, row, groupMode);
    }

    public static String groupKey(LogIndex idx, int row, GroupMode mode) {
        String v = (mode == GroupMode.RAW_EVENT) ? idx.event(row) : idx.dimension(row);
        return v == null ? "" : v;
    }

    /** True if the row passes every active constraint. */
    public boolean test(LogIndex idx, int row) {
        Long lt = idx.logTime(row);
        if (lt != null) {
            if (fromMillis != null && lt < fromMillis) return false;
            if (toMillis != null && lt > toMillis) return false;
        }
        return testExceptTime(idx, row);
    }

    /**
     * Like {@link #test} but ignoring the time range — the dimension + text constraints only. The graph
     * uses this to extract series <b>across all time</b> once (cached), then windows by time via the
     * chart's view, so dragging the time slider never re-parses the log.
     */
    public boolean testExceptTime(LogIndex idx, int row) {
        if (dimensions != null && !dimensions.contains(groupKey(idx, row))) return false;
        if (!text.isEmpty()) {
            String needle = text.toLowerCase();
            boolean match;
            if (textSource != null) {
                // full-text over the raw record: covers eventToString, thread AND nodeLogs
                String raw = textSource.apply(row);
                match = raw != null && raw.toLowerCase().contains(needle);
            } else {
                String es = idx.eventToString(row);
                String th = idx.thread(row);
                match = (es != null && es.toLowerCase().contains(needle))
                        || (th != null && th.toLowerCase().contains(needle));
            }
            if (!match) return false;
        }
        return true;
    }
}
