package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VerbSchemasTest {

    private final Map<String, Object> schemas = VerbSchemas.all();

    @SuppressWarnings("unchecked")
    private Map<String, Object> schema(String verb) {
        return (Map<String, Object>) schemas.get(verb);
    }

    @SuppressWarnings("unchecked")
    private Set<String> props(String verb) {
        return ((Map<String, Object>) schema(verb).get("properties")).keySet();
    }

    @Test
    void coversEveryDispatchedVerb() {
        // must stay in step with ActionDispatcher / ActionServer's verb list
        assertEquals(Set.of("aggregate", "read", "filter", "graph", "goto", "flag",
                "topology", "open", "source_root", "screenshot"), schemas.keySet());
    }

    @Test
    void everySchemaIsAnObjectWithProperties() {
        for (String verb : schemas.keySet()) {
            assertEquals("object", schema(verb).get("type"), verb + " schema type");
            assertFalse(props(verb).isEmpty(), verb + " has properties");
            assertNotNull(schema(verb).get("description"), verb + " has a description");
        }
    }

    @Test
    void keyVerbParamsArePublished() {
        assertTrue(props("read").containsAll(Set.of("recordIndex", "byteOffset", "count", "before", "after")));
        assertTrue(props("graph").contains("rationale"), "AV.2 provenance param");
        assertTrue(props("goto").contains("reveal"), "AV.4 reveal param");
        assertTrue(props("filter").containsAll(Set.of("from", "to", "dimensions", "text")));
        assertTrue(props("flag").containsAll(Set.of("byteOffsets", "recordIndexes", "note")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aggregateRequiresMetric() {
        List<String> required = (List<String>) schema("aggregate").get("required");
        assertNotNull(required);
        assertTrue(required.contains("metric"));
    }
}
