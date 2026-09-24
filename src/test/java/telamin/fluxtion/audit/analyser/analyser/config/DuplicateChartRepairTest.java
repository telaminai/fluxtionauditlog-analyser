package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.config.DuplicateChartRepair.Action.DELETE;
import static telamin.fluxtion.audit.analyser.analyser.config.DuplicateChartRepair.Action.RENAME;

/**
 * The repair policy for duplicate chart names (owner decision, 2026-09-24).
 *
 * <p>These assertions are on the pure function rather than the dialog, because the dialog is the part a
 * test cannot reach and the policy is the part that can destroy a chart. The previous attempt to protect a
 * rule of this kind lived inside a Swing surface and was reverted to its broken form with the whole suite
 * still green.
 */
class DuplicateChartRepairTest {

    private static GraphSpec chart(String name, String explanation, boolean open) {
        return new GraphSpec(name, List.of("a" + (char) 1 + "x"), List.of(), null, null, null, explanation,
                List.of(new GraphSpec.NoteSpec(1L, "a finding", null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "step", open);
    }

    private static Map<Integer, DuplicateChartRepair.Choice> choices(Object... pairs) {
        Map<Integer, DuplicateChartRepair.Choice> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Integer) pairs[i], (DuplicateChartRepair.Choice) pairs[i + 1]);
        }
        return m;
    }

    private static DuplicateChartRepair.Choice rename(String to) {
        return new DuplicateChartRepair.Choice(RENAME, to);
    }

    private static final DuplicateChartRepair.Choice DELETE_IT = new DuplicateChartRepair.Choice(DELETE, null);

    // ---- finding ------------------------------------------------------------------------------------

    @Test
    void findsOnlyContestedNames() {
        List<GraphSpec> saved = List.of(
                chart("Unique", "", true), chart("Same", "first", true),
                chart("Other", "", false), chart("Same", "second", false));

        List<DuplicateChartRepair.Duplicate> found = DuplicateChartRepair.find(saved);
        assertEquals(1, found.size());
        assertEquals("Same", found.get(0).name());
        assertEquals(List.of(1, 3), found.get(0).indices(),
                "indices identify WHICH definition, because two charts with one name cannot be told apart by it");
        assertTrue(DuplicateChartRepair.find(List.of(chart("A", "", true))).isEmpty());
        assertTrue(DuplicateChartRepair.find(List.of()).isEmpty());
        assertTrue(DuplicateChartRepair.find(null).isEmpty());
    }

    @Test
    void describeSaysWhatWouldBeLost() {
        String d = DuplicateChartRepair.describe(chart("Same", "why it exists", false));
        assertTrue(d.contains("1 note"), d);
        assertTrue(d.contains("an explanation"), d);
        assertTrue(d.contains("closed"), d);
        assertTrue(DuplicateChartRepair.describe(chart("Same", "", true)).contains("open"));
        assertEquals("(missing)", DuplicateChartRepair.describe(null));
    }

    // ---- applying -----------------------------------------------------------------------------------

    @Test
    void renamingOneAndKeepingTheOtherResolvesIt() {
        List<GraphSpec> saved = List.of(chart("Same", "first", true), chart("Same", "second", false));
        List<GraphSpec> out = DuplicateChartRepair.apply(saved,
                choices(0, rename("First"), 1, rename("Second")));

        assertEquals(List.of("First", "Second"), out.stream().map(GraphSpec::name).toList());
        assertEquals("first", out.get(0).explanation(), "renaming keeps everything else about the chart");
        assertEquals("second", out.get(1).explanation());
        assertFalse(out.get(1).open(), "including whether it was open");
        assertEquals(1, out.get(0).notes().size());
    }

    @Test
    void deletingOneResolvesItAndTheSurvivorKeepsTheName() {
        List<GraphSpec> saved = List.of(chart("Same", "keep", true), chart("Same", "drop", false));
        List<GraphSpec> out = DuplicateChartRepair.apply(saved, choices(0, rename("Same"), 1, DELETE_IT));

        assertEquals(List.of("Same"), out.stream().map(GraphSpec::name).toList());
        assertEquals("keep", out.get(0).explanation());
    }

    @Test
    void untouchedChartsAreCarriedThroughUnchanged() {
        GraphSpec bystander = chart("Bystander", "not involved", false);
        List<GraphSpec> out = DuplicateChartRepair.apply(
                List.of(bystander, chart("Same", "a", true), chart("Same", "b", true)),
                choices(1, rename("A"), 2, DELETE_IT));

        assertSame(bystander, out.get(0), "a chart nobody contested must be the same object, not a copy");
        assertEquals(List.of("Bystander", "A"), out.stream().map(GraphSpec::name).toList());
    }

    // ---- refusals: nothing is chosen for the person -------------------------------------------------

    @Test
    void aPartialRepairIsRefused() {
        List<GraphSpec> saved = List.of(chart("Same", "a", true), chart("Same", "b", true));
        var e = assertThrows(IllegalArgumentException.class,
                () -> DuplicateChartRepair.apply(saved, choices(0, rename("A"))));
        assertTrue(e.getMessage().contains("[1]"), e.getMessage());
        assertTrue(e.getMessage().contains("partial repair"), e.getMessage());
    }

    @Test
    void noChoicesAtAllIsRefused() {
        List<GraphSpec> saved = List.of(chart("Same", "a", true), chart("Same", "b", true));
        assertThrows(IllegalArgumentException.class, () -> DuplicateChartRepair.apply(saved, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> DuplicateChartRepair.apply(saved, null));
    }

    @Test
    void repairingSomethingUnambiguousIsRefused() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> DuplicateChartRepair.apply(List.of(chart("A", "", true)), choices(0, DELETE_IT)));
        assertTrue(e.getMessage().contains("nothing is ambiguous"), e.getMessage());
    }

    @Test
    void aChoiceForAnUncontestedChartIsRefused() {
        List<GraphSpec> saved = List.of(chart("Free", "", true), chart("Same", "a", true), chart("Same", "b", true));
        var e = assertThrows(IllegalArgumentException.class, () -> DuplicateChartRepair.apply(saved,
                choices(0, DELETE_IT, 1, rename("A"), 2, rename("B"))));
        assertTrue(e.getMessage().contains("not ambiguous"), e.getMessage());
    }

    @Test
    void aBlankRenameIsRefused() {
        List<GraphSpec> saved = List.of(chart("Same", "a", true), chart("Same", "b", true));
        for (String blank : new String[]{"", "   ", null}) {
            assertThrows(IllegalArgumentException.class, () -> DuplicateChartRepair.apply(saved,
                    choices(0, rename(blank), 1, DELETE_IT)));
        }
    }

    @Test
    void aRepairThatWouldStillCollideIsRefused() {
        List<GraphSpec> saved = List.of(chart("Same", "a", true), chart("Same", "b", true));
        var e = assertThrows(IllegalArgumentException.class,
                () -> DuplicateChartRepair.apply(saved, choices(0, rename("X"), 1, rename("X"))));
        assertTrue(e.getMessage().contains("two charts called 'X'"), e.getMessage());

        List<GraphSpec> withBystander = List.of(chart("Taken", "", true),
                chart("Same", "a", true), chart("Same", "b", true));
        assertThrows(IllegalArgumentException.class, () -> DuplicateChartRepair.apply(withBystander,
                choices(1, rename("Taken"), 2, DELETE_IT)),
                "a rename onto an UNCONTESTED name must be refused too");
    }

    @Test
    void deletingEveryCopyIsAllowedButLosesTheChart() {
        List<GraphSpec> out = DuplicateChartRepair.apply(
                List.of(chart("Same", "a", true), chart("Same", "b", true)),
                choices(0, DELETE_IT, 1, DELETE_IT));
        assertTrue(out.isEmpty(), "the person may decide neither copy is wanted; it is their data");
    }

    @Test
    void theInputIsNeverModified() {
        List<GraphSpec> saved = new java.util.ArrayList<>(
                List.of(chart("Same", "a", true), chart("Same", "b", true)));
        List<GraphSpec> before = List.copyOf(saved);
        DuplicateChartRepair.apply(saved, choices(0, rename("A"), 1, DELETE_IT));
        assertEquals(before, saved, "a repair that is refused halfway must leave the profile untouched");
    }
}
