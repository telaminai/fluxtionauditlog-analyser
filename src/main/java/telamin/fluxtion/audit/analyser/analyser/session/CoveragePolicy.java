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
                    "this graph does not describe this log (" + pairing.reason() + "), so the "
                            + "denominator belongs to a different system or build. It was kept because "
                            + "you opened it deliberately; scoring against it would still be wrong");
        }
        if (mostVerboseLevel != null && !TRACE.equalsIgnoreCase(mostVerboseLevel)) {
            return new Assessment(Claim.QUALIFIED,
                    "the most verbose record in this log is " + mostVerboseLevel + ", not TRACE, so a "
                            + "node may have run, logged, and had its output discarded for being below "
                            + "the captured level — a gap here is not proof a node never ran");
        }
        if (total > sampled && sampled > 0) {
            return new Assessment(Claim.QUALIFIED,
                    "the graph/log pairing was judged from the first " + sampled + " of " + total
                            + " records, so it is a sample rather than a whole-log claim");
        }
        return new Assessment(Claim.FULL, "the graph is declared, describes this log, and the log was "
                + "captured at TRACE");
    }
}
