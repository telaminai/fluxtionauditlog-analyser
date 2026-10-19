package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderValidator;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.StatusLineState.Subject.*;

/**
 * INTEG-R10 on the real generated processor: which line the status bar states is the {@code statusLine} node's
 * decision, and it is the same whichever of the log fact and the graph fact arrives first.
 *
 * <p>Witness for the ordering: before the node, the frame had two writers and no decision — the log line was set when
 * the evidence landed and the graph line only when a graph opened AFTER a log, so {@link #graphThenLog} and
 * {@link #graphWhileTheLogLoads} ended on the log line. The frame half is
 * {@code BrokenValueFrameTest#theGraphLineIsTheSameWhicheverArrivesFirst}.
 */
class StatusLineOrderTest {

    /** A finding that warns, so the line has a warning to keep. */
    private static final ProducerDiagnostics EMPTY_LOG =
            ProducerDiagnostics.of(new LogIndex(), i -> null, List.of(), List.of(), false);

    private static void evidenceLands(SessionDriver d) {
        long g = d.snapshot().logGeneration();
        String rec = "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n---\n";
        d.post(new SessionEvents.ProducerFindingsObserved(g, EMPTY_LOG));
        d.post(new SessionEvents.TimeOrderObserved(g,
                TimeOrderValidator.validate(new HeapLogStore("---\n" + rec.formatted(1000)).index())));
    }

    private static void openLog(SessionDriver d, FakeSessionAdapter a) {
        SessionFixtures.openLog(d, a, "/DEMO.yaml", "DECLARED", Set.of("a"), 1, 1, "TRACE");
    }

    private static void openGraph(SessionDriver d) {
        d.post(SessionFixtures.graph("/DEMO.graphml", "OPENED", Set.of("a"), List.of("A")));
    }

    private static void assertGraphLine(SessionSnapshot s, String order) {
        assertEquals(LOG_AND_GRAPH, s.statusLine().subject(), order + ": the graph line is in force");
        assertEquals(s.logGeneration(), s.statusLine().logGeneration(), order + ": about the log now open");
        assertNotNull(s.publishedPairing(), order + ": with a current verdict to state");
        assertFalse(s.producerFindings().isClean(), order + ": and the warning it must keep");
    }

    @Test
    @DisplayName("log, its evidence, then the graph: the graph line")
    void logThenGraph() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        openLog(d, a);
        assertEquals(WAITING, d.snapshot().statusLine().subject(), "nothing is stated before the evidence lands");
        evidenceLands(d);
        assertEquals(LOG, d.snapshot().statusLine().subject(), "the log's own line while no graph is open");
        openGraph(d);
        assertGraphLine(d.snapshot(), "log then graph");
    }

    @Test
    @DisplayName("the graph, then the log and its evidence: the same graph line")
    void graphThenLog() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        openGraph(d);
        assertEquals(NO_LOG, d.snapshot().statusLine().subject(), "a graph with no log states no log line");
        openLog(d, a);
        evidenceLands(d);
        assertGraphLine(d.snapshot(), "graph then log");
    }

    @Test
    @DisplayName("the graph lands while the log loads (open {log, graphml}): the same graph line")
    void graphWhileTheLogLoads() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        openLog(d, a);
        openGraph(d);
        assertEquals(WAITING, d.snapshot().statusLine().subject(), "the graph line waits for the log's warnings");
        evidenceLands(d);
        assertGraphLine(d.snapshot(), "graph while the log loads");
    }

    @Test
    @DisplayName("every order ends on one decision")
    void everyOrderEndsOnOneDecision() {
        java.util.Set<StatusLineState.Subject> ends = new java.util.HashSet<>();
        for (int order = 0; order < 3; order++) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = new SessionDriver(a);
            switch (order) {
                case 0 -> { openLog(d, a); evidenceLands(d); openGraph(d); }
                case 1 -> { openGraph(d); openLog(d, a); evidenceLands(d); }
                default -> { openLog(d, a); openGraph(d); evidenceLands(d); }
            }
            ends.add(d.snapshot().statusLine().subject());
        }
        assertEquals(Set.of(LOG_AND_GRAPH), ends);
    }

    @Test
    @DisplayName("Follow states the log's Following line; the graph line returns when Follow stops; a close states none")
    void followAndClose() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        openLog(d, a);
        evidenceLands(d);
        openGraph(d);
        long g = d.snapshot().logGeneration();
        // records appended: a rescan is outstanding, so the line waits rather than count new rows beside old findings
        d.post(new SessionEvents.LogAppended(g, Set.of("a"), 2, 2, "TRACE"));
        assertEquals(WAITING, d.snapshot().statusLine().subject(), "the line waits for the rescan");
        evidenceLands(d);
        assertEquals(LOG_AND_GRAPH, d.snapshot().statusLine().subject(), "and states the graph line again once it lands");
        d.post(new SessionEvents.FollowToggled(g, true));
        assertEquals(LOG, d.snapshot().statusLine().subject(), "a live tail is about the log");
        d.post(new SessionEvents.FollowToggled(g, false));
        assertEquals(LOG_AND_GRAPH, d.snapshot().statusLine().subject());
        d.post(new SessionEvents.GraphCleared());
        assertEquals(LOG, d.snapshot().statusLine().subject(), "the graph closed: the log's line");
        d.post(new SessionEvents.LogCleared(g));
        assertEquals(NO_LOG, d.snapshot().statusLine().subject());
    }
}
