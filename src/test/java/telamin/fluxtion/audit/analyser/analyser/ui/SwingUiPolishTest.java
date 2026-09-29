package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.design.ProducerResult;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import telamin.fluxtion.audit.analyser.analyser.report.ReportResolver;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SwingUiPolishTest {
    @Test void reportActionsAreScopedToReportsAndTheListNamesTheReportForPeople() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var store = new HeapLogStore(Samples.sample());
            var spec = new ReportSpec("internal-id", "Human-readable title", "", "", null, null, List.of());
            var panel = new ReportsPanel(() -> List.of(spec),
                    s -> ReportResolver.resolve(s, store.index(), Map.of(), Set.of(), Set.of(), new FilterState()),
                    s -> null, r -> { }, g -> { }, f -> { }, filter -> { }, path -> { },
                    name -> { }, (from, to) -> null);
            JPanel walks = new JPanel();
            panel.addWalks(walks);
            panel.refresh();
            JTabbedPane categories = assertInstanceOf(JTabbedPane.class, panel.getComponent(0),
                    "report actions must live inside the investigation reports tab");
            JButton export = button(panel, "Export PDF…");
            assertTrue(SwingUtilities.isDescendingFrom(export, categories.getComponentAt(0)));
            assertFalse(SwingUtilities.isDescendingFrom(export, categories.getComponentAt(1)),
                    "the report export must not sit above Spotlight walks");
            assertFalse(SwingUtilities.isDescendingFrom(export, categories.getComponentAt(2)),
                    "producer findings must not show actions for an investigation report");
            @SuppressWarnings("unchecked") JList<String> list = (JList<String>) find(panel, JList.class);
            JLabel row = (JLabel) list.getCellRenderer().getListCellRendererComponent(list, spec.name(), 0, true, false);
            assertTrue(row.getText().contains("Human-readable title"), row.getText());
            assertTrue(row.getText().contains("internal-id"), row.getText());
        });
    }

    @Test void manyGraphsUseOneSelectorAndSelectionDoesNotSaveAnEdit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GraphTabs graphs = new GraphTabs();
            graphs.bind(new HeapLogStore(Samples.sample()), new FilterState());
            int[] edits = {0};
            graphs.setChangeListener(() -> edits[0]++);
            for (int i = 2; i <= 8; i++) graphs.addGraph("Graph " + i);
            @SuppressWarnings("unchecked") JComboBox<GraphPanel> selector =
                    (JComboBox<GraphPanel>) find(graphs, JComboBox.class);
            assertEquals(8, selector.getItemCount());
            int before = edits[0];
            selector.setSelectedIndex(2);
            assertEquals("Graph 3", graphs.selectedGraphName());
            assertEquals(before, edits[0], "switching the visible chart must not persist a chart edit");
            graphs.renameSelected("Renamed chart");
            assertEquals("Renamed chart", graphs.selectedGraphName());
            JLabel rendered = (JLabel) selector.getRenderer().getListCellRendererComponent(
                    new JList<>(), (GraphPanel) selector.getSelectedItem(), 2, true, false);
            assertEquals("Renamed chart", rendered.getText(), "the selector tracks a rename");
        });
    }

    @Test void seriesEditorUsesTheFullWidthOfANarrowGraphPane() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GraphPanel graph = new GraphPanel();
            graph.setSize(500, 500);
            try {
                var toggleField = GraphPanel.class.getDeclaredField("editSeriesButton");
                toggleField.setAccessible(true);
                ((JToggleButton) toggleField.get(graph)).doClick();
                var splitField = GraphPanel.class.getDeclaredField("seriesSplit");
                splitField.setAccessible(true);
                JSplitPane split = (JSplitPane) splitField.get(graph);
                assertEquals(JSplitPane.VERTICAL_SPLIT, split.getOrientation(),
                        "a 500px pane cannot show a readable plot and a 280px editor side by side");
                var tabsField = GraphPanel.class.getDeclaredField("seriesEditorTabs");
                tabsField.setAccessible(true);
                JTabbedPane editor = (JTabbedPane) tabsField.get(graph);
                assertEquals(2, editor.getTabCount());
                assertEquals("Add series", editor.getTitleAt(editor.getSelectedIndex()),
                        "an empty chart opens where a person can add its first series");
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test void producerFindingSeparatesDiagnosisReasonAndFixWithoutLosingSourceText() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var finding = new ProducerResult.Finding("DEMO_RULE", "WARNING", "A binding is missing",
                    "The handler cannot run.", "Add the DEMO binding.",
                    Map.of("kind", "UNKNOWN", "xmlDeclaration", "<bean id='DEMO'/>") , Map.of(), List.of());
            var result = new ProducerResult("/tmp/DEMO/result.json", "validate", "", "",
                    List.of(finding), Map.of(), Map.of());
            var panel = new ProducerFindingsPanel();
            panel.render(result, null, null, null, "", location -> { });
            List<String> labels = labels(panel);
            List<String> bodies = bodies(panel);
            assertTrue(labels.contains("Why this matters"), labels.toString());
            assertTrue(labels.contains("Suggested fix"), labels.toString());
            assertTrue(labels.contains("XML declaration"), labels.toString());
            assertTrue(bodies.contains("DEMO_RULE"), bodies.toString());
            assertTrue(bodies.contains("A binding is missing"), bodies.toString());
            assertTrue(bodies.contains("The handler cannot run."), bodies.toString());
            assertTrue(bodies.contains("Add the DEMO binding."), bodies.toString());
            assertTrue(bodies.contains("<bean id='DEMO'/>"), bodies.toString());
            assertNotNull(button(panel, "Unavailable"), "the source action still reports an unmapped finding");
        });
    }

    private static List<String> labels(Component root) {
        List<String> out = new java.util.ArrayList<>();
        collect(root, JLabel.class, label -> out.add(label.getText()));
        return out;
    }

    private static List<String> bodies(Component root) {
        List<String> out = new java.util.ArrayList<>();
        collect(root, JTextArea.class, area -> out.add(area.getText()));
        return out;
    }

    private static <T extends Component> void collect(Component root, Class<T> kind, java.util.function.Consumer<T> add) {
        if (kind.isInstance(root)) add.accept(kind.cast(root));
        if (root instanceof java.awt.Container container)
            for (Component child : container.getComponents()) collect(child, kind, add);
    }

    private static Component find(Component root, Class<?> kind) {
        if (kind.isInstance(root)) return root;
        if (root instanceof java.awt.Container container)
            for (Component child : container.getComponents()) {
                Component found = find(child, kind);
                if (found != null) return found;
            }
        return null;
    }

    private static JButton button(Component root, String text) {
        JButton result = findButton(root, text);
        if (result == null) throw new AssertionError("missing button: " + text);
        return result;
    }

    private static JButton findButton(Component root, String text) {
        if (root instanceof JButton b && text.equals(b.getText())) return b;
        if (root instanceof java.awt.Container container)
            for (Component child : container.getComponents()) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        return null;
    }
}
