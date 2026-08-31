package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;

/**
 * <b>Does this graph describe this log?</b> — one of three questions the first M44 draft merged into
 * one node, and the review was right that they are different (F3).
 *
 * <p>This one needs a log and a graph and answers only about the pair. It is not
 * {@link AuditInstallation}, which needs no log at all and is answerable before a run happens; and it
 * is not a statement about what a surface may assert, which additionally depends on graph provenance
 * and the audit regime. Merging them would have regressed a distinction the analyser already ships.
 *
 * <p><b>The scoring is not reimplemented here.</b> It delegates to the existing pure
 * {@link GraphPairing}, which already compares declared ids with logged ids and already names the
 * reverse direction as the interesting fault. A second scorer would be a second answer to one
 * question, and they would drift.
 *
 * <p>The node holds the raw id sets and recomputes on either observation, so the verdict cannot go
 * stale against a log or graph that has since changed.
 */
public class Pairing implements EventLogSource {

    private final OpenLog openLog;
    private final OpenGraph openGraph;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private GraphPairing verdict;
    private int sampled;
    private int total;

    public Pairing(OpenLog openLog, OpenGraph openGraph) {
        this.openLog = openLog;
        this.openGraph = openGraph;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /**
     * ONE method, fired when either parent changed.
     *
     * <p>This replaced three {@code @OnEventHandler}s that each called the same recompute. The
     * compiler renders an {@code @OnTrigger} as an OR of its parents' dirty flags —
     * {@code guardCheck_pairing() { return isDirty_openLog | isDirty_openGraph; }} — evaluated once
     * per cycle, so the node is invoked at most once however many of its inputs moved. That is the
     * de-duplication the three handlers were hand-rolling, done by the compiler and visible in the
     * generated source.
     */
    @OnTrigger
    public boolean recomputeOnStateChange() {
        return recompute();
    }

    private boolean recompute() {
        boolean haveGraph = openGraph.isOpen();
        boolean haveLog = openLog.isOpen();
        java.util.Set<String> declared = openGraph.declaredNodeIds();
        java.util.Set<String> logged = openLog.loggedNodeIds();
        sampled = openLog.sampled();
        total = openLog.total();
        if (!haveGraph || !haveLog) {
            // "Cannot say" is a verdict, not a gap. A pairing needs both artefacts, and inventing one
            // when a log is open on its own is how a graph gets judged against nothing.
            verdict = null;
            auditLog.info("pairing", "cannotSay")
                    .info("haveGraph", haveGraph)
                    .info("haveLog", haveLog);
            return true;
        }
        verdict = GraphPairing.of(declared, logged);
        auditLog.info("pairing", verdict.applies() ? "applies" : "doesNotApply")
                .info("applies", verdict.applies())
                .info("declared", declared.size())
                .info("logged", verdict.logged())
                .info("matched", verdict.matched())
                .info("sampled", sampled)
                .info("total", total);
        return true;
    }

    /** The verdict, or {@code null} when either artefact is missing — never a guessed one. */
    public GraphPairing verdict() {
        return verdict;
    }

    public boolean canSay() {
        return verdict != null;
    }

    /** True only when a verdict exists AND it is negative — the state that costs someone a graph. */
    public boolean doesNotApply() {
        return verdict != null && !verdict.applies();
    }

    /** Whether the numbers describe a SAMPLE, so a caller cannot state them as a whole-log claim. */
    public boolean sampledOnly() {
        return total > sampled && sampled > 0;
    }

    public int sampled() {
        return sampled;
    }

    public int total() {
        return total;
    }
}
