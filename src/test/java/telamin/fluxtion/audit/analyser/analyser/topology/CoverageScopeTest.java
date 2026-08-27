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

    /** The demo sources ship in the jar; this is the same text the resolver hands the panel. */
    private static final java.util.function.Function<String, java.util.Optional<String>> DEMO_SRC = fqn -> {
        java.nio.file.Path p = java.nio.file.Path.of("src/main/resources/demo", fqn.replace('.', '/') + ".java");
        try {
            return java.nio.file.Files.exists(p)
                    ? java.util.Optional.of(java.nio.file.Files.readString(p)) : java.util.Optional.empty();
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    };

    @Test
    void withSourceTheDemosLastGapIsProvenUnobservableRatherThanSuspicious() throws Exception {
        // M40.2b, measured on the shipped fixture: spreadCalculator was the one id still counted as
        // "never logged" after .2a. Its source declares no supertype and never mentions auditLog, so it
        // cannot write audit output at all — its absence is not evidence of anything.
        var authored = Scaffolding.authoredNodes(demo());
        CoverageScope.Scope withSource = CoverageScope.of(demo(), authored, DEMO_SRC);

        assertEquals(java.util.List.of("spreadCalculator"),
                withSource.excludedFor(CoverageScope.Reason.SILENT_BY_CONSTRUCTION));
        assertFalse(withSource.loggable().contains("spreadCalculator"));

        // riskMonitor extends SingleNamedNode, NOT EventLogNode — the false-exclusion case. It logs,
        // and it must still be scored.
        assertTrue(withSource.loggable().contains("riskMonitor"),
                "a node that logs through a framework base must stay in the denominator");
    }

    @Test
    void withNoSourceNothingIsDroppedForSilence_theSafeDirection() throws Exception {
        var authored = Scaffolding.authoredNodes(demo());
        CoverageScope.Scope none = CoverageScope.of(demo(), authored, null);
        assertTrue(none.excludedFor(CoverageScope.Reason.SILENT_BY_CONSTRUCTION).isEmpty());
        assertTrue(none.loggable().contains("spreadCalculator"),
                "with no evidence the node stays counted — never assume silence");
    }

    @Test
    void theNoteNamesTheUnobservableNodeInsteadOfQuietlyImprovingTheScore() throws Exception {
        String note = CoverageScope.of(demo(), Scaffolding.authoredNodes(demo()), DEMO_SRC).note();
        assertTrue(note.contains("spreadCalculator"), note);
        assertTrue(note.contains("says nothing about whether it ran"),
                "excluding it is not a clean bill of health — it is unobservable: " + note);
        assertTrue(note.contains("1 node(s) that cannot log at all"), note);
        assertTrue(note.contains("3 event class(es)") && note.contains("1 exported service(s)"),
                "counted by reason, not by string containment (review N3): " + note);
    }

    @Test
    void bothSetsComeBackInGRAPHorder_notWhateverTheHashGave() {
        // review N2: the javadoc promised graph order and the record's own Set.copyOf/Map.copyOf threw
        // it away, so the live echo listed the exclusions scrambled. A documented order nobody delivers
        // is worse than none — a reader lines the list up against the Topology tab and finds it wrong.
        // `authored` is deliberately built here in the WRONG order, and as an unordered set at that.
        ProcessorTopology t = graphOf("alpha", "NODE", "BravoEvent", "EVENT", "charlie", "NODE",
                "DeltaEvent", "EVENT", "echo", "NODE");
        CoverageScope.Scope scope = CoverageScope.of(t,
                Set.of("echo", "DeltaEvent", "alpha", "BravoEvent", "charlie"));

        assertEquals(java.util.List.of("alpha", "charlie", "echo"),
                java.util.List.copyOf(scope.loggable()), "loggable must follow the graph");
        assertEquals(java.util.List.of("BravoEvent", "DeltaEvent"),
                java.util.List.copyOf(scope.excluded().keySet()), "excluded must follow the graph");
    }

    @Test
    void anIdTheGraphDoesNotHoldStillComesBackStably() {
        // it cannot have a graph position, so it goes last in a deterministic order rather than a
        // hash-dependent one — two runs of the same verb must not print two different lists
        ProcessorTopology t = graphOf("beta", "NODE");
        var first = CoverageScope.of(t, Set.of("zulu", "beta", "alpha"));
        var again = CoverageScope.of(t, new java.util.HashSet<>(Set.of("alpha", "zulu", "beta")));

        assertEquals(java.util.List.of("beta", "alpha", "zulu"), java.util.List.copyOf(first.loggable()));
        assertEquals(java.util.List.copyOf(first.loggable()), java.util.List.copyOf(again.loggable()));
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
