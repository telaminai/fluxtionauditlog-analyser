package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.CoveragePolicy;

/**
 * The node that asks {@link CoveragePolicy} what may be asserted, and puts the answer in the record.
 *
 * <p><b>Everything is not a node.</b> The policy itself — four ways coverage stops meaning anything,
 * and the sentences that justify each — is a plain class with no state and no lifecycle. It is a pure
 * function of six facts, so it is readable and exhaustively testable without a processor. What is left
 * here is what genuinely needs to be a node: gathering those facts from parents, being re-derived when
 * any of them moves, and writing the decision to the audit log.
 *
 * <p>One {@code @OnTrigger}, which the compiler renders as an OR of its four parents' dirty flags —
 * this node previously carried four {@code @OnEventHandler}s doing that de-duplication by hand.
 */
public class CoverageClaim implements EventLogSource {

    private final Pairing pairing;
    private final AuditInstallation auditInstallation;
    private final OpenGraph openGraph;
    private final OpenLog openLog;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private CoveragePolicy.Assessment assessment =
            new CoveragePolicy.Assessment(CoveragePolicy.Claim.REFUSED, "no log and no graph are open");

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

    @OnTrigger
    public boolean recomputeOnStateChange() {
        assessment = CoveragePolicy.decide(
                openGraph.isOpen(),
                openLog.isOpen(),
                openGraph.source(),
                installed(),
                pairing.verdict(),
                pairing.sampled(),
                pairing.total(),
                openLog.mostVerboseLevel());
        auditLog.info("coverageClaim", assessment.claim().name())
                .info("claim", assessment.claim().name())
                .info("whyNot", assessment.reason())
                .info("provenance", String.valueOf(openGraph.source()))
                .info("auditInstallation", auditInstallation.verdict().name())
                .info("level", String.valueOf(openLog.mostVerboseLevel()));
        return true;
    }

    /** Translate the node's verdict into the policy's vocabulary — the node knows both, the policy one. */
    private CoveragePolicy.AuditInstalled installed() {
        return switch (auditInstallation.verdict()) {
            case ENABLED -> CoveragePolicy.AuditInstalled.YES;
            case NOT_ENABLED -> CoveragePolicy.AuditInstalled.NO;
            case UNKNOWN -> CoveragePolicy.AuditInstalled.UNKNOWN;
        };
    }

    /** What a surface may assert, and why — never a bare refusal. */
    public CoveragePolicy.Assessment assessment() {
        return assessment;
    }
}
