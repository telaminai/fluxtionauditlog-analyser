package telamin.fluxtion.audit.analyser.analyser.report;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.Optional;

/**
 * The identity of the log a report was authored against (spec-investigation-reports D-I3a): name,
 * record count, first and last {@code logTime}. Identity data, never evidence — a report stores
 * REFERENCES (D-I3), and references only mean anything relative to a specific log. The dangerous
 * failure D-I3a exists for arrives RESOLVED, not dangling: {@code recordIndex 42} resolves against
 * any log with 43 records, so without this comparison a report opened on the wrong log renders
 * confidently wrong evidence under narrative written about different data.
 *
 * @param logName   the log's display name (file name, or "name (+N rolled)" for a set)
 * @param records   how many records the log held at authoring
 * @param firstTime first timed record's logTime, or {@code null} when the log had none
 * @param lastTime  last timed record's logTime, or {@code null}
 */
public record LogFingerprint(String logName, int records, Long firstTime, Long lastTime) {

    public static LogFingerprint of(LogIndex idx, String logName) {
        Long first = null, last = null;
        for (int i = 0; i < idx.size(); i++) {
            Long lt = idx.logTime(i);
            if (lt != null) { first = lt; break; }
        }
        for (int i = idx.size() - 1; i >= 0; i--) {
            Long lt = idx.logTime(i);
            if (lt != null) { last = lt; break; }
        }
        return new LogFingerprint(logName == null ? "" : logName, idx.size(), first, last);
    }

    /**
     * The D-I3a rendering rule's first half: COMPARE, and if the loaded log differs, ANNOUNCE — the
     * message is composed here so every surface (panel, PDF, verb echo) says the same words. Returns
     * empty when the fingerprints agree.
     *
     * <p>The VERDICT is deliberately coarse and compares CONTENT only — record count and time range.
     * It separates the two cases that matter — same log (re-verification, D-I3's best property) vs a
     * different or moved-on log (misapplication, or evidence that the ground moved) — without
     * pretending to a byte-level identity a references-only artefact cannot honestly claim. A file
     * renamed or re-opened from another directory is therefore not a mismatch, which is intended.
     *
     * <p>The name is not part of the verdict, but it IS part of the message: {@code loaded} must
     * carry the name of the log actually open, because the announce line's whole job is to say which
     * log you are looking at instead.
     */
    public Optional<String> mismatch(LogFingerprint loaded) {
        if (loaded == null) return Optional.of("written against " + describe() + "; no log is loaded");
        if (!sameContent(loaded)) {
            return Optional.of("written against " + describe() + "; the loaded log differs ("
                    + loaded.describe() + ")");
        }
        // Q1 (owner decision, review of feat/m33-reports): same content under a DIFFERENT name gets
        // the softer announce — "same content, different file" is still a fact the reader needs, but
        // a legitimate copy/rename must not wear the strong different-log banner. Same content, same
        // name stays quiet.
        if (!logName.isEmpty() && !loaded.logName().isEmpty() && !logName.equals(loaded.logName())) {
            return Optional.of("written against '" + logName + "'; the loaded log matches on content "
                    + "but is a different file ('" + loaded.logName() + "')");
        }
        return Optional.empty();
    }

    /** Content identity — what the verdict turns on. The display name is excluded by design. */
    public boolean sameContent(LogFingerprint other) {
        return other != null && records == other.records
                && java.util.Objects.equals(firstTime, other.firstTime)
                && java.util.Objects.equals(lastTime, other.lastTime);
    }

    /** "demo.yaml · 726 records · 09:00:04.500→09:18:12.000" — the identity, human-readable. */
    public String describe() {
        StringBuilder sb = new StringBuilder(logName.isEmpty() ? "(unnamed log)" : logName);
        sb.append(" · ").append(records).append(" record(s)");
        if (firstTime != null && lastTime != null) {
            sb.append(" · ").append(fmt(firstTime)).append("→").append(fmt(lastTime));
        }
        return sb.toString();
    }

    private static String fmt(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
    }
}
