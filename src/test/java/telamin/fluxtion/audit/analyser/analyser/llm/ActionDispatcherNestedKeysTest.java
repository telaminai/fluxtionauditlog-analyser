package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.4 (D-E3), "not audited: keys nested inside items" — closed in set 13 (E). An unknown key inside a note, a marker,
 * a guide, a band or a report section was dropped without a word, where the same mistake at the top level is named.
 * Nested keys are now checked against the item's declared schema and named by path, on success and on refusal alike.
 * A free-form object (a section's {@code call}) declares no properties, and is not second-guessed.
 */
class ActionDispatcherNestedKeysTest {

    private static ActionDispatcher dispatcher(boolean ok) {
        RenderExecutor render = (action, params) -> ok
                ? ActionResult.ok(action, "result", Map.of("done", true))
                : ActionResult.error("refused for its own reasons");
        return new ActionDispatcher(false, null, () -> { throw new IllegalStateException("no log"); },
                row -> null, row -> null, render);
    }

    @Test
    @DisplayName("E: an unknown key inside an item is named by its path — and a correct item names nothing")
    void aNestedUnknownKeyIsNamed() {
        // witness: withIgnoredParams without the nested walk
        var r = dispatcher(true).dispatch(Map.of("action", "graph", "params", Map.of(
                "notes", List.of(Map.of("text", "fine", "at", 1L), Map.of("text", "typo", "txt", "x")))));
        assertTrue(r.ok(), r.toMap().toString());
        assertEquals(List.of("notes[1].txt"), r.payload().get("ignoredParams"), "E: " + r.toMap());

        var clean = dispatcher(true).dispatch(Map.of("action", "graph", "params", Map.of(
                "notes", List.of(Map.of("text", "fine", "at", 1L)))));
        assertNull(clean.payload().get("ignoredParams"), clean.toMap().toString());
    }

    @Test
    @DisplayName("E: a refusal names nested keys too; a section's free-form call is not checked")
    void aRefusalNamesThemAndAFreeFormObjectIsLeftAlone() {
        var refused = dispatcher(false).dispatch(Map.of("action", "report", "params", Map.of("name", "r",
                "sections", List.of(Map.of("kind", "table", "call", Map.of("verb", "read", "anything", 1), "focuss", "x")))));
        assertFalse(refused.ok());
        String said = String.valueOf(refused.toMap().get("error"));
        assertTrue(said.contains("sections[0].focuss"), "E: " + said);
        assertFalse(said.contains("anything"), "the call's keys belong to the verb it names: " + said);
    }
}
