package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.session.generated.SessionProcessor;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;
import telamin.fluxtion.audit.analyser.analyser.topology.PairingQualifications;

/**
 * M44.4b (spec §13, D-S13.1/.3): what the session has decided, as of the last operation that completed.
 *
 * <p>Immutable, and published by {@link SessionDriver} through a {@code volatile} reference after every operation, so
 * any thread may read it: the socket thread's coverage verb used to read the processor's live fields while the EDT
 * could be mid-dispatch, and a reader could see half a cycle. A surface RENDERS this; it never computes a verdict of
 * its own beside it — that duplicate is what four rounds of M68.1 review kept finding out of step.
 *
 * <p>{@code logGeneration} and {@code graphRevision} identify the PAIR a verdict is about: a later snapshot with both
 * unchanged is the same log and graph re-scoped (a Follow append), so anything qualifying the earlier verdict still
 * describes this one; a change in either means a different pair.
 *
 * @param pairing        null when either artefact is missing — "cannot say" is a verdict, not a gap
 * @param pending        a log open is in flight (M44.4c): the verdict in force is about the log being replaced, so a
 *                       surface states it as pending rather than as current (review B1)
 * @param qualifications what wider comparisons say about {@code pairing}, or null — an independent copy (M44.4c)
 * @param filterKey      the view filter in force, by identity, so a comparison made under another reads as stale
 * @param logIdentity    M68.5: Follow's verdict about the log FILE ({@code VERIFIED}/{@code UNVERIFIED}/{@code REPLACEMENT}),
 *                       {@code REOPENED} after a replacement, or null before Follow has polled
 */
public record SessionSnapshot(boolean logOpen, String logPath, long logGeneration, int sampled, int total,
                              boolean graphOpen, String graphPath, String graphSource, long graphRevision,
                              GraphPairing pairing, CoveragePolicy.Assessment claim, boolean pending,
                              PairingQualifications qualifications, String filterKey,
                              String logIdentity, String logIdentityReason) {

    /** Before the first operation: nothing is open and nothing may be claimed. */
    public static final SessionSnapshot EMPTY =
            new SessionSnapshot(false, null, 0, 0, 0, false, null, null, 0, null, null, false, null, null, null, null);

    static SessionSnapshot of(SessionProcessor p) {
        return new SessionSnapshot(p.openLog.isOpen(), p.openLog.logPath(), p.openLog.generation(),
                p.openLog.sampled(), p.openLog.total(),
                p.openGraph.isOpen(), p.openGraph.graphPath(), p.openGraph.source(), p.openGraph.revision(),
                p.pairing.verdict(), p.coverageClaim.assessment(),
                p.operationGate.inFlightWhat() != null,
                p.pairingQualifier.qualifications(), p.pairingQualifier.filterKey(),
                p.openLog.identity(), p.openLog.identityReason());
    }

    /** The verdict a surface may state as CURRENT: none while a log open is pending. */
    public GraphPairing publishedPairing() {
        return pending ? null : pairing;
    }

    /** Whether {@code other} is about the same log and the same graph — the qualifications' binding. */
    public boolean samePairAs(SessionSnapshot other) {
        return other != null && logOpen && graphOpen && other.logOpen && other.graphOpen
                && logGeneration == other.logGeneration && graphRevision == other.graphRevision;
    }
}
