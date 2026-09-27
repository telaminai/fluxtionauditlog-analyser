package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two lifecycle gaps in the {@code graph} verb: re-sending notes doubled them, and a chart could be
 * created and renamed but never removed.
 */
class GraphNotesReplaceAndChartsDeleteTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private ActionExecutor executor(GraphTabs tabs) {
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        return new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
    }

    private static List<Map<String, Object>> twoNotes() {
        return List.of(Map.of("at", 1_000L, "text", "A"), Map.of("at", 2_000L, "text", "B"));
    }

    private static int noteCount(GraphTabs tabs, String name) {
        return tabs.specs().stream().filter(s -> name.equals(s.name())).findFirst().orElseThrow()
                .notes().size();
    }

    // ---- #47: notes REPLACE ----------------------------------------------------------------------

    @Test
    @DisplayName("Re-sending the same notes leaves the same notes, not twice as many")
    void notesReplaceOnResend() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        assertTrue(ex.render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"), "notes", twoNotes())).ok());
        assertEquals(2, noteCount(tabs, "g"));

        assertTrue(ex.render("graph", Map.of("name", "g", "notes", twoNotes())).ok());

        assertEquals(2, noteCount(tabs, "g"),
                "re-sending a chart definition to adjust one thing is the normal authoring loop; it "
                        + "used to silently double every pin");
    }

    @Test
    @DisplayName("A different set of notes replaces the old set entirely")
    void aNewSetReplacesTheOld() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"), "notes", twoNotes()));

        ex.render("graph", Map.of("name", "g",
                "notes", List.of(Map.of("at", 3_000L, "text", "only this one"))));

        assertEquals(1, noteCount(tabs, "g"));
    }

    @Test
    @DisplayName("A call with no 'notes' key leaves the pins alone")
    void silenceKeepsTheNotes() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"), "notes", twoNotes()));

        ex.render("graph", Map.of("name", "g", "style", "line"));

        assertEquals(2, noteCount(tabs, "g"), "replace applies to a SUPPLIED set, not to silence");
    }

    @Test
    @DisplayName("clearNotes still clears, and still works alongside a new set")
    void clearNotesStillWorks() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"), "notes", twoNotes()));

        ex.render("graph", Map.of("name", "g", "clearNotes", true));
        assertEquals(0, noteCount(tabs, "g"));

        ex.render("graph", Map.of("name", "g", "clearNotes", true,
                "notes", List.of(Map.of("at", 9_000L, "text", "fresh"))));
        assertEquals(1, noteCount(tabs, "g"));
    }

    // ---- #50: a chart can be deleted ------------------------------------------------------------

    @Test
    @DisplayName("A chart can be deleted over the socket, and says what is left")
    void aChartCanBeDeleted() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "keep", "series", List.of("bidMakerOrder.price")));
        ex.render("graph", Map.of("newTab", true, "name", "throwaway", "series", List.of("bidMakerOrder.price")));

        var r = ex.render("graph", Map.of("name", "throwaway", "delete", true));

        assertTrue(r.ok(), () -> "delete failed: " + r);
        assertEquals("throwaway", r.payload().get("deleted"));
        assertFalse(tabs.graphNames().contains("throwaway"));
        assertTrue(tabs.graphNames().contains("keep"), "and it deleted only the one named");
        assertFalse(tabs.hasDefinition("throwaway"), "the DEFINITION goes, not just the tab — "
                + "otherwise the name stays taken and the chart returns on the next project open");
    }

    @Test
    @DisplayName("Deleting a name nothing holds is refused, and names what there is")
    void deletingSomethingThatIsNotThere() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "real", "series", List.of("bidMakerOrder.price")));

        var r = ex.render("graph", Map.of("name", "nope", "delete", true));

        assertFalse(r.ok());
        assertTrue(r.error().contains("real"), () -> "should list what exists: " + r.error());
    }

    /**
     * The destroy-on-replace trap, in its chart form. A delete carries a name and no series, which the
     * build path below would read as "replace with an empty chart" — creating the very thing being
     * deleted. The report verb puts its delete first for exactly this reason.
     */
    @Test
    @DisplayName("A delete does not fall through and re-create the chart it removed")
    void deleteDoesNotFallThroughToTheBuildPath() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "g", "series", List.of("bidMakerOrder.price")));

        ex.render("graph", Map.of("name", "g", "delete", true));

        assertFalse(tabs.hasDefinition("g"), "an empty chart named 'g' must not be sitting here");
    }

    @Test
    @DisplayName("A delete does only the delete")
    void deleteRefusesToDoAnythingElse() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "g", "series", List.of("bidMakerOrder.price")));

        var r = ex.render("graph", Map.of("name", "g", "delete", true, "style", "line"));

        assertFalse(r.ok(), "same rule rename already follows: refuse the whole call, change nothing");
        assertTrue(tabs.hasDefinition("g"), "and nothing was changed");
    }
}
