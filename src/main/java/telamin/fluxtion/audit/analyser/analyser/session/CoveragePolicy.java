package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;

import java.util.Locale;

/**
 * <b>May a surface assert a coverage number, and if not, why not?</b> — the policy, as a plain class.
 *
 * <p>Not a node, deliberately. It has no state, no lifecycle and no reason to know what an event is:
 * it is a pure function of six facts, and it is the sort of thing that should be readable and testable
 * without a processor. {@code CoverageClaim} is the node that gathers those facts from its parents and
 * hands them here — which leaves the node about ten lines long and this class exhaustively testable in
 * isolation.
 *
 * <p>Coverage is <b>"declared minus observed"</b>, and that subtraction stops meaning anything four
 * separate ways. Each refusal below has its own reason and its own history:
 *
 * <ol>
 *   <li><b>An INFERRED graph</b> — the declared set IS the observed set, so the answer is 100% by
 *       construction: a tautology that still prints a number. The only one of the four that was
 *       checked before M44.2, in {@code ActionExecutor.doCoverage}.</li>
 *   <li><b>No auditor on the graph</b> — a processor built without {@code addEventAudit()} writes
 *       nothing at all, so every declared node reads as never-logged and the number blames the nodes
 *       for the build.</li>
 *   <li><b>A graph that does not describe this log</b> — the denominator belongs to a different system.
 *       M35.2 closes such a graph when a log arrives, but M35.3 deliberately KEEPS one a person opened
 *       against a log: announce, never forbid. Keeping the graph and refusing the number are the same
 *       respect for intent, not a contradiction.</li>
 *   <li><b>A level below TRACE</b> — <em>not</em> a refusal. A node may have run, logged, and had its
 *       output discarded for being below the captured threshold. The number is computable, so it is
 *       given, and it carries what it hides.</li>
 * </ol>
 *
 * <p>The REFUSED/QUALIFIED distinction is the whole value: <b>refusing a computable number is as much a
 * failure as printing a meaningless one.</b>
 */
public final class CoveragePolicy {

    /** Provenance that makes the subtraction empty by construction. */
    private static final String INFERRED = "INFERRED";

    /** The only level at which absence from the log is evidence a node did not run. */
    private static final String TRACE = "TRACE";

    public enum Claim {
        /** Coverage means what it says. */
        FULL,
        /** Computable, but the number hides something a reader must be told. */
        QUALIFIED,
        /** Not computable in any meaningful sense — printing a number would mislead. */
        REFUSED
    }

    /** The verdict and the sentence that justifies it, so a surface never has to invent one. */
    public record Assessment(Claim claim, String reason) {

        public boolean allowed() {
            return claim != Claim.REFUSED;
        }
    }

    /** Whether the graph's processor can write an audit log at all. */
    public enum AuditInstalled {
        YES, NO, UNKNOWN
    }

    private CoveragePolicy() {
    }

    /** The level caveat, stated once so the level branch and the pairing branches cannot word it differently. */
    private static String levelReason(String mostVerboseLevel) {
        return "the most verbose record in this log is " + mostVerboseLevel + ", not TRACE, so a "
                + "node may have run, logged, and had its output discarded for being below "
                + "the captured level — a gap here is not proof a node never ran";
    }

    /**
     * @param graphOpen        is a topology loaded
     * @param logOpen          is a log loaded
     * @param graphProvenance  {@code OPENED} / {@code READER_DECLARED} / {@code READER_INFERRED} / null
     * @param auditInstalled   what the graph says about its own ability to log
     * @param pairing          the graph/log comparison, or {@code null} when it cannot be made
     * @param sampled          records scanned for the pairing, and {@code total} how many exist
     * @param mostVerboseLevel the most verbose level observed, a LOWER BOUND on the threshold
     */
    public static Assessment decide(boolean graphOpen, boolean logOpen, String graphProvenance,
                                    AuditInstalled auditInstalled, GraphPairing pairing,
                                    int sampled, int total, String mostVerboseLevel) {
        if (!graphOpen) {
            return new Assessment(Claim.REFUSED,
                    "no graph is open — coverage compares a graph against a log, so it needs one");
        }
        if (!logOpen) {
            return new Assessment(Claim.REFUSED,
                    "no log is open — coverage scores what a log recorded against what the graph "
                            + "declares, so it needs both");
        }
        if (graphProvenance != null && graphProvenance.toUpperCase(Locale.ROOT).contains(INFERRED)) {
            return new Assessment(Claim.REFUSED,
                    "this graph was inferred from what ran, so coverage cannot mean anything: it "
                            + "subtracts what ran from what was declared, and here the declared set IS "
                            + "what ran. Open a declared graph to get a real answer");
        }
        if (auditInstalled == AuditInstalled.NO) {
            return new Assessment(Claim.REFUSED,
                    "this graph's processor was built without audit logging, so it writes no records "
                            + "at all — every declared node would read as never-logged, and the number "
                            + "would blame the nodes for the build");
        }
        if (pairing != null && !pairing.applies()) {
            return new Assessment(Claim.REFUSED,
                    "the graph and this log disagree about which nodes exist (" + pairing.reason() + "), "
                            + "so a denominator taken from the graph would score nodes this log may not "
                            + "contain. It was kept because it was opened deliberately; scoring against it "
                            + "would still be wrong");
        }
        // M68.1 (D-E1): applies() is a RETENTION policy, never evidence of fit. Two retained cases reached
        // FULL before, and FULL's sentence says the graph describes this log — a claim neither supports.
        // Re-review O4: one reason used to hide the others, so these two now carry the level caveat as well
        // whenever it also applies — a reader needs both facts, and adding one can never change the claim.
        String levelCaveat = mostVerboseLevel != null && !TRACE.equalsIgnoreCase(mostVerboseLevel)
                ? " Also: " + levelReason(mostVerboseLevel) : "";
        if (pairing != null && !pairing.evidenced()) {
            return new Assessment(Claim.QUALIFIED,
                    "no node output was recorded in the records the pairing checked, so it could not "
                            + "establish that this graph describes this log. The graph is kept and the number "
                            + "is computable, but it rests on no membership evidence, and every eligible node "
                            + "therefore reads as uncovered: " + pairing.reason() + "." + levelCaveat);
        }
        if (pairing != null && !pairing.everyObservedIdDeclared()) {
            return new Assessment(Claim.QUALIFIED,
                    "the graph was kept on a partial match — it does not declare every node id this log "
                            + "writes (" + pairing.reason() + "). Kept is not the same as fits: the number "
                            + "describes the graph, not the ids it lacks." + levelCaveat);
        }
        if (mostVerboseLevel != null && !TRACE.equalsIgnoreCase(mostVerboseLevel)) {
            return new Assessment(Claim.QUALIFIED, levelReason(mostVerboseLevel));
        }
        // the pairing's own scope, when it recorded one, is the same fact as the (sampled, total) arguments;
        // either source is enough to say the fit was judged on part of the log
        boolean argsSampled = total > sampled && sampled > 0;
        if (argsSampled || (pairing != null && pairing.sampled())) {
            String scope = argsSampled
                    ? "the first " + sampled + " of " + total + " records"
                    : "the " + pairing.scope();
            return new Assessment(Claim.QUALIFIED,
                    "the graph/log pairing was judged from " + scope
                            + ", so it is a sample rather than a whole-log claim");
        }
        return new Assessment(Claim.FULL, "the graph is declared, declares every node id this log writes "
                + "in the records compared, and the log was captured at TRACE");
    }
}
