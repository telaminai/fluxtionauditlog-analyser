package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditReadiness;

/**
 * <b>Can this processor write an audit log at all?</b> — the second of the three questions the first
 * draft merged (review F3), and the one whose independence matters most.
 *
 * <p>It is answerable <b>without a log</b>, which is the entire point: everything else that catches
 * this mistake catches it too late, because a log that was never written cannot be examined. The
 * graph is emitted by the same build and records the answer — if {@code addEventAudit()} was called
 * the compiler installs {@code EventLogManager} as a node, and if it was not, the node is absent and
 * the processor will write nothing however carefully its nodes narrate themselves.
 *
 * <p>Merging this into a pairing verdict would have made it need a log, and destroyed the one
 * property that makes it useful.
 *
 * <p>The evidence types are read from {@link AuditReadiness#evidenceTypes()} rather than restated, so
 * the two cannot disagree about what counts as evidence.
 */
public class AuditInstallation implements EventLogSource {

    private EventLogger auditLog = NullEventLogger.INSTANCE;

    private Verdict verdict = Verdict.UNKNOWN;
    private int nodeCount;

    public enum Verdict {
        /** The auditor is on the graph: a run can produce a log. */
        ENABLED,
        /** No auditor: this processor writes NOTHING, whatever its nodes do. */
        NOT_ENABLED,
        /** No graph observed, so nothing can be said — never guessed from a log's contents. */
        UNKNOWN
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onGraphObserved(SessionEvents.GraphObserved event) {
        if (!event.open() || event.nodeTypes().isEmpty()) {
            verdict = Verdict.UNKNOWN;
            nodeCount = 0;
            auditLog.info("auditInstallation", verdict.name()).info("nodeCount", 0);
            return true;
        }
        String auditor = AuditReadiness.evidenceTypes().get(0);
        verdict = event.nodeTypes().contains(auditor) ? Verdict.ENABLED : Verdict.NOT_ENABLED;
        nodeCount = event.nodeTypes().size();
        auditLog.info("auditInstallation", verdict.name())
                .info("auditor", auditor)
                .info("nodeCount", nodeCount);
        return true;
    }

    @OnEventHandler
    public boolean onGraphClosed(SessionEvents.GraphClosed event) {
        verdict = Verdict.UNKNOWN;
        nodeCount = 0;
        auditLog.info("auditInstallation", verdict.name()).info("via", "GraphClosed");
        return true;
    }

    public Verdict verdict() {
        return verdict;
    }

    /** True only when we positively know audit is off — the state worth interrupting someone for. */
    public boolean isProblem() {
        return verdict == Verdict.NOT_ENABLED;
    }

    public int nodeCount() {
        return nodeCount;
    }
}
