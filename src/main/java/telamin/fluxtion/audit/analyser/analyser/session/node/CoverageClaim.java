package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;

/**
 * <b>What may a surface actually ASSERT about coverage?</b> — the third of the three questions the
 * first M44 draft merged into one node, and the only one that is a policy rather than a fact.
 *
 * <p>{@link Pairing} says whether a graph describes a log. {@link AuditInstallation} says whether a
 * processor can log at all. Neither is a permission. Coverage is <b>"declared minus observed"</b>, and
 * that subtraction is only meaningful when four separate things hold — which is precisely why the
 * review objected to folding them together.
 *
 * <h2>The four ways coverage stops meaning anything, each with its own reason</h2>
 * <ol>
 *   <li><b>An INFERRED graph.</b> If the graph was derived from what ran, the declared set IS the
 *       observed set and the answer is 100% by construction — a tautology that still prints a number.
 *       This rule already existed, in {@code ActionExecutor.doCoverage}; it has moved here.</li>
 *   <li><b>No auditor on the graph.</b> A processor built without {@code addEventAudit()} writes
 *       nothing at all, so every declared node reads as never-logged and coverage blames the nodes for
 *       the build. <b>New</b> — nothing checked this before.</li>
 *   <li><b>A graph that does not describe this log.</b> The denominator then belongs to a different
 *       system or build. M35.2 closes such a graph when a LOG arrives, but M35.3 deliberately KEEPS one
 *       that a person opened against a log — announce, never forbid — and until now coverage would
 *       score against it in silence. <b>Also new</b>, and it is the gap that M35.3's own exception
 *       created.</li>
 *   <li><b>A level below TRACE.</b> Not a refusal: a node may have run, logged, and had its output
 *       discarded for being below the captured threshold. That is a fourth cause of a coverage gap
 *       alongside the three {@code NodeCoverage} lists, so the number is computable and must be
 *       qualified rather than withheld.</li>
 * </ol>
 *
 * <p>The distinction between REFUSED and QUALIFIED is the whole value: refusing a computable number is
 * as much a failure as printing a meaningless one.
 */
public class CoverageClaim implements EventLogSource {

    /** Provenance strings that make the subtraction empty by construction. */
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

    private final Pairing pairing;
    private final AuditInstallation auditInstallation;
    private final OpenGraph openGraph;
    private final OpenLog openLog;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private Assessment assessment = new Assessment(Claim.REFUSED, "no log and no graph are open");

    public CoverageClaim(Pairing pairing, AuditInstallation auditInstallation,
                         OpenGraph openGraph, OpenLog openLog) {
        this.pairing = pairing;
        this.auditInstallation = auditInstallation;
        this.openGraph = openGraph;
        this.openLog = openLog;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /**
     * One method, fired when any of the four things it combines changed — see {@code Pairing} for the
     * mechanism. This node previously carried FOUR handlers, one per event that could move its answer,
     * each calling this same reassessment.
     */
    @OnTrigger
    public boolean recomputeOnStateChange() {
        return reassess();
    }

    private boolean reassess() {
        assessment = decide();
        auditLog.info("coverageClaim", assessment.claim().name())
                .info("claim", assessment.claim().name())
                .info("whyNot", assessment.reason())
                .info("provenance", String.valueOf(openGraph.source()))
                .info("auditInstallation", auditInstallation.verdict().name())
                .info("level", String.valueOf(openLog.mostVerboseLevel()));
        return true;
    }

    private Assessment decide() {
        if (!openGraph.isOpen()) {
            return new Assessment(Claim.REFUSED,
                    "no graph is open — coverage compares a graph against a log, so it needs one");
        }
        if (!openLog.isOpen()) {
            return new Assessment(Claim.REFUSED, "no log is open");
        }
        String source = openGraph.source();
        if (source != null && source.toUpperCase(java.util.Locale.ROOT).contains(INFERRED)) {
            return new Assessment(Claim.REFUSED,
                    "this graph was inferred from what ran, so coverage cannot mean anything: it "
                            + "subtracts what ran from what was declared, and here the declared set IS "
                            + "what ran. Open a declared graph to get a real answer");
        }
        if (auditInstallation.verdict() == AuditInstallation.Verdict.NOT_ENABLED) {
            return new Assessment(Claim.REFUSED,
                    "this graph's processor was built without audit logging, so it writes no records "
                            + "at all — every declared node would read as never-logged, and the number "
                            + "would blame the nodes for the build");
        }
        if (pairing.doesNotApply()) {
            return new Assessment(Claim.REFUSED,
                    "this graph does not describe this log (" + pairing.verdict().reason() + "), so the "
                            + "denominator belongs to a different system or build. It was kept because "
                            + "you opened it deliberately; scoring against it would still be wrong");
        }
        String mostVerboseLevel = openLog.mostVerboseLevel();
        if (mostVerboseLevel != null && !TRACE.equalsIgnoreCase(mostVerboseLevel)) {
            return new Assessment(Claim.QUALIFIED,
                    "the most verbose record in this log is " + mostVerboseLevel + ", not TRACE, so a "
                            + "node may have run, logged, and had its output discarded for being below "
                            + "the captured level — a gap here is not proof a node never ran");
        }
        if (pairing.sampledOnly()) {
            return new Assessment(Claim.QUALIFIED,
                    "the graph/log pairing was judged from the first " + pairing.sampled() + " of "
                            + pairing.total() + " records, so it is a sample rather than a whole-log claim");
        }
        return new Assessment(Claim.FULL, "the graph is declared, describes this log, and the log was "
                + "captured at TRACE");
    }

    /** What a surface may assert, and why — never a bare refusal. */
    public Assessment assessment() {
        return assessment;
    }
}
