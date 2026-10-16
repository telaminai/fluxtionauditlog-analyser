package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import telamin.fluxtion.audit.analyser.analyser.report.ReportResolver;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.table.DefaultTableModel;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SF-2, headless: foreign text shown by the analyser's surfaces is never parsed as HTML, so an {@code <img src=…>} in
 * it is never fetched. Swing builds an HTML view — and starts the image fetch — when the text is SET, display or
 * not, so this runs in the default build. Every check is paired with a WITNESS on the same probe: the unprotected
 * shape (the code as it shipped) must reach the server, or "nothing was fetched" would prove nothing.
 * {@link ForeignTextFrameTest} repeats the decisive cases on a shown window, painted and hovered.
 */
class UiTextTest {

    private static LookAndFeel previous;

    /** The app's look and feel: FlatLaf's tooltip and label UIs are the ones that decide, not Metal's. */
    @BeforeAll
    static void flatLaf() throws Exception {
        previous = UIManager.getLookAndFeel();
        SwingUtilities.invokeAndWait(() -> {
            try {
                UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @AfterAll
    static void restore() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                if (previous != null) UIManager.setLookAndFeel(previous);
            } catch (Exception ignored) {
                // the next test's own setup decides
            }
        });
    }

    // ---- the helper -----------------------------------------------------------------------------------------------

    @Test
    void inertBreaksEveryHtmlTagAndLeavesOtherTextAlone() {
        String plain = "a < b && c > d";
        assertSame(plain, UiText.inert(plain), "text with no <html> is returned untouched");
        assertNull(UiText.inert((String) null));
        for (String s : List.of("<html><b>x", "<HTML><b>x", "<HtMl>", "a\n<html>b</html>")) {
            String inert = UiText.inert(s);
            assertFalse(UiText.rendersAsHtml(inert), s);
            assertFalse(inert.toLowerCase().contains("<html>"), "an option pane looks for <html> anywhere: " + inert);
            assertEquals(s, inert.replace(String.valueOf(UiText.BREAK), ""), "only an invisible character is added");
        }
        assertEquals("&lt;img src=&#39;x&#39;&gt; &amp; &quot;", UiText.escape("<img src='x'> & \""));
    }

    // ---- the fetch, and its witness -------------------------------------------------------------------------------

    @Test
    void aPlainLabelNeverFetchesButAPlainOwnersTooltipDoes() throws Exception {
        try (LoopbackProbe probe = new LoopbackProbe()) {
            onEdt(() -> {
                new JLabel(probe.payload("/witness-label"));                       // the shape that shipped
                UiText.label(probe.payload("/label"));
                UiText.button(probe.payload("/button"));
                UiText.menuItem(probe.payload("/menu-item"));
                UiText.checkBox(probe.payload("/check-box"));
                // the finding: html.disable on the OWNER does not reach its tooltip — JToolTip renders it itself
                JLabel owner = UiText.label("x");
                owner.setToolTipText(probe.payload("/witness-tooltip"));
                tip(owner);
                JLabel fixed = UiText.label("x");
                fixed.setToolTipText(UiText.tooltip(probe.payload("/tooltip")));
                tip(fixed);
                new JLabel(probe.payload("/witness-last"));           // fetches are queued in order: this one is last
            });
            assertTrue(probe.awaitHit("/witness-label", 5000), "witness: an unprotected label fetches its <img>");
            assertTrue(probe.awaitHit("/witness-tooltip", 5000),
                    "witness: a tooltip on an html.disable owner still renders HTML — hence UiText.tooltip");
            assertTrue(probe.awaitHit("/witness-last", 5000), "the last witness, built after every protected component");
            for (String path : List.of("/label", "/button", "/menu-item", "/check-box", "/tooltip")) {
                assertEquals(0, probe.hits(path), path + " was fetched");
            }
        }
    }

