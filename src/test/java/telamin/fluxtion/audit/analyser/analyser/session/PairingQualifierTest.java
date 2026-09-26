package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M44.4c (spec §13, D-S13.1). What wider comparisons say about the pairing is the session's, bound to the pair by log
 * generation and graph revision. Events in, snapshot out; each test names the mutation that must turn it red.
 */
class PairingQualifierTest {

    private static final Set<String> DECLARED = Set.of("a", "b", "c");

    private static Map<String, Object> wholeLog(int records, List<String> foreign) {
        return Map.of("scope", "whole log", "recordsScanned", records, "logRecords", records,
                "loggedButNotInTopology", foreign,
                "membership", Map.of("scope", "whole log", "established", true, "loggedIds", 3 + foreign.size(),
                        "declaredOfLogged", 3));
    }

    private static Map<String, Object> filtered(String key) {
        return Map.of("scope", "current filter", "recordsScanned", 40, "logRecords", 600,
                "loggedButNotInTopology", List.of(), "filterKey", key, "filterLabel", "label " + key,
                "membership", Map.of("scope", "current filter", "established", true, "loggedIds", 2, "declaredOfLogged", 2));
    }

    private static SessionDriver pairOpen(FakeSessionAdapter adapter) {
        SessionDriver d = new SessionDriver(adapter);
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", DECLARED, 500, 600, "TRACE");
        d.submit(SessionFixtures.graph("/g.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        return d;
    }

    private static void compare(SessionDriver d, Map<String, Object> echo) {
        SessionSnapshot s = d.snapshot();
        d.post(new SessionEvents.MembershipCompared(s.logGeneration(), s.graphRevision(), echo));
    }

    @Test
    @DisplayName("a whole-log comparison qualifies the sampled pairing, in the snapshot")
    void aComparisonQualifiesThePairing() {
        SessionDriver d = pairOpen(new FakeSessionAdapter());
        assertNull(d.snapshot().qualifications());

        compare(d, wholeLog(600, List.of("foreignAfter500")));

        var held = d.snapshot().qualifications();
        assertNotNull(held);
        assertNotNull(d.processor().pairingQualifier.lastSaid(), "the coverage reply has its sentence");
        var map = held.toMap(d.snapshot().total(), d.snapshot().filterKey());
        assertEquals(Boolean.FALSE, map.get("everyObservedIdDeclared"), "the whole log found what the sample could not: " + map);
    }

    @Test
    @DisplayName("a Follow append keeps the comparison and makes it read stale")
    void aReScopeKeepsItStale() {
        // witness: PairingQualifier.onPairChanged clearing on every change
        SessionDriver d = pairOpen(new FakeSessionAdapter());
        compare(d, wholeLog(600, List.of()));

        d.post(new SessionEvents.LogAppended(d.snapshot().logGeneration(), DECLARED, 500, 601, "TRACE"));

        var held = d.snapshot().qualifications();
        assertNotNull(held, "the same pair, re-scoped: the comparison still describes it");
        assertEquals(Boolean.TRUE, held.toMap(d.snapshot().total(), null).get("stale"),
                "and says it was made at 600 of a log now 601 long");
    }

    @Test
    @DisplayName("a different graph, or a new log, clears the comparisons")
    void aDifferentPairClearsThem() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = pairOpen(adapter);
        compare(d, wholeLog(600, List.of()));
        d.submit(SessionFixtures.graph("/other.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        assertNull(d.snapshot().qualifications(), "another graph: nothing earlier qualifies it");

        compare(d, wholeLog(600, List.of()));
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", DECLARED, 500, 600, "TRACE");
        assertNull(d.snapshot().qualifications(), "the same file reopened is a new log");
    }

    @Test
    @DisplayName("a comparison made against a pair that is no longer open is refused — the scan-during-open race")
    void aStaleComparisonIsRefused() {
        // witness: PairingQualifier.onMembershipCompared without the generation/revision check
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = pairOpen(adapter);
        SessionSnapshot scanned = d.snapshot();                        // the coverage verb captures this, then scans
        SessionFixtures.openLog(d, adapter, "/next.yaml", "DECLARED", DECLARED, 500, 600, "TRACE");   // meanwhile

        d.post(new SessionEvents.MembershipCompared(scanned.logGeneration(), scanned.graphRevision(),
                wholeLog(600, List.of("fromTheOldLog"))));

        assertNull(d.snapshot().qualifications(), "the old log's comparison must not qualify the new log's verdict");
        assertNull(d.processor().pairingQualifier.lastSaid());
        assertFalse(d.auditSink().matching("staleFact: MembershipCompared").isEmpty(), "and the refusal is on the record");
    }

    @Test
    @DisplayName("the filter in force is the session's, so a comparison under another filter reads stale")
    void aFilterChangeIsAFact() {
        SessionDriver d = pairOpen(new FakeSessionAdapter());
        d.post(new SessionEvents.ViewFilterChanged("K1"));
        compare(d, filtered("K1"));
        assertEquals(Boolean.FALSE, d.snapshot().qualifications().toMap(600, d.snapshot().filterKey()).get("filterStale"));

        d.post(new SessionEvents.ViewFilterChanged("K2"));

        assertEquals("K2", d.snapshot().filterKey());
        assertEquals(Boolean.TRUE, d.snapshot().qualifications().toMap(600, d.snapshot().filterKey()).get("filterStale"));
    }

    @Test
    @DisplayName("review B1: while a log open is in flight the snapshot publishes no verdict as current")
    void aPendingOpenPublishesNoVerdict() {
        // witness: SessionSnapshot.of with pending = false
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = pairOpen(adapter);
        assertNotNull(d.snapshot().publishedPairing());
        adapter.pendingOpens = true;

        d.submit(new SessionEvents.OpenLogRequested(d.nextOpId(), "/next.yaml", null, "DECLARED", false));

        assertTrue(d.snapshot().pending());
        assertNotNull(d.snapshot().pairing(), "the old pair's verdict still exists in the session");
        assertNull(d.snapshot().publishedPairing(), "but no surface may state it as current");
    }
}
