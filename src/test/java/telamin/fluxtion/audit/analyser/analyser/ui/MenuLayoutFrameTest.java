package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;

/** The three concerns have separate real menus; this suite is included in the CI display/skip gate. */
class MenuLayoutFrameTest {
    @TempDir Path tmp;
    private static List<String> labels(JMenu menu) {
        return Arrays.stream(menu.getMenuComponents())
                .map(c -> c instanceof JMenuItem i ? i.getText() : MenuInventory.SEPARATOR).toList();
    }
    @Test void projectSourcesAndAuditHaveTheirOwnActions() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try(var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                JMenuBar bar = f.frame.getJMenuBar();
                assertEquals(List.of("Project", "Sources", "Audit log"), IntStream.range(0,3)
                        .mapToObj(i -> bar.getMenu(i).getText()).toList());
                var relocated = new ArrayList<String>();
                for (String name : MenuInventory.RESOURCE_MENUS) {
                    List<String> actual = labels(menu(f.frame, name));
                    assertEquals(MenuInventory.MENUS.get(name), actual, name + " exact ordered menu and separators");
                    relocated.addAll(actual.stream().filter(t -> !t.equals(MenuInventory.SEPARATOR)).toList());
                }
                var expected = new ArrayList<>(MenuInventory.BASE_FILE.stream()
                        .map(t -> t.equals("Reset (close log + graph)") ? "Close log and topology" : t).toList());
                expected.addAll(MenuInventory.SHORTCUTS);
                assertEquals(26, MenuInventory.BASE_FILE.size(), "base File inventory");
                assertEquals(29, relocated.size(), "all legacy actions plus the three shortcuts");
                assertEquals(new HashSet<>(expected), new HashSet<>(relocated), "legacy action inventory is preserved");
                for (String label : expected) assertEquals(1, Collections.frequency(relocated, label), label + " appears exactly once");
                assertEquals("Records", bar.getMenu(3).getText());
                assertEquals(MenuInventory.MENUS.get("Records"), labels(bar.getMenu(3)), "Records inventory also backs the docs check");
            });
        }
    }

    private static JMenu menu(MainFrame frame, String name) {
        return IntStream.range(0, frame.getJMenuBar().getMenuCount())
                .mapToObj(i -> frame.getJMenuBar().getMenu(i))
                .filter(m -> name.equals(m.getText())).findFirst().orElseThrow();
    }
    private static JMenuItem item(MainFrame frame, String menu, String name) {
        return Arrays.stream(menu(frame, menu).getMenuComponents())
                .filter(c -> c instanceof JMenuItem i && name.equals(i.getText()))
                .map(c -> (JMenuItem)c).findFirst().orElseThrow();
    }

    @Test void sourceShortcutsOpenTheNamedSettingsPage() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            f.dialogs.stop(); // This test owns and inspects the real modal, rather than dismissing it unseen.
            for (String page : List.of("Source roots", "Event processor", "Maven repos")) {
                AtomicReference<String> selected = new AtomicReference<>();
                AtomicReference<Throwable> failure = new AtomicReference<>();
                onEdt(() -> {
                    Timer inspect = new Timer(20, e -> {
                        for (Window w : Window.getWindows()) if (w instanceof ConfigPanel && w.isShowing()) {
                            try {
                                JTabbedPane tabs = (JTabbedPane)field(w, "tabs");
                                selected.set(tabs.getTitleAt(tabs.getSelectedIndex()));
                            } catch (Throwable t) { failure.set(t); }
                            finally { w.dispose(); }
                        }
                    });
                    Timer timeout = new Timer(5000, e -> {
                        for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing()) w.dispose();
                    });
                    timeout.setRepeats(false);
                    inspect.start(); timeout.start();
                    try { item(f.frame, "Sources", page + "…").doClick(); }
                    finally { inspect.stop(); timeout.stop(); }
                });
                assertNull(failure.get());
                assertEquals(page, selected.get(), "shortcut must open its own page in the actual Settings dialog");
            }
        }
    }

    @Test void closeLogFromItsMenuPreservesTheProjectAndSavedChart() throws Exception {
        ChartLifecycleReviewFrameTest owner = new ChartLifecycleReviewFrameTest();
        owner.tmp = tmp;
        try (var f = owner.new Fixture(ChartLifecycleReviewFrameTest.chart("Retained", true))) {
            assertNotNull(ChartLifecycleReviewFrameTest.edt(() -> field(f.frame, "store")));
            onEdt(() -> clickAsHuman(f.frame, "Audit log", "Close log"));
            assertNull(ChartLifecycleReviewFrameTest.edt(() -> field(f.frame, "store")));
            assertEquals(f.profile.toString(), f.config.activeProjectPath, "closing the log keeps the project");
            assertEquals(List.of("Retained"), f.config.savedGraphs.stream().map(g -> g.name()).toList());
            onEdt(() -> {
                assertFalse(item(f.frame, "Audit log", "Close log").isEnabled());
                assertTrue(item(f.frame, "Project", "Close project").isEnabled());
                item(f.frame, "Project", "Close project").doClick();
                assertFalse(item(f.frame, "Project", "Close project").isEnabled());
                assertEquals("", f.config.activeProjectPath, "Close project completes the CLOSE transition");
            });
        }
    }
    private static void clickAsHuman(MainFrame frame, String menu, String label) {
        try {
        var intent = MainFrame.class.getDeclaredField("sessionInteractive");
        intent.setAccessible(true);
        intent.setBoolean(frame, false);
        JMenuItem action = item(frame, menu, label);
        assertTrue(action.isEnabled(), label + " is enabled on the loaded fixture");
        action.doClick();
        assertTrue(intent.getBoolean(frame), label + " declares human intent");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private TopologyPanel loadTopology(ChartLifecycleReviewFrameTest.Fixture f) throws Exception {
        // Constructed regression fixture: one declared node matches the fixture's recorded node id.
        Path graph = Files.writeString(tmp.resolve("close.graphml"), """
                <graphml xmlns="http://graphml.graphdrawing.org/xmlns" xmlns:jGraph="http://www.jgraph.com/">
                  <key id="vertex_label" for="node" attr.name="nodeData" attr.type="string"/>
                  <graph edgedefault="directed"><node id="node"><data key="vertex_label">
                    <jGraph:ShapeNode><jGraph:Geometry height="70" width="160" x="20" y="20"/>
                      <jGraph:label text="id:node&#10;class:com.acme.Node"/>
                      <jGraph:Style properties="NODE"/>
                    </jGraph:ShapeNode>
                  </data></node></graph>
                </graphml>
                """);
        assertTrue(f.executor.render("open", Map.of("graphml", graph.toString())).ok());
        TopologyPanel topology = (TopologyPanel)field(f.frame, "topologyPanel");
        onEdt(() -> assertTrue(topology.hasGraph(), "fixture has a topology before the menu click"));
        return topology;
    }

    @Test void closeGraphFromItsMenuKeepsLogProjectAndCharts() throws Exception {
        ChartLifecycleReviewFrameTest owner = new ChartLifecycleReviewFrameTest(); owner.tmp = tmp;
        try (var f = owner.new Fixture(ChartLifecycleReviewFrameTest.chart("Retained", true))) {
            TopologyPanel topology = loadTopology(f);
            onEdt(() -> {
                clickAsHuman(f.frame, "Sources", "Close graph");
                assertFalse(topology.hasGraph(), "Close graph closes the topology");
                assertNotNull(field(f.frame, "store"), "Close graph keeps the log");
                assertEquals(f.profile.toString(), f.config.activeProjectPath);
                assertEquals(List.of("Retained"), f.config.savedGraphs.stream().map(g -> g.name()).toList());
            });
        }
    }

    @Test void closeBothFromItsMenuKeepsProjectAndCharts() throws Exception {
        ChartLifecycleReviewFrameTest owner = new ChartLifecycleReviewFrameTest(); owner.tmp = tmp;
        try (var f = owner.new Fixture(ChartLifecycleReviewFrameTest.chart("Retained", true))) {
            TopologyPanel topology = loadTopology(f);
            onEdt(() -> {
                clickAsHuman(f.frame, "Project", "Close log and topology");
                assertNull(field(f.frame, "store"), "Close log and topology closes the log");
                assertFalse(topology.hasGraph(), "Close log and topology closes the topology");
                assertEquals(f.profile.toString(), f.config.activeProjectPath, "close both keeps the project");
                assertEquals(List.of("Retained"), f.config.savedGraphs.stream().map(g -> g.name()).toList());
            });
        }
    }

}
