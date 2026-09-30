package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The Project panel's row actions: the row is where a source root you did not expect is SEEN, so it is
 * where you can act on it. Headless — asserts the contract the panel asks of the frame, not pixels.
 */
class ProjectRowActionsTest {

    /** Records what the panel asked the frame to do. */
    private static final class Spy implements ProjectRevealer.Surface {
        final List<String> settingsPages = new ArrayList<>();
        final List<String> highlights = new ArrayList<>();
        final List<String> removed = new ArrayList<>();

        @Override public void selectTab(String title) { }
        @Override public void openSettings(String page) {
            settingsPages.add(page);
            highlights.add(null);
        }
        @Override public void openSettings(String page, String highlight) {
            settingsPages.add(page);
            highlights.add(highlight);
        }
        @Override public void removeSourceRoot(String path) { removed.add(path); }
        @Override public void selectReport(String name) { }
        @Override public boolean openSaved(telamin.fluxtion.audit.analyser.analyser.config.GraphSpec spec) { return true; }
        @Override public boolean selectGraph(String name) { return true; }
        @Override public String definitionRefusal() { return null; }
        @Override public void say(String message) { }
    }

    @Test
    @DisplayName("Settings… for a source root arrives AT that root, not merely on the page")
    void settingsHighlightsTheRoot() {
        Spy spy = new Spy();
        new ProjectRevealer(spy, java.util.List::of).openSettings("Source roots", "/w/mine/src/main/java");

        assertEquals(List.of("Source roots"), spy.settingsPages, "theRightPage");
        assertEquals(List.of("/w/mine/src/main/java"), spy.highlights, "andTheRightRowOnIt");
    }

    @Test
    @DisplayName("a row with no path still opens the page")
    void settingsWithoutAPath() {
        Spy spy = new Spy();
        new ProjectRevealer(spy, java.util.List::of).openSettings("Source roots", null);

        assertEquals(List.of("Source roots"), spy.settingsPages);
        assertNull(spy.highlights.getFirst(), "nothingToHighlightIsNotAnError");
    }

    @Test
    @DisplayName("Remove source root reaches the frame with the row's own path")
    void removeReachesTheFrame() {
        Spy spy = new Spy();
        new ProjectRevealer(spy, java.util.List::of).removeSourceRoot("/tmp/scratch/demo-src");

        assertEquals(List.of("/tmp/scratch/demo-src"), spy.removed, "theRowRemovesItsOwnRoot");
    }
}
