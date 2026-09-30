package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VerbSchemasTest {

    @Test
    @SuppressWarnings("unchecked")
    void reportRestorePublishesBothNamesAndTheBooleanListRequest() {
        var properties = (Map<String, Object>) schema("report").get("properties");
        var restore = (Map<String, Object>) properties.get("restore");
        assertEquals(List.of(Map.of("type", "string"), Map.of("type", "boolean", "enum", List.of(true))),
                restore.get("anyOf"), "the manifest must accept a report name or boolean true, not reserve a string name");
    }

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
                "topology", "open", "source", "source_root", "screenshot", "coverage", "context", "series", "spotlight", "walk",
                "import"),
                schemas.keySet());
    }

    /**
     * Verbs that legitimately take no parameters. Empty since §H feedback 17 gave {@code context} its one
     * optional {@code sections}; the checks below stay for the next verb that needs no arguments.
     */
    private static final Set<String> NO_PARAMS = Set.of();

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
        assertTrue(aggregate.contains("records you happened to read are only a sample"), aggregate);
        assertTrue(aggregate.contains("returns firstRecordIndex and lastRecordIndex, the first and last record it counted")
                && aggregate.contains("filter to the event an application logs to learn when it first logged it"), aggregate);
        // Haiku 4v4 replication (2026-09-27): runs that FILTERED aggregate to the event received firstRecordIndex and
        // were right; the run that never filtered was wrong. The description shows the filtered call itself.
        assertTrue(aggregate.contains("{metric: count, filter: {dimensions: [\"<EventName>\"]}}")
                && aggregate.contains("as firstRecordIndex, the record where it first did"), aggregate);
        assertFalse(aggregate.contains("limit"), "no limit wording on aggregate: earlier trials showed it misleads: " + aggregate);
        String metric = (String) ((Map<String, Object>) ((Map<String, Object>) schema("aggregate").get("properties"))
                .get("metric")).get("description");
        assertTrue(metric.contains("application itself logged a breach flag")
                && metric.contains("a value exceeding a limit is not the same"), metric);
        String read = (String) schema("read").get("description");
        assertTrue(read.contains("A window of records is a sample: do not count events from it"), read);
        // 10-vs-10s (2026-09-26): prose defining a 'first' occurrence lowered first-breach answers (1/10, 2/10 vs 5/10)
        assertFalse(read.contains("earliest record of the event"), "the first-occurrence prose stays out: " + read);
        // 10-vs-10 (2026-09-26): steering 'first' questions to a series crossing sent models to the value crossing
        String series = (String) schema("series").get("description");
        assertFalse(series.contains("crossing of the key it writes"), "the series sentence that misled models stays out: " + series);
    }

    /**
     * Virgin-LLM run on 1.22.1: a smaller model lit the status bar and toolbar:flag for menu answers — the two
     * targets the description itself used as examples. It now asks for the item and gives a menu item as the example.
     */
    @Test
    @SuppressWarnings("unchecked")
    void spotlightSendsMenuAnswersToTheItem() {
        String spot = (String) schema("spotlight").get("description");
        assertTrue(spot.contains("To show where a command is, light its ITEM, menu:<Menu>:<item>"), spot);
        String target = (String) ((Map<String, Object>) ((Map<String, Object>) schema("spotlight").get("properties"))
                .get("target")).get("description");
        assertTrue(target.contains("menu:Audit log:Follow (tail)"), target);
        assertFalse(target.contains("toolbar:flag") || target.contains(" status."), "the examples no longer suggest the status bar or a toolbar button: " + target);
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
    void contextPublishesItsSectionNames_andRequiresNone() {
        // §H feedback 17: the names an agent is offered are the names the parser accepts — one list
        Map<String, Object> sections = (Map<String, Object>) ((Map<String, Object>) schema("context")
                .get("properties")).get("sections");
        Map<String, Object> items = (Map<String, Object>) sections.get("items");
        assertEquals(ContextSections.NAMES, items.get("enum"));
        assertNull(schema("context").get("required"), "the full context stays the default");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aggregateRequiresMetric() {
        List<String> required = (List<String>) schema("aggregate").get("required");
        assertNotNull(required);
        assertTrue(required.contains("metric"));
    }
}
