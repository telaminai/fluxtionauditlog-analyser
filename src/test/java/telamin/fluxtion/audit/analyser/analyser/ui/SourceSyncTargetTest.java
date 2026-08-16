package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static telamin.fluxtion.audit.analyser.analyser.ui.MainFrame.SourceTarget.EMBEDDED;
import static telamin.fluxtion.audit.analyser.analyser.ui.MainFrame.SourceTarget.SOURCE_TAB;

/**
 * Which source viewer a record selection updates.
 *
 * <p>There are two of them now — the Source tab and the Topology tab's embedded pane — and the rule has
 * to hold three lines at once: sync what the user can see, never switch tabs under them, and leave the
 * other viewer correct for when they do switch. Headless because the decision is a pure function; the
 * Swing wiring around it just supplies the three booleans.
 */
class SourceSyncTargetTest {

    @Test
    void aVisibleViewerWins() {
        assertEquals(EMBEDDED, MainFrame.chooseSourceTarget(true, false, true));
        assertEquals(SOURCE_TAB, MainFrame.chooseSourceTarget(false, true, false));
    }

    @Test
    void theTopologyWithoutItsSourcePaneSyncsTheSourceTabSilently() {
        // nothing visible to update, and switching tabs would yank the user off the graph they are reading
        assertEquals(SOURCE_TAB, MainFrame.chooseSourceTarget(true, false, false));
    }

    @Test
    void aVisibleEmbeddedPaneBeatsTheSourceTabEvenWhenBothCouldApply() {
        // the tabs are siblings so both cannot be in front; this pins the precedence anyway
        assertEquals(EMBEDDED, MainFrame.chooseSourceTarget(true, true, true));
    }

    @Test
    void withNeitherInFrontThePaneTheUserOpenedIsPreferred() {
        assertEquals(EMBEDDED, MainFrame.chooseSourceTarget(false, false, true));
        assertEquals(SOURCE_TAB, MainFrame.chooseSourceTarget(false, false, false));
    }
}
