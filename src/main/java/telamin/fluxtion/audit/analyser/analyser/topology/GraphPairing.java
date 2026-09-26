package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.Set;

/**
 * Does a loaded graph still describe the log that was just opened? (M35.2)
 *
 * <p>M35.1 gave the app a way to close a graph. This is the question it must answer automatically,
 * because the failure it prevents is silent: open a second log and the FIRST log's topology stays on
 * screen, and coverage, "did not run" shading and step-through then describe a graph that has nothing
 * to do with the records. Reproduced on the shipped app in M35.1's report — a 21-record market-maker
 * log with a 301-node supermarket graph loaded, and coverage happily reporting 301 declared nodes.
 *
 * <p><b>Reuses {@link NodeCoverage}, deliberately.</b> The comparison "which logged instanceIds does
 * this graph declare?" already exists and already names the reverse direction as the interesting
 * fault — {@code loggedButNotInTopology}, ids written that the graph does not declare. A second scorer
 * would be a second answer to one question, and they would drift. (That field used to be glossed as
 * "the graphml is probably from a different build"; a name mismatch does not establish that, so it is
 * now reported as the fact it is.)
 *
 * <p><b>The verdict is the same; the ACTION depends on which artefact just arrived.</b>
 * <ul>
 *   <li><b>A log arrives</b> and finds a graph already there (M35.2): that graph is <i>residue</i>
 *       from the previous investigation, nobody asked for it here, and it is CLOSED. An offer was
 *       considered and loses twice over — a modal on every open is friction for a human, and on the
 *       agent path there is nobody to answer it, so it would be silently defaulted; defaulting to
 *       "keep" is precisely the defect.</li>
 *   <li><b>A graph arrives</b> against an open log (M35.3): that graph is <i>intent</i> — someone
 *       named this processor — so a mismatch is announced and the graph is KEPT. Announce-never-forbid
 *       (D-I3a) applies where there is an intention to respect, and comparing build A's graph with
 *       build B's log is a real forensic act.</li>
 * </ul>
 * Which is why {@link #reason} states the FACT and never the action: one comparison, two verbs.
 *
 * <p>Pure: no log, no Swing, no IO.
 *
 * <p><b>Three questions, not one (M68.1, D-E1).</b> {@link #applies()} is a <em>retention policy</em> — keep
 * this graph open against this log — and it is deliberately permissive: it keeps a graph when a majority of
 * the observed ids are declared, and it keeps one when nothing was logged at all, because silence cannot
 * convict it. Neither of those is evidence that the graph describes the log. So the policy boolean never
 * travels alone: {@link #evidenced()} says whether any membership comparison was possible,
 * {@link #everyObservedIdDeclared()} says whether it found no foreign id, and {@link #sampled()} says the
 * comparison covered only part of the log. A surface that wants to say "this graph describes this log"
 * needs all of them, and must not read {@code applies=true} as that statement. Before this, a log with no
 * node output produced {@code applies=true}, the coverage policy read it as a fit, and returned its fullest
 * verdict — "the graph is declared, describes this log" — about a pairing that had never been judged.
 *
 * <p><b>Wording.</b> The reason states what was counted. It no longer concludes that a low overlap means a
 * "different system or build": matching names establish nothing about build identity either way, and the
 * reader, not the tool, decides which artefact is right (M68, conformance note).
 *
 * @param logged          distinct instanceIds seen in the records the comparison looked at
 * @param matched         how many of those the graph declares
 * @param applies         whether the graph should be KEPT — a policy, never a proof of fit
 * @param reason          the FACT, always populated and always carrying the numbers. Deliberately not the
 *                        ACTION: the same verdict closes a stale graph on log-open and merely warns about a
 *                        deliberately-opened one (M35.3), so each caller supplies its own verb
 * @param recordsScanned  how many records the comparison read, or {@code -1} when the caller did not say
 * @param recordsTotal    how many records the log holds, or {@code -1} when the caller did not say
 */
