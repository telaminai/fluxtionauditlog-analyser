package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage's denominator, and the measurement that motivated it: the shipped demo reported 50% because
 * three event classes and a service interface were counted as nodes that "never ran". The real figure
 * was 100% of what can log — the tool wrong in the ALARMING direction about its own fixture, on the
 * number a support engineer reads first.
 */
class CoverageScopeTest {

    /**
     * A graph in the shape the compiler emits: the kind is a jGraph Style `properties`, the class a
     * `class:` line inside the label. Built the real way on purpose — a helper that invents a simpler
     * shape tests a parser nobody ships, which is exactly how AuditReadinessTest's first cut failed
     * while its production code was right.
     */
    private static ProcessorTopology graphOf(String... idAndStyle) {
        StringBuilder nodes = new StringBuilder();
        for (int i = 0; i < idAndStyle.length; i += 2) {
            String id = idAndStyle[i], style = idAndStyle[i + 1];
            nodes.append("<node id=\"").append(id).append("\">")
                 .append("<data key=\"vertex_label\"><jGraph:ShapeNode>")
                 .append("<jGraph:label text=\"&lt;&lt;Node&gt;&gt;&#10;id:").append(id)
                 .append("&#10;class:com.acme.demo.").append(id).append("\"/>")
                 .append("<jGraph:Style properties=\"").append(style).append("\"/>")
                 .append("</jGraph:ShapeNode></data></node>");
        }
        return GraphMlParser.parse("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" xmlns:jGraph=\"http://www.jgraph.com/\">"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>"
                + "<graph edgedefault=\"directed\">" + nodes + "</graph></graphml>");
    }

    private static ProcessorTopology demo() throws Exception {
        return GraphMlParser.parse(
                Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml")));
    }

    @Test
    void theDemoStopsCountingEventClassesAsNodesThatNeverRan() throws Exception {
        ProcessorTopology t = demo();
        Set<String> authored = Scaffolding.authoredNodes(t);
        CoverageScope.Scope scope = CoverageScope.of(t, authored);

        // the three event classes and the exported service leave; nothing else does
        assertTrue(scope.excluded().containsKey("MarketDataEvent"), scope.excluded().toString());
        assertTrue(scope.excluded().containsKey("OrderUpdateEvent"), scope.excluded().toString());
        assertTrue(scope.excluded().containsKey("RiskBreachEvent"), scope.excluded().toString());
        assertTrue(scope.excluded().containsKey("QuoteControl"), scope.excluded().toString());
        assertEquals(4, scope.excluded().size(), scope.excluded().toString());

        // and the node that is genuinely a node stays, even though it never logs — that is slice 2's
        // job and counting it meanwhile is the honest answer: it IS a node and it DID never log
        assertTrue(scope.loggable().contains("spreadCalculator"), scope.loggable().toString());
        assertTrue(scope.loggable().contains("quotePublisher"));
    }

    @Test
    void everyExclusionCarriesItsReason() throws Exception {
        CoverageScope.Scope scope = CoverageScope.of(demo(), Scaffolding.authoredNodes(demo()));
        for (var e : scope.excluded().entrySet()) {
            assertFalse(e.getValue().isBlank(), e.getKey() + " was dropped with no reason");
        }
        String note = scope.note();
        assertNotNull(note);
        assertTrue(note.contains("3 event class(es)"), note);
        assertTrue(note.contains("1 exported service"), note);
        assertTrue(note.contains("category error"), "say WHY, not just what: " + note);
    }

    @Test
    void aGraphOfOnlyNodesLosesNothingAndSaysNothing() {
        ProcessorTopology t = graphOf("a", "NODE", "b", "EVENTHANDLER");
        CoverageScope.Scope scope = CoverageScope.of(t, Set.of("a", "b"));

        assertEquals(Set.of("a", "b"), scope.loggable());
        assertTrue(scope.excluded().isEmpty());
        assertNull(scope.note(), "silence when nothing was dropped — no note to learn to ignore");
    }

    @Test
    void anUNKNOWNkindIsKeptRatherThanAssumedSilent() {
        // dropping something we cannot classify would flatter the score, which is the same defect as
        // the one this class fixes, pointing the other way
        ProcessorTopology t = graphOf("mystery", "SOMETHING_ELSE");
        CoverageScope.Scope scope = CoverageScope.of(t, Set.of("mystery"));

        assertTrue(scope.loggable().contains("mystery"));
        assertTrue(scope.excluded().isEmpty());
    }

    @Test
    void anIdWithNoNodeInTheGraphIsKept() {
        // defensive: an id we cannot look up is not an id we may quietly discard
        CoverageScope.Scope scope = CoverageScope.of(graphOf(), Set.of("ghost"));
        assertTrue(scope.loggable().contains("ghost"));
    }
}
