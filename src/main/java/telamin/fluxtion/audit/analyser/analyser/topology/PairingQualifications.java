package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The comparisons held against ONE published pairing (M68.1 rounds 3 and 4).
 *
 * <p>The rules, each written because a plain overwrite broke it:
 * <ul>
 *   <li><b>A narrower comparison never replaces a wider one</b> (round 3, N2). A whole-log comparison is the widest,
 *       and a filtered one is kept beside it.</li>
 *   <li><b>A whole-log comparison replaces every earlier filtered one</b>, because it dominates them: every id a filter
 *       can find undeclared, the whole log finds too.</li>
 *   <li><b>Two filtered comparisons do not erase each other's findings</b> (round 4, Q5a). Neither dominates the
 *       other, so every undeclared id any filtered comparison found is kept, with the filter it was found under,
 *       until a whole-log comparison replaces them all. The latest filtered comparison is the one shown beside the
 *       widest; its reply says what it replaced and what that had found.</li>
 * </ul>
 *
 * <p><b>Why ids, not every scope.</b> Keeping every filtered scope would grow without bound and fill the panel with
 * views nobody is looking at. An undeclared id, once found, stays a fact about this log and this graph (the holder is
 * reset when either changes, and a log only grows), so it is the part worth keeping; a clean filtered result is
 * superseded by the next one, and the reply says so.
 *
 * <p>Pure, so the rules are tested without a window.
 */
public final class PairingQualifications {

    private PairingQualification widest;
    private PairingQualification narrower;
    /** Undeclared id, and the filtered comparison that first found it. Emptied by a whole-log comparison. */
    private final Map<String, PairingQualification> filterFindings = new LinkedHashMap<>();

    /** Record the comparison just made. Returns the sentence the coverage reply states for it. */
    public String record(PairingQualification q) {
        if (q.wholeLog()) {
            widest = q;
            narrower = null;
            filterFindings.clear();
            return q.note();
        }
        PairingQualification replaced = narrower;
        narrower = q;
        for (String id : q.notDeclared()) filterFindings.putIfAbsent(id, q);
        StringBuilder said = new StringBuilder(q.note());
        if (replaced != null && !Objects.equals(replaced.filterKey(), q.filterKey())) {
            said.append(" · replaces, as the latest filtered comparison, the one under ")
                    .append(replaced.filterLabel() == null ? "an earlier filter" : replaced.filterLabel())
                    .append(", which found ")
                    .append(replaced.notDeclared().isEmpty() ? "every logged id declared"
                            : String.join(", ", replaced.notDeclared()) + " not declared — that finding is kept");
        }
        List<String> earlier = earlierFindings(q);
        if (!earlier.isEmpty()) said.append(" · still recorded from earlier filters: ").append(String.join("; ", earlier));
        if (widest != null) said.append(" · still in force from the whole log: ").append(widest.headline());
        return said.toString();
    }

    /**
     * M44.4c: an independent copy, so the session snapshot can hold these without sharing the node's mutable state.
     * The comparisons are immutable records, so copying the three holders is a deep copy.
     */
    public PairingQualifications copy() {
        PairingQualifications c = new PairingQualifications();
        c.widest = widest;
        c.narrower = narrower;
        c.filterFindings.putAll(filterFindings);
        return c;
    }

    /** Value equality, so an unchanged snapshot is recognised as unchanged and nothing repaints. */
    @Override
    public boolean equals(Object o) {
        return o instanceof PairingQualifications q && Objects.equals(widest, q.widest)
                && Objects.equals(narrower, q.narrower) && filterFindings.equals(q.filterFindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(widest, narrower, filterFindings);
    }

    public void clear() {
        widest = null;
        narrower = null;
        filterFindings.clear();
    }

    public boolean isEmpty() {
        return widest == null && narrower == null && filterFindings.isEmpty();
    }

    public PairingQualification widest(int logRecordsNow, String filterNow) {
        return widest == null ? null : widest.atView(logRecordsNow, filterNow);
    }

    public PairingQualification narrower(int logRecordsNow, String filterNow) {
        return narrower == null ? null : narrower.atView(logRecordsNow, filterNow);
    }

    /** Ids found by earlier filtered comparisons and not stated by the latest one, each with where it was found. */
    private List<String> earlierFindings(PairingQualification latest) {
        List<String> out = new ArrayList<>();
        filterFindings.forEach((id, by) -> {
            if (latest == null || !latest.notDeclared().contains(id)) {
                out.add(id + " (under " + (by.filterLabel() == null ? "an earlier filter" : by.filterLabel()) + ")");
            }
        });
        return out;
    }

    /** {@code context.graphPairing.qualifiedBy}: the widest leads, a narrower one rides beside it, findings kept. */
    public Map<String, Object> toMap(int logRecordsNow, String filterNow) {
        PairingQualification w = widest(logRecordsNow, filterNow);
        PairingQualification n = narrower(logRecordsNow, filterNow);
        if (w == null && n == null) return null;
        Map<String, Object> out = new LinkedHashMap<>((w != null ? w : n).toMap());
        if (w != null && n != null) out.put("narrower", n.toMap());
        List<Map<String, Object>> kept = new ArrayList<>();
        filterFindings.forEach((id, by) -> {
            if (n == null || !n.notDeclared().contains(id)) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("id", id);
                f.put("foundUnder", by.filterLabel());
                f.put("recordsCompared", by.recordsCompared());
                kept.add(f);
            }
        });
        if (!kept.isEmpty()) out.put("undeclaredFromEarlierFilters", kept);
        return out;
    }

    /**
     * The panel note. With a whole-log comparison, its finding leads (set 3). Without one, an undeclared id that any
     * filtered comparison found leads (round 4, Q5a): it is the most consequential fact known, and a clipped line would
     * otherwise lead with a sample that could not see it.
     */
    public String panelNote(GraphPairing published, int logRecordsNow, String filterNow) {
        PairingQualification w = widest(logRecordsNow, filterNow);
        PairingQualification n = narrower(logRecordsNow, filterNow);
        String base = PairingQualification.panelNote(published, w, n);
        if (w != null || filterFindings.isEmpty() || base == null) return base;
        return "filtered coverage found " + filterFindings.size() + " undeclared id(s) ("
                + String.join(", ", filterFindings.keySet()) + ") · " + base;
    }
}