public record GraphPairing(int logged, int matched, boolean applies, String reason,
                           int recordsScanned, int recordsTotal) {

    /** A comparison whose scope was not recorded — a pure call with no log in hand. */
    public GraphPairing(int logged, int matched, boolean applies, String reason) {
        this(logged, matched, applies, reason, -1, -1);
    }

    /**
     * The one-word state this verdict is recorded under in the session's own audit log (review O3). That log
     * is openable in the analyser, so it must not state the retention decision as the fit: a graph kept with
     * nothing compared is {@code keptUnjudged}, one kept on a partial match {@code keptPartial}, and only a
     * comparison that found every observed id declared is {@code applies}.
     */
    public String auditLabel() {
        if (!applies) return "doesNotApply";
        if (!evidenced()) return "keptUnjudged";
        return everyObservedIdDeclared() ? "applies" : "keptPartial";
    }

    /** True when there was something to compare: at least one observed node id. */
    public boolean evidenced() {
        return logged > 0;
    }

    /** True when the comparison found no observed id missing from the graph. Implies {@link #evidenced()}. */
    public boolean everyObservedIdDeclared() {
        return evidenced() && matched == logged;
    }

    /** True when the comparison read only part of the log. */
    public boolean sampled() {
        return recordsScanned >= 0 && recordsTotal > recordsScanned;
    }

    /** The observation scope in words: "first 500 of 2,000 records", "all 21 records", or unrecorded. */
    public String scope() {
        if (recordsScanned < 0 || recordsTotal < 0) return "scope not recorded";
        return sampled()
                ? "first " + recordsScanned + " of " + recordsTotal + " records"
                : "all " + recordsTotal + " records";
    }

    /**
     * The same verdict, re-stated for a log that now holds {@code total} records (round 3, N1). A Follow append
     * leaves the first {@link #recordsScanned} records — the ones this verdict compared — unchanged, so the
     * counts stand and only the scope moves. Before this, a followed log of 601 records went on publishing
     * "first 500 of 600 records". The caller re-judges instead when the sample itself could grow.
     */
    public GraphPairing rescoped(int total) {
        if (recordsScanned < 0 || total == recordsTotal) return this;
        String base = reason.replaceFirst(" \\(judged on the first \\d+ of \\d+ records\\)$", "");
        return new GraphPairing(logged, matched, applies, base, recordsScanned, -1).withScope(recordsScanned, total);
    }

    /**
     * The facts {@link #applies()} does not carry, for every agent surface to state beside it, so none of
     * them can be read as "the graph describes this log" when only the retention policy said keep (M68.1).
     */
    public java.util.Map<String, Object> facts() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("appliesMeans", "retention policy: keep this graph open against this log — not proof of fit");
        m.put("membershipEstablished", evidenced());
        m.put("everyObservedIdDeclared", everyObservedIdDeclared());
        m.put("pairingScope", scope());
        m.put("pairingSampled", sampled());
        return m;
    }

    /**
     * The persistent human note, in the states the verdict can actually be in (M68.1, D-E1/D-E2). "fits
     * this log" used to cover all of them, including a graph kept against a log with no node output and a
     * graph kept on a partial match — neither of which the comparison showed to fit.
     */
    public String note() {
        // Re-review set 3 (P14): the SCOPE leads a sampled note. At the end, as it was, the clip fell on it, so
        // a 500-record sample read on screen as "every node id checked is declared" with nothing saying which.
        String lead = sampled() ? scope() + ": " : "";
        if (!applies) return "\u26a0 DOES NOT FIT THIS LOG \u2014 " + reason;
        if (!evidenced()) return lead + "kept, not confirmed \u2014 no node output in the records checked";
        if (!everyObservedIdDeclared()) {
            return lead + "kept on a partial match (" + matched + "/" + logged + " ids declared)";
        }
        return lead + "every node id checked is declared (" + matched + "/" + logged + ")";
    }

    /**
     * The same verdict, told what it looked at. A sampled comparison says so in its reason as well as in
     * its fields: the numbers describe the sample, and "the N node(s) this log writes" is otherwise a
     * whole-log claim the comparison never checked (review F1).
     */
    public GraphPairing withScope(int scanned, int total) {
        String said = total > scanned
                ? reason + " (judged on the first " + scanned + " of " + total + " records)"
                : reason;
        return new GraphPairing(logged, matched, applies, said, scanned, total);
    }

    /**
     * Keep the graph when a majority of what the log logged is declared in it. A threshold is
     * arbitrary by nature, which is why the numbers travel with the verdict — a caller that
     * disagrees can see exactly what was counted. What it must separate is the honest case (a
     * slightly different build of the same system: most ids match, a few are new) from the defect
     * (a different system entirely: no overlap at all).
     */
    public static final double KEEP_ABOVE = 0.5;

    /** All declared ids, independent of scaffolding visibility (TA-1). Also used at the session input boundary. */
    public static Set<String> declaredNodeIds(ProcessorTopology topology) {
        if (topology == null) return Set.of();
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (var node : topology.nodes()) ids.add(node.id());
        return java.util.Collections.unmodifiableSet(ids);
    }

    public static GraphPairing of(Set<String> declared, Set<String> logged) {
        if (declared == null || declared.isEmpty()) {
            return new GraphPairing(logged == null ? 0 : logged.size(), 0, false,
                    "the loaded graph declares no nodes");
        }
        if (logged == null || logged.isEmpty()) {
            // nothing logged says nothing about the graph — a log with no nodeLogs cannot convict it
            // policy: kept. evidence: none. evidenced() is false, so no surface may call this a fit
            return new GraphPairing(0, 0, true,
                    "no node output was recorded in the records checked, so no membership comparison was "
                            + "possible — the graph is kept, not confirmed");
        }
        NodeCoverage cov = NodeCoverage.of(declared, logged, Set.of());
        int matched = logged.size() - cov.loggedButNotInTopology().size();
        double share = (double) matched / logged.size();
        if (share > KEEP_ABOVE) {
            return new GraphPairing(logged.size(), matched, true,
                    matched == logged.size()
                            ? "the graph declares all " + matched + " node(s) this log writes"
                            : "the graph declares " + matched + " of the " + logged.size()
                              + " node(s) this log writes; " + (logged.size() - matched)
                              + " written id(s) are not in the graph");
        }
        return new GraphPairing(logged.size(), matched, false,
                "the graph declares only " + matched + " of the " + logged.size()
                        + " node(s) this log writes");
    }
}
