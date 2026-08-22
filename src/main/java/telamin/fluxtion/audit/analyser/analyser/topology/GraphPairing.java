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
 * fault — {@code loggedButNotInTopology} means "the graphml is probably from a different build,
 * which makes every other number on screen suspect". A second scorer would be a second answer to one
 * question, and they would drift.
 *
 * <p><b>Why this ACTS rather than offers.</b> The rest of this codebase prefers announce-never-forbid
 * (D-I3a), and an offer was considered. It loses on two counts. A modal question on every log open is
 * friction on the path people actually use; and on the AGENT path there is nobody to answer it, so an
 * offer would either hang an automated open or be silently defaulted — and silently defaulting to
 * "keep" is exactly the defect. Closing is safe, cheap to undo (reopen the graphml), and the reason
 * is always stated with its numbers, so the decision is checkable rather than magic. The one case
 * this costs — deliberately comparing build A's graph against build B's log — is served by reopening
 * the graph after the log, which is an explicit act rather than an accident.
 *
 * <p>Pure: no log, no Swing, no IO.
 *
 * @param logged   distinct instanceIds seen in the sampled records
 * @param matched  how many of those the graph declares
 * @param applies  whether the graph should be kept
 * @param reason   why — always populated, and always carrying the numbers
 */
public record GraphPairing(int logged, int matched, boolean applies, String reason) {

    /**
     * Keep the graph when a majority of what the log logged is declared in it. A threshold is
     * arbitrary by nature, which is why the numbers travel with the verdict — a caller that
     * disagrees can see exactly what was counted. What it must separate is the honest case (a
     * slightly different build of the same system: most ids match, a few are new) from the defect
     * (a different system entirely: no overlap at all).
     */
    public static final double KEEP_ABOVE = 0.5;

    public static GraphPairing of(Set<String> declared, Set<String> logged) {
        if (declared == null || declared.isEmpty()) {
            return new GraphPairing(logged == null ? 0 : logged.size(), 0, false,
                    "the loaded graph declares no nodes");
        }
        if (logged == null || logged.isEmpty()) {
            // nothing logged says nothing about the graph — a log with no nodeLogs cannot convict it
            return new GraphPairing(0, 0, true,
                    "kept — this log records no node output, so it cannot say whether the graph applies");
        }
        NodeCoverage cov = NodeCoverage.of(declared, logged, Set.of());
        int matched = logged.size() - cov.loggedButNotInTopology().size();
        double share = (double) matched / logged.size();
        if (share > KEEP_ABOVE) {
            return new GraphPairing(logged.size(), matched, true,
                    "kept — the graph declares " + matched + " of the " + logged.size()
                            + " node(s) this log writes");
        }
        return new GraphPairing(logged.size(), matched, false,
                "closed — the graph declares only " + matched + " of the " + logged.size()
                        + " node(s) this log writes, so it describes a different system or build. "
                        + "Reopen it deliberately if you meant to compare them.");
    }
}
