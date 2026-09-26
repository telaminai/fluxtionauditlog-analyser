package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphmlDiscovery;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M44.4 acceptance (spec §13): the M68.1 lifecycle journeys, re-expressed as event sequences against the session —
 * events in, snapshot out, no window. They run on headless CI, which the frame tests only do under xvfb.
 *
 * <p>Each journey names the frame test it re-expresses. The frame tests stay, as RENDERING witnesses — that the panel
 * and {@code context} show the snapshot they were given. What they asserted about the verdict itself is asserted here.
 * Mapping, for the frame tests not re-expressed in this class:
 * <ul>
 *   <li>{@code aFollowAppendEvictsNoTransitionRecordAndTheClaimIsCurrent} — {@code SessionSnapshotTest.reScopesNeverEvictTransitions}
 *       and {@code anAppendIsTheSamePairReScoped}. Its other half, that the ADAPTER posts no non-change, cannot be
 *       headless: it is a property of MainFrame's reporters, and the frame test is the tier that can see it.</li>
 *   <li>{@code freshWindow_socketGraphThenMismatchingSocketLog_closesTheGraphWithoutADialog} — the decision is
 *       {@code LogArrivalReplayTest}; the no-dialog half is the window's.</li>
 *   <li>the follow echo, pending-record, copies and dialog tests — UI behaviour, not session state; not re-expressed.</li>
 * </ul>
 */
class SessionLifecycleJourneysTest {

    /** The committed graph the M68.1 frame tests use: three authored nodes plus the framework logger. */
    private static final Path GRAPH =
            Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");
    private static final Set<String> LOGGED = Set.of("rootNode", "riskCheck", "output");

    private static SessionEvents.GraphOpened committedGraph() {
        var topology = GraphMlParser.parse(GRAPH);
        List<String> types = topology.nodes().stream().map(n -> n.simpleName()).toList();
        return SessionFixtures.graph(GRAPH.toAbsolutePath().toString(), "OPENED",
                GraphPairing.declaredNodeIds(topology), types);
    }

    private static Map<String, Object> wholeLog(int records) {
        return Map.of("scope", "whole log", "recordsScanned", records, "logRecords", records,
                "loggedButNotInTopology", List.of(),
                "membership", Map.of("scope", "whole log", "established", true, "loggedIds", 3, "declaredOfLogged", 3));
    }

    private static void compare(SessionDriver d, Map<String, Object> echo) {
        SessionSnapshot s = d.snapshot();
        d.post(new SessionEvents.MembershipCompared(s.logGeneration(), s.graphRevision(), echo));
    }

    @Test
    @DisplayName("re-expresses committedGraphPairsIdentically… and aSampledPairingAgrees…: session and discovery, one verdict")
    void sessionAndDiscoveryAgreeOnTheCommittedGraph() {
        for (int[] scope : new int[][]{{1, 1}, {500, 600}}) {
            FakeSessionAdapter adapter = new FakeSessionAdapter();
            SessionDriver d = new SessionDriver(adapter);
            SessionFixtures.openLog(d, adapter, "/constructed.yaml", "DECLARED", LOGGED, scope[0], scope[1], "TRACE");
            d.submit(committedGraph());

            GraphPairing discovered = GraphmlDiscovery.scan(List.of(GRAPH.toAbsolutePath().getParent().toString()),
                            LOGGED, scope[0], scope[1]).candidates().stream()
                    .filter(c -> c.file().toAbsolutePath().normalize().equals(GRAPH.toAbsolutePath().normalize()))
                    .findFirst().orElseThrow().pairing();
            assertEquals(discovered, d.snapshot().publishedPairing(), "scope " + scope[0] + "/" + scope[1]);
            assertEquals(3, discovered.matched());
            assertFalse(discovered.reason().contains("different build"), discovered.reason());
        }
    }

