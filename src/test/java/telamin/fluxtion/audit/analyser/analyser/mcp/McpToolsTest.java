package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The MCP tool adapter (spec-assistant-actions-mcp §9, M13.2). The point of these assertions is that
 * {@link VerbSchemas} stays the single source of truth: the tool set is derived, never re-declared, so a
 * new verb publishes a tool for free and no tool can exist without a dispatcher verb behind it.
 */
class McpToolsTest {

    private final List<Map<String, Object>> tools = McpTools.list();

    private Map<String, Object> tool(String verb) {
        return tools.stream()
                .filter(t -> ("analyser_" + verb).equals(t.get("name")))
                .findFirst().orElseThrow(() -> new AssertionError("no tool for verb " + verb));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> annotations(String verb) {
        return (Map<String, Object>) tool(verb).get("annotations");
    }

    @Test
    void exposesExactlyTheVerbSchemasVerbSet() {
        Set<String> expected = VerbSchemas.all().keySet().stream()
                .map(v -> "analyser_" + v).collect(Collectors.toSet());
        Set<String> actual = tools.stream().map(t -> (String) t.get("name")).collect(Collectors.toSet());
        assertEquals(expected, actual, "no more, no fewer — the adapter must not fork the schema set");
        assertEquals(13, tools.size(), "thirteen verbs ship today: 4 query, 5 render, 4 control");
    }

    @Test
    void orderIsDeterministic() {
        // MCP asks servers to keep tools/list ordering stable so clients can cache it
        assertEquals(tools.stream().map(t -> t.get("name")).toList(),
                McpTools.list().stream().map(t -> t.get("name")).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void descriptorsAreValidMcpTools() {
        for (Map<String, Object> t : tools) {
            String name = (String) t.get("name");
            assertNotNull(t.get("description"), name + " has a description");
            assertFalse(((String) t.get("description")).isBlank(), name + " description is non-empty");

            Map<String, Object> schema = (Map<String, Object>) t.get("inputSchema");
            assertNotNull(schema, name + " has an inputSchema");
            assertEquals("object", schema.get("type"), name + " inputSchema must be a JSON Schema object");
            assertInstanceOf(Map.class, schema.get("properties"), name + " inputSchema has properties");
            assertNull(schema.get("description"), name + " description is lifted to the tool, not duplicated");
        }
    }

    @Test
    void readOnlyHintOnQueryVerbsOnly() {
        assertEquals(true, annotations("aggregate").get("readOnlyHint"));
        assertEquals(true, annotations("read").get("readOnlyHint"));
        for (String render : List.of("filter", "graph", "goto", "flag")) {
            assertEquals(false, annotations(render).get("readOnlyHint"), render + " changes the UI");
            assertEquals(false, annotations(render).get("destructiveHint"), render + " is reversible");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchemaCarriesTheVerbsParams() {
        Map<String, Object> schema = (Map<String, Object>) tool("graph").get("inputSchema");
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("rationale"), "the shipped graph params come through unchanged");
        assertTrue(props.containsKey("exprs"));
    }

    @Test
    void verbForMapsBackAndRejectsForeignNames() {
        assertEquals("aggregate", McpTools.verbFor("analyser_aggregate"));
        assertEquals("goto", McpTools.verbFor("analyser_goto"));
        assertNull(McpTools.verbFor("analyser_launch_missiles"), "not a dispatcher verb");
        assertNull(McpTools.verbFor("aggregate"), "unprefixed");
        assertNull(McpTools.verbFor(null));
    }

    @Test
    void mutatingTheAdaptersOutputCannotCorruptTheSchemas() {
        tool("aggregate").put("name", "tampered");
        assertEquals("analyser_aggregate", McpTools.list().get(0).get("name"));
    }
}
