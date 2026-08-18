package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of time-order validation (spec-rolled-logs D-R3): clean, or a bounded list of violations
 * with anchors. Violations are REPORTED, never repaired — a backwards timestamp IS a finding (a clock
 * step, a mis-merge, a bad transport), and re-sorting records would destroy the evidence. Both checks
 * read {@code logTime} and only {@code logTime} — {@code eventTime} carries a {@code -1} sentinel on
 * exported-service calls and is never consulted (review R3).
 */
public record TimeOrderReport(List<Violation> violations) {

    /** Bounded per report — the summary names how many more exist. */
    public static final int MAX_VIOLATIONS = 50;

    public enum Kind {
        /** A file's first timed record precedes the previous file's last (cross-file continuity). */
        FILE_OVERLAP,
        /** Records within one file go backwards (within-file monotonicity). */
        OUT_OF_ORDER,
        /** A file with no timed record — positioned by NAME, order unverifiable (D-R1). */
        UNTIMED_FILE
    }

    /** {@code recordIndex} is file-local and -1 when the violation is file-level. */
    public record Violation(Kind kind, String file, int recordIndex, String message) {
    }

    public static TimeOrderReport clean() {
        return new TimeOrderReport(List.of());
    }

    public boolean isClean() {
        return violations.isEmpty();
    }

    /** One line per violation, for banners / verb echoes / {@code context}. */
    public List<String> summarise() {
        List<String> out = new ArrayList<>();
        for (Violation v : violations) out.add(v.message());
        return out;
    }

    public TimeOrderReport merged(TimeOrderReport other) {
        if (other == null || other.isClean()) return this;
        if (isClean()) return other;
        List<Violation> all = new ArrayList<>(violations);
        all.addAll(other.violations());
        return new TimeOrderReport(List.copyOf(all));
    }
}
