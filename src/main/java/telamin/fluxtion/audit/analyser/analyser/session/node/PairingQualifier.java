package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.topology.PairingQualification;
import telamin.fluxtion.audit.analyser.analyser.topology.PairingQualifications;

/**
 * <b>What later, wider comparisons say about the published pairing</b> — M44.4c, spec §13 D-S13.1.
 *
 * <p>The pairing is judged on a sample when a log and graph meet; a {@code coverage} call then compares the whole
 * log (or a filtered view) against every declared node, and that comparison QUALIFIES the sampled verdict wherever it
 * is shown (M68.1 D-E2). The rules for combining comparisons live in the pure {@link PairingQualifications}; this node
 * owns WHICH PAIR they are about, which the frame used to track by object identity ({@code qualifiedPairing ==
 * lastPairing}) — a rule no audit record could show.
 *
 * <p>The binding is now two numbers: the log generation and the graph revision. A re-scope (a Follow append) changes
 * neither, so the comparisons stand and are re-read at the new size, which is how they report themselves stale. A
 * different log or graph changes one, and they are cleared. A comparison stamped with a pair that is no longer open —
 * a scan that ran while another log opened — is refused as {@code staleFact}.
 */
public class PairingQualifier implements EventLogSource {

    private final OpenLog openLog;
    private final OpenGraph openGraph;
    private final Pairing pairing;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // Not final: a final field is constructor-mapped by the generator, and this is node-local state (FLX-1009 said so,
    // naming the field — the rule spec SCORED §1 recorded, met again).
    private PairingQualifications held = new PairingQualifications();
    private long boundGeneration = -1;
    private long boundRevision = -1;
    private String filterKey;
    private String lastSaid;

    public PairingQualifier(OpenLog openLog, OpenGraph openGraph, Pairing pairing) {
        this.openLog = openLog;
        this.openGraph = openGraph;
        this.pairing = pairing;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    /** The pair moved: keep the comparisons for a re-scope of the same pair, clear them for a different one. */
    @OnTrigger
    public boolean onPairChanged() {
        long generation = openLog.generation();
        long revision = openGraph.revision();
        if (generation == boundGeneration && revision == boundRevision && openLog.isOpen() && openGraph.isOpen()) {
            return true;                                   // the same pair, re-scoped: the comparisons still describe it
        }
        boolean had = !held.isEmpty();
        held.clear();
        boundGeneration = generation;
        boundRevision = revision;
        if (had) auditLog.info("qualifications", "cleared").info("reason", "a different log or graph");
        return had;
    }

    @OnEventHandler
    public boolean onMembershipCompared(SessionEvents.MembershipCompared event) {
        lastSaid = null;
        if (event.logGeneration() != openLog.generation() || event.graphRevision() != openGraph.revision()) {
            auditLog.warn("staleFact", "MembershipCompared")
                    .warn("comparedGeneration", event.logGeneration()).warn("currentGeneration", openLog.generation())
                    .warn("comparedRevision", event.graphRevision()).warn("currentRevision", openGraph.revision());
            return false;
        }
        if (pairing.verdict() == null) {
            auditLog.info("noOp", "MembershipCompared").info("reason", "no published pairing to qualify");
            return false;
        }
        PairingQualification q = PairingQualification.fromCoverage(pairing.verdict(), event.echo());
        lastSaid = held.record(q);
        boundGeneration = openLog.generation();
        boundRevision = openGraph.revision();
        auditLog.info("qualified", q.scope()).info("recordsCompared", q.recordsCompared())
                .info("wholeLog", q.wholeLog()).info("notDeclared", q.notDeclared().size());
        return true;
    }

    @OnEventHandler
    public boolean onViewFilterChanged(SessionEvents.ViewFilterChanged event) {
        boolean moved = !java.util.Objects.equals(filterKey, event.filterKey());
        filterKey = event.filterKey();
        return moved;
    }

    /** An independent copy for the snapshot, or null when nothing qualifies the pairing. */
    public PairingQualifications qualifications() {
        return held.isEmpty() ? null : held.copy();
    }

    /** The filter in force, by identity — null for none. */
    public String filterKey() {
        return filterKey;
    }

    /** What the last comparison's reply should state, or null if it was refused or qualified nothing. */
    public String lastSaid() {
        return lastSaid;
    }
}