    @Test
    void anOptionPaneMessageAndItsChoicesAreInert() throws Exception {
        try (LoopbackProbe probe = new LoopbackProbe()) {
            onEdt(() -> {
                // BasicOptionPaneUI renders a message that merely CONTAINS <html>…</html> — a name quoted mid-sentence;
                // it cuts that span out and drops the words before it
                new JOptionPane("Delete the walk \"" + probe.payload("/witness-message") + "</html>\"?");
                new JOptionPane(UiText.inert("Delete the walk \"" + probe.payload("/message") + "</html>\"?"));
                // and each line of a multi-line message is its own label
                new JOptionPane(UiText.inert("Delete the walk?\n" + probe.payload("/message-line")));
                new JOptionPane("Restore which?", JOptionPane.QUESTION_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                        UiText.inert(new Object[]{probe.payload("/option")}));
                new JLabel(probe.payload("/witness-last"));
            });
            assertTrue(probe.awaitHit("/witness-message", 5000), "witness: a quoted name in a message is rendered as HTML");
            assertTrue(probe.awaitHit("/witness-last", 5000), "the last witness, built after every protected component");
            assertEquals(0, probe.hits("/message"));
            assertEquals(0, probe.hits("/message-line"));
            assertEquals(0, probe.hits("/option"));
        }
    }

    @Test
    void cellsOfAListAComboAndATableAreTheirCharacters() throws Exception {
        try (LoopbackProbe probe = new LoopbackProbe()) {
            onEdt(() -> {
                JList<String> raw = new JList<>(new String[]{probe.payload("/witness-cell")});
                render(raw, 0);
                JList<String> list = UiText.plainCells(new JList<>(new String[]{probe.payload("/list")}));
                render(list, 0);
                JComboBox<String> combo = UiText.plainCells(new JComboBox<>(new String[]{probe.payload("/combo")}));
                // the theme toggle replaces a look-and-feel renderer; a wrapped one must survive it
                combo.updateUI();
                assertInstanceOf(UiText.PlainRenderer.class, combo.getRenderer(), "the wrap survives updateUI");
                combo.getRenderer().getListCellRendererComponent(new JList<>(), combo.getItemAt(0), 0, false, false);
                JTable table = UiText.plainCells(new JTable(new DefaultTableModel(
                        new Object[][]{{probe.payload("/table-cell")}}, new Object[]{probe.payload("/table-header")})));
                table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
                table.getTableHeader().getDefaultRenderer()
                        .getTableCellRendererComponent(table, table.getColumnName(0), false, false, -1, 0);
            });
            assertTrue(probe.awaitHit("/witness-cell", 5000), "witness: a default list renderer renders HTML");
            Thread.sleep(300);
            for (String path : List.of("/list", "/combo", "/table-cell", "/table-header")) {
                assertEquals(0, probe.hits(path), path + " was fetched");
            }
        }
    }

