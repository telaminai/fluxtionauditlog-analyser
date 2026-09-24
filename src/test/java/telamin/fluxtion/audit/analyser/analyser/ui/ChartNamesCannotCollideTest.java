package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.config.SavedGraphMerge;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * f6e8d7e0 — a chart's NAME is its identity, so nothing may hand out one that is already taken.
 *
 * <p>The defect this pins, found by independent review of 38ecc7f3: {@code doRestore} resets the name counter
 * and skips closed charts, so a closed definition reserved nothing. "New graph" — and the placeholder tab
 * created when the last chart is deleted — could therefore be named onto a closed, annotated chart, and
 * the name-keyed merge would replace that chart's definition with the empty new one. Deleting or creating
 * one chart destroyed a different one that no dialog ever named.
 *
 * <p>These drive the real {@link GraphTabs}, with a stand-in for the project's saved list, and assert on
 * the names it actually hands out.
 */
class ChartNamesCannotCollideTest {

    /** A project whose saved charts include closed ones, as MainFrame supplies them. */
    private static Set<String> named(String... names) {
        return new LinkedHashSet<>(List.of(names));
    }

    private static GraphTabs tabsKnowing(Set<String> known) {
        GraphTabs tabs = new GraphTabs();
        tabs.setKnownNames(() -> known);
        return tabs;
    }

