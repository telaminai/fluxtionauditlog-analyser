package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spike round 3 (PREDICTIONS-3.md): what a Follow append wakes, on the real generated processor. An append moves the
 * open log's COUNT; only nodes that state the count, or decide on it, should run. The rest read {@code OpenLog} as
 * data and wake on {@code LogLifecycle}, and the nodes that do run state nothing they have already stated.
 */
class PropagationRoundThreeTest {

    private static SessionDriver open(FakeSessionAdapter a, SessionAuditSink sink) {
        SessionDriver d = new SessionDriver(a, sink);
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, 25, "TRACE");
        d.post(new SessionEvents.FollowToggled(d.snapshot().logGeneration(), true));
        return d;
    }

    private static String lastAppend(SessionAuditSink sink) {
        List<String> appended = sink.matching("event: LogAppended");
        assertFalse(appended.isEmpty(), "control: an append was recorded");
        return appended.get(appended.size() - 1);
    }

    @Test
    @DisplayName("an append wakes the lifecycle, and not the nodes that read the open log as data")
    void anAppendWakesOnlyWhatItMoves() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink(10_000);
        SessionDriver d = open(a, sink);
        d.post(new SessionEvents.LogAppended(d.snapshot().logGeneration(), Set.of("a"), 1, 26, "TRACE"));

        String r = lastAppend(sink);
        assertTrue(r.contains("- logLifecycle:"), "the narrow trigger runs, and reports no change:\n" + r);
        assertFalse(r.contains("- identityBannerView:"), "the banner wakes on the lifecycle, not on a count:\n" + r);
        assertFalse(r.contains("- pairingQualifier:"), "the qualifier binds to WHICH pair, not its size:\n" + r);
        assertFalse(r.contains("- logEvidence: { thread: main, method: onOpenLogChanged"),
                "logEvidence's lifecycle trigger stays asleep; its append handler does the work:\n" + r);
        assertTrue(r.contains("scanCoalesced") || r.contains("scanLogEvidence"),
                "control: logEvidence still handles the append itself:\n" + r);
    }

    @Test
    @DisplayName("coverage and pairing state their decision once, not again on every append")
    void anUnchangedDecisionIsNotRestated() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink(10_000);
        SessionDriver d = open(a, sink);
        long g = d.snapshot().logGeneration();
        assertFalse(sink.matching("whyNot:").isEmpty(), "control: the claim was stated when the log opened");
        assertFalse(sink.matching("pairing: cannotSay").isEmpty(), "control: so was the pairing");

        d.post(new SessionEvents.LogAppended(g, Set.of("a"), 1, 26, "TRACE"));
        d.post(new SessionEvents.LogAppended(g, Set.of("a"), 1, 27, "TRACE"));

        String r = lastAppend(sink);
        assertFalse(r.contains("whyNot:"), "an unchanged coverage claim is not restated:\n" + r);
        assertFalse(r.contains("pairing: cannotSay"), "nor an unchanged 'cannot say':\n" + r);
    }

    @Test
    @DisplayName("a lifecycle change still reaches every consumer: a close clears the evidence and takes the banner down")
    void aLifecycleChangeStillReachesTheConsumers() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink(10_000);
        SessionDriver d = open(a, sink);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.ProducerFindingsObserved(g,
                telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.clean()));
        d.post(new SessionEvents.TimeOrderObserved(g,
                telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean()));
        assertNotNull(d.snapshot().timeOrder(), "control: logEvidence holds this log's evidence");
        d.post(new SessionEvents.LogIdentityObserved(g, "REPLACEMENT", "replaced"));
        assertTrue(a.identityBanners.get(a.identityBanners.size() - 1).shown(), "an identity change reaches the banner");

        d.post(new SessionEvents.LogCleared(g));

        assertFalse(a.identityBanners.get(a.identityBanners.size() - 1).shown(), "a close reaches the banner");
        assertFalse(sink.matching("logEvidence: cleared").isEmpty(), "and logEvidence, which clears what it held");
        assertNull(d.snapshot().timeOrder(), "so a closed log states no evidence");
    }

    @Test
    @DisplayName("a close with no identity change still reaches logEvidence — the lifecycle watches the open flag itself")
    void aCloseAloneStillClearsTheEvidence() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink(10_000);
        SessionDriver d = open(a, sink);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.ProducerFindingsObserved(g,
                telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.clean()));
        d.post(new SessionEvents.TimeOrderObserved(g,
                telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport.clean()));
        assertNotNull(d.snapshot().timeOrder(), "control: logEvidence holds this log's evidence");
        assertNull(d.snapshot().logIdentity(), "control: no identity verdict, so a close changes only the open flag");

        d.post(new SessionEvents.LogCleared(g));

        assertNull(d.snapshot().timeOrder(), "the close reached logEvidence through the lifecycle, and it cleared");
    }
}
