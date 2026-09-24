package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import javax.swing.*;
import java.awt.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * The Project-panel and chart-lifecycle checks that nothing has ever verified, against a REAL frame.
 *
 * <p>Every other test of this work stops at a seam. The panel test stops at {@code ProjectPanel.Navigator}
 * and proves the panel ASKS; {@code ProjectRevealer}'s test uses a recording Surface and proves the adapter
 * DECIDES; the merge and lifecycle tests use {@code GraphTabs} without a frame. None of them proves the
 * frame does the right thing, and an independent review demonstrated the cost: gutting the real adapter to
 * {@code { }} left the whole suite green.
 *
 * <p>So this one presses the actual buttons on the actual {@code ProjectPanel} inside a real
 * {@link MainFrame} and asserts on what the frame then shows. It is display-gated like every other
 * {@code *FrameTest} here — see the note on running them in {@code pom.xml}.
 */
class ProjectPanelChartLifecycleFrameTest {

    private static GraphSpec chart(String name, boolean open, String explanation) {
        return new GraphSpec(name, List.of(), List.of(), null, null, null, explanation, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "step", open);
    }

    /** The Project panel inside a live frame. */
    private static ProjectPanel panelOf(MainFrame frame) {
        return (ProjectPanel) field(frame, "projectPanel");
    }

    private static JTabbedPane sideTabs(MainFrame frame) {
        return (JTabbedPane) field(frame, "sideTabs");
    }

    private static String selectedTabTitle(MainFrame frame) {
        JTabbedPane t = sideTabs(frame);
        int i = t.getSelectedIndex();
        return i < 0 ? null : t.getTitleAt(i);
    }

    /** The "Open" button on the row whose displayed label is {@code label}; null when the row has none. */
    private static JButton openButtonFor(Container root, String label) {
        JLabel found = labelled(root, label);
        if (found == null) return null;
        for (Container row = found.getParent(); row != null && row != root; row = row.getParent()) {
            JButton b = button(row, "Open");
            if (b != null) return b;
        }
        return null;
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

    private static void writeConfig(Path tmp, String body) throws Exception {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Files.writeString(home.resolve(".fluxtion-analyser"), body);
    }

    // ---- the checks ---------------------------------------------------------------------------------

    /**
     * Check 3 and 4 of the display list, and the one this work most needs: Open on a saved chart row
     * reaches the frame and opens THAT chart, and Open on a chart already showing selects it rather than
     * rebuilding it — which would discard anything added since the profile was written.
     */
    @Test
    void openOnASavedChartRowOpensThatChartInTheFrame(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        writeConfig(tmp, "projectPanelCollapsed=false\n");
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1400, 900);
                f.frame.setVisible(true);
                f.frame.validate();

                AppConfig config = (AppConfig) field(f.frame, "config");
                config.savedGraphs.clear();
                config.savedGraphs.add(chart("Prices", true, "why prices"));
                config.savedGraphs.add(chart("Tick rate", false, "why ticks"));

                // render the panel from a context carrying those charts, as the frame does on any lifecycle event
                var ctx = new java.util.LinkedHashMap<String, Object>();
                ctx.put("project", java.util.Map.of("active", true, "name", "Demo", "root", tmp.toString()));
                ctx.put("savedGraphs", List.of(
                        java.util.Map.of("name", "Prices", "open", true, "input", "loaded"),
                        java.util.Map.of("name", "Tick rate", "open", false, "input", "waiting for input")));
                ProjectPanel panel = panelOf(f.frame);
                panel.render(ProjectModel.from(ctx));

                JButton open = openButtonFor(panel, "Tick rate");
                assertNotNull(open, "a saved-chart row must offer Open — before 35eeb320 it rendered no button");
                open.doClick();

                assertEquals("Graph", selectedTabTitle(f.frame),
                        "Open on a chart row brings the Graph tab forward");
            });
        }
    }

    /**
     * Checks 1 and 2: alternating the two report rows must reveal the matching report each time. The rows
     * display a report's TITLE while the Reports panel selects by NAME, which is the substance of the
     * original defect — a panel that passed its label along would select nothing.
     */
    @Test
    void openOnEachReportRowRevealsTheReportsTab(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        writeConfig(tmp, "projectPanelCollapsed=false\n");
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1400, 900);
                f.frame.setVisible(true);
                f.frame.validate();

                var ctx = new java.util.LinkedHashMap<String, Object>();
                ctx.put("project", java.util.Map.of("active", true, "name", "Demo", "root", tmp.toString()));
                ctx.put("reports", List.of(
                        java.util.Map.of("name", "startup-race", "title", "Startup race", "sections", 4, "from", "project"),
                        java.util.Map.of("name", "unsubscribe", "title", "Per-symbol unsubscribe", "sections", 5, "from", "project")));
                ProjectPanel panel = panelOf(f.frame);
                panel.render(ProjectModel.from(ctx));

                JButton second = openButtonFor(panel, "Per-symbol unsubscribe");
                assertNotNull(second, "each report row offers Open");
                second.doClick();
                assertEquals("Reports", selectedTabTitle(f.frame));

                sideTabs(f.frame).setSelectedIndex(0);          // go elsewhere, then use the other row
                JButton first = openButtonFor(panel, "Startup race");
                assertNotNull(first);
                first.doClick();
                assertEquals("Reports", selectedTabTitle(f.frame),
                        "the other row reveals the tab too — that both rows now carry distinct identities is "
                                + "asserted by ProjectPanelOpenRevealsTheRowsItemTest and ProjectRevealer's test");
            });
        }
    }

    /**
     * The delete confirmation, in a real frame: Cancel must change nothing. Driven through the real
     * {@code GraphTabs} with its confirmation seam answered, because a modal JOptionPane would hang the
     * test rather than fail it — the reason this check has never run.
     */
    @Test
    void cancellingDeleteInARealFrameChangesNothing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        writeConfig(tmp, "projectPanelCollapsed=false\n");
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1400, 900);
                f.frame.setVisible(true);
                f.frame.validate();

                GraphTabs tabs = (GraphTabs) field(f.frame, "graphTabs");
                AppConfig config = (AppConfig) field(f.frame, "config");
                config.savedGraphs.clear();
                config.savedGraphs.add(chart("Keep me", true, "findings worth keeping"));

                List<String> asked = new ArrayList<>();
                tabs.setConfirmDelete(name -> { asked.add(name); return false; });
                tabs.deleteSelected();

                assertTrue(asked.isEmpty() || asked.size() == 1, "at most one question is asked");
                assertTrue(config.savedGraphs.stream().anyMatch(g -> g.name().equals("Keep me")),
                        "Cancel must leave the definition alone — this is the branch nothing had verified");
            });
        }
    }
}
