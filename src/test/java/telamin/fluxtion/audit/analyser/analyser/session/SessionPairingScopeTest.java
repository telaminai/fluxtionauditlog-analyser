package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.1 re-review R2, headless. The session's verdict is what {@code context} publishes on the combined and
 * graph-first open paths. It used to be built unscoped, so a 500-record sample was published as a whole-log claim
 * in two of the three open orders. Its scope must match the frame's, whichever artefact arrives first.
 */
class SessionPairingScopeTest {

    private static final Set<String> DECLARED = Set.of("checked", "child", "rootNode", "serviceRegistry");

    private static GraphPairing verdict(boolean graphFirst, Set<String> logged, int sampled, int total) {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        var graph = SessionFixtures.graph("/g.graphml", "OPENED", DECLARED, List.of("EventLogManager", "child"));
        if (graphFirst) d.submit(graph);
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", logged, sampled, total, "TRACE");
        if (!graphFirst) d.submit(graph);
        assertTrue(d.processor().openGraph.isOpen(), "precondition: every logged id is declared, so the arrival keeps it");
        return d.processor().pairing.verdict();
    }

    @Test
    @DisplayName("a sampled log is published as a sample, in either order")
    void theSessionVerdictCarriesItsScope() {
        for (boolean graphFirst : new boolean[]{true, false}) {
            GraphPairing v = verdict(graphFirst, Set.of("checked", "child", "rootNode"), 500, 600);
            assertTrue(v.sampled(), "graphFirst=" + graphFirst + ": " + v);
            assertEquals("first 500 of 600 records", v.scope());
            assertTrue(v.reason().contains("judged on the first 500 of 600 records"), v.reason());
        }
        assertEquals(verdict(true, Set.of("checked", "child"), 500, 600),
                verdict(false, Set.of("checked", "child"), 500, 600), "open order does not change the verdict");
    }

    @Test
    @DisplayName("a whole log is published as a whole log, and its scope is still recorded")
    void aWholeLogIsNotCalledASample() {
        GraphPairing v = verdict(true, Set.of("child"), 21, 21);
        assertFalse(v.sampled());
        assertEquals("all 21 records", v.scope());
        assertFalse(v.reason().contains("judged on the first"), v.reason());
    }

    @Test
    @DisplayName("round 3 N1: a Follow append re-scopes the session's own verdict")
    void aGrownLogReScopesTheSessionVerdict() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        d.submit(SessionFixtures.graph("/g.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        Set<String> ids = Set.of("checked", "child", "rootNode");
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", ids, 500, 600, "TRACE");
        assertEquals("first 500 of 600 records", d.processor().pairing.verdict().scope());
        // same log, same sampled ids — only the total moved, which is exactly what an append does
        d.submit(new SessionEvents.LogAppended(d.processor().openLog.generation(), ids, 500, 601, "TRACE"));
        assertEquals("first 500 of 601 records", d.processor().pairing.verdict().scope(),
                "the session must not go on stating the pre-append total");
    }

    @Test
    @DisplayName("review O3: the audit label never states retention as fit")
    void theAuditLabelSeparatesKeepFromFit() {
        assertEquals("applies", GraphPairing.of(Set.of("a", "b"), Set.of("a", "b")).auditLabel());
        assertEquals("keptPartial", GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "x")).auditLabel());
        assertEquals("keptUnjudged", GraphPairing.of(Set.of("a"), Set.of()).auditLabel());
        assertEquals("doesNotApply", GraphPairing.of(Set.of("a"), Set.of("x", "y")).auditLabel());
    }
}