    /** Review of 7f825492: the wrapper is not a Component, so a theme change must still reach the renderer it wraps. */
    @Test
    void aThemeChangeReachesTheWrappedRenderer() throws Exception {
        onEdt(() -> {
            try {
                JComboBox<String> combo = UiText.plainCells(new JComboBox<>(new String[]{"a"}));
                var wrapper = (UiText.PlainRenderer<?>) combo.getRenderer();
                JComponent delegate = (JComponent) wrapper.delegate();
                Object before = delegate.getUI();
                UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatDarkLaf());
                SwingUtilities.updateComponentTreeUI(combo);                  // what the theme toggle does
                combo.getRenderer().getListCellRendererComponent(new JList<>(), "a", 0, false, false);
                assertNotSame(before, delegate.getUI(), "the wrapped renderer was updated for the new look and feel");
                assertSame(wrapper, combo.getRenderer(), "and the wrap itself survived the change");
            } catch (javax.swing.UnsupportedLookAndFeelException e) {
                throw new AssertionError(e);
            } finally {
                try {
                    UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
                } catch (Exception ignored) {
                    // restore() puts the original back after the class
                }
            }
        });
    }

    // ---- the surfaces the spec names -------------------------------------------------------------------------------

    @Test
    void theStartPagesRecentProjectAndBundleRowsShowTheirCharacters() throws Exception {
        try (LoopbackProbe probe = new LoopbackProbe()) {
            AtomicReference<String> shown = new AtomicReference<>();
            onEdt(() -> {
                StartPanel start = new StartPanel(new NoActions(), s -> { });
                // a folder name cannot hold '/', so the folder carries markup and the configured path the <img>:
                // the button's tooltip was the raw path
                String folder = "<html><u>DEMO";
                String project = "/tmp/" + folder + "/.analyser/project.yml";
                String recent = probe.payload("/recent-project");
                start.setRecentProjects(List.of(project, recent));
                start.setRecentBundles(List.of(new AppConfig.RecentBundle("/tmp/<html><u>bundle.fexp", "",
                        probe.payload("/bundle-note"))));
                JButton row = button(start, folder + " / project.yml");
                assertNotNull(row, "the recent project's row");
                assertEquals(Boolean.TRUE, row.getClientProperty(UiText.HTML_DISABLE));
                assertNull(row.getClientProperty(BasicHTML.propertyKey), "the folder's markup was rendered");
                shown.set(row.getText());
                for (AbstractButton b : buttons(start)) {
                    if (b.getToolTipText() != null) tip(b);
                    b.getPreferredSize();
                }
                JButton bundle = button(start, "bundle.fexp");
                assertNotNull(bundle, "the recent bundle's row");
                assertNull(bundle.getClientProperty(BasicHTML.propertyKey));
            });
            assertTrue(shown.get().startsWith("<html><u>DEMO"), "shown as its characters: " + shown.get());
            witness(probe);
            assertEquals(0, probe.hits("/recent-project"), "a recent project's path was fetched on hover");
            assertEquals(0, probe.hits("/bundle-note"), "a bundle sender's note was fetched");
        }
    }

    @Test
    void aReportTitleAndNameAreTheirCharactersInTheReportsListAndTheProjectPanel() throws Exception {
        try (LoopbackProbe probe = new LoopbackProbe()) {
            onEdt(() -> {
                var store = new HeapLogStore(Samples.sample());
                var spec = new ReportSpec(probe.payload("/report-name"), probe.payload("/report-title"), "", "",
                        null, null, List.of());
                var reports = new ReportsPanel(() -> List.of(spec),
                        s -> ReportResolver.resolve(s, store.index(), Map.of(), Set.of(), Set.of(), new FilterState()),
                        s -> null, r -> { }, g -> { }, f -> { }, filter -> { }, path -> { },
                        name -> { }, (from, to) -> null);
                reports.refresh();
                @SuppressWarnings("unchecked") JList<String> list = (JList<String>) find(reports, JList.class);
                JLabel cell = (JLabel) render(list, 0);
                assertTrue(cell.getText().contains("&lt;img"), "the title is escaped inside the list's own markup");
                tip(cell);

                ProjectPanel project = new ProjectPanel(new ProjectPanel.Navigator() {
                    @Override public void showTab(String title) { }
                    @Override public void openSettings(String page) { }
                    @Override public void showReport(String name) { }
                    @Override public void showGraph(String name) { }
                });
                var ctx = new LinkedHashMap<String, Object>();
                ctx.put("project", Map.of("active", true, "name", "DEMO", "root", "/demo"));
                // short enough that the panel draws it whole (a long value is abbreviated in the middle)
                ctx.put("reports", List.of(Map.of("name", "r1", "title", probe.payload("/p"),
                        "sections", 1, "from", "project")));
                project.render(ProjectModel.from(ctx));
                for (JLabel l : labels(project)) {
                    l.getPreferredSize();
                    if (l.getToolTipText() != null) tip(l);
                }
            });
            witness(probe);
            for (String path : List.of("/report-name", "/report-title", "/p")) {
                assertEquals(0, probe.hits(path), path + " was fetched");
            }
        }
    }

    /** The topology tooltip spliced the node's id into its own markup unescaped (shipped). */
    @Test
    void aNodeTooltipEscapesTheNodesWords() throws Exception {
        try (LoopbackProbe probe = new LoopbackProbe()) {
            onEdt(() -> {
                String id = "<img src='" + probe.url("/node-id") + "'>";
                var node = new ProcessorTopology.Node(id, id, "com.acme." + id, ProcessorTopology.Kind.values()[0]);
                String tip = TopologyCanvas.nodeTooltip(node, null, id);
                assertFalse(tip.contains("<img"), tip);
                JToolTip shown = new JToolTip();
                shown.setTipText(tip);
                JToolTip witness = new JToolTip();
                witness.setTipText("<html><b>" + id.replace("/node-id", "/witness-node") + "</b></html>");
            });
            assertTrue(probe.awaitHit("/witness-node", 5000), "witness: the raw splice fetches");
            Thread.sleep(300);
            assertEquals(0, probe.hits("/node-id"));
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

    /** The same as ToolTipManager does on hover: a tooltip built from the component's text. */
    static void tip(JComponent c) {
        JToolTip t = c.createToolTip();
        t.setTipText(c.getToolTipText());
        t.getPreferredSize();
    }

    private static Component render(JList<?> list, int index) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        javax.swing.ListCellRenderer<Object> r = (javax.swing.ListCellRenderer) list.getCellRenderer();
        Component c = r.getListCellRendererComponent(list, list.getModel().getElementAt(index), index, false, false);
        c.getPreferredSize();
        return c;
    }

    /** An unprotected label on the same probe, so a zero below is a measurement and not a blind spot. */
    private static void witness(LoopbackProbe probe) throws Exception {
        onEdt(() -> new JLabel(probe.payload("/witness")));
        assertTrue(probe.awaitHit("/witness", 5000), "witness: fetches are visible from this test");
        Thread.sleep(300);
    }

    static void onEdt(Runnable r) throws Exception {
        AtomicReference<Throwable> failed = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                failed.set(t);
            }
        });
        if (failed.get() instanceof Error e) throw e;
        if (failed.get() != null) throw new AssertionError(failed.get());
    }

    static JButton button(Container root, String startsWith) {
        for (AbstractButton b : buttons(root)) {
            if (b instanceof JButton j && b.getText() != null && b.getText().contains(startsWith)) return j;
        }
        return null;
    }

    static List<AbstractButton> buttons(Container root) {
        List<AbstractButton> out = new ArrayList<>();
        for (Component c : all(root)) if (c instanceof AbstractButton b) out.add(b);
        return out;
    }

    static List<JLabel> labels(Container root) {
        List<JLabel> out = new ArrayList<>();
        for (Component c : all(root)) if (c instanceof JLabel l) out.add(l);
        return out;
    }

    static Component find(Container root, Class<?> type) {
        for (Component c : all(root)) if (type.isInstance(c)) return c;
        return null;
    }

    static List<Component> all(Container root) {
        List<Component> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof Container k) out.addAll(all(k));
        }
        return out;
    }

    /** A start page whose actions do nothing — only its rows are under test. */
    static final class NoActions implements StartPanel.Actions {
        public void openDemo(Path p, boolean graph) { }
        public void showTab(String n) { }
        public void openOwnLog() { }
        public void openSettings() { }
        public void openMcpSetup(McpSetupDialog.Target t) { }
        public void openFluxtionKey() { }
        public boolean fluxtionKeyPresent() { return false; }
        public void backToRecords() { }
        public void newProject() { }
        public void newProjectFromTemplate() { }
        public void openExperiment() { }
        public void investigateIncident() { }
        public void openGraphml() { }
        public void openRecentProject(String path) { }
        public void openExistingProject() { }
    }
}
