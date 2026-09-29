package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.GraphicsEnvironment;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class StartWorkspaceFrameTest {
    @TempDir Path temporary;

    @Test void startPageReplacesTheWholeInvestigationArea() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    frame.set(new MainFrame());
                    JPanel cards = (JPanel) field(frame.get(), "workspaceCards");
                    StartPanel start = (StartPanel) field(frame.get(), "startPanel");
                    JSplitPane west = (JSplitPane) field(frame.get(), "westOuter");
                    assertSame(cards, start.getParent(), "the start page owns the complete content area");
                    assertEquals(2, cards.getComponentCount(), "start and investigation are alternative workspaces");
                    assertSame(west, cards.getComponent(1), "the left rail is inside the investigation only");
                    assertInstanceOf(JSplitPane.class, west.getRightComponent(),
                            "the records and output tabs remain together in an investigation");
                    assertFalse(((JPanel) field(frame.get(), "workspaceChrome")).isVisible(),
                            "time-range and toolbar controls are irrelevant on the start page");
                    assertFalse(frame.get().getJMenuBar().isVisible(), "the start page has its own actions");
                    assertFalse(((JPanel) field(frame.get(), "workspaceStatusBar")).isVisible(),
                            "the start page does not repeat the application status line");
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void droppingSpringDesignThenGraphAndLogOpensTheirRealViews() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("drop-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            Path root = Files.createDirectories(temporary.resolve("source"));
            Path design = Files.writeString(root.resolve("demo-design.xml"), "<beans><bean id='DEMO'/></beans>");
            var executor = (ActionExecutor) field(frame.get(), "actionExecutor");
            assertTrue(executor.render("source_root", java.util.Map.of("add", List.of(root.toString()))).ok());
            StartPanel start = (StartPanel) field(frame.get(), "startPanel");
            assertTrue(drop(start, design.toFile()), "Spring XML drop is accepted by the start page");
            for (int i = 0; i < 200 && onEdt(() -> {
                var running = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(frame.get(), "session");
                return running == null || running.processor().designSession.path() == null;
            }); i++) Thread.sleep(20);
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(frame.get(), "session");
            assertEquals(design.toRealPath().toString(), session.processor().designSession.path());
            assertTrue(onEdt(() -> ((JPanel) field(frame.get(), "workspaceChrome")).isVisible()));

            JPanel workspace = (JPanel) field(frame.get(), "workspaceCards");
            Path graph = Path.of("src/test/resources/topology/demo-quote-processor.graphml").toAbsolutePath();
            assertTrue(drop(workspace, graph.toFile()), "GraphML drop is accepted in the workspace");
            assertTrue(onEdt(() -> ((TopologyPanel) field(frame.get(), "topologyPanel")).hasGraph()));
            Path log = Path.of("src/test/resources/topology/demo-quote-audit.yaml").toAbsolutePath();
            assertTrue(drop(workspace, log.toFile()), "audit log drop is accepted in the workspace");
            for (int i = 0; i < 200 && onEdt(() -> field(frame.get(), "store") == null); i++) Thread.sleep(20);
            assertNotNull(onEdt(() -> field(frame.get(), "store")),
                    "the dropped log reaches the log reader: " + onEdt(() ->
                            ((javax.swing.JLabel) field(frame.get(), "status")).getText())
                            + " / inFlight=" + field(frame.get(), "loadInFlight")
                            + " / sessionProblem=" + field(frame.get(), "sessionProblem")
                            + " / projectOffer=" + field(frame.get(), "pendingProjectOffer"));
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void recentProjectChoiceOpensItsWorkspaceWithoutOpeningALog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("recent-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            Path profile = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(
                    Files.createDirectories(temporary.resolve("DEMO-project")));
            telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile,
                    new telamin.fluxtion.audit.analyser.analyser.config.AppConfig(),
                    new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
            SwingUtilities.invokeAndWait(() -> {
                try {
                    frame.set(new MainFrame());
                    var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(frame.get(), "config");
                    config.recentProjects.add(profile.toString());
                    StartPanel start = (StartPanel) field(frame.get(), "startPanel");
                    start.setRecentProjects(config.recentProjects);
                    javax.swing.JButton recent = buttonContaining(start, profile.toString());
                    assertNotNull(recent, "the recent project is visible on the start page");
                    recent.doClick();
                    var project = (telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(frame.get(), "project");
                    assertTrue(project.hasProject(), "clicking a recent project enters its workspace");
                    assertTrue(((JPanel) field(frame.get(), "workspaceChrome")).isVisible());
                    assertTrue(frame.get().getJMenuBar().isVisible());
                    assertNull(field(frame.get(), "store"), "a project switch never invents a previous log");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void sampleChoiceOpensARealDemoProject() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("sample-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    frame.set(new MainFrame());
                    var sample = buttonContaining((StartPanel) field(frame.get(), "startPanel"), "Open sample project");
                    assertNotNull(sample);
                    sample.doClick();
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            for (int i = 0; i < 200 && onEdt(() -> field(frame.get(), "store") == null); i++) Thread.sleep(20);
            assertNotNull(onEdt(() -> field(frame.get(), "store")), "the sample loads a real audit log");
            var project = (telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(frame.get(), "project");
            assertTrue(project.hasProject());
            assertTrue(Files.isRegularFile(project.activeFile()), "the sample has a real project profile");
            assertTrue(onEdt(() -> ((TopologyPanel) field(frame.get(), "topologyPanel")).hasGraph()));
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void droppingVerifiedExperimentOpensItsOwnProjectGraphAndLog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("bundle-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            Path payload = Files.createDirectories(temporary.resolve("payload"));
            Path profile = payload.resolve("profile/project.fluxtion-settings");
            telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(profile,
                    new telamin.fluxtion.audit.analyser.analyser.config.AppConfig(),
                    new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
            Files.createDirectories(payload.resolve("graph"));
            Files.createDirectories(payload.resolve("log"));
            Files.copy(Path.of("src/test/resources/topology/demo-quote-processor.graphml"),
                    payload.resolve("graph/demo.graphml"));
            Files.copy(Path.of("src/test/resources/topology/demo-quote-audit.yaml"),
                    payload.resolve("log/demo.yaml"));
            Path bundle = temporary.resolve("verified.fexp");
            telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.pack(payload, bundle,
                    Instant.parse("2026-01-01T00:00:00Z"), "test");
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            javax.swing.Timer dismiss = new javax.swing.Timer(100, event -> {
                for (java.awt.Window window : java.awt.Window.getWindows()) {
                    if (window instanceof javax.swing.JDialog dialog && dialog.isShowing()
                            && dialog.getTitle().equals("Experiment verified")) dialog.dispose();
                }
            });
            dismiss.start();
            try {
                assertTrue(drop((StartPanel) field(frame.get(), "startPanel"), bundle.toFile()));
                for (int i = 0; i < 300 && onEdt(() -> field(frame.get(), "store") == null); i++) Thread.sleep(20);
                assertNotNull(onEdt(() -> field(frame.get(), "store")), "the verified bundle's log is read");
                assertTrue(onEdt(() -> ((TopologyPanel) field(frame.get(), "topologyPanel")).hasGraph()));
                var project = (telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(frame.get(), "project");
                assertTrue(project.hasProject());
                assertTrue(project.activeFile().startsWith(Path.of(System.getProperty("user.home"),
                        ".fluxtion-analyser", "bundles")), "the bundle opens in an isolated working copy");
            } finally { dismiss.stop(); }
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    private static javax.swing.JButton buttonContaining(java.awt.Container root, String text) {
        for (var component : root.getComponents()) {
            if (component instanceof javax.swing.JButton button && (
                    button.getText() != null && button.getText().contains(text)
                    || button.getAccessibleContext().getAccessibleName() != null
                    && button.getAccessibleContext().getAccessibleName().contains(text))) return button;
            if (component instanceof java.awt.Container child) {
                var found = buttonContaining(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean drop(JComponent target, File file) throws Exception {
        AtomicReference<Boolean> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Transferable files = new Transferable() {
                public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
                public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.javaFileListFlavor.equals(flavor); }
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                    return List.of(file);
                }
            };
            result.set(target.getTransferHandler().importData(new TransferHandler.TransferSupport(target, files)));
        });
        return result.get();
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(work.call()); } catch (Throwable e) { error.set(e); }
        });
        if (error.get() != null) throw new AssertionError(error.get());
        return result.get();
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
}
