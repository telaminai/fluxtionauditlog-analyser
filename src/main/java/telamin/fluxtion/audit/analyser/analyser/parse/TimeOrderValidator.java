package telamin.fluxtion.audit.analyser.analyser.parse;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * Within-file monotonicity (spec-rolled-logs D-R3, second half): {@code logTime} must be
 * non-decreasing record to record <b>within each member file</b> — the assumption `read {at}`'s
 * binary search, rolling windows and buckets have leaned on since M26/M28 (A2), finally CHECKED, for
 * single files as much as for sets. Violations are counted per file with a first-violation anchor;
 * records are never re-ordered (a backwards timestamp IS a finding).
 */
public final class TimeOrderValidator {

    private TimeOrderValidator() {
    }

    public static TimeOrderReport validate(LogIndex idx) {
        return validate(idx, TimeOrderReport.MAX_VIOLATIONS);
    }

    /** {@code maxViolations} parameterised for tests; production callers use the report's cap. */
    static TimeOrderReport validate(LogIndex idx, int maxViolations) {
        List<TimeOrderReport.Violation> out = new ArrayList<>();
        int files = idx.fileCount();
        int unexamined = 0;
        for (int f = 0; f < files; f++) {
            long prev = Long.MIN_VALUE;
            int count = 0;
            int firstAt = -1;
            for (int i = 0; i < idx.size(); i++) {
                if (files > 1 && idx.fileId(i) != f) continue;
                Long lt = idx.logTime(i);
                if (lt == null) continue;             // untimed records are ordinary; they order nothing
                if (lt < prev) {
                    count++;
                    if (firstAt < 0) firstAt = i;
                } else {
                    prev = lt;
                }
            }
            if (count > 0) {
                String name = files > 1 ? idx.files().get(f) : "this log";
                out.add(new TimeOrderReport.Violation(TimeOrderReport.Kind.OUT_OF_ORDER, name, firstAt,
                        count + " record(s) out of time order within " + (files > 1 ? "'" + name + "'" : name)
                                + ", first at record " + firstAt
                                + " — time-anchored features may be approximate; records are never re-sorted"));
            }
            if (out.size() >= maxViolations) {
                unexamined = files - f - 1;   // the cap keeps its promise: dropped work is COUNTED (F1)
                break;
            }
        }
        return new TimeOrderReport(List.copyOf(out), unexamined);
    }
}
