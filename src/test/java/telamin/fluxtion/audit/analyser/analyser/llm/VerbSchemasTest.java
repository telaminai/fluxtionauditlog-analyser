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
        assertEquals(Set.of("aggregate", "read", "filter", "graph", "goto", "flag", "report",
                "topology", "open", "source", "source_root", "screenshot", "coverage", "context", "series", "spotlight"),
                schemas.keySet());
    }

    /** Verbs that legitimately take no parameters — "what are you looking at?" needs no arguments. */
    private static final Set<String> NO_PARAMS = Set.of("context");

    @Test
    void everySchemaIsAnObjectWithProperties() {
        for (String verb : schemas.keySet()) {
            assertEquals("object", schema(verb).get("type"), verb + " schema type");
            if (!NO_PARAMS.contains(verb)) {
                assertFalse(props(verb).isEmpty(), verb + " has properties");
            }
            assertNotNull(schema(verb).get("description"), verb + " has a description");
        }
    }

    @Test
    void aNoParamVerbStillPublishesAnEmptyPropertiesObject() {
        // MCP clients build a form from `properties`; omitting the key entirely makes some of them treat
        // the tool as untyped rather than as taking nothing
        for (String verb : NO_PARAMS) {
            assertNotNull(schema(verb).get("properties"), verb + " must publish an (empty) properties map");
            assertTrue(props(verb).isEmpty(), verb + " takes no parameters");
        }
    }

    /**
     * Virgin-LLM runs on the demo log (2026-09-24): a smaller model counted breaches from a window of records
     * it had read, and named the first record whose value passed the limit as the first breach, although the
     * application logged its breach one record later. The descriptions must say where each answer comes from.
     */
    @Test
    @SuppressWarnings("unchecked")
    void countingAndFirstOccurrenceQuestionsAreSentToTheToolsThatAnswerThem() {
        String aggregate = (String) schema("aggregate").get("description");
        assertTrue(aggregate.contains("counts, not record positions") && aggregate.contains("series crossings"), aggregate);
        String metric = (String) ((Map<String, Object>) ((Map<String, Object>) schema("aggregate").get("properties"))
                .get("metric")).get("description");
        assertTrue(metric.contains("application itself logged a breach flag")
                && metric.contains("a value exceeding a limit is not the same"), metric);
        String series = (String) schema("series").get("description");
        assertTrue(series.contains("crossing of the key it writes for that event"), series);
        String read = (String) schema("read").get("description");
        assertTrue(read.contains("do not count events or name a 'first' from it"), read);
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
