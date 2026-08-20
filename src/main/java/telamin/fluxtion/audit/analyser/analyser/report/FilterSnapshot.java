package telamin.fluxtion.audit.analyser.analyser.report;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The view a report was authored under (spec-investigation-reports D-I3a, second half): the filter
 * context is part of the claim. Evidence sections re-render live — under the CURRENT filter — while
 * the narrative was written against a specific extraction; without this capture the same prose can
 * sit over silently different-but-resolved evidence, one document carrying two accounts of itself.
 *
 * <p>The rendering rule is OFFER, never act (M20.5/D-R5's pattern): the stored context is proposed on
 * open; declining renders under the current filter with {@link #difference} stated on the page.
 *
 * @param fromMillis time-range lower bound, or {@code null} = unbounded
 * @param toMillis   time-range upper bound, or {@code null}
 * @param dimensions selected dimensions, or {@code null} = all (matching {@link FilterState}'s rule)
 * @param text       the free-text query, never null
 * @param groupMode  DIMENSION or RAW_EVENT
 */
public record FilterSnapshot(Long fromMillis, Long toMillis, Set<String> dimensions,
                             String text, FilterState.GroupMode groupMode) {

    public FilterSnapshot {
        text = text == null ? "" : text;
        dimensions = dimensions == null ? null : Set.copyOf(dimensions);
        groupMode = groupMode == null ? FilterState.GroupMode.DIMENSION : groupMode;
    }

    public static FilterSnapshot of(FilterState f) {
        return new FilterSnapshot(f.fromMillis(), f.toMillis(), f.dimensions(), f.text(), f.groupMode());
    }

    /** An unconstrained view — what a fresh log opens under. */
    public static FilterSnapshot all() {
        return new FilterSnapshot(null, null, null, "", FilterState.GroupMode.DIMENSION);
    }

    /**
     * COMPARE, second half of D-I3a: empty when the current filter matches this snapshot; otherwise a
     * one-line description of what differs, composed here so every surface says the same words.
     */
    public Optional<String> difference(FilterState current) {
        StringBuilder d = new StringBuilder();
        if (!java.util.Objects.equals(fromMillis, current.fromMillis())
                || !java.util.Objects.equals(toMillis, current.toMillis())) {
            d.append("time range");
        }
        Set<String> cur = current.dimensions();
        boolean dimsDiffer = (dimensions == null) != (cur == null)
                || (dimensions != null && !dimensions.equals(cur));
        if (dimsDiffer) d.append(d.isEmpty() ? "" : ", ").append("event types");
        if (!text.equals(current.text())) d.append(d.isEmpty() ? "" : ", ").append("text query");
        if (groupMode != current.groupMode()) d.append(d.isEmpty() ? "" : ", ").append("grouping");
        if (d.isEmpty()) return Optional.empty();
        return Optional.of("authored under a different view (" + d
                + " differ) — the stored context can be applied, or the page renders under the "
                + "current filter and says so");
    }

    /** The OFFER accepted: apply this snapshot to the live filter (one change event). */
    public void applyTo(FilterState f) {
        f.setGroupMode(groupMode);                    // resets dimensions; order matters
        f.setDimensions(dimensions == null ? null : new java.util.HashSet<>(dimensions));
        f.setText(text);
        f.setTimeRange(fromMillis, toMillis);
    }

    /** Stable, human-readable summary for persistence echoes and the rendered page. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(dimensions == null ? "all event types" : new TreeSet<>(dimensions).toString());
        if (!text.isEmpty()) sb.append(" · text \"").append(text).append('"');
        if (fromMillis != null || toMillis != null) sb.append(" · time-bounded");
        if (groupMode == FilterState.GroupMode.RAW_EVENT) sb.append(" · grouped by raw event");
        return sb.toString();
    }
}
