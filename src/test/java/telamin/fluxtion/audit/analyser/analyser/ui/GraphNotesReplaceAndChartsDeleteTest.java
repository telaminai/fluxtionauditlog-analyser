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

    // ---- #50, review: close is the safe half of removal -----------------------------------------

    /**
     * Close and delete, side by side against the SAME definition store — because the difference
     * between them is the only reason close exists, and it is invisible unless both are shown.
     * {@code deleteListener} is the profile's one removal channel, so a definition surviving it is
     * the whole claim.
     */
    @org.junit.jupiter.api.Test
    @DisplayName("close keeps the definition; delete removes it")
    void closeKeepsWhatDeleteRemoves() {
        GraphTabs tabs = new GraphTabs();
        java.util.List<String> definitions = new java.util.ArrayList<>(List.of("putAway", "discard"));
        tabs.setDeleteListener(definitions::remove);
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "putAway", "series", List.of("bidMakerOrder.price")));
        ex.render("graph", Map.of("newTab", true, "name", "discard", "series", List.of("bidMakerOrder.price")));

        var closed = ex.render("graph", Map.of("name", "putAway", "close", true));

        assertTrue(closed.ok(), () -> "close failed: " + closed);
        assertEquals("putAway", closed.payload().get("closed"));
        assertFalse(tabs.graphNames().contains("putAway"), "off the screen");
        assertEquals(List.of("putAway", "discard"), definitions,
                "close must NOT reach the profile's removal channel — that is the whole difference from "
                        + "delete, and what makes the delete reply's advice actionable over the socket");

        ex.render("graph", Map.of("name", "discard", "delete", true));

        assertEquals(List.of("putAway"), definitions, "delete does reach it");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Closing the last chart is refused rather than leaving a blank in its place")
    void theLastChartCannotBeClosed() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "only", "series", List.of("bidMakerOrder.price")));
        while (tabs.graphNames().size() > 1) {
            ex.render("graph", Map.of("name", tabs.graphNames().get(0), "delete", true));
        }

        var r = ex.render("graph", Map.of("name", "only", "close", true));

        assertFalse(r.ok(), "the strip keeps one tab; closing the last would just swap it for a blank");
        assertTrue(tabs.hasDefinition("only"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A close does only the close")
    void closeRefusesToDoAnythingElse() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "a", "series", List.of("bidMakerOrder.price")));
        ex.render("graph", Map.of("newTab", true, "name", "b", "series", List.of("bidMakerOrder.price")));

        var r = ex.render("graph", Map.of("name", "b", "close", true, "style", "line"));

        assertFalse(r.ok(), "same rule as rename and delete: refuse the whole call, change nothing");
        assertTrue(tabs.graphNames().contains("b"));
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

    // ---- PR #51 review: the last chart, and a closed one ---------------------------------------------

    private static List<String> saved(GraphTabs tabs) {
        return tabs.specs().stream().map(s -> s.name()).toList();
    }

    /**
     * The decided behaviour for deleting the LAST chart: it is gone, and nothing takes its place in the profile.
     * The tab strip keeps one tab, so a blank one opens — but that tab is not a chart until something is put on
     * it: it is not saved, and a delete does not report it as remaining. Before, each "last" delete answered
     * with a fresh "Graph N" and saved it, so an assistant clearing up could never reach zero.
     */
    @Test
    @DisplayName("Deleting the last chart leaves none behind — in the reply or the profile")
    void deletingTheLastChartLeavesNoneBehind() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "probe", "series", List.of("bidMakerOrder.price")));
        for (String name : List.copyOf(tabs.graphNames())) {
            if (!name.equals("probe")) ex.render("graph", Map.of("name", name, "delete", true));
        }
        assertEquals(List.of("probe"), tabs.graphNames(), "precondition: the probe is the only chart");

        var r = ex.render("graph", Map.of("name", "probe", "delete", true));

        assertTrue(r.ok(), () -> "delete failed: " + r);
        assertEquals(List.of(), r.payload().get("remaining"), "no chart remains — the blank tab is not one");
        assertEquals(List.of(), saved(tabs), "and nothing is saved in its place");
        assertEquals(1, tabs.graphNames().size(), "the strip still keeps one (blank) tab to work in");
    }

    @Test
    @DisplayName("…and that blank tab becomes a chart the moment something is put on it")
    void aUsedPlaceholderIsSaved() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "only", "series", List.of("bidMakerOrder.price")));
        for (String name : List.copyOf(tabs.graphNames())) ex.render("graph", Map.of("name", name, "delete", true));
        String blank = tabs.graphNames().get(0);
        assertEquals(List.of(), saved(tabs), "precondition: an unused blank tab is not saved");

        ex.render("graph", Map.of("name", blank, "series", List.of("bidMakerOrder.price")));

        assertEquals(List.of(blank), saved(tabs), "given a series, it is a chart like any other");
    }

    /**
     * A CLOSED chart is only a definition. The PR's own javadoc says deleting one must work, and nothing tested
     * it: making the closed-chart branch return true without removing the definition left every test green.
     */
    @Test
    @DisplayName("A closed chart can be deleted — its definition goes")
    void aClosedChartCanBeDeleted() {
        GraphTabs tabs = new GraphTabs();
        java.util.List<String> definitions = new java.util.ArrayList<>(List.of("closed-one"));
        tabs.setKnownNames(() -> new java.util.LinkedHashSet<>(definitions));
        tabs.setDeleteListener(definitions::remove);
        ActionExecutor ex = executor(tabs);
        assertFalse(tabs.graphNames().contains("closed-one"), "precondition: it is not open");
        assertTrue(tabs.hasDefinition("closed-one"), "precondition: but it is defined");

        var r = ex.render("graph", Map.of("name", "closed-one", "delete", true));

        assertTrue(r.ok(), () -> "delete failed: " + r);
        assertFalse(definitions.contains("closed-one"), "the stored definition was removed, not only reported");
        assertFalse(tabs.hasDefinition("closed-one"), "and the name is free again");
    }

    /**
     * PR #51 review. Under REPLACE a skipped note is not merely "not added" — the old pins are already gone. A re-send
     * whose anchors do not resolve (a recordIndex past a shorter log) left the chart with none, and said nothing.
     */
    @Test
    @DisplayName("A re-send that drops notes says so, and says what the chart now carries")
    void droppedNotesAreNamed() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        ex.render("graph", Map.of("newTab", true, "name", "g", "series", List.of("bidMakerOrder.price"), "notes", twoNotes()));
        assertEquals(2, noteCount(tabs, "g"), "precondition");

        var r = ex.render("graph", Map.of("name", "g",
                "notes", List.of(Map.of("recordIndex", 99_999, "text", "A"), Map.of("text", "B"))));

        assertTrue(r.ok());
        assertEquals(0, noteCount(tabs, "g"), "the set was replaced — that is the rule, and it is not the defect");
        assertEquals(0, r.payload().get("notes"), "the reply states what the chart carries now");
        String warnings = String.valueOf(r.payload().get("warnings"));
        assertTrue(warnings.contains("2 note(s) skipped") && warnings.contains("recordIndex")
                        && warnings.contains("the 2 the chart had were replaced by 0"),
                () -> "the loss must be stated, not silent: " + warnings);
    }
}