    @Test
    void aGeneratedNameSkipsOneAClosedChartStillHolds() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GraphTabs tabs = tabsKnowing(named("Graph 1", "Graph 2"));   // both saved, neither open
            assertEquals("Graph 3", tabs.nextFreeDefaultName(),
                    "Graph 1 and Graph 2 are spoken for by CLOSED definitions — reusing one lets the "
                            + "name-keyed merge overwrite an annotated chart with an empty placeholder");
            assertEquals("Graph 4", tabs.nextFreeDefaultName(), "and it keeps moving past taken names");
        });
    }

    @Test
    void aGeneratedNameIsFreeWhenNothingIsSaved() throws Exception {
        SwingUtilities.invokeAndWait(() ->
                assertEquals("Graph 1", tabsKnowing(named()).nextFreeDefaultName()));
    }

    @Test
    void theTabsSeeClosedDefinitionsNotJustOpenOnes() throws Exception {
        SwingUtilities.invokeAndWait(() -> assertEquals(named("Saved but closed"),
                tabsKnowing(named("Saved but closed")).takenNames(),
                "without this the tabs are blind to every chart that is not currently open"));
    }

    // ---- the merge-level consequence, stated end to end -------------------------------------------

    @Test
    void deletingOneChartDoesNotTakeAClosedOneWithIt() {
        // the reported failure, expressed against the real merge: the project holds an open chart being
        // deleted and a CLOSED annotated chart. Delete drops only the named definition, then a placeholder
        // may appear. With names reserved, the placeholder cannot be "Graph 2".
        List<GraphSpec> saved = new ArrayList<>(List.of(
                spec("Graph 1", true, ""),
                spec("Graph 2", false, "three months of findings")));

        saved.removeIf(g -> g.name().equals("Graph 1"));            // deleteListener, now running FIRST
        List<GraphSpec> merged = SavedGraphMerge.merge(saved, List.of(spec("Graph 3", true, "")));

        assertEquals(2, merged.size());
        assertEquals("Graph 2", merged.get(0).name());
        assertEquals("three months of findings", merged.get(0).explanation(),
                "the chart nobody named must be untouched — this is the regression");
        assertFalse(merged.get(0).open(), "and still closed");
        assertEquals("Graph 3", merged.get(1).name(), "the placeholder took a free name");
    }

    @Test
    void renamingMovesTheDefinitionInsteadOfOrphaningIt() {
        List<GraphSpec> saved = new ArrayList<>(List.of(spec("Old", true, "kept work")));

        // renameListener: move the stored definition with the tab
        for (int i = 0; i < saved.size(); i++) {
            if (saved.get(i).name().equals("Old")) saved.set(i, saved.get(i).withName("New"));
        }
        List<GraphSpec> merged = SavedGraphMerge.merge(saved, List.of(spec("New", true, "kept work")));

        assertEquals(List.of("New"), merged.stream().map(GraphSpec::name).toList(),
                "renaming must not leave 'Old' behind as a closed ghost that can never be reopened");
        assertEquals("kept work", merged.get(0).explanation());
    }

    private static GraphSpec spec(String name, boolean open, String explanation) {
        return new GraphSpec(name, List.of(), List.of(), null, null, null, explanation, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "step", open);
    }

    // ---- restore, against real tabs bound to a real store -----------------------------------------

    private static GraphTabs boundTabs() {
        GraphTabs tabs = new GraphTabs();
        tabs.bind(new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(
                        telamin.fluxtion.audit.analyser.analyser.parse.Samples.sample()),
                new telamin.fluxtion.audit.analyser.analyser.filter.FilterState());
        return tabs;
    }

    @Test
    void restoreReopensOpenChartsAndLeavesClosedOnesClosed() {
        GraphTabs tabs = boundTabs();
        tabs.restore(List.of(spec("Open one", true, ""), spec("Closed one", false, "findings")));

        assertEquals(List.of("Open one"), tabs.graphNames(),
                "a chart closed in an earlier session must NOT spring back as a tab — otherwise closing it "
                        + "was undone by the next reload and the close meant nothing");
    }

    @Test
    void restoringOnlyClosedChartsStillLeavesSomethingToLookAt() {
        GraphTabs tabs = boundTabs();
        tabs.restore(List.of(spec("Closed one", false, "findings")));

        assertEquals(1, tabs.graphNames().size(), "a placeholder appears rather than an empty tab strip");
        assertNotEquals("Closed one", tabs.graphNames().get(0),
                "and it must not be named after the closed chart, or the merge would overwrite it");
    }

    /**
     * The reported destruction, driven through the real delete path with the real merge behind it.
     *
     * <p>Order is everything here. {@code addGraph} ends in a save, so if the fallback placeholder is
     * created before the definition is dropped, the list is persisted while the deleted chart is still in
     * it — and if that placeholder also took a closed chart's name, the merge overwrites an annotated
     * chart nobody named. This drives {@code deleteConfirmed} (the dialog's other half) and then the merge.
     */
    @Test
    void deletingTheLastChartDoesNotTakeAClosedAnnotatedChartWithIt() {
        GraphTabs tabs = boundTabs();
        List<GraphSpec> saved = new ArrayList<>(List.of(
                spec("Graph 1", true, ""),
                spec("Graph 2", false, "three months of findings")));
        tabs.setKnownNames(() -> {
            Set<String> n = new LinkedHashSet<>();
            for (GraphSpec g : saved) n.add(g.name());
            return n;
        });
        tabs.setDeleteListener(name -> saved.removeIf(g -> g.name().equals(name)));
        tabs.restore(List.of(spec("Graph 1", true, "")));   // only the open one becomes a tab

        tabs.deleteConfirmed(0);   // delete the only tab; a placeholder must appear

        assertFalse(saved.stream().anyMatch(g -> g.name().equals("Graph 1")), "the named chart is gone");
        assertNotEquals("Graph 2", tabs.graphNames().get(0),
                "the placeholder must NOT take the closed chart's name — that is the collision that let "
                        + "the merge overwrite an annotated chart the dialog never mentioned");

        List<GraphSpec> merged = SavedGraphMerge.merge(saved, tabs.specs());
        GraphSpec survivor = merged.stream().filter(g -> g.name().equals("Graph 2")).findFirst()
                .orElseThrow(() -> new AssertionError("the closed chart was destroyed by deleting another"));
        assertEquals("three months of findings", survivor.explanation(), "with its annotations intact");
        assertFalse(survivor.open(), "and still closed");
    }

    @Test
    void reopeningASavedChartAsksToBeSaved() {
        GraphTabs tabs = boundTabs();
        GraphSpec closed = spec("Closed one", false, "findings");
        tabs.restore(List.of(spec("Open one", true, ""), closed));

        int[] edits = {0};
        tabs.setChangeListener(() -> edits[0]++);
        assertTrue(tabs.openSaved(closed), "the saved definition opens");

        assertTrue(tabs.graphNames().contains("Closed one"), "it is a tab now");
        assertEquals(1, edits[0],
                "since 38ecc7f3 the open/closed flag is durable, so reopening changes persisted state and must "
                        + "request a save — otherwise it sticks only if some later unrelated edit writes");
    }
}
