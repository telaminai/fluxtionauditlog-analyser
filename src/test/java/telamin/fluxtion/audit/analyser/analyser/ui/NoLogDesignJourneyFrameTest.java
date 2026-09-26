package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateArchive;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/**
 * Edit-loop spec §I1: the first tour step needs no audit log. A Spring template installed through the analyser,
 * opened as a project, shows its authored XML design, its shipped GraphML topology and a node's Java beside that
 * topology — with no log loaded, no pairing claimed and no session restored. Driven through the real Project and
 * Sources menu items and their file choosers, not the assistant verbs. The archive is constructed to the
 * template's shape (branch fixture); the published download's own acceptance is separate.
 */
class NoLogDesignJourneyFrameTest {

    static final String PROCESSOR = "com.example.myapp.generated.MarketProcessor";
    /** The template's design directory as a profile source root — the default slice 8 adds to the Spring template. */
    static final String DESIGN_ROOT = "src/main/fluxtion/designer";

    static byte[] springTemplate(String... sourceRoots) throws Exception {
        StringBuilder profile = new StringBuilder("share.version=1\nsourceRoot.count=" + sourceRoots.length + "\n");
        for (int i = 0; i < sourceRoots.length; i++) profile.append("sourceRoot.").append(i).append('=').append(sourceRoots[i]).append('\n');
        profile.append("eventProcessorFqn.count=1\neventProcessorFqn.0=").append(PROCESSOR)
               .append("\nselectedEventProcessor=").append(PROCESSOR).append('\n');
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("spring-demo/.analyser/project.fluxtion-settings", profile.toString());
        entries.put("spring-demo/" + DESIGN_ROOT + "/application-context.xml",
                "<beans>\n  <bean id=\"riskCheck\" class=\"com.example.myapp.node.RiskCheck\"/>\n</beans>\n");
        entries.put("spring-demo/src/main/resources/MarketProcessor.graphml", graph("riskCheck"));
        entries.put("spring-demo/src/main/java/com/example/myapp/generated/MarketProcessor.java",
                "package com.example.myapp.generated;\nimport com.example.myapp.node.RiskCheck;\n"
                + "public class MarketProcessor {\n    private final RiskCheck riskCheck = new RiskCheck();\n}\n");
        entries.put("spring-demo/src/main/java/com/example/myapp/node/RiskCheck.java",
                "package com.example.myapp.node;\npublic class RiskCheck {\n    double notionalLimit = 1_000_000;\n}\n");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (var e : entries.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue().getBytes()); out.closeEntry(); }
        }
        return bytes.toByteArray();
    }

    @Test
    @SuppressWarnings("unchecked")
    void designTopologyAndJavaOpenWithNoLogAndClaimNoComparison(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        LookAndFeel previous = flatLafLikeTheApp();
        try (var f = new Frame(tmp)) {                                   // isolated user.home
            f.dialogs.stop();              // this test answers the real file choosers itself; the watchdog would cancel them
            Path project = new TemplateArchive().install(springTemplate("src/main/java", DESIGN_ROOT), tmp.resolve("my-project")).projectRoot();
            onEdt(() -> { f.frame.setSize(1500, 950); f.frame.setVisible(true); f.frame.validate(); });

            choose(f, "Project", "Open project…", project.resolve(".analyser/project.fluxtion-settings").toFile());
            awaitContext(f, c -> String.valueOf(c.get("project")).contains("my-project"), "project opened");

            choose(f, "Sources", "Open design…", project.resolve(DESIGN_ROOT + "/application-context.xml").toFile());
            var design = awaitContext(f, c -> c.get("design") instanceof Map<?, ?> d && d.get("file") != null, "design opened (read through the design root)");
            assertTrue(String.valueOf(((Map<String, Object>) design.get("design")).get("file")).endsWith("application-context.xml"));

            choose(f, "Sources", "Open GraphML…", project.resolve("src/main/resources/MarketProcessor.graphml").toFile());
            var withGraph = awaitContext(f, c -> c.get("graphPairing") instanceof Map<?, ?>, "graph opened");

            AtomicReference<SourcePanel> beside = new AtomicReference<>();
            onEdt(() -> {
                var topology = (TopologyPanel) field(f.frame, "topologyPanel");
                topology.ensureSourcePaneVisible();
                beside.set(topology.sourceViewer());
                beside.get().openInstance("riskCheck", null);
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            AtomicReference<String> node = new AtomicReference<>("");
            do { onEdt(() -> node.set(beside.get().nodePaneText())); Thread.sleep(20); }
            while (!node.get().contains("notionalLimit") && System.nanoTime() < deadline);
            assertTrue(node.get().contains("class RiskCheck"), "the node's Java beside the topology: " + node.get());

            var context = f.ex.render("context", Map.of()).payload();
            assertFalse(context.containsKey("log"), "no log was loaded: " + context.keySet());
            var pairing = (Map<String, Object>) withGraph.get("graphPairing");
            assertFalse(pairing.containsKey("verdict") || pairing.containsKey("appliesToOpenLog"),
                    "with no log the graph is not compared, so no pairing verdict is claimed: " + pairing);
            AtomicReference<String> visible = new AtomicReference<>();
            onEdt(() -> visible.set(topologyStatus((TopologyPanel) field(f.frame, "topologyPanel"))));
            assertTrue(visible.get().contains(MainFrame.NO_LOG_PAIRING_NOTE),
                    "the Topology tab says, where the graph is shown, that nothing was compared: " + visible.get());
            var restoration = (Map<String, Object>) context.get("restoration");
            assertNotEquals("finished", restoration == null ? null : restoration.get("state"), "no session was restored");
            assertNotEquals("restoring", restoration == null ? null : restoration.get("state"));
        } finally {
            // a failure must not leave a modal dialog blocking the EDT for the next test
            SwingUtilities.invokeLater(() -> { for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isShowing()) d.dispose(); });
            restoreLaf(previous);
        }
    }

    // ---- driving the real menu items --------------------------------------------------------------------------

    /** Click Menu ▸ Item (it must be present and enabled with no log) and answer its file chooser with {@code file}. */
    private static void choose(Frame f, String menu, String item, File file) throws Exception {
        AtomicReference<JMenuItem> found = new AtomicReference<>();
        onEdt(() -> {
            JMenuBar bar = f.frame.getJMenuBar();
            for (int i = 0; i < bar.getMenuCount(); i++) {
                JMenu m = bar.getMenu(i);
                if (m == null || !menu.equals(m.getText())) continue;
                for (int j = 0; j < m.getItemCount(); j++) {
                    JMenuItem it = m.getItem(j);
                    if (it != null && item.equals(it.getText())) found.set(it);
                }
            }
        });
        assertNotNull(found.get(), menu + " ▸ " + item + " is on the menu bar");
        assertTrue(found.get().isEnabled(), menu + " ▸ " + item + " is enabled with no log open");
        SwingUtilities.invokeLater(found.get()::doClick);               // the chooser is modal: do not wait on it
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        JFileChooser chooser = null;
        while (chooser == null && System.nanoTime() < deadline) {
            Thread.sleep(20);
            AtomicReference<JFileChooser> seen = new AtomicReference<>();
            onEdtLater(() -> { for (Window w : Window.getWindows()) if (w.isShowing() && w instanceof JDialog d) {
                JFileChooser c = find(d.getContentPane()); if (c != null) seen.set(c); } });
            chooser = seen.get();
        }
        assertNotNull(chooser, menu + " ▸ " + item + " opened a file chooser");
        JFileChooser c = chooser;
        onEdtLater(() -> { c.setSelectedFile(file); c.approveSelection(); });
    }

    /** Runs on the EDT without blocking behind a modal dialog's nested event loop. */
    private static void onEdtLater(Runnable r) throws Exception {
        var task = new java.util.concurrent.FutureTask<Void>(r, null);
        SwingUtilities.invokeLater(task);
        task.get(5, TimeUnit.SECONDS);
    }

    /** The Topology tab's status line — the persistent place its pairing qualification is shown. */
    private static String topologyStatus(TopologyPanel topology) {
        return ((JLabel) field(topology, "status")).getText();
    }

    private static JFileChooser find(Container c) {
        for (Component child : c.getComponents()) {
            if (child instanceof JFileChooser chooser) return chooser;
            if (child instanceof Container inner) { JFileChooser deeper = find(inner); if (deeper != null) return deeper; }
        }
        return null;
    }

    private static Map<String, Object> awaitContext(Frame f, Predicate<Map<String, Object>> done, String what) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        Map<String, Object> c;
        do { c = f.ex.render("context", Map.of()).payload(); if (done.test(c)) return c; Thread.sleep(25); }
        while (System.nanoTime() < deadline);
        fail("timed out waiting for " + what + ": " + c.keySet() + " project=" + c.get("project") + " status=" + f.status() + " windows=" + java.util.Arrays.stream(Window.getWindows()).filter(Window::isShowing).map(w -> w.getClass().getSimpleName() + (w instanceof JDialog d ? ":" + d.getTitle() : "")).toList());
        return c;
    }

    private static LookAndFeel flatLafLikeTheApp() throws Exception {
        LookAndFeel[] previous = new LookAndFeel[1];
        SwingUtilities.invokeAndWait(() -> {
            previous[0] = UIManager.getLookAndFeel();
            if (!(previous[0] instanceof com.formdev.flatlaf.FlatLaf)) {
                try { UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf()); } catch (Exception e) { throw new IllegalStateException(e); }
            }
        });
        return previous[0];
    }

    private static void restoreLaf(LookAndFeel previous) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { if (previous != null && UIManager.getLookAndFeel() != previous) UIManager.setLookAndFeel(previous); }
            catch (Exception e) { throw new IllegalStateException(e); }
        });
    }
}
