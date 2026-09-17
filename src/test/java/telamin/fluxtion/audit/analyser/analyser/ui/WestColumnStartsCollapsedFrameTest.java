package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JSplitPane;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * Reported by the owner on 1.14.0: with Event types and Project both toggled off, the app STARTS with the left
 * column expanded to the last chosen width and nothing in it. Toggling them off at runtime collapsed it; starting
 * that way did not — two copies of one rule. The rule is pure ({@link MainFrame#westDividerFor}); the frame test
 * starts a real frame from the reporter's own saved values.
 */
class WestColumnStartsCollapsedFrameTest {

    @Test
    void theRule_aChosenWidthWhileAPanelShows_theRailAloneWhenNoneDoes() {
        assertEquals(517, MainFrame.westDividerFor(true, 517, 30));
        assertEquals(38, MainFrame.westDividerFor(true, 10, 30), "a panel is never squeezed narrower than the rail beside it");
        assertEquals(34, MainFrame.westDividerFor(false, 517, 30), "no panel showing: the chosen width is irrelevant");
    }

    @Test
    void startingWithBothPanelsOff_theColumnIsTheRail_notTheLastChosenWidth(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        writeConfig(tmp, "eventFilterCollapsed=true\nprojectPanelCollapsed=true\nwestWidth=517\n");
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
                JSplitPane west = (JSplitPane) field(f.frame, "westOuter");
                int rail = ((java.awt.Component) field(f.frame, "navRail")).getPreferredSize().width;
                assertTrue(west.getDividerLocation() <= rail + 8,
                        "both panels off at startup: the column is the rail (" + rail + "px), not " + west.getDividerLocation());
            });
        }
    }

    @Test
    void control_startingWithAPanelOn_stillOpensAtTheChosenWidth(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        writeConfig(tmp, "eventFilterCollapsed=false\nprojectPanelCollapsed=true\nwestWidth=517\n");
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
                assertEquals(517, ((JSplitPane) field(f.frame, "westOuter")).getDividerLocation(),
                        "the person's chosen width survives a restart while a panel is showing");
            });
        }
    }

    private static void writeConfig(Path tmp, String body) throws Exception {
        Path dir = Files.createDirectories(tmp.resolve("home").resolve(".fluxtion-analyser"));
        Files.writeString(dir.resolve("config"), body);
    }
}
