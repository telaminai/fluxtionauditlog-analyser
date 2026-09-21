package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.*;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DispatchHierarchyTest {
    private static final Path FIXTURE = Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/desk-quote-supertype.graphml");

    @Test void committedSupertypeGraphCannotExcludeAnUnmappedRoute() throws Exception {
        var graph = GraphMlParser.parse(FIXTURE);
        var entries = EntryPointResolver.resolve(graph, "com.example.myapp.event.MarketPrice", null);
        assertEquals(java.util.Set.of("MarketPrice"), entries);
        assertFalse(graph.childrenOf("MarketPrice").contains("Quote"), "fixture really lacks the supertype edge");
        var result = graph.classifyCycle(List.of("priceBook"), entries);
        assertEquals(ProcessorTopology.Execution.LOGGED, result.get("priceBook"));
        assertEquals(ProcessorTopology.Execution.MAY_HAVE_RUN, result.get("acmeQuoteFeed"), "unknown hierarchy cannot exclude the vendor handler");
        assertFalse(result.containsValue(ProcessorTopology.Execution.OFF_PATH));
        // Negative control: explicit complete invocation evidence still makes absence conclusive.
        assertEquals(ProcessorTopology.Execution.DID_NOT_RUN,
                graph.classifyCycle(List.of("priceBook"), entries, true).get("acmeQuoteFeed"));
    }

    @Test void qualificationReachesHumanContextAndCoverageReportNotes() throws Exception {
        var panel = new TopologyPanel();
        panel.load(FIXTURE);
        assertEquals("unknown", panel.cursorState().get("dispatchHierarchy"));
        assertEquals(EntryPointResolver.HIERARCHY_NOTE, panel.cursorState().get("dispatchNote"));
        assertTrue(((javax.swing.JLabel)panel.statusComponent()).getText().contains(EntryPointResolver.HIERARCHY_NOTE));
        var graph = panel.fullTopology();
        // Constructed record: no runtime replay or inferred hierarchy.
        var log = new HeapLogStore("eventLogRecord:\n  event: MarketPrice\n  nodeLogs:\n    - priceBook: {v: 1}\n---\n");
        var coverage = CoverageService.assess(log, false, null,
                new CoverageService.Input(graph, Scaffolding.authoredNodes(graph), null));
        assertEquals("unknown", coverage.echo().get("dispatchHierarchy"));
        assertTrue(coverage.notes().contains(EntryPointResolver.HIERARCHY_NOTE), "report gets the same qualification");
    }
}
