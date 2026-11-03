package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.EvidenceSetState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Evidence-set key ({@code docs/specs/spec-evidence-set-key.md} §4.5) — <b>was the graph on screen the shape of the graph
 * that wrote this log, and which runs does the log hold?</b>
 *
 * <p>One owner, so the status line, the Project panel, {@code context} and every result over a pair state the same
 * verdict (rule 9). The decision is {@link EvidenceSetState#decide}, pure; this node holds its inputs and decides when
 * they are stale:
 * <ul>
 *   <li>a different log (a new generation) forgets the headers it held — they described the previous log — until the
 *       adapter's scan of the new one reports them ({@link SessionEvents.StreamHeadersObserved});</li>
 *   <li>a Follow append that moves the header count asks for that scan through {@link LogEvidence}'s content signature,
 *       and the result lands here like any other;</li>
 *   <li>a graph opened, replaced or closed re-decides at once from what is held.</li>
 * </ul>
 * A result naming another generation is refused, so a scan of a log since replaced can never judge the new one.
 *
 * <p>It never refuses a pairing (D2) and never words a match as "the same build" (D4).
 */
public class EvidenceSet implements EventLogSource {

    private final OpenLog openLog;
    private final OpenGraph openGraph;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private long boundGeneration = -1;
    private java.util.List<telamin.fluxtion.audit.analyser.analyser.parse.StreamHeader> headers;
    private int records;
    private EvidenceSetState state = EvidenceSetState.NONE;

    public EvidenceSet(OpenLog openLog, OpenGraph openGraph) {
        this.openLog = openLog;
        this.openGraph = openGraph;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** The log or the graph changed: a new log forgets the previous log's headers, then the pair is re-decided. */
    @OnTrigger
    public boolean onPairChanged() {
        if (!openLog.isOpen() || openLog.generation() != boundGeneration) {
            headers = null;
            records = 0;
            boundGeneration = openLog.isOpen() ? openLog.generation() : -1;
        }
        return decide("pair changed");
    }

    /** The adapter's reading of the open log's stream headers. */
    @OnEventHandler
    public boolean onStreamHeadersObserved(SessionEvents.StreamHeadersObserved event) {
        if (!openLog.isOpen()) {
            auditLog.info("noOp", "StreamHeadersObserved").info("reason", "no log open");
            return false;
        }
        if (event.generation() != openLog.generation()) {
            auditLog.warn("staleFact", "StreamHeadersObserved").warn("generation", event.generation())
                    .warn("current", openLog.generation());
            return false;
        }
        boundGeneration = event.generation();
        headers = event.headers();
        records = event.records();
        return decide("headers observed");
    }

    private boolean decide(String why) {
        EvidenceSetState next = EvidenceSetState.decide(openLog.isOpen(), openLog.generation(), headers, records,
                openGraph.isOpen(), openGraph.revision(), openGraph.sourceFingerprint());
        if (next.equals(state)) return false;
        state = next;
        auditLog.info("evidenceSet", state.verdict() == null ? "none" : state.verdict().name())
                .info("why", why)
                .info("headers", state.headers())
                .info("segments", state.segments().size())
                .info("logFingerprint", state.logFingerprint() == null ? "none" : state.logFingerprint())
                .info("graphFingerprint", state.graphFingerprint() == null ? "none" : state.graphFingerprint());
        return true;
    }

    /** The verdict and the runs, as decided. */
    public EvidenceSetState state() {
        return state;
    }
}
