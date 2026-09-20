package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectLandingTest {
    @Test void projectLandingShowsSavedIntentAndUsesExplicitOpenActions() throws Exception {
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
            assertTrue(text(panel).contains("PnL — waiting for input"));
            assertTrue(text(panel).contains("runtime processor; no fixed generated type declared"));
            assertTrue(calls.isEmpty(), "rendering never opens evidence or a demo");
            button(panel, "Open design…").doClick();
            assertEquals(List.of("design"), calls);
            ctx.put("log", Map.of("path", "/demo/run.yml"));
            panel.renderProject(ctx);
            button(panel, "Back to records").doClick();
            assertEquals(List.of("design", "back"), calls);
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
}
