package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a DataFlow {@code .push()} looks like by the time it reaches us — tracker M22.20.
 *
 * <p>The fixture is a real processor compiled on 2026-08-17 for the GraphML investigation recorded in
 * {@code docs/proposals/upstream-asks.md} §2c. It contains one of every construct whose meaning the
 * emitted file might have carried: a plain handler, a {@code filterString} handler, a
 * {@code FilterType.defaultCase} handler, an {@code @OnEventHandler(propagate=false)} handler, a node with
 * one <b>trigger</b> parent and one {@code @NoTriggerReference} <b>data</b> parent, an
 * {@code @ExportService(propagate=false)} service, and {@code .push(pushTarget::setPushed)}.
 *
 * <p><b>These tests pin a DEFECT, not a desired behaviour.</b> They exist so the evidence behind M22.20
 * and upstream asks UP-FLX-28/29/31 lives in the repository rather than in a session, and so that fixing
 * either end breaks a test that says why. When M22.20 lands, {@link #pushTargetIsOrphanedWhenScaffoldingIsHidden}
 * is the one that must change.
 */
class PushChainTest {

    private static final Path FIXTURE = Path.of("src/test/resources/topology/push-probe.graphml");

    private static ProcessorTopology topology() {
        return GraphMlParser.parse(FIXTURE);
    }

    /**
     * The push is not one edge — it is a chain through three framework nodes. Nothing on the chain says
     * "this is a push"; only the generated node <em>names</em> hint at it.
     */
    @Test
    void aPushBecomesAChainOfFrameworkFlowNodes() {
        ProcessorTopology t = topology();
        assertTrue(hasEdge(t, "rawFeed", "nodeToFlowFunction_8"));
        assertTrue(hasEdge(t, "nodeToFlowFunction_8", "mapRef2RefFlowFunction_9"));
        assertTrue(hasEdge(t, "mapRef2RefFlowFunction_9", "pushFlowFunction_10"));
        assertTrue(hasEdge(t, "pushFlowFunction_10", "pushTarget"));

        assertFalse(hasEdge(t, "rawFeed", "pushTarget"),
                "there is no direct edge — the relationship exists only through the chain");
    }

    /**
     * The defect. Every link of the chain touches a node whose class matches the framework package
     * prefix, so hiding scaffolding — the DEFAULT — drops all four edges and leaves the push target
     * connected to nothing.
     */
    @Test
    void pushTargetIsOrphanedWhenScaffoldingIsHidden() {
        ProcessorTopology t = topology();
        Set<String> authored = Scaffolding.authoredNodes(t);

        assertTrue(authored.contains("pushTarget"), "the target is the user's own node, so it is shown");
        assertFalse(authored.contains("pushFlowFunction_10"), "its only neighbour is framework plumbing");

        long surviving = t.edges().stream()
                .filter(e -> authored.contains(e.source()) && authored.contains(e.target()))
                .filter(e -> e.source().equals("pushTarget") || e.target().equals("pushTarget"))
                .count();
        assertEquals(0, surviving,
                "M22.20: pushTarget renders as a disconnected box — the rawFeed -> pushTarget "
                        + "relationship is invisible in the default view");
    }

    /**
     * Four semantically different event edges, one structure. This is the whole of UP-FLX-31 and half of
     * UP-FLX-28, stated as an assertion: the analyser cannot tell these apart because the file does not.
     */
    @Test
    void filteredDefaultCaseAndNonPropagatingEdgesAreIndistinguishable() {
        ProcessorTopology t = topology();
        for (String handler : new String[]{"rawFeed", "acmeFeed", "fallbackFeed", "quietFeed"}) {
            assertTrue(hasEdge(t, "PriceEvent", handler), handler + " is fed by PriceEvent");
        }
        // rawFeed always fires; acmeFeed only for filterString "ACME"; fallbackFeed only when nothing
        // matched; quietFeed fires but never propagates. The topology model has no field that differs.
        assertEquals(1, t.edges().stream()
                        .filter(e -> e.source().equals("PriceEvent"))
                        .map(e -> e.getClass().getSimpleName())
                        .distinct().count(),
                "one edge type for four different behaviours");
    }

    /** A trigger parent and a {@code @NoTriggerReference} data parent are the same edge here. */
    @Test
    void triggerAndDataParentsAreTheSameEdge() {
        ProcessorTopology t = topology();
        assertTrue(hasEdge(t, "rawFeed", "consumer"), "trigger parent");
        assertTrue(hasEdge(t, "dataBag", "consumer"), "@NoTriggerReference data parent");
        // Nothing distinguishes them — UP-FLX-28's refKind is the ask that would.
    }

    private static boolean hasEdge(ProcessorTopology t, String from, String to) {
        return t.edges().stream().anyMatch(e -> e.source().equals(from) && e.target().equals(to));
    }
}
