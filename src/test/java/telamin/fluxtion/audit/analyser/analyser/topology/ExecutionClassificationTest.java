package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology.Execution.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Execution vs logging. A node only appears in {@code nodeLogs} if it writes audit output, and whether it
 * does depends on the node and the audit level — so <b>absence of a log entry is not absence of
 * execution</b>. These tests pin the four claims apart, because collapsing them into "fired / didn't
 * fire" would have the UI assert something the log never said.
 */
class ExecutionClassificationTest {

    private static ProcessorTopology topo(String... edgeSpecs) {
        Map<String, ProcessorTopology.Node> nodes = new LinkedHashMap<>();
        List<ProcessorTopology.Edge> edges = new ArrayList<>();
        for (String spec : edgeSpecs) {
            String[] parts = spec.split("->");
            for (String id : parts) {
                nodes.computeIfAbsent(id.trim(), n ->
                        new ProcessorTopology.Node(n, "", "com.acme." + n, ProcessorTopology.Kind.NODE));
            }
            if (parts.length == 2) {
                edges.add(new ProcessorTopology.Edge(spec, parts[0].trim(), parts[1].trim()));
            }
        }
        return new ProcessorTopology(nodes, edges);
    }

    @Test
    void aSoleParentOfALoggedNodeMustHaveRun() {
        // a→b→c, only c logged: c has exactly one way in, and so does b — dispatch had no alternative.
        // The entry must be known for the claim to be made at all (see the lifecycle test below).
        Map<String, ProcessorTopology.Execution> state =
                topo("a->b", "b->c").classifyCycle(List.of("c"), List.of("a"));
        assertEquals(LOGGED, state.get("c"));
        assertEquals(RAN_SILENTLY, state.get("b"), "it is the only route to c");
        assertEquals(RAN_SILENTLY, state.get("a"), "and the only route to b");
    }

    @Test
    void nothingIsForcedWhenTheRecordIsNotEventDispatch() {
        // A @Initialise/@Start callback logs too, and nothing upstream ran to cause it. With no
        // resolvable entry point this may be such a record, so "its parent must have run" is not a
        // claim the log supports — the same over-claiming, pointed upstream.
        Map<String, ProcessorTopology.Execution> state =
                topo("a->b", "b->c").classifyCycle(List.of("c"));
        assertEquals(LOGGED, state.get("c"));
        assertEquals(MAY_HAVE_RUN, state.get("b"), "unknown without knowing how the cycle started");
        assertEquals(MAY_HAVE_RUN, state.get("a"));
    }

    @Test
    void anAncestorIsNotForcedWhenTheLoggedNodeHasSeveralWaysIn() {
        // c has two parents; only one of them needed to fire, so NEITHER is certain. Claiming both ran
        // would manufacture evidence — the same over-claiming this classification exists to prevent.
        Map<String, ProcessorTopology.Execution> state =
                topo("a->c", "b->c").classifyCycle(List.of("c"));
        assertEquals(LOGGED, state.get("c"));
        assertEquals(MAY_HAVE_RUN, state.get("a"));
        assertEquals(MAY_HAVE_RUN, state.get("b"));
    }

    @Test
    void aSilentNodeDownstreamIsUnknownNotAbsent() {
        // a logged, b is downstream: dispatch may have continued, or been guarded. The log doesn't say.
        Map<String, ProcessorTopology.Execution> state =
                topo("a->b", "b->c").classifyCycle(List.of("a"));
        assertEquals(LOGGED, state.get("a"));
        assertEquals(MAY_HAVE_RUN, state.get("b"), "unknown — must not read as 'did not run'");
        assertEquals(MAY_HAVE_RUN, state.get("c"));
    }

    @Test
    void anUnconnectedNodeIsOffThePath() {
        Map<String, ProcessorTopology.Execution> state =
                topo("a->b", "x->y").classifyCycle(List.of("a"));
        assertEquals(OFF_PATH, state.get("x"));
        assertEquals(OFF_PATH, state.get("y"));
    }

    @Test
    void theStrongerClaimWinsWhenANodeIsBothUpstreamAndDownstream() {
        Map<String, ProcessorTopology.Execution> state = topo("source->mid", "mid->sink")
                .classifyCycle(List.of("source", "sink"), List.of("source"));
        assertEquals(RAN_SILENTLY, state.get("mid"),
                "sink's only way in is mid, so mid is forced — the stronger claim wins");
    }

    @Test
    void everyNodeIsClassifiedExactlyOnce() {
        ProcessorTopology t = topo("a->b", "b->c", "x->y");
        Map<String, ProcessorTopology.Execution> state = t.classifyCycle(List.of("b"));
        assertEquals(t.nodeCount(), state.size());
        assertTrue(state.values().stream().allMatch(java.util.Objects::nonNull));
    }

    @Test
    void withNothingLoggedNothingIsClaimed() {
        // no evidence at all: every node is off-path, and crucially none is marked as having not run
        Map<String, ProcessorTopology.Execution> state = topo("a->b").classifyCycle(List.of());
        assertEquals(OFF_PATH, state.get("a"));
        assertEquals(OFF_PATH, state.get("b"));
        assertFalse(state.containsValue(LOGGED));
    }

    @Test
    void instanceIdsAbsentFromTheTopologyAreIgnoredRatherThanCrashing() {
        // a build mismatch is reported by match(), not by throwing here
        Map<String, ProcessorTopology.Execution> state =
                topo("a->b").classifyCycle(java.util.Arrays.asList("a", "ghost_9", null));
        assertEquals(LOGGED, state.get("a"));
        assertEquals(2, state.size());
    }

    @Test
    void aRealisticCycleLeavesMostOfTheGraphOffPath() {
        ProcessorTopology t = topo("evt->handler", "handler->calc", "calc->publisher",
                "other->unrelated", "unrelated->elsewhere");
        Map<String, ProcessorTopology.Execution> state = t.classifyCycle(List.of("calc"), List.of("evt"));
        assertEquals(RAN_SILENTLY, state.get("handler"), "calc's only parent");
        assertEquals(RAN_SILENTLY, state.get("evt"), "the entry itself");
        assertEquals(MAY_HAVE_RUN, state.get("publisher"));
        assertEquals(OFF_PATH, state.get("other"));
    }
}
