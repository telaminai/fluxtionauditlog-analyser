package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.Path;
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
        return Arrays.stream(menu.getMenuComponents()).filter(c -> c instanceof JMenuItem)
                .map(c -> ((JMenuItem)c).getText()).toList();
    }
    @Test void projectSourcesAndAuditHaveTheirOwnActions() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try(var f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                JMenuBar bar = f.frame.getJMenuBar();
                assertEquals(List.of("Project", "Sources", "Audit log"), IntStream.range(0,3)
                        .mapToObj(i -> bar.getMenu(i).getText()).toList());
                assertTrue(labels(bar.getMenu(0)).containsAll(List.of("Open project…", "Close project",
                        "New project from template…", "Save project as…")));
                assertTrue(labels(bar.getMenu(1)).containsAll(List.of("Source roots…", "Event processor…",
                        "Maven repos…", "Open GraphML…", "Open design…", "Open producer diagnostics…")));
                assertTrue(labels(bar.getMenu(2)).containsAll(List.of("Open log…", "Close log", "Follow (tail)",
                        "Export records (CSV)…", "Export records (YAML)…")));
                assertEquals("Records", bar.getMenu(3).getText());
                assertFalse(labels(bar.getMenu(0)).contains("Close log"));
                assertFalse(labels(bar.getMenu(2)).contains("Close project"));
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
            onEdt(() -> item(f.frame, "Audit log", "Close log").doClick());
            assertNull(ChartLifecycleReviewFrameTest.edt(() -> field(f.frame, "store")));
            assertEquals(f.profile.toString(), f.config.activeProjectPath, "closing the log keeps the project");
            assertEquals(List.of("Retained"), f.config.savedGraphs.stream().map(g -> g.name()).toList());
            onEdt(() -> {
                assertFalse(item(f.frame, "Audit log", "Close log").isEnabled());
                assertTrue(item(f.frame, "Project", "Close project").isEnabled());
                item(f.frame, "Project", "Close project").doClick();
                assertFalse(item(f.frame, "Project", "Close project").isEnabled());
            });
        }
    }
}
