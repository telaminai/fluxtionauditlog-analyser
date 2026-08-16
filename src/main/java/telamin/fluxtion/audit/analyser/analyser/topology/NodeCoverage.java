package telamin.fluxtion.audit.analyser.analyser.topology;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of a processor's nodes actually did anything in a run — coverage, for a graph.
 *
 * <p>Raised by a real failure. A 309-node estate was built by instantiating one class per chiller, till,
 * aisle and zone; the test harness emitted readings on a modulus that shared a factor with the estate
 * size, so <b>only 2 of 24 chillers were ever reachable</b> and 54 of 275 nodes never ran once. Every
 * test passed. The graph was correct, the nodes were wired, the ordering was right — and a fifth of the
 * application was shipping untested with nothing to say so.
 *
 * <p>The question "which nodes never logged?" is answerable from two things the analyser already has —
 * the node ids in the GraphML and the instanceIds in the audit log — and needs no new instrumentation.
 * At small scale it is a curiosity. At 300 nodes it is the most useful question in the tool, because it
 * is the one a human cannot answer by looking.
 *
 * <h2>What a gap does and does not mean</h2>
 *
 * <p>This is <b>coverage, not correctness</b>, and it inherits the same caveat as the rest of this
 * codebase's reasoning about silence: a node appears in the log only if it <em>writes</em> audit output.
 * So "never logged" means one of three things, and the tool must not collapse them:
 *
 * <ul>
 *   <li><b>never ran</b> — the interesting case, and the one that found the dead chillers;</li>
 *   <li><b>ran and logged nothing</b> — a node with no {@code auditLog} calls, or one whose dirty
 *       contract stops it before it logs;</li>
 *   <li><b>ran below the audit level</b> — the graph was built without {@code addEventAudit(…)}, or the
 *       level was raised.</li>
 * </ul>
 *
 * <p>{@link #silentByDesign} carries the first distinction: a node whose class never calls
 * {@code auditLog} can never appear, and reporting it as uncovered would be noise that trains people to
 * ignore the report. The caller supplies that set when it can determine it; when it cannot, the result
 * says so rather than guessing.
 *
 * <p>Pure — no log, no Swing, no IO. The scan belongs to the caller.
 */
public record NodeCoverage(List<String> covered,
                           List<String> uncovered,
                           List<String> silentByDesign,
                           List<String> loggedButNotInTopology) {

    public NodeCoverage {
        covered = List.copyOf(covered);
        uncovered = List.copyOf(uncovered);
        silentByDesign = List.copyOf(silentByDesign);
        loggedButNotInTopology = List.copyOf(loggedButNotInTopology);
    }

    /**
     * Compare what the graph declares against what the log shows.
     *
     * @param declared       node ids from the topology — normally the authored nodes, scaffolding excluded
     * @param logged         instanceIds that appear at least once in the log
     * @param neverLogs      ids known to contain no {@code auditLog} call, or empty when not determinable
     */
    public static NodeCoverage of(Set<String> declared, Set<String> logged, Set<String> neverLogs) {
        Set<String> cov = new LinkedHashSet<>();
        Set<String> unc = new LinkedHashSet<>();
        Set<String> silent = new LinkedHashSet<>();
        for (String id : declared) {
            if (logged.contains(id)) {
                cov.add(id);
            } else if (neverLogs.contains(id)) {
                silent.add(id);
            } else {
                unc.add(id);
            }
        }
        // the reverse direction is a different fault and worth reporting separately: an instanceId in the
        // log that the topology does not contain means the graphml is from a different build, which makes
        // every other number on screen suspect
        Set<String> orphan = new LinkedHashSet<>(logged);
        orphan.removeAll(declared);
        return new NodeCoverage(List.copyOf(cov), List.copyOf(unc), List.copyOf(silent), List.copyOf(orphan));
    }

    /** Nodes that could have logged and did, as a fraction of those that could have. */
    public double ratio() {
        int denominator = covered.size() + uncovered.size();
        return denominator == 0 ? 1.0 : covered.size() / (double) denominator;
    }

    public int declaredCount() {
        return covered.size() + uncovered.size() + silentByDesign.size();
    }

    /** True when the topology and the log disagree about which nodes exist — a build mismatch. */
    public boolean buildMismatch() {
        return !loggedButNotInTopology.isEmpty();
    }
}
