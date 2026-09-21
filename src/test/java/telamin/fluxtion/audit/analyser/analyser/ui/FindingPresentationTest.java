package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.report.*;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed regression cases for the same finding's human and assistant surfaces. */
class FindingPresentationTest {
    @Test void confirmationUsesNeutralLabelsOnTableTopologyAndReports() throws Exception {
        Finding f = new Finding(0, "at-limit accepted", "matches expected boundary", "confirmation");
        assertEquals("Observation: at-limit accepted\nAssessment: matches expected boundary", f.tableText());
        BufferedImage image = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            String text = String.join("\n", TopologyCanvas.calloutLines(f, graphics.getFontMetrics(), 550)
                    .stream().map(TopologyCanvas.CalloutLine::text).toList());
            assertTrue(text.contains("Observation · at-limit accepted"));
            assertTrue(text.contains("Assessment · matches expected boundary"));
            assertFalse(text.contains("Fix ·"));
        } finally { graphics.dispose(); }
        var store = new HeapLogStore("---\neventLogRecord:\n  event: Tick\n  logTime: 1000\n---\n");
        var spec = new ReportSpec("confirmation", "Boundary evidence", "2026-09-21T00:00:00Z", "",
                LogFingerprint.of(store.index(), "constructed.yaml"), FilterSnapshot.all(),
                List.of(ReportSpec.SectionSpec.finding(0)));
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ReportsPanel(() -> List.of(spec), s -> ReportResolver.resolve(s,
                    store.index(), Map.of(0, f), Set.of(), Set.of(), new FilterState()), s -> null,
                    r -> { }, g -> { }, focus -> { }, filter -> { }, path -> { });
            panel.refresh();
            String text = componentText(panel);
            assertTrue(text.contains("Observation — record #0"), text);
            assertTrue(text.contains("Assessment"), text);
            assertFalse(text.contains("What is wrong"), text);
        });
    }

    @Test void faultRemainsTheDefaultAndInvalidKindsNeverReachTheSink() {
        var store = new HeapLogStore("---\neventLogRecord:\n  event: Tick\n  logTime: 1000\n---\n");
        var filter = new FilterState();
        var tabs = new GraphTabs();
        tabs.bind(store, filter);
        java.util.concurrent.atomic.AtomicReference<Finding> saved = new java.util.concurrent.atomic.AtomicReference<>();
        var ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(),
                (rows, note, fix, kind) -> saved.set(new Finding(rows[0], note, fix, kind)));
        assertTrue(ex.render("flag", Map.of("recordIndexes", List.of(0), "note", "fault")).ok());
        assertEquals("fault", saved.get().kind());
        assertEquals("What is wrong", saved.get().noteLabel());
        assertFalse(ex.render("flag", Map.of("recordIndexes", List.of(0), "kind", "invalid")).ok());
        assertEquals("fault", saved.get().kind());
        assertTrue(ex.render("flag", Map.of("recordIndexes", List.of(0), "kind", "confirmation")).ok());
        assertEquals("confirmation", saved.get().kind());
    }

    private static String componentText(Component component) {
        StringBuilder text = new StringBuilder();
        if (component instanceof JTextComponent field) text.append(field.getText()).append('\n');
        if (component instanceof JLabel label) text.append(label.getText()).append('\n');
        if (component instanceof Container container)
            for (Component child : container.getComponents()) text.append(componentText(child));
        return text.toString();
    }
}
