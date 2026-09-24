package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 35eeb320 — Open on a Project-panel row reveals THAT row's item.
 *
 * <p>The gap this closes was named in {@code docs/investigations/profile-project-root-resolution.md}:
 * {@code ProjectPanelIsRevealOnlyTest} covers the panel structurally (bytecode, Navigator shape) and the
 * python harnesses drive verbs over the action socket, so nothing clicked a row's button and asserted
 * which navigation resulted. Both reported defects lived precisely in that gap — Open on any report
 * revealed whichever report was already selected, while saved charts had no Open button before the navigation amendment.
 *
 * <p>So these assertions are on the BUTTON, deliberately. A test of the model alone ("does the row carry
 * the right target?") passes while the panel drops the identity on the way to the Navigator, which is the
 * defect that actually happened.
 */
class ProjectPanelOpenRevealsTheRowsItemTest {

    /** What the panel asked for, in order — the whole point is WHICH item was named. */
    private static final class RecordingNavigator implements ProjectPanel.Navigator {
        final List<String> calls = new ArrayList<>();
        @Override public void showTab(String title) { calls.add("showTab:" + title); }
        @Override public void openSettings(String page) { calls.add("openSettings:" + page); }
        @Override public void showReport(String name) { calls.add("showReport:" + name); }
        @Override public void showGraph(String name) { calls.add("showGraph:" + name); }
    }

    /**
     * Two reports whose TITLE differs from their NAME, and two saved charts. The title/name split is not
     * incidental: the row shows the title and {@code ReportsPanel.select} matches the name, so a panel
     * that passed the label along would select nothing at all.
     */
    private static Map<String, Object> context() {
        var ctx = new LinkedHashMap<String, Object>();
        ctx.put("project", Map.of("active", true, "name", "Demo", "root", "/demo"));
        ctx.put("reports", List.of(
                Map.of("name", "startup-race", "title", "Startup race", "sections", 4, "from", "project"),
                Map.of("name", "unsubscribe", "title", "Per-symbol unsubscribe", "sections", 5, "from", "project")));
        ctx.put("savedGraphs", List.of(
                Map.of("name", "Prices", "open", true, "input", "4 series"),
                Map.of("name", "Tick rate", "open", false, "input", "waiting for input")));
        return ctx;
    }

    @Test
    void openOnTheSecondReportRevealsTheSecondReport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecordingNavigator nav = new RecordingNavigator();
            ProjectPanel panel = new ProjectPanel(nav);
            panel.render(ProjectModel.from(context()));

            openIn(panel, "Per-symbol unsubscribe").doClick();
            assertEquals(List.of("showReport:unsubscribe"), nav.calls,
                    "Open must name the row's own report, by NAME not title — revealing the tab alone is what "
                            + "made every Open land on whichever report happened to be selected");

            nav.calls.clear();
            openIn(panel, "Startup race").doClick();
            assertEquals(List.of("showReport:startup-race"), nav.calls,
                    "the other row must reveal the OTHER report — the defect was that both did the same thing");
        });
    }

    @Test
    void openOnASavedChartRevealsThatChart() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecordingNavigator nav = new RecordingNavigator();
            ProjectPanel panel = new ProjectPanel(nav);
            panel.render(ProjectModel.from(context()));

            openIn(panel, "Tick rate").doClick();
            assertEquals(List.of("showGraph:Tick rate"), nav.calls,
                    "a saved chart that is not open must still be openable from its row — these rows carried "
                            + "Target.NONE, so no Open button was rendered");
        });
    }

    @Test
    void aPlaceholderRowNamesNoItemAndStillOnlyRevealsTheTab() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RecordingNavigator nav = new RecordingNavigator();
            ProjectPanel panel = new ProjectPanel(nav);
            var ctx = new LinkedHashMap<String, Object>();
            ctx.put("project", Map.of("active", true, "name", "Demo", "root", "/demo"));
            panel.render(ProjectModel.from(ctx));

            openIn(panel, "No saved reports").doClick();
            assertEquals(List.of("showTab:Reports"), nav.calls,
                    "there is no report to reveal, so the row must not pretend to name one");
        });
    }

    @Test
    void theModelCarriesIdentitySeparatelyFromTheLabel() {
        List<ProjectModel.Row> reports = rowsOf(ProjectModel.from(context()), ProjectModel.REPORTS);
        ProjectModel.Row row = reports.stream().filter(r -> "unsubscribe".equals(r.item())).findFirst()
                .orElseThrow(() -> new AssertionError("no row carried the report's name as its item"));
        assertEquals("Per-symbol unsubscribe", row.primary(), "the row still READS as its title");
        assertNotEquals(row.primary(), row.item(),
                "identity and label are different strings here on purpose — collapsing them is the bug");

        for (ProjectModel.Row r : rowsOf(ProjectModel.from(context()), ProjectModel.SAVED_GRAPHS)) {
            assertEquals(ProjectModel.Target.CHART, r.target(),
                    "a saved chart row must offer an action; Target.NONE previously provided no Open action");
            assertNotNull(r.item(), "and it must say which chart");
        }
    }

    private static List<ProjectModel.Row> rowsOf(ProjectModel model, String section) {
        for (ProjectModel.Section s : model.sections()) if (s.title().equals(section)) return s.rows();
        throw new AssertionError("no section " + section);
    }

    /**
     * The "Open" button belonging to the row that displays {@code label} — scoped to that row, because the
     * panel renders one Open per report and per chart and picking the first would assert nothing.
     */
    private static JButton openIn(Container root, String label) {
        JLabel found = labelled(root, label);
        assertNotNull(found, "no row displays " + label);
        for (Container row = found.getParent(); row != null; row = row.getParent()) {
            JButton open = button(row, "Open");
            if (open != null) return open;
            if (row == root) break;
        }
        throw new AssertionError("the row for " + label + " has no Open button");
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
}
