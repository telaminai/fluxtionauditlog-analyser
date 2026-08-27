package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * M40.3 — what audit LEVEL was this log captured at, and what does that hide?
 *
 * <h2>Why this is read from the LOG and not from the graph</h2>
 * The slice was recorded as "only if the graph distinguishes INFO from TRACE". It does not, and two
 * separate readings say so rather than one guess:
 * <ul>
 *   <li>The compiler's GraphML carries {@code id}, {@code class} and a style per node and nothing
 *       else — the demo fixture, emitted by a real build with audit installed, contains no level
 *       string anywhere.</li>
 *   <li>Even if it did, it would be the wrong fact. {@code addEventAudit(LogLevel.INFO)} is a
 *       build-time default, but {@code DataFlow.setAuditLogLevel(…)} resets it at runtime — the M40.1
 *       harness does exactly that, after {@code init()}. A graph states what was BUILT; a level
 *       claimed from it would be a statement about a different run than the one in hand.</li>
 * </ul>
 * So the evidence is the artefact the user actually has: every record header carries the level it was
 * written at.
 *
 * <h2>What can honestly be concluded</h2>
 * Observed levels give a LOWER BOUND on the threshold and nothing more. A log with only INFO records
 * was captured at INFO or finer; the absence of DEBUG records means either the threshold excluded
 * them or nothing called {@code debug()}. <b>The log cannot distinguish those</b>, so this class does
 * not try: it states what is present and names what that would hide, which is the same two-facts-and-
 * no-verdict discipline the rest of the analyser uses.
 *
 * <p>This matters because it is a FOURTH cause of a coverage gap, alongside the three
 * {@code NodeCoverage} already lists: a node may have run, logged, and had its output discarded for
 * being below the captured level.
 */
public record AuditLevel(List<String> observed, String mostVerbose) {

    /** Least to most verbose, as read from {@code EventLogControlEvent.LogLevel} in the runtime jar. */
    private static final List<String> ORDER = List.of("NONE", "ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    public AuditLevel {
        observed = List.copyOf(observed);
    }

    public static AuditLevel none() {
        return new AuditLevel(List.of(), null);
    }

    /** @param levels the {@code level} of every record, duplicates and nulls welcome */
    public static AuditLevel of(Collection<String> levels) {
        if (levels == null) return none();
        Set<String> seen = new LinkedHashSet<>();
        for (String l : levels) {
            if (l != null && !l.isBlank()) seen.add(l.trim().toUpperCase(Locale.ROOT));
        }
        if (seen.isEmpty()) return none();
        List<String> sorted = seen.stream()
                .sorted(java.util.Comparator.comparingInt(AuditLevel::rank))
                .toList();
        return new AuditLevel(sorted, sorted.get(sorted.size() - 1));
    }

    /** Unknown level names sort last: a level we do not recognise is not evidence of a low threshold. */
    private static int rank(String level) {
        int i = ORDER.indexOf(level);
        return i < 0 ? ORDER.size() : i;
    }

    /** Levels finer than the most verbose one seen — the ones that would have been discarded. */
    public List<String> hidden() {
        int top = rank(mostVerbose);
        if (mostVerbose == null || top >= ORDER.size()) return List.of();
        return ORDER.subList(top + 1, ORDER.size());
    }

    /**
     * The statement a reader is owed when a coverage gap might be a level, not a silence. Null when
     * there is nothing to say — no records, or the log is already at the finest level.
     */
    public String note() {
        if (mostVerbose == null || hidden().isEmpty()) return null;
        String finer = String.join(" or ", hidden().stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
        return "every record in this log was written at " + String.join("/", observed)
                + "; nothing finer than " + mostVerbose + " appears. If any node logs at " + finer
                + ", those calls wrote nothing here — so a node missing from this log may have run and "
                + "logged below the captured level. This log cannot tell those apart: set the level "
                + "with setAuditLogLevel before the run to rule it out.";
    }

    /** The verdict as data, for the echo. */
    public java.util.Map<String, Object> echo() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (mostVerbose == null) return out;
        out.put("auditLevels", observed);
        out.put("auditLevelFinest", mostVerbose);
        if (note() != null) out.put("auditLevelNote", note());
        return out;
    }
}
