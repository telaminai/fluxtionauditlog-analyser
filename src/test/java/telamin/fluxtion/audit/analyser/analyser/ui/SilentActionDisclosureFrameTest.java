package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * The silent dead end, closed: with NO log loaded, Open on a saved chart cannot plot anything — and must
 * say so in the frame rather than reveal an empty Graph tab and stop.
 *
 * <p>This is the defect the whole round of work began with, in its last remaining form. It has its own
 * class because {@code ChartLifecycleReviewFrameTest.Fixture} always opens a log, and the absence of one
 * is precisely the condition under test.
 *
 * <p>Asserted on the real status bar, because a disclosure only counts if it reaches a person.
 */
class SilentActionDisclosureFrameTest {

    private static GraphSpec chart(String name, boolean open) {
        return new GraphSpec(name, List.of(), List.of(), null, null, null, "why it exists", List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "step", open);
    }

    private static JLabel labelled(Container parent, String text) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JLabel l && text.equals(l.getText())) return l;
            if (c instanceof Container child) {
                JLabel l = labelled(child, text);
                if (l != null) return l;
            }
        }
        return null;
    }

    private static JButton button(Container parent, String label) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && label.equals(b.getText())) return b;
            if (c instanceof Container child) {
                JButton b = button(child, label);
                if (b != null) return b;
            }
        }
        return null;
    }

    /** The real Open button on the row that displays {@code label}. */
    private static JButton openButtonFor(Container root, String label) {
        JLabel found = labelled(root, label);
        assertNotNull(found, "no Project-panel row displays " + label);
        for (Container row = found.getParent(); row != null && row != root; row = row.getParent()) {
            JButton open = button(row, "Open");
            if (open != null) return open;
        }
        throw new AssertionError("the row for " + label + " has no Open button");
    }

    @Test
    void openingASavedChartWithNoLogTellsThePersonWhy(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        // ConfigStore reads ~/.fluxtion-analyser/config — a DIRECTORY holding a file, not a file
        // (config/ConfigStore.java:21). Seeding the directory path as a file, as an earlier version of
        // this test did, silently loads nothing and leaves a file where the app expects a directory.
        // Caught by review finding R3 against ProjectPanelChartLifecycleFrameTest; the same line was here.
        Files.writeString(
                Files.createDirectories(tmp.resolve("home").resolve(".fluxtion-analyser")).resolve("config"),
                "projectPanelCollapsed=false\n");

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1400, 900);
                f.frame.setVisible(true);
                f.frame.validate();

                AppConfig config = (AppConfig) field(f.frame, "config");
                config.savedGraphs.clear();
                config.savedGraphs.add(chart("Tick rate", false));   // saved, and no log is open

                var ctx = new java.util.LinkedHashMap<String, Object>();
                ctx.put("project", java.util.Map.of("active", true, "name", "Demo", "root", tmp.toString()));
                ctx.put("savedGraphs", List.of(
                        java.util.Map.of("name", "Tick rate", "open", false, "input", "waiting for input")));
                ProjectPanel panel = (ProjectPanel) field(f.frame, "projectPanel");
                panel.render(ProjectModel.from(ctx));

                JLabel status = (JLabel) field(f.frame, "status");
                String before = status.getText();

                openButtonFor(panel, "Tick rate").doClick();

                assertNotEquals(before, status.getText(),
                        "with no log the chart cannot open, and saying nothing is the silent Open this work "
                                + "exists to remove");
                assertTrue(status.getText().contains("Tick rate"),
                        "the message names the chart the person clicked: " + status.getText());
                assertTrue(status.getText().toLowerCase().contains("log"),
                        "and gives the actionable reason: " + status.getText());
            });
        }
    }
}
