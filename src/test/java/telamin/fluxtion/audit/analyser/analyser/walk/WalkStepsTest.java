package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S1 (spec-spotlight-walks.md §3.3, W-A7): a step carries only the allow-listed view and supported targets, and
 * anything else is refused BY NAME before a walk is saved.
 */
class WalkStepsTest {

    private static WalkSteps.Parsed parse(Object... steps) {
        return WalkSteps.parse(List.of(steps));
    }

    /** A refusal is asserted as a FAILURE: no refusal fails here, rather than throwing on a null message. */
    private static void refused(WalkSteps.Parsed p, String mentions) {
        assertFalse(p.ok(), "expected a refusal mentioning '" + mentions + "', but it parsed");
        assertTrue(p.error().contains(mentions), "the refusal should mention '" + mentions + "': " + p.error());
    }

    private static Map<String, Object> step(Map<String, Object> view, Object... targets) {
        return view == null ? Map.of("targets", List.of(targets)) : Map.of("view", view, "targets", List.of(targets));
    }

    @Test
    @DisplayName("a full step parses: tab, complete filter, record, chart, focus, and targets with captions")
    void aFullStepParses() {
        var p = parse(Map.of("caption", "why",
                "view", Map.of("tab", "graph", "record", 36, "graph", "Alarm lifecycle", "focus", "alarm path",
                        "filter", Map.of("from", 10, "to", 90, "groupMode", "RAW_EVENT", "dimensions", List.of("Row"), "text", "rej")),
                "targets", List.of(Map.of("target", "graph:note:1", "caption", "RAISED"), "records:row:36")));
        assertTrue(p.ok(), p.error());
        var s = p.steps().get(0);
        assertEquals(new WalkSpec.Filter(10L, 90L, "RAW_EVENT", List.of("Row"), "rej"), s.view().filter());
        assertEquals(36, s.view().record());
        assertEquals("alarm path", s.view().focus().name());
        assertEquals(List.of("graph:note:1", "records:row:36"), s.targets().stream().map(WalkSpec.Target::target).toList());
    }

    @Test
    @DisplayName("a filter missing fields takes the documented defaults, never 'whatever is selected now'")
    void aPartialFilterTakesDefaults() {
        var p = parse(Map.of("view", Map.of("filter", Map.of("text", "x")), "targets", List.of("status")));
        assertTrue(p.ok(), p.error());
        assertEquals(new WalkSpec.Filter(null, null, "DIMENSION", null, "x"), p.steps().get(0).view().filter());
    }

    @Test
    @DisplayName("review PR57 R9: a number is range-checked BEFORE narrowing — 2^32 is not record 0, infinity is not a record")
    void numbersAreRangeCheckedBeforeNarrowing() {
        refused(parse(step(Map.of("record", 4294967296L), "records:row:0")), "view.record");
        refused(parse(step(Map.of("record", Double.POSITIVE_INFINITY), "records:row:0")), "view.record");
        refused(parse(step(Map.of("record", -1), "records:row:0")), "view.record");
        refused(parse(step(Map.of("record", new java.math.BigInteger("18446744073709551616")), "records:row:0")), "view.record");
        refused(parse(step(Map.of("filter", Map.of("from", 1e300)), "status")), "view.filter.from");
        refused(parse(step(Map.of("filter", Map.of("to", 1.5)), "status")), "view.filter.to");
        var ok = parse(step(Map.of("record", 2147483647L), "status"));
        assertTrue(ok.ok(), "the largest supported index is accepted: " + ok.error());
        assertEquals(Integer.MAX_VALUE, ok.steps().get(0).view().record());
    }

    @Test
    @DisplayName("every field outside the allow-list is refused by name — a chart window explains itself")
    void fieldsOutsideTheAllowListAreRefusedByName() {
        refused(parse(Map.of("view", Map.of("window", 1), "targets", List.of("status"))), "view.window");
        refused(parse(Map.of("view", Map.of("window", 1), "targets", List.of("status"))), "window is not restored");
        refused(parse(Map.of("view", Map.of("open", "x.yaml"), "targets", List.of("status"))), "view.open");
        refused(parse(Map.of("view", Map.of("filter", Map.of("pin", 1)), "targets", List.of("status"))), "view.filter.pin");
        refused(parse(Map.of("delete", true, "targets", List.of("status"))), "'delete' is not a step field");
        refused(parse(Map.of("targets", List.of(Map.of("target", "status", "bounds", 1)))), "'bounds'");
        refused(parse(step(Map.of("filter", Map.of("groupMode", "SIDEWAYS")), "status")), "groupMode");
        refused(parse(step(Map.of("tab", "nowhere"), "status")), "view.tab");
        refused(parse(step(Map.of("graph", "a:b"), "status")), "view.graph");
        refused(parse(step(Map.of("filter", Map.of("from", 9, "to", 1)), "status")), "after");
    }

    @Test
    @DisplayName("source, toolbar and menu targets are refused in a walk step, saying why")
    void unsupportedTargetFamiliesAreRefused() {
        for (String t : List.of("menu:Audit log", "toolbar:flag", "source:design", "source:java:com.acme.A")) {
            var p = parse(Map.of("targets", List.of(t)));
            assertFalse(p.ok(), t);
            assertTrue(p.error().contains("cannot be a walk step"), p.error());
        }
    }

    @Test
    @DisplayName("targets that mean 'the selected one' need the step to select it")
    void selectedTargetsNeedTheirSelection() {
        refused(parse(Map.of("targets", List.of("detail:node:a"))), "view.record");
        refused(parse(Map.of("targets", List.of("graph:note:1"))), "view.graph");
        assertTrue(parse(Map.of("targets", List.of("graph:Named:note:1"))).ok(), "a named chart needs no selection");
    }

    @Test
    @DisplayName("limits: six targets, one line of caption, no duplicates, no empty step, at least one step")
    void limits() {
        List<Object> seven = List.of("records:row:1", "records:row:2", "records:row:3", "records:row:4", "records:row:5",
                "records:row:6", "records:row:7");
        refused(parse(Map.of("targets", seven)), "at most 6");
        refused(parse(Map.of("targets", List.of("status", "status"))), "twice");
        refused(parse(Map.of("caption", "a\nb", "targets", List.of("status"))), "one line");
        refused(parse(Map.of()), "empty step");
        refused(WalkSteps.parse(List.of()), "at least one step");
        refused(WalkSteps.parse("steps"), "a list");
    }

    @Test
    @DisplayName("each target's basis kind follows §3.5")
    void basisKinds() {
        assertEquals("record", WalkSteps.basisKind("records:row:3"));
        assertEquals("record", WalkSteps.basisKind("detail:node:a"));
        assertEquals("chart", WalkSteps.basisKind("graph:G:series:x"));
        assertEquals("graph", WalkSteps.basisKind("topology:node:a"));
        assertEquals("none", WalkSteps.basisKind("tab:graph"));
        assertEquals("none", WalkSteps.basisKind("topology:verdict"));
    }
}
