package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M35.2 — does a loaded graph still describe the log that just arrived? The decision is pure and
 * pinned here; what it does to the UI is verified by running the jar (rule 4).
 */
class GraphPairingTest {

    @Test
    void aDifferentSystemIsClosed_theDefectM35ExistsToPrevent() {
        // the M35.1 report's reproduction, as data: a market-maker log against a supermarket graph
        var p = GraphPairing.of(
                Set.of("chiller1", "chiller2", "tillHealth", "stockLedger", "dailyReport"),
                Set.of("bidMakerOrder", "askMakerOrder", "positionNode"));
        assertFalse(p.applies());
        assertEquals(3, p.logged());
        assertEquals(0, p.matched());
        assertTrue(p.reason().contains("different system or build"), p.reason());
        assertFalse(p.reason().contains("closed"), "the reason states the FACT; the ACTION "
                + "is the caller's word, because log-open closes and graph-open keeps: " + p.reason());
        assertTrue(p.reason().contains("0 of the 3"), "the numbers travel with the verdict: " + p.reason());
    }

    @Test
    void theSameSystemWithNewNodesIsKept_aRebuildIsNotAMismatch() {
        // build B added a node; everything the log writes is still declared but one
        var p = GraphPairing.of(
                Set.of("bidMakerOrder", "askMakerOrder", "positionNode", "riskMonitor"),
                Set.of("bidMakerOrder", "askMakerOrder", "positionNode", "newlyAdded"));
        assertTrue(p.applies(), p.reason());
        assertEquals(3, p.matched());
        assertTrue(p.reason().startsWith("the graph declares"), p.reason());
    }

    @Test
    void anExactMatchIsKept() {
        var ids = Set.of("a", "b", "c");
        var p = GraphPairing.of(ids, ids);
        assertTrue(p.applies());
        assertEquals(3, p.matched());
    }

    @Test
    void aBareMajorityIsNotEnough_theThresholdIsStrictlyAbove() {
        // 2 of 4 is exactly KEEP_ABOVE and must NOT keep: half a graph describing half a log is
        // not evidence, and the safe reading is the one that cannot be confidently wrong
        var p = GraphPairing.of(Set.of("a", "b"), Set.of("a", "b", "x", "y"));
        assertFalse(p.applies(), p.reason());
    }

    @Test
    void aLogThatWritesNothingCannotConvictTheGraph() {
        var p = GraphPairing.of(Set.of("a", "b"), Set.of());
        assertTrue(p.applies(), "silence is not evidence of mismatch");
        assertTrue(p.reason().contains("cannot say"), p.reason());
    }

    @Test
    void anEmptyGraphNeverApplies() {
        var p = GraphPairing.of(Set.of(), Set.of("a"));
        assertFalse(p.applies());
        assertTrue(p.reason().contains("declares no nodes"), p.reason());
    }

    @Test
    void theVerdictAlwaysCarriesItsReason_neverASilentBoolean() {
        for (var p : java.util.List.of(
                GraphPairing.of(Set.of("a"), Set.of("a")),
                GraphPairing.of(Set.of("a"), Set.of("z")),
                GraphPairing.of(Set.of(), Set.of("a")),
                GraphPairing.of(Set.of("a"), Set.of()))) {
            assertNotNull(p.reason());
            assertFalse(p.reason().isBlank(), "a decision the user cannot check is a decision they "
                    + "cannot disagree with");
        }
    }
}
