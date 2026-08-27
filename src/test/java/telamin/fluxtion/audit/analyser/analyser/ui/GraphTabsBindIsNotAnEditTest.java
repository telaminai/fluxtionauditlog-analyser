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
 * A project's saved graphs were destroyed the first time a log was opened under it (ledger, 2026-08-26 —
 * reported against "open project with no log"; reproduced live against "open a LOG with the project
 * active"). The mechanism: {@code bind()} opened its placeholder tab through {@code addGraph()}, which
 * fired the change listener; since B-M20-3 that listener persists the open tabs, and the store was
 * already assigned, so {@code config.savedGraphs} became ["Graph 1"] a line before {@code restore()}
 * read it. This test plays MainFrame's exact sequence with MainFrame's exact listener shape.
 */
class GraphTabsBindIsNotAnEditTest {

    private static GraphSpec named(String name) {
        return new GraphSpec(name, List.of("priceListener.mid"), List.of(), null, null, "",
                "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void bindingALogDoesNotFireAnEdit_soTheProfilesGraphsSurviveToBeRestored() {
        GraphTabs tabs = new GraphTabs();
        List<GraphSpec> saved = new ArrayList<>(List.of(named("Alpha"), named("Beta"), named("Gamma")));
        int[] edits = {0};
        // MainFrame.onGraphsEdited → saveConfigQuietly → syncOpenGraphsIntoConfig: rewrite the profile's
        // list from whatever tabs are open. With the store bound, that is exactly what clobbered it.
        tabs.setChangeListener(() -> {
            edits[0]++;
            saved.clear();
            saved.addAll(tabs.specs());
        });

        tabs.bind(new HeapLogStore(Samples.sample()), new FilterState());
        assertEquals(0, edits[0], "a fresh binding's placeholder tab is structure, not an edit");
        assertEquals(3, saved.size(), "the profile's graphs are intact for restore to read");

        tabs.restore(saved);
        assertEquals(List.of("Alpha", "Beta", "Gamma"),
                tabs.specs().stream().map(GraphSpec::name).toList(), "and they come back as tabs");
        assertEquals(0, edits[0], "restoring persisted state is not an edit either");
    }

    /**
     * The other half of the fix, and the thing a suppression bug would break silently: the guard stops
     * the placeholder being ECHOED as an edit, it does not stop it being opened. Asked for by the fix's
     * own ledger entry as a reviewer check (2026-08-27) and pinned here so it stays checked.
     */
    @Test
    void aFreshLogWithNoSavedGraphsStillOpensThePlaceholderTab() {
        GraphTabs tabs = new GraphTabs();
        tabs.setChangeListener(() -> { });
        tabs.bind(new HeapLogStore(Samples.sample()), new FilterState());
        assertEquals(List.of("Graph 1"), tabs.specs().stream().map(GraphSpec::name).toList(),
                "the placeholder is structure: suppressed as an EDIT, still opened as a TAB");

        // MainFrame calls restore() unconditionally, including for a profile with nothing saved
        tabs.restore(List.of());
        assertEquals(List.of("Graph 1"), tabs.specs().stream().map(GraphSpec::name).toList(),
                "an empty profile must not clear the tab the user is looking at");
    }

    @Test
    void aRealEditStillPersists_theFixDidNotSilenceTheListener() {
        GraphTabs tabs = new GraphTabs();
        int[] edits = {0};
        tabs.setChangeListener(() -> edits[0]++);
        tabs.bind(new HeapLogStore(Samples.sample()), new FilterState());
        tabs.addGraph("Delta");
        assertTrue(edits[0] >= 1, "a graph the user adds is an edit and must persist as before (B-M20-3)");
    }
}
