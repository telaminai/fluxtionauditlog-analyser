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
 * first and <em>say that it did</em>. Before this, a coverage run that found a foreign id past the sample left
 * {@code context.graphPairing} reading "declares all 3 node(s) this log writes" beside a warning that it did not.
 *
 * <p>The qualification is bound by the caller to the exact pairing object it qualifies, and is dropped the moment
 * that pairing is replaced, so it can never outlive the log or graph it was computed against.
 *
 * @param scope               "whole log" or "current filter", as coverage reported it
 * @param recordsCompared     records coverage scanned
 * @param established         whether coverage saw any node output at all in its scope
 * @param loggedIds           distinct node ids coverage saw
 * @param declaredOfLogged    how many of those the graph declares
 * @param notDeclared         the ids the graph does not declare, capped at twenty
 * @param supersedesSample    true when coverage looked at the whole log and the published pairing did not
 */
public record PairingQualification(String scope, int recordsCompared, boolean established, int loggedIds,
                                   int declaredOfLogged, List<String> notDeclared, boolean supersedesSample) {

    public PairingQualification {
        notDeclared = notDeclared == null ? List.of() : List.copyOf(notDeclared);
    }

    /** Built from coverage's own echo, so the two can never state different numbers. */
    @SuppressWarnings("unchecked")
    public static PairingQualification fromCoverage(GraphPairing published, Map<String, Object> coverageEcho) {
        Map<String, Object> m = (Map<String, Object>) coverageEcho.getOrDefault("membership", Map.of());
        String scope = String.valueOf(m.getOrDefault("scope", coverageEcho.getOrDefault("scope", "whole log")));
        int records = intOf(coverageEcho.get("recordsScanned"));
        List<String> foreign = coverageEcho.get("loggedButNotInTopology") instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        boolean wholeLog = "whole log".equals(scope);
        boolean publishedPartial = published == null || published.sampled()
                || published.recordsScanned() < 0;
        return new PairingQualification(scope, records, Boolean.TRUE.equals(m.get("established")),
                intOf(m.get("loggedIds")), intOf(m.get("declaredOfLogged")), foreign,
                wholeLog && publishedPartial);
    }

    public boolean everyObservedIdDeclared() {
        return established && declaredOfLogged == loggedIds;
    }

    /** The sentence every surface states. It says what was compared, what it found, and what it changes. */
    public String note() {
        String where = "coverage compared the " + scope + " (" + recordsCompared + " records) against every "
                + "declared node";
        if (!established) {
            return where + " and found no node output, so membership is not established there either";
        }
        if (everyObservedIdDeclared()) {
            return where + ": all " + loggedIds + " logged id(s) are declared"
                    + (supersedesSample ? " — this confirms the sampled pairing for the whole log" : "");
        }
        return where + ": " + (loggedIds - declaredOfLogged) + " of " + loggedIds + " logged id(s) are not declared ("
                + String.join(", ", notDeclared) + ")"
                + (supersedesSample ? " — this supersedes the sampled pairing, which could not see them" : "");
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
        out.put("note", note());
        return out;
    }

    private static int intOf(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
