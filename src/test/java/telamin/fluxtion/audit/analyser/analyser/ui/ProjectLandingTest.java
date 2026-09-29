package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLandingTest {
    @Test void fullStartPageRoutesDistinctChoicesAndRecentProjectToItsWorkspace() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> calls = new ArrayList<>();
            var panel = new StartPanel(new StartPanel.Actions() {
                public void openDemo(Path p, boolean graph) { calls.add("sample:" + graph); }
                public void showTab(String n) { }
                public void openOwnLog() { calls.add("log"); }
                public void openSettings() { }
                public void openMcpSetup(McpSetupDialog.Target t) { }
                public void openFluxtionKey() { }
                public boolean fluxtionKeyPresent() { return false; }
                public void backToRecords() { }
                public void newProject() { calls.add("new"); }
                public void newProjectFromTemplate() { calls.add("template"); }
                public void openExperiment() { calls.add("bundle"); }
                public void investigateIncident() { calls.add("incident"); }
                public void openGraphml() { calls.add("graphml"); }
                public void openRecentProject(String path) { calls.add("recent:" + path); }
                public void openExistingProject() { calls.add("open-project"); }
                public void openGuidedTour() { calls.add("tour"); }
            }, null);
            String recent = "/tmp/DEMO/.analyser/project.fluxtion-settings";
            panel.setRecentProjects(List.of(recent));
            assertTrue(text(panel).contains("global source roots are defaults; an active project can override them"));
            for (String name : List.of("Take a guided tour", "Load an experiment", "Investigate an incident", "Author a new project",
                    "Author from template", "Open project", "Open audit log", "Open GraphML", "Open sample project")) {
                assertNotNull(action(panel, name), "start page action: " + name);
                action(panel, name).doClick();
            }
            assertNotNull(buttonContaining(panel, recent), "recent project appears as a workspace action");
            buttonContaining(panel, recent).doClick();
            assertEquals(List.of("tour", "bundle", "incident", "new", "template", "open-project", "log", "graphml",
                    "sample:true", "recent:" + recent), calls);
        });
    }

    @Test void activeProjectKeepsStartChoicesAndReturnActionVisible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> calls = new ArrayList<>();
            var panel = new StartPanel(new StartPanel.Actions() {
                public void openDemo(Path p, boolean graph) { calls.add("demo"); }
                public void showTab(String n) { }
                public void openOwnLog() { calls.add("log"); }
                public void openSettings() { }
                public void openMcpSetup(McpSetupDialog.Target t) { }
                public void openFluxtionKey() { }
                public boolean fluxtionKeyPresent() { return false; }
                public void backToRecords() { calls.add("back"); }
                public void openProjectDesign() { calls.add("design"); }
            }, null);
            var ctx = new LinkedHashMap<String, Object>();
            ctx.put("project", Map.of("active", true, "name", "Demo", "root", "/demo"));
            ctx.put("savedGraphs", List.of(Map.of("name", "PnL", "open", false, "input", "waiting for input")));
            ctx.put("processorDeclarations", List.of(Map.of("name", "Live graph", "kind", "runtime", "status", "runtime processor; no fixed generated type declared")));
            panel.renderProject(ctx);
            assertTrue(visibleIn(panel, action(panel, "Load an experiment")));
            assertTrue(visibleIn(panel, action(panel, "Open project")));
            panel.showReturnToRecords(true);
            assertTrue(visibleIn(panel, buttonContaining(panel, "Return to workspace")));
            assertTrue(calls.isEmpty(), "rendering never opens evidence or a demo");
            buttonContaining(panel, "Return to workspace").doClick();
            assertEquals(List.of("back"), calls);
            ctx.put("log", Map.of("path", "/demo/run.yml"));
            panel.renderProject(ctx);
            assertTrue(visibleIn(panel, action(panel, "Load an experiment")));
        });
    }
    private static String text(Container parent) {
        StringBuilder s = new StringBuilder();
        for (Component c : parent.getComponents()) {
            if (c instanceof JLabel l) s.append(l.getText());
            if (c instanceof JTextArea t) s.append(t.getText());
            if (c instanceof Container child) s.append(text(child));
        }
        return s.toString();
    }
    private static JButton button(Container parent, String label) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && label.equals(b.getText())) return b;
            if (c instanceof Container child) { JButton b = button(child, label); if (b != null) return b; }
        }
        return null;
    }

    private static JButton action(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && name.equals(b.getAccessibleContext().getAccessibleName())) return b;
            if (c instanceof Container child) { JButton b = action(child, name); if (b != null) return b; }
        }
        return null;
    }

    private static JButton buttonContaining(Container parent, String text) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && b.getText() != null && b.getText().contains(text)) return b;
            if (c instanceof Container child) { JButton b = buttonContaining(child, text); if (b != null) return b; }
        }
        return null;
    }
    private static boolean visibleIn(Container root, Component child) {
        if (child == null) return false;
        for (Component c = child; c != root; c = c.getParent()) {
            if (c == null || !c.isVisible()) return false;
        }
        return root.isVisible();
    }
}
