package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JPanel;
import javax.swing.JButton;
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
import java.lang.reflect.Method;
import java.awt.Robot;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StartWorkspaceFrameTest {
    @TempDir Path temporary;

    /** Poll a native precondition briefly — the same helper shape TableDragCancellationFrameTest uses. */
    private static boolean until(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(20);
        }
        return condition.getAsBoolean();
    }

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

    @Test void startChoicesGroupByActivityAndStackOnANarrowWindow() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("workstreams-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            onEdt(() -> {
                MainFrame f = new MainFrame();
                f.setSize(1200, 800);
                f.setVisible(true);
                frame.set(f);
                return null;
            });
            new Robot().waitForIdle();
            onEdt(() -> {
                StartPanel start = (StartPanel) field(frame.get(), "startPanel");
                JComponent[] groups = workstreams(start);
                java.awt.Rectangle[] positions = groupPositions(start, groups);
                assertTrue(positions[0].x < positions[1].x, "Start and My work share the top row");
                assertEquals(positions[0].y, positions[1].y);
                assertEquals(positions[0].x, positions[2].x);
                assertEquals(positions[1].x, positions[3].x);
                assertEquals(positions[0].y + positions[0].height, positions[2].y,
                        "New work follows Start without an empty grid row");
                assertEquals(positions[1].y + positions[1].height, positions[3].y,
                        "Configuration follows My work without an empty grid row");
                assertNotNull(buttonContaining(groups[0], "Take a guided tour"));
                assertNotNull(buttonContaining(groups[1], "Open evidence bundle"));
                assertNotNull(buttonContaining(groups[1], "Open project"));
                assertNotNull(buttonContaining(groups[2], "Create project profile"));
                assertNotNull(buttonContaining(groups[3], "Source and assistant settings"));
                return null;
            });
            onEdt(() -> { frame.get().setSize(700, 650); return null; });
            new Robot().waitForIdle();
            onEdt(() -> {
                StartPanel start = (StartPanel) field(frame.get(), "startPanel");
                JComponent[] groups = workstreams(start);
                java.awt.Rectangle[] positions = groupPositions(start, groups);
                for (int i = 1; i < groups.length; i++) {
                    assertEquals(positions[0].x, positions[i].x, "narrow groups form one column");
                    assertTrue(positions[i].y >= positions[i - 1].y + positions[i - 1].height,
                            "narrow groups stack without overlap");
                }
                assertTrue(groups[0].getWidth() > 0 && groups[3].getWidth() > 0);
                return null;
            });
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    private static JComponent[] workstreams(StartPanel start) throws ReflectiveOperationException {
        JComponent[] groups = (JComponent[]) field(start, "workstreamSections");
        assertEquals(4, groups.length);
        assertEquals("Explore DEMO workstream", groups[0].getAccessibleContext().getAccessibleName());
        assertEquals("Open your work workstream", groups[1].getAccessibleContext().getAccessibleName());
        assertEquals("Create a project workstream", groups[2].getAccessibleContext().getAccessibleName());
        assertEquals("Assistant and settings workstream", groups[3].getAccessibleContext().getAccessibleName());
        return groups;
    }

    private static java.awt.Rectangle[] groupPositions(StartPanel start, JComponent[] groups)
            throws ReflectiveOperationException {
        JPanel grid = (JPanel) field(start, "workstreams");
        java.awt.Rectangle[] positions = new java.awt.Rectangle[groups.length];
        for (int i = 0; i < groups.length; i++) {
            Point at = SwingUtilities.convertPoint(groups[i].getParent(), groups[i].getLocation(), grid);
            positions[i] = new java.awt.Rectangle(at, groups[i].getSize());
        }
        return positions;
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
                    assertTrue(recent.getText().startsWith("DEMO-project"),
                            "the recent entry names the workspace rather than its hidden profile directory");
                    recent.doClick();
                    var project = (telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(frame.get(), "project");
                    assertTrue(project.hasProject(), "clicking a recent project enters its workspace");
                    assertTrue(((JPanel) field(frame.get(), "workspaceChrome")).isVisible());
                    assertTrue(frame.get().getJMenuBar().isVisible());
                    assertNull(field(frame.get(), "store"), "a project switch never invents a previous log");
                    invoke(frame.get(), "showStartPage", new Class<?>[]{});
                    assertTrue(((JPanel) field(frame.get(), "workspaceChrome")).isVisible() == false);
                    assertNotNull(buttonContaining(start, "Open project"));
                    assertNotNull(buttonContaining(start, "Open evidence bundle"));
                    javax.swing.JButton back = buttonContaining(start, "Return to workspace");
                    assertNotNull(back);
                    assertTrue(back.isVisible());
                    back.doClick();
                    assertTrue(((JPanel) field(frame.get(), "workspaceChrome")).isVisible());
                    assertTrue(project.hasProject());
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

    @Test void guidedTourChoiceSavesAndPlaysTheDemoSpotlightWalk() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("tour-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    frame.set(new MainFrame());
                    var tour = buttonContaining((StartPanel) field(frame.get(), "startPanel"), "Take a guided tour");
                    assertNotNull(tour);
                    frame.get().setSize(1400, 900);
                    frame.get().setVisible(true);
                    tour.doClick();
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            for (int i = 0; i < 300 && !onEdt(() -> {
                var running = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(frame.get(), "session");
                return running != null && running.snapshot().walkPlayback().showing()
                        && "SHOWN".equals(running.snapshot().walkPlayback().phase());
            }); i++) Thread.sleep(20);
            var session = (telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) field(frame.get(), "session");
            assertTrue(onEdt(() -> session.snapshot().walkPlayback().showing()),
                    "the start action plays the saved tour: " + onEdt(() ->
                            ((javax.swing.JLabel) field(frame.get(), "status")).getText()));
            assertEquals(DemoTour.NAME, onEdt(() -> session.snapshot().walkPlayback().walk()));
            assertEquals(0, onEdt(() -> session.snapshot().walkPlayback().step()));
            assertTrue(onEdt(() -> ((SpotlightOverlay) field(frame.get(), "spotlight")).lit().stream()
                    .anyMatch(lit -> lit.target().equals("records"))),
                    "the first step lights the records table, not only the walk strip");
            for (int step = 1; step < DemoTour.steps().size(); step++) {
                int next = step;
                assertNull(onEdt(() -> frame.get().playWalk(DemoTour.NAME, next, "test")));
                for (int i = 0; i < 200 && !onEdt(() -> session.snapshot().walkPlayback().step() == next
                        && "SHOWN".equals(session.snapshot().walkPlayback().phase())); i++) Thread.sleep(20);
                assertEquals(next, onEdt(() -> session.snapshot().walkPlayback().step()));
                String target = DemoTour.steps().get(next).targets().getFirst().target();
                assertTrue(onEdt(() -> ((SpotlightOverlay) field(frame.get(), "spotlight")).lit().stream()
                        .anyMatch(lit -> lit.target().equals(target))),
                        "tour step " + (next + 1) + " must light " + target);
            }
            var config = (telamin.fluxtion.audit.analyser.analyser.config.AppConfig) field(frame.get(), "config");
            assertEquals(1, config.walks.stream().filter(w -> w.name().equals(DemoTour.NAME)).count());
            assertEquals(4, config.walks.stream().filter(w -> w.name().equals(DemoTour.NAME))
                    .findFirst().orElseThrow().steps().size());
            assertTrue(onEdt(() -> ((JPanel) field(frame.get(), "workspaceChrome")).isVisible()));
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
            java.util.concurrent.atomic.AtomicBoolean sawIdentity = new java.util.concurrent.atomic.AtomicBoolean();
            javax.swing.Timer dismiss = new javax.swing.Timer(100, event -> {
                for (java.awt.Window window : java.awt.Window.getWindows()) {
                    if (window instanceof javax.swing.JDialog dialog && dialog.isShowing()
                            && dialog.getTitle().equals("Experiment verified")) {
                        sawIdentity.set(true);
                        dialog.dispose();
                    }
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
                for (int i = 0; i < 150 && !sawIdentity.get(); i++) Thread.sleep(20);
                assertTrue(sawIdentity.get(), "identity and working-copy limits must be shown after verification");
            } finally { dismiss.stop(); }
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void delayedBundleCompletionCannotReplaceANewerProject() throws Exception {
        assertDelayedBundleCannotReplaceNewerChoice(false);
    }

    @Test void delayedBundleCompletionCannotReplaceANewerGraph() throws Exception {
        assertDelayedBundleCannotReplaceNewerChoice(true);
    }

    private void assertDelayedBundleCannotReplaceNewerChoice(boolean chooseGraph) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("race-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            Path payload = Files.createDirectories(temporary.resolve("race-payload"));
            Path bundledProfile = payload.resolve("profile/project.fluxtion-settings");
            telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(bundledProfile,
                    new telamin.fluxtion.audit.analyser.analyser.config.AppConfig(),
                    new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
            Files.createDirectories(payload.resolve("log"));
            Files.copy(Path.of("src/test/resources/topology/demo-quote-audit.yaml"), payload.resolve("log/demo.yaml"));
            Path bundle = temporary.resolve("slow.fexp");
            telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.pack(payload, bundle,
                    Instant.parse("2026-01-01T00:00:00Z"), "DEMO");
            Path newer = telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.pathFor(
                    Files.createDirectories(temporary.resolve("newer-project")));
            telamin.fluxtion.audit.analyser.analyser.config.ProjectProfile.save(newer,
                    new telamin.fluxtion.audit.analyser.analyser.config.AppConfig(),
                    new telamin.fluxtion.audit.analyser.analyser.config.SettingsShare());
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            onEdt(() -> {
                frame.get().setSize(1000, 760);
                frame.get().setVisible(true);
                invoke(frame.get(), "loadExperiment", new Class<?>[]{Path.class}, bundle);
                var start = (StartPanel) field(frame.get(), "startPanel");
                var feedback = (javax.swing.JTextArea) field(start, "operationFeedback");
                assertTrue(feedback.isShowing() && feedback.getText().contains("Verifying"),
                        "verification progress belongs to the visible start page");
                Path copies = Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "bundles");
                long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
                while (System.nanoTime() < end) {
                    if (Files.isDirectory(copies)) {
                        try (var paths = Files.walk(copies)) {
                            if (paths.anyMatch(p -> p.endsWith("project.fluxtion-settings"))) break;
                        }
                    }
                    Thread.sleep(10);
                }
                if (chooseGraph) {
                    var topology = (TopologyPanel) field(frame.get(), "topologyPanel");
                    topology.load(Path.of("src/test/resources/topology/demo-quote-processor.graphml"));
                    assertTrue(topology.hasGraph(), "control: the newer graph opened before the old result arrived");
                } else {
                    invoke(frame.get(), "requestProject", new Class<?>[]{Path.class,
                            telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.class, String.class},
                            newer, telamin.fluxtion.audit.analyser.analyser.session.TransitionKind.EXPLICIT_SWITCH,
                            "newer-choice");
                }
                return null;
            });
            for (int i = 0; i < 120 && onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver)
                    field(frame.get(), "session")).processor().operationGate.accepted()); i++) Thread.sleep(20);
            assertFalse(onEdt(() -> ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver)
                    field(frame.get(), "session")).processor().operationGate.accepted()),
                    "the old verification result must have reached the session and been refused");
            var project = (telamin.fluxtion.audit.analyser.analyser.config.ProjectSession) field(frame.get(), "project");
            assertEquals(chooseGraph ? null : newer, onEdt(project::activeFile), "the old bundle must not switch projects");
            assertNull(onEdt(() -> field(frame.get(), "store")), "the stale bundle log must not open");
            assertEquals(chooseGraph, onEdt(() -> ((TopologyPanel) field(frame.get(), "topologyPanel")).hasGraph()),
                    "a newer explicit graph must survive the old bundle completion");
            assertFalse(onEdt(() -> java.util.Arrays.stream(java.awt.Window.getWindows())
                    .anyMatch(w -> w instanceof javax.swing.JDialog dialog && dialog.isShowing()
                            && dialog.getTitle().toLowerCase(java.util.Locale.ROOT).contains("experiment"))), "no obsolete bundle dialog may appear");
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void nativeFileDropOnHeroTextOpensAuditLog() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("native-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        AtomicReference<javax.swing.JFrame> sourceFrame = new AtomicReference<>();
        AtomicReference<javax.swing.JLabel> source = new AtomicReference<>();
        try {
            File log = Path.of("src/test/resources/topology/demo-quote-audit.yaml").toAbsolutePath().toFile();
            onEdt(() -> {
                MainFrame f = new MainFrame();
                f.setBounds(20, 30, 800, 700);
                f.setVisible(true);
                frame.set(f);
                javax.swing.JFrame sf = new javax.swing.JFrame("DEMO drag source");
                javax.swing.JLabel label = new javax.swing.JLabel("Drag DEMO log", javax.swing.SwingConstants.CENTER);
                label.setTransferHandler(new TransferHandler() {
                    @Override public int getSourceActions(JComponent c) { return COPY; }
                    @Override protected Transferable createTransferable(JComponent c) {
                        return new Transferable() {
                            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
                            public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.javaFileListFlavor.equals(flavor); }
                            public Object getTransferData(DataFlavor flavor) { return List.of(log); }
                        };
                    }
                });
                label.addMouseMotionListener(new MouseAdapter() {
                    @Override public void mouseDragged(MouseEvent e) {
                        label.getTransferHandler().exportAsDrag(label, e, TransferHandler.COPY);
                    }
                });
                sf.add(label);
                sf.setBounds(850, 40, 230, 150);
                sf.setVisible(true);
                sourceFrame.set(sf);
                source.set(label);
                return null;
            });
            Robot robot = new Robot();
            robot.setAutoDelay(15);
            robot.waitForIdle();
            Point from = onEdt(() -> {
                Point p = source.get().getLocationOnScreen(); p.translate(80, 60); return p;
            });
            Point to = onEdt(() -> {
                var start = (StartPanel) field(frame.get(), "startPanel");
                javax.swing.JTextArea hero = findText(start, "Open evidence,");
                assertNotNull(hero);
                Point p = hero.getLocationOnScreen(); p.translate(35, 10); return p;
            });
            robot.mouseMove(from.x, from.y);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            // Same precondition TableDragCancellationFrameTest states: a desktop that does not deliver
            // native mouse events to the JVM (no Accessibility grant, or no window manager focus) makes
            // this untestable rather than failing. Without it this suite FAILS where its sibling SKIPS,
            // and the failure reads as a regression in code it never touched.
            assumeTrue(until(() -> sourceFrame.get() != null && sourceFrame.get().isFocused()),
                    "native mouse press must reach the drag source, not be dropped by the desktop");
            for (int i = 1; i <= 40; i++) robot.mouseMove(
                    from.x + (to.x - from.x) * i / 40, from.y + (to.y - from.y) * i / 40);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            for (int i = 0; i < 200 && onEdt(() -> field(frame.get(), "store") == null); i++) Thread.sleep(20);
            assertNotNull(onEdt(() -> field(frame.get(), "store")),
                    "a native file-list drop on the hero text must reach the start-page opener");
        } finally {
            if (sourceFrame.get() != null) SwingUtilities.invokeAndWait(sourceFrame.get()::dispose);
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void mixedDropRefusalIsVisibleOnTheStartPage() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(temporary.resolve("mixed-home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            Path bundle = Files.writeString(temporary.resolve("DEMO-invalid.fexp"), "invalid");
            File log = Path.of("src/test/resources/topology/demo-quote-audit.yaml").toAbsolutePath().toFile();
            onEdt(() -> {
                MainFrame f = new MainFrame();
                f.setSize(1000, 760);
                f.setVisible(true);
                frame.set(f);
                assertFalse((Boolean) invoke(f, "openDroppedFiles", new Class<?>[]{List.class},
                        List.of(bundle.toFile(), log)));
                StartPanel start = (StartPanel) field(f, "startPanel");
                javax.swing.JTextArea feedback = (javax.swing.JTextArea) field(start, "operationFeedback");
                assertTrue(feedback.isShowing(), "the refusal must be on the visible start page");
                assertTrue(feedback.getText().contains(".fexp"));
                assertNull(field(f, "store"));
                assertFalse(((telamin.fluxtion.audit.analyser.analyser.config.ProjectSession)
                        field(f, "project")).hasProject());
                return null;
            });
            new Robot().waitForIdle();
            assertTrue(onEdt(() -> {
                var start = (StartPanel) field(frame.get(), "startPanel");
                var feedback = (javax.swing.JTextArea) field(start, "operationFeedback");
                return feedback.getVisibleRect().width > 0 && feedback.getVisibleRect().height > 0;
            }), "the visible refusal must occupy readable space");
        } finally {
            if (frame.get() != null) SwingUtilities.invokeAndWait(frame.get()::dispose);
            System.setProperty("user.home", previousHome);
        }
    }

    @Test void narrowWalkSeriesAndFindingControlsRemainReachable() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display");
        AtomicReference<javax.swing.JFrame> window = new AtomicReference<>();
        try {
            onEdt(() -> {
                var walk = new telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec("DEMO walk", "",
                        "person", "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", null,
                        List.of(), DemoTour.steps(), java.util.Map.of());
                var walks = new WalksPanel(() -> List.of(walk),
                        ignored -> telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.ok("walk", "DEMO", java.util.Map.of()),
                        List::of);
                walks.refresh();
                var f = new javax.swing.JFrame("DEMO narrow pane");
                f.setContentPane(walks);
                f.setBounds(25, 30, 220, 600);
                f.setVisible(true);
                window.set(f);
                return null;
            });
            new Robot().waitForIdle();
            onEdt(() -> {
                WalksPanel walks = (WalksPanel) window.get().getContentPane();
                javax.swing.JButton more = buttonContaining(walks, "More");
                assertNotNull(more);
                assertTrue(more.getVisibleRect().width > 0 && more.getVisibleRect().height > 0,
                        "walk management must be visible in a 220-pixel pane");
                more.doClick();
                assertTrue(more.getComponentPopupMenu().isShowing(), "Rename and Restore must open");
                more.getComponentPopupMenu().setVisible(false);
                assertEquals(javax.swing.JSplitPane.VERTICAL_SPLIT,
                        ((javax.swing.JSplitPane) walks.getComponent(1)).getOrientation());
                return null;
            });
            onEdt(() -> {
                GraphPanel graph = new GraphPanel();
                graph.bind(new telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore(
                        telamin.fluxtion.audit.analyser.analyser.parse.Samples.sample()),
                        new telamin.fluxtion.audit.analyser.analyser.filter.FilterState());
                window.get().setContentPane(graph);
                window.get().setSize(330, 600);
                var toggle = (javax.swing.JToggleButton) field(graph, "editSeriesButton");
                toggle.doClick();
                return null;
            });
            new Robot().waitForIdle();
            onEdt(() -> {
                GraphPanel graph = (GraphPanel) window.get().getContentPane();
                var tabs = (javax.swing.JTabbedPane) field(graph, "seriesEditorTabs");
                tabs.setSelectedIndex(1);
                JButton add = buttonContaining(tabs, "Add");
                JButton pick = buttonContaining(tabs, "Pick");
                assertNotNull(add); assertNotNull(pick);
                assertTrue(add.getVisibleRect().width > 0 && add.getVisibleRect().height > 0,
                        "Add must not be clipped at 330 pixels");
                assertTrue(pick.getVisibleRect().width > 0 && pick.getVisibleRect().height > 0,
                        "Pick must not be clipped at 330 pixels");
                var resolver = (javax.swing.JComboBox<?>) field(graph, "resolveCombo");
                assertTrue(resolver.getVisibleRect().width > 0, "formula resolution must remain reachable");
                var key = (javax.swing.JComboBox<?>) field(graph, "keyCombo");
                assertTrue(key.getItemCount() > 0, "the DEMO log provides a key to add");
                add.doClick();
                assertEquals(1, ((List<?>) field(graph, "activeKeys")).size(), "Add creates the chosen series");
                return null;
            });
            onEdt(() -> {
                var finding = new telamin.fluxtion.audit.analyser.analyser.design.ProducerResult.Finding(
                        "DEMO_RULE_WITH_A_LONG_CODE", "WARNING", "A binding is missing",
                        "The handler cannot run.", "Add the DEMO binding.",
                        java.util.Map.of("kind", "UNKNOWN"), java.util.Map.of(), List.of());
                var result = new telamin.fluxtion.audit.analyser.analyser.design.ProducerResult(
                        "/tmp/DEMO/result.json", "validate", "", "", List.of(finding),
                        java.util.Map.of(), java.util.Map.of());
                var findings = new ProducerFindingsPanel();
                findings.render(result, null, null, null, "", ignored -> { });
                window.get().setContentPane(findings);
                window.get().setSize(220, 600);
                return null;
            });
            new Robot().waitForIdle();
            onEdt(() -> {
                var findings = (ProducerFindingsPanel) window.get().getContentPane();
                javax.swing.JTextArea code = findText(findings, "DEMO_RULE_WITH_A_LONG_CODE");
                javax.swing.JTextArea message = findText(findings, "A binding is missing");
                javax.swing.JTextArea reason = findText(findings, "The handler cannot run.");
                javax.swing.JTextArea fix = findText(findings, "Add the DEMO binding.");
                assertNotNull(code); assertNotNull(message);
                assertNotNull(reason); assertNotNull(fix);
                assertTrue(code.getVisibleRect().height > 0, "the finding code must be painted");
                var card = message.getParent();
                int readableWidth = card.getWidth() - card.getInsets().left - card.getInsets().right;
                assertTrue(message.getWidth() >= readableWidth - 12,
                        "the finding message should use the narrow card's width: "
                                + message.getWidth() + " of " + readableWidth);
                assertTrue(reason.getWidth() >= readableWidth - 12 && fix.getWidth() >= readableWidth - 12,
                        "the reason and fix should use the same reading width");
                return null;
            });
        } finally {
            if (window.get() != null) SwingUtilities.invokeAndWait(window.get()::dispose);
        }
    }

    private static javax.swing.JTextArea findText(java.awt.Container root, String prefix) {
        for (var c : root.getComponents()) {
            if (c instanceof javax.swing.JTextArea text && text.getText().startsWith(prefix)) return text;
            if (c instanceof java.awt.Container child) {
                var found = findText(child, prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
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
