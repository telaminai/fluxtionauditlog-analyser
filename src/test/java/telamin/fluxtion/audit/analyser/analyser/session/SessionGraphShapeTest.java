package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditReadiness;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M44 §10 — <b>the picture has to be checkable, and it is checked by the analyser's own reader.</b>
 *
 * <p>The committed GraphML is parsed with {@code GraphMlParser}, the same code that reads a customer's
 * graph. That is the dog-food test in miniature: if our own emitted topology were unreadable by our own
 * parser we would want to know before a user did.
 *
 * <p>What it asserts is one structural property. <b>An effect request must descend from a decision, not
 * from an input event.</b> A short-circuit — an event wired straight to something that acts — is
 * precisely the shape of the orchestration this milestone is removing, and it is visible in the picture
 * if you know to look. This test is knowing to look.
 */
class SessionGraphShapeTest {

    private static final Path GRAPHML = Path.of(
            "src/main/resources/telamin/fluxtion/audit/analyser/analyser/session/generated/SessionProcessor.graphml");

    private static ProcessorTopology topology() {
        return GraphMlParser.parse(GRAPHML);
    }

    @Test
    @DisplayName("our own reader can read our own graph")
    void theGraphParses() {
        ProcessorTopology topology = topology();
        Set<String> ids = topology.nodes().stream().map(ProcessorTopology.Node::id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(Set.of(
                        "operationGate", "activeProject", "openLog", "openGraph",
                        "sessionBoundary", "effectQueue", "effectOutcomes")),
                "slice 1's nodes are all present: " + ids);
    }

    @Test
    @DisplayName("effects descend FROM the decision — the @PushReference is what makes this true")
    void effectsDescendFromTheDecision() {
        ProcessorTopology topology = topology();

        assertTrue(hasEdge(topology, "sessionBoundary", "effectQueue"),
                "the queue must be downstream of the decision. Without @PushReference the reference "
                        + "reads as an ordinary dependency and the arrow points the other way, which "
                        + "would draw effects as feeding INTO the decision that produced them.");
        assertFalse(hasEdge(topology, "effectQueue", "sessionBoundary"));

        // Nothing may reach the queue except through a DECISION. Asserted as the property rather than
        // as a fixed list of node names: M44.2 added `logArrival`, which is a second decision and a
        // legitimate second source, and a test that pinned the names would have called that a defect.
        // What must never appear is an EVENT — an input wired straight to the effect queue is a short
        // circuit round the policy, and that is what this catches.
        Set<String> intoQueue = topology.edges().stream()
                .filter(e -> e.target().equals("effectQueue"))
                .map(ProcessorTopology.Edge::source)
                .collect(Collectors.toSet());
        assertFalse(intoQueue.isEmpty(), "something must fill the queue");
        for (String source : intoQueue) {
            ProcessorTopology.Node node = topology.node(source);
            assertNotNull(node, source);
            assertNotEquals(ProcessorTopology.Kind.EVENT, node.kind(),
                    source + " is an EVENT wired straight to the effect queue — that is a short "
                            + "circuit round the policy, which is exactly what this picture exists "
                            + "to make visible");
        }
        assertTrue(intoQueue.contains("sessionBoundary"), "the session-boundary decision fills it");
    }

    @Test
    @DisplayName("the gate is upstream of the state it guards, and of the decision")
    void theGateGuardsEverythingDownstream() {
        ProcessorTopology topology = topology();
        for (String guarded : new String[]{"activeProject", "openLog", "openGraph", "sessionBoundary",
                "effectOutcomes"}) {
            assertTrue(hasEdge(topology, "operationGate", guarded),
                    "operationGate must run before " + guarded + ", or a stale result is believed "
                            + "before it is refused");
        }
    }

    @Test
    @DisplayName("the decision reads the state it decides about")
    void theDecisionReadsState() {
        ProcessorTopology topology = topology();
        for (String read : new String[]{"activeProject", "openLog", "openGraph"}) {
            assertTrue(hasEdge(topology, read, "sessionBoundary"),
                    read + " must be upstream of sessionBoundary: the decision needs the state as it "
                            + "was BEFORE this transition applies");
        }
    }

    @Test
    @DisplayName("the processor can actually write an audit log — asked of the graph, as a user would")
    void auditIsInstalled() {
        AuditReadiness readiness = AuditReadiness.of(topology());
        assertTrue(readiness.isEnabled(),
                "addEventAudit is what installs EventLogManager. Without it the nodes narrate "
                        + "themselves into a processor that writes nothing: " + readiness.message());
    }

    private static boolean hasEdge(ProcessorTopology topology, String source, String target) {
        return topology.edges().stream()
                .anyMatch(e -> e.source().equals(source) && e.target().equals(target));
    }
}
