package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
  * the two paths an independent review left flagged as unverifiable.
 *
 * <p><b>Cancel.</b> Delete is the only action in the app that destroys user data on purpose, and its
 * "changed nothing" branch was guarded by a modal {@link javax.swing.JOptionPane} that cannot run
 * headless. Making the question a {@code Predicate} lets a test answer it both ways, so the branch that
 * must do nothing is finally held to that.
 *
 * <p><b>The adapter.</b> Gutting the frame's {@code showReport}/{@code showGraph} to {@code { }} left the
 * suite green, because the panel-side test stops at the {@link ProjectPanel.Navigator} boundary and proves
 * only that the panel ASKS. {@link ProjectRevealer} is the doing, and these assert on it.
 */
class DeleteConfirmationAndRevealerTest {

    private static GraphSpec spec(String name) {
        return new GraphSpec(name, List.of(), List.of(), null, null, null, "why it exists", List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "step", true);
    }

    private static GraphTabs boundTabs() {
        GraphTabs tabs = new GraphTabs();
        tabs.bind(new HeapLogStore(Samples.sample()), new FilterState());
        return tabs;
    }

    // ---- Cancel ------------------------------------------------------------------------------------

    @Test
    void cancellingTheDeleteChangesNothingAtAll() {
        GraphTabs tabs = boundTabs();
        List<GraphSpec> saved = new ArrayList<>(List.of(spec("Keep me")));
        tabs.setDeleteListener(name -> saved.removeIf(g -> g.name().equals(name)));
        tabs.restore(List.of(spec("Keep me")));

        int[] edits = {0};
        tabs.setChangeListener(() -> edits[0]++);
        List<String> asked = new ArrayList<>();
        tabs.setConfirmDelete(name -> { asked.add(name); return false; });   // the person clicks Cancel

        tabs.deleteSelected();

        assertEquals(List.of("Keep me"), asked, "the question names the chart it is about");
        assertEquals(List.of("Keep me"), tabs.graphNames(), "the tab is still there");
        assertEquals(1, saved.size(), "and so is the definition — Cancel must not remove it");
        assertEquals(0, edits[0], "Cancel is not an edit, so nothing should even ask to be saved");
    }

    @Test
    void confirmingTheDeleteRemovesThatChartAndOnlyThatChart() {
        GraphTabs tabs = boundTabs();
        List<GraphSpec> saved = new ArrayList<>(List.of(spec("Doomed"), spec("Bystander")));
        tabs.setDeleteListener(name -> saved.removeIf(g -> g.name().equals(name)));
        tabs.restore(List.of(spec("Doomed"), spec("Bystander")));
        tabs.selectGraph("Doomed");

        tabs.setConfirmDelete(name -> true);   // the person clicks OK
        tabs.deleteSelected();

        assertFalse(tabs.graphNames().contains("Doomed"), "the named chart goes");
        assertTrue(saved.stream().anyMatch(g -> g.name().equals("Bystander")),
                "and nothing else does — the review found deleting one chart taking another with it");
        assertEquals(1, saved.size());
    }

    // ---- the adapter -------------------------------------------------------------------------------

    /** Records what the frame was asked to do, in order. */
    private static final class RecordingSurface implements ProjectRevealer.Surface {
        final List<String> calls = new ArrayList<>();
        boolean openSavedSucceeds = true;
        @Override public void selectTab(String title) { calls.add("tab:" + title); }
        @Override public void openSettings(String page) { calls.add("settings:" + page); }
        @Override public void selectReport(String name) { calls.add("report:" + name); }
        @Override public boolean openSaved(GraphSpec spec) { calls.add("openSaved:" + spec.name()); return openSavedSucceeds; }
        @Override public void selectGraph(String name) { calls.add("selectGraph:" + name); }
    }

    @Test
    void showReportRevealsTheReportsTabAndThenThatReport() {
        RecordingSurface surface = new RecordingSurface();
        new ProjectRevealer(surface, List::of).showReport("unsubscribe");

        assertEquals(List.of("tab:Reports", "report:unsubscribe"), surface.calls,
                "the tab comes forward and the named report is selected — in that order, or the person "
                        + "watches the previous report flash past");
    }

    @Test
    void showGraphOpensTheSavedDefinitionRatherThanOnlySelectingATab() {
        RecordingSurface surface = new RecordingSurface();
        new ProjectRevealer(surface, () -> List.of(spec("Prices"), spec("Ticks"))).showGraph("Ticks");

        assertEquals(List.of("tab:Graph", "openSaved:Ticks"), surface.calls,
                "a saved chart is opened from its definition, which also selects it when already open — "
                        + "selectGraph alone is a no-op for a chart that is not currently a tab");
    }

    @Test
    void showGraphFallsBackToSelectingAnUnsavedTab() {
        RecordingSurface surface = new RecordingSurface();
        new ProjectRevealer(surface, () -> List.of(spec("Prices"))).showGraph("Scratch");

        assertEquals(List.of("tab:Graph", "selectGraph:Scratch"), surface.calls,
                "a tab with no saved definition is all there is to reveal");
    }

    @Test
    void aMissingNameRevealsTheTabAndAsksForNothingElse() {
        RecordingSurface reports = new RecordingSurface();
        new ProjectRevealer(reports, List::of).showReport(null);
        assertEquals(List.of("tab:Reports"), reports.calls, "a placeholder row names no report");

        RecordingSurface graphs = new RecordingSurface();
        new ProjectRevealer(graphs, List::of).showGraph(null);
        assertEquals(List.of("tab:Graph"), graphs.calls);
    }

    @Test
    void theAdapterNeverCreatesEditsOrDiscardsAnything() {
        RecordingSurface surface = new RecordingSurface();
        ProjectRevealer revealer = new ProjectRevealer(surface, () -> List.of(spec("Prices")));
        revealer.showTab("Topology");
        revealer.openSettings("Source roots");
        revealer.showReport("r");
        revealer.showGraph("Prices");

        assertTrue(surface.calls.stream().noneMatch(c -> c.startsWith("delete") || c.startsWith("rename")),
                "D-L3 as amended: every call reveals something that already exists");
        assertEquals(List.of("tab:Topology", "settings:Source roots", "tab:Reports", "report:r",
                "tab:Graph", "openSaved:Prices"), surface.calls);
    }

    @Test
    void aNullSavedListIsNotACrash() {
        RecordingSurface surface = new RecordingSurface();
        new ProjectRevealer(surface, () -> null).showGraph("anything");
        assertEquals(List.of("tab:Graph", "selectGraph:anything"), surface.calls);
    }
}