    @Test
    @DisplayName("re-expresses M68.1 R2: the open ORDER does not change the verdict")
    void openOrderDoesNotChangeTheVerdict() {
        GraphPairing logFirst;
        GraphPairing graphFirst;
        GraphPairing graphWhileLoading;
        {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = new SessionDriver(a);
            SessionFixtures.openLog(d, a, "/l.yaml", "DECLARED", LOGGED, 500, 600, "TRACE");
            d.submit(committedGraph());
            logFirst = d.snapshot().publishedPairing();
        }
        {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = new SessionDriver(a);
            d.submit(committedGraph());
            SessionFixtures.openLog(d, a, "/l.yaml", "DECLARED", LOGGED, 500, 600, "TRACE");
            graphFirst = d.snapshot().publishedPairing();
        }
        {
            FakeSessionAdapter a = new FakeSessionAdapter();
            a.pendingOpens = true;
            SessionDriver d = new SessionDriver(a);
            long op = d.nextOpId();
            d.submit(new SessionEvents.OpenLogRequested(op, "/l.yaml", null, "DECLARED", false));
            d.submit(committedGraph());                         // the combined open: the graph lands mid-load
            assertNull(d.snapshot().publishedPairing(), "pending: nothing to state yet");
            d.submit(new SessionEvents.LogOpened(op, "/l.yaml", "DECLARED", LOGGED, 500, 600, "TRACE"));
            graphWhileLoading = d.snapshot().publishedPairing();
        }
        assertNotNull(logFirst);
        assertEquals("first 500 of 600 records", logFirst.scope());
        assertEquals(logFirst, graphFirst);
        assertEquals(logFirst, graphWhileLoading);
    }

    @Test
    @DisplayName("re-expresses aGraphOpenedWhileTheNextLogLoads…: pending, then the NEW pair is judged")
    void aGraphOpenedDuringTheNextLoadIsJudgedAgainstTheNewLog() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/a.yaml", "DECLARED", Set.of("aNode"), 1, 1, "TRACE");
        d.submit(SessionFixtures.graph("/a.graphml", "OPENED", Set.of("aNode"), List.of("EventLogManager")));
        assertTrue(d.snapshot().publishedPairing().applies(), "A/A");

        a.pendingOpens = true;
        long op = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(op, "/b.yaml", null, "DECLARED", false));
        d.submit(SessionFixtures.graph("/b.graphml", "OPENED", Set.of("bNode"), List.of("EventLogManager")));

        SessionSnapshot during = d.snapshot();
        assertTrue(during.pending());
        assertNull(during.publishedPairing(), "review B1: graph A's verdict must not be stated about graph B");

        d.submit(new SessionEvents.LogOpened(op, "/b.yaml", "DECLARED", Set.of("bNode"), 1, 1, "TRACE"));
        SessionSnapshot after = d.snapshot();
        assertFalse(after.pending());
        assertTrue(after.publishedPairing().applies(), "B/B: " + after.publishedPairing());
        assertEquals("/b.graphml", after.graphPath());
        assertEquals(1, after.publishedPairing().matched());
    }

    @Test
    @DisplayName("re-expresses aFollowAppendMakesTheWholeLogVerdictStale: stale, scoped, superseding nothing")
    void aFollowAppendMakesTheWholeLogComparisonStale() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/l.yaml", "DECLARED", LOGGED, 500, 600, "TRACE");
        d.submit(committedGraph());
        compare(d, wholeLog(600));
        var fresh = d.snapshot().qualifications().toMap(d.snapshot().total(), d.snapshot().filterKey());
        assertEquals(Boolean.FALSE, fresh.get("stale"), fresh.toString());

        d.post(new SessionEvents.LogAppended(d.snapshot().logGeneration(), LOGGED, 500, 601, "TRACE"));

        SessionSnapshot s = d.snapshot();
        assertEquals("first 500 of 601 records", s.publishedPairing().scope(), "the published pairing counts it");
        var q = s.qualifications().toMap(s.total(), s.filterKey());
        assertEquals(Boolean.TRUE, q.get("stale"), "the whole-log comparison is about the old revision: " + q);
        assertEquals("first 600 of 601 records", q.get("scope"), "a stale scope states what was compared: " + q);
        assertEquals(Boolean.FALSE, q.get("supersedesSample"), "a stale comparison supersedes nothing: " + q);
        String note = s.qualifications().panelNote(s.publishedPairing(), s.total(), s.filterKey());
        assertTrue(note.startsWith("first 600 of 601 records: all 3 logged id(s) declared"), "the note leads with it: " + note);
        assertFalse(note.contains("confirms"), note);
    }

    @Test
    @DisplayName("closing the log retires the verdict AND its qualifications; the claim refuses")
    void closingTheLogRetiresEverythingAboutIt() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/l.yaml", "DECLARED", LOGGED, 500, 600, "TRACE");
        d.submit(committedGraph());
        compare(d, wholeLog(600));

        d.post(new SessionEvents.LogCleared(d.snapshot().logGeneration()));

        SessionSnapshot s = d.snapshot();
        assertFalse(s.logOpen());
        assertTrue(s.graphOpen(), "M35.1: closing a log leaves the graph the person opened");
        assertNull(s.publishedPairing(), "review F2: the verdict was about THAT log");
        assertNull(s.qualifications(), "and so was every comparison");
        assertEquals(CoveragePolicy.Claim.REFUSED, s.claim().claim());
    }
}
