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
 * @param logged   distinct instanceIds seen in the sampled records
 * @param matched  how many of those the graph declares
 * @param applies  whether the graph should be kept
 * @param reason   the FACT, always populated and always carrying the numbers. Deliberately not the
 *                 ACTION: the same verdict closes a stale graph on log-open and merely warns about a
 *                 deliberately-opened one (M35.3), so each caller supplies its own verb
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
                    "this log records no node output, so it cannot say whether the graph applies");
        }
        NodeCoverage cov = NodeCoverage.of(declared, logged, Set.of());
        int matched = logged.size() - cov.loggedButNotInTopology().size();
        double share = (double) matched / logged.size();
        if (share > KEEP_ABOVE) {
            return new GraphPairing(logged.size(), matched, true,
                    "the graph declares " + matched + " of the " + logged.size()
                            + " node(s) this log writes");
        }
        return new GraphPairing(logged.size(), matched, false,
                "the graph declares only " + matched + " of the " + logged.size()
                        + " node(s) this log writes, so it describes a different system or build");
    }
}
