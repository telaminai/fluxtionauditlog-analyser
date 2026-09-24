package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a later, broader membership comparison says about a published pairing (M68.1 re-review R2, D-E2,
 * acceptance 3).
 *
 * <p>The pairing shown on open is judged on a sample. {@code coverage} compares every node id in its scope
 * against every declared node. When the two differ, the second must be able to <em>qualify or replace</em> the
 * first and <em>say that it did</em>.
 *
 * <p><b>Bound to a log revision, not only to a pairing (round 3, N1).</b> A Follow append changes the log without
 * replacing the pairing, so binding to the pairing object alone let a verdict about 600 records keep saying
 * "confirms the sampled pairing for the whole log" about a log of 601 that had since gained an undeclared id. A
 * qualification now records how many records the log held when it was computed, {@link #logRecords}, and is read
 * at the log's current size through {@link #atLogSize}. A grown log makes it {@link #stale()}: it still states what
 * it compared, precisely, and it no longer claims to speak for the whole log.
 *
 * <p><b>Why stale, not dropped.</b> Dropping would put the sampled verdict back in the lead of a clipped status line
 * after coverage had already proved it wrong — the defect round 3's N2 found by another route. A stale
 * qualification is still the widest comparison anyone has made, and it says so.
 *
 * @param scope             "whole log" or "current filter", as coverage reported it
 * @param recordsCompared   records coverage scanned
 * @param established       whether coverage saw any node output at all in its scope
 * @param loggedIds         distinct node ids coverage saw
 * @param declaredOfLogged  how many of those the graph declares
 * @param notDeclared       the ids the graph does not declare, capped at twenty
 * @param supersedesSample  true when coverage looked at the whole log and the published pairing did not
 * @param logRecords        records the log held when coverage compared it
 * @param logRecordsNow     records the log holds as this is read; greater than {@code logRecords} means stale
 */
public record PairingQualification(String scope, int recordsCompared, boolean established, int loggedIds,
                                   int declaredOfLogged, List<String> notDeclared, boolean supersedesSample,
                                   int logRecords, int logRecordsNow) {

    public PairingQualification {
        notDeclared = notDeclared == null ? List.of() : List.copyOf(notDeclared);
    }

    /** Built from coverage's own echo, so the two can never state different numbers. */
    @SuppressWarnings("unchecked")
    public static PairingQualification fromCoverage(GraphPairing published, Map<String, Object> coverageEcho) {
        Map<String, Object> m = (Map<String, Object>) coverageEcho.getOrDefault("membership", Map.of());
        String scope = String.valueOf(m.getOrDefault("scope", coverageEcho.getOrDefault("scope", "whole log")));
        int records = intOf(coverageEcho.get("recordsScanned"));
        int logRecords = coverageEcho.get("logRecords") instanceof Number n ? n.intValue() : records;
        List<String> foreign = coverageEcho.get("loggedButNotInTopology") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        boolean wholeLog = "whole log".equals(scope);
        boolean publishedPartial = published == null || published.sampled()
                || published.recordsScanned() < 0;
        return new PairingQualification(scope, records, Boolean.TRUE.equals(m.get("established")),
                intOf(m.get("loggedIds")), intOf(m.get("declaredOfLogged")), foreign,
                wholeLog && publishedPartial, logRecords, logRecords);
    }

    /** This qualification as read against a log that now holds {@code records} records. */
    public PairingQualification atLogSize(int records) {
        return new PairingQualification(scope, recordsCompared, established, loggedIds, declaredOfLogged,
                notDeclared, supersedesSample, logRecords, Math.max(records, logRecords));
    }

    /** True when the log has grown since coverage compared it: this is then a verdict about an older revision. */
    public boolean stale() {
        return logRecordsNow > logRecords;
    }

    public boolean wholeLog() {
        return "whole log".equals(scope);
    }

    public boolean everyObservedIdDeclared() {
        return established && declaredOfLogged == loggedIds;
    }

    /** What was compared, precise even when stale: a grown whole log is "the first N of M records". */
    private String scopeNow() {
        if (!stale()) return scope;
        return wholeLog() ? "first " + logRecords + " of " + logRecordsNow + " records"
                : "current filter, before the log grew to " + logRecordsNow + " records";
    }

    /** The sentence every surface states. It says what was compared, what it found, and what it changes. */
    public String note() {
        String where = "coverage compared the " + (stale() ? scopeNow() : scope) + " ("
                + recordsCompared + " records) against every declared node";
        String grew = stale() ? " — the log has grown since, so this no longer covers the whole log; run "
                + "coverage again" : "";
        if (!established) {
            return where + " and found no node output, so membership is not established there either" + grew;
        }
        if (everyObservedIdDeclared()) {
            return where + ": all " + loggedIds + " logged id(s) are declared"
                    + (stale() ? grew : supersedesSample ? " — this confirms the sampled pairing for the whole log" : "");
        }
        return where + ": " + (loggedIds - declaredOfLogged) + " of " + loggedIds + " logged id(s) are not declared ("
                + String.join(", ", notDeclared) + ")"
                + (stale() ? grew : supersedesSample ? " — this supersedes the sampled pairing, which could not see them" : "");
    }

    /** The finding in a few words, scope first — what a clipped status line must show before anything else. */
    public String headline() {
        String s = scopeNow();
        if (!established) return s + ": no node output, membership not established";
        if (everyObservedIdDeclared()) return s + ": all " + loggedIds + " logged id(s) declared";
        return s + ": " + (loggedIds - declaredOfLogged) + " of " + loggedIds + " logged id(s) not declared ("
                + String.join(", ", notDeclared) + ")";
    }

    /**
     * The Topology panel's pairing note. The panel's status line is clipped, so the FIRST words must be the ones
     * that are currently true (set 3): once a whole-log comparison exists, its finding leads and the sampled verdict
     * follows. A narrower comparison is appended after both, never in place of the wider one (round 3, N2).
     */
    public static String panelNote(GraphPairing published, PairingQualification widest, PairingQualification narrower) {
        if (published == null) return null;
        String note;
        if (widest != null && widest.supersedesSample()) {
            note = widest.headline() + (widest.stale() ? " — the log has grown since coverage ran"
                    : widest.everyObservedIdDeclared() ? " — confirms" + " the sample taken on open"
                    : " — supersedes the sample taken on open") + " · on open: " + published.note();
        } else if (widest != null) {
            note = published.note() + " · " + widest.headline()
                    + (widest.stale() ? " — the log has grown since coverage ran" : "");
        } else {
            note = published.note();
        }
        return narrower == null ? note : note + " · " + narrower.headline();
    }

    /** As {@link #panelNote(GraphPairing, PairingQualification, PairingQualification)} for one comparison. */
    public static String panelNote(GraphPairing published, PairingQualification q) {
        if (q == null) return panelNote(published, null, null);
        return q.wholeLog() ? panelNote(published, q, null) : panelNote(published, null, q);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("by", "coverage");
        out.put("scope", scope);
        out.put("recordsCompared", recordsCompared);
        out.put("membershipEstablished", established);
        out.put("everyObservedIdDeclared", everyObservedIdDeclared());
        out.put("notDeclared", notDeclared);
        out.put("supersedesSample", supersedesSample);
        out.put("stale", stale());
        out.put("logRecordsAtComparison", logRecords);
        out.put("logRecordsNow", logRecordsNow);
        out.put("note", note());
        return out;
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
