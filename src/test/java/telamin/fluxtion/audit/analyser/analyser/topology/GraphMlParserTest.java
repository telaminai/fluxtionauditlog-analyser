package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parsing the GraphML Fluxtion emits (M21.1). The fixture mirrors the real emitted shape — jGraph
 * ShapeNode/label/Style, entity-encoded newlines and stereotypes, {@code edgedefault="undirected"} on a
 * graph whose edges are actually directed.
 */
class GraphMlParserTest {

    private static String fixture() throws IOException {
        try (InputStream in = GraphMlParserTest.class.getResourceAsStream("/topology/sample-processor.graphml")) {
            assertNotNull(in, "fixture missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ProcessorTopology topology() throws IOException {
        return GraphMlParser.parse(fixture());
    }

    @Test
    void readsEveryNodeAndEdge() throws IOException {
        ProcessorTopology t = topology();
        assertEquals(7, t.nodeCount());
        assertEquals(5, t.edgeCount());
        assertFalse(t.isEmpty());
    }

    @Test
    void nodeIdIsTheInstanceIdSeenInNodeLogs() throws IOException {
        // the join key for step-through — must equal what the log calls the node
        assertTrue(topology().contains("midPriceCalculator_1"));
        assertTrue(topology().contains("quotePublisher_3"));
    }

    @Test
    void classNameIsExtractedFromTheLabel() throws IOException {
        ProcessorTopology.Node node = topology().node("positionCalculator_0");
        assertEquals("com.acme.demo.node.PositionCalculator", node.className(),
                "SourceNavigation needs the FQN to open the file");
        assertEquals("PositionCalculator", node.simpleName());
    }

    @Test
    void innerClassesSimplifyToTheInnerName() throws IOException {
        ProcessorTopology.Node node = topology().node("midPriceCalculator_1");
        assertEquals("com.acme.demo.node.MidPriceCalculator$Inner", node.className());
        assertEquals("Inner", node.simpleName());
    }

    @Test
    void styleBecomesNodeKind() throws IOException {
        ProcessorTopology t = topology();
        assertEquals(ProcessorTopology.Kind.EVENT_HANDLER, t.node("priceListener_2").kind());
        assertEquals(ProcessorTopology.Kind.EVENT, t.node("marketDataEvent").kind());
        assertEquals(ProcessorTopology.Kind.NODE, t.node("positionCalculator_0").kind());
        assertEquals(ProcessorTopology.Kind.EXPORT_SERVICE, t.node("quotePublisher_3").kind());
        assertEquals(ProcessorTopology.Kind.UNKNOWN, t.node("unstyledNode_9").kind(),
                "a node with no Style must parse, not vanish");
    }

    @Test
    void edgesAreDirectedDespiteTheUndirectedDeclaration() throws IOException {
        ProcessorTopology t = topology();
        assertEquals(Set.of("midPriceCalculator_1", "positionCalculator_0"), t.childrenOf("priceListener_2"));
        assertEquals(Set.of("marketDataEvent"), t.parentsOf("priceListener_2"));
        assertTrue(t.childrenOf("quotePublisher_3").isEmpty(), "a sink node feeds nothing");
    }

    @Test
    void rootsAreTheNodesNothingFeeds() throws IOException {
        List<String> roots = topology().roots().stream().map(ProcessorTopology.Node::id).toList();
        assertEquals(List.of("clock", "marketDataEvent", "unstyledNode_9"), roots,
                "document order preserved; isolated nodes count as roots");
    }

    @Test
    void documentOrderIsPreservedSoRenderingIsDeterministic() throws IOException {
        assertEquals(topology().ids().stream().toList(), topology().ids().stream().toList());
        assertEquals("clock", topology().ids().iterator().next());
    }

    @Test
    void stereotypeLinesAreNotMistakenForFields() {
        var fields = GraphMlParser.labelFields("<<EventHandle>>\nid:a_1\nclass:com.acme.A");
        assertEquals("a_1", fields.get("id"));
        assertEquals("com.acme.A", fields.get("class"));
        assertEquals(2, fields.size(), "the stereotype is not a field");
    }

    @Test
    void labelFieldsToleratesEncodedAndRawNewlines() {
        assertEquals("b_2", GraphMlParser.labelFields("id:b_2&#10;class:com.acme.B").get("id"));
        assertEquals("b_2", GraphMlParser.labelFields("id:b_2\r\nclass:com.acme.B").get("id"));
    }

    // ---- leniency ---------------------------------------------------------------------------------

    @Test
    void unusableInputYieldsAnEmptyTopologyNotAnException() {
        assertTrue(GraphMlParser.parse((String) null).isEmpty());
        assertTrue(GraphMlParser.parse("").isEmpty());
        assertTrue(GraphMlParser.parse("not xml at all").isEmpty());
        assertTrue(GraphMlParser.parse("<graphml><graph><node/></graph>").isEmpty(), "truncated document");
    }

    @Test
    void nodesWithoutAnIdAreSkippedRatherThanFailingTheFile() {
        ProcessorTopology t = GraphMlParser.parse("""
                <graphml><graph>
                  <node/>
                  <node id="good_1"><data><jGraph:label text="id:good_1&#10;class:com.acme.Good"/></data></node>
                </graph></graphml>
                """);
        assertEquals(1, t.nodeCount());
        assertTrue(t.contains("good_1"));
    }

    @Test
    void edgesToNowhereAreSkipped() {
        ProcessorTopology t = GraphMlParser.parse("""
                <graphml><graph>
                  <edge id="1" source="a"/>
                  <edge id="2" source="a" target="b"/>
                </graph></graphml>
                """);
        assertEquals(1, t.edgeCount());
    }

    @Test
    void externalEntitiesAreNotResolved() {
        // a .graphml may arrive from a shared store or a server, so it is untrusted input
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE graphml [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <graphml><graph><node id="&xxe;"/></graph></graphml>
                """;
        ProcessorTopology t = GraphMlParser.parse(xxe);
        assertTrue(t.isEmpty(), "a DOCTYPE must be refused outright, not expanded");
    }

    @Test
    void parsesFromAPathAndDegradesOnAMissingFile() throws IOException {
        Path tmp = Files.createTempFile("topology", ".graphml");
        try {
            Files.writeString(tmp, fixture(), StandardCharsets.UTF_8);
            assertEquals(7, GraphMlParser.parse(tmp).nodeCount());
        } finally {
            Files.deleteIfExists(tmp);
        }
        assertTrue(GraphMlParser.parse(Path.of("/no/such/file.graphml")).isEmpty());
    }

    @Test
    void looksLikeGraphMlGuardsTheOpenDialog() throws IOException {
        assertTrue(GraphMlParser.looksLikeGraphMl(fixture()));
        assertFalse(GraphMlParser.looksLikeGraphMl("eventLogRecord:\n  eventTime: 1"));
        assertFalse(GraphMlParser.looksLikeGraphMl(null));
    }
}
