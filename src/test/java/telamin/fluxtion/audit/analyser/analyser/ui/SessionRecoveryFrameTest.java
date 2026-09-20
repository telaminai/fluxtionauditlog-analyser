package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.*;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Real project boundary and real asynchronous readers, never synthetic "opened" completions. */
class SessionRecoveryFrameTest {
    @TempDir Path tmp;
    private MainFrame frame;
    private ActionExecutor executor;
    private String originalHome;
    private Path profile, log, design, diagnostics, graph;

    @Test void closeReopenOffersThenRestoresLogDesignDiagnosticsAndGraph() throws Exception {
        start();
        try {
            openEvidence();
            act("topology", Map.of("select", "child", "scope", "node", "focus", true));
            act("open", Map.of("close", "project"));
            act("open", Map.of("project", profile.toString()));
            var offered = await(c -> "offered".equals(map(c.get("restoration")).get("state")));
            capture("recovery-offer", offered);
            assertFalse(offered.containsKey("log"), offered.toString());
            assertFalse(map(offered.get("design")).containsKey("file"), offered.toString());
            act("open", Map.of("restore", "last"));
            var restored = await(c -> "finished".equals(map(c.get("restoration")).get("state")));
            assertEquals(log.toRealPath().toString(), map(restored.get("log")).get("path"), restored.toString());
            assertEquals(design.toRealPath().toString(), map(restored.get("design")).get("file"), restored.toString());
            assertEquals(diagnostics.toRealPath().toString(), map(restored.get("design")).get("diagnosticsFile"), restored.toString());
            assertTrue(String.valueOf(map(restored.get("restoration")).get("message")).contains("topology opened"), restored.toString());
            assertEquals("explicit session restore", map(restored.get("log")).get("openedBy"));
            assertEquals(1, map(restored.get("topology")).get("contextDepth"), restored.toString());
            capture("recovery-finished", restored);
        } finally { stop(); }
    }

    @Test void changedLogIsRefusedWhileUnchangedDesignIsRestored() throws Exception {
        start();
        try {
            openEvidence();
            act("open", Map.of("close", "project"));
            // Opening and waiting serialises the outgoing snapshot before we mutate a file.
            act("open", Map.of("project", profile.toString()));
            await(c -> "offered".equals(map(c.get("restoration")).get("state")));
            Files.writeString(log, Files.readString(log) + "\n# changed bytes\n");
            act("open", Map.of("restore", "last"));
            var restored = await(c -> "finished".equals(map(c.get("restoration")).get("state")));
            assertFalse(restored.containsKey("log"), restored.toString());
            assertEquals(design.toRealPath().toString(), map(restored.get("design")).get("file"));
            assertEquals(diagnostics.toRealPath().toString(), map(restored.get("design")).get("diagnosticsFile"));
            assertTrue(String.valueOf(map(restored.get("restoration")).get("message")).contains("content changed"));
        } finally { stop(); }
    }

    @Test void fileEditedBeforeCloseCannotAcquireTheOldViewsIdentity() throws Exception {
        start();
        try {
            openEvidence();
            act("goto", Map.of("recordIndex", 5));
            Files.writeString(log, Files.readString(log) + "\n# changed before capture\n");
            act("open", Map.of("close", "project"));
            act("open", Map.of("project", profile.toString()));
            await(c -> "offered".equals(map(c.get("restoration")).get("state")));
            act("open", Map.of("restore", "last"));
            var restored = await(c -> "finished".equals(map(c.get("restoration")).get("state")));
            assertTrue(restored.containsKey("log"), "the explicitly requested current file can still open");
            assertTrue(String.valueOf(map(restored.get("restoration")).get("message")).contains("Saved view withheld"), restored.toString());
            assertEquals(List.of(), restored.get("selection"));
        } finally { stop(); }
    }

    @Test void aMissingMemberRefusesTheWholeRolledSet() throws Exception {
        start();
        try {
            Path a = Files.writeString(log.getParent().resolve("first.yml"), "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - child: {value: 1}\n---\n");
            Path b = Files.writeString(log.getParent().resolve("second.yml"), "---\neventLogRecord:\n  logTime: 2000\n  event: Tick\n  nodeLogs:\n    - child: {value: 2}\n---\n");
            act("open", Map.of("logs", List.of(b.toString(), a.toString())));
            await(c -> c.containsKey("log"));
            act("open", Map.of("design", design.toString()));
            act("open", Map.of("close", "project"));
            act("open", Map.of("project", profile.toString()));
            var offered = await(c -> "offered".equals(map(c.get("restoration")).get("state")));
            var inputs = (List<?>)map(offered.get("restoration")).get("inputs");
            assertEquals(List.of(a.toRealPath().toString(), b.toRealPath().toString()), inputs.stream()
                    .map(SessionRecoveryFrameTest::map).filter(i -> "log".equals(i.get("role"))).map(i -> i.get("path")).toList());
            Files.delete(b);
            act("open", Map.of("restore", "last"));
            var restored = await(c -> "finished".equals(map(c.get("restoration")).get("state")));
            assertFalse(restored.containsKey("log"), restored.toString());
            assertEquals(design.toRealPath().toString(), map(restored.get("design")).get("file"));
            assertTrue(String.valueOf(map(restored.get("restoration")).get("message")).contains("another member"));
        } finally { stop(); }
    }

    @Test void shutdownCaptureReappearsOnlyAsAnOfferAndCanBeDeclined() throws Exception {
        start();
        try {
            openEvidence();
            drainRecovery();
            disposeFrame();
            frame = edt(MainFrame::new);
            executor = field(frame, "actionExecutor", ActionExecutor.class);
            edt(() -> { frame.offerSessionRecovery(); return null; });
            var offered = await(c -> "offered".equals(map(c.get("restoration")).get("state")));
            assertFalse(offered.containsKey("log"));
            assertFalse(map(offered.get("design")).containsKey("file"));
            act("open", Map.of("restore", "dismiss"));
            var declined = context();
            assertEquals("dismissed", map(declined.get("restoration")).get("state"));
            assertFalse(declined.containsKey("log"));
            assertFalse(executor.render("open", Map.of("restore", "last")).ok());
        } finally { stop(); }
    }

    private void start() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "real display required");
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        Path project = Files.createDirectories(tmp.resolve("project"));
        AppConfig config = new AppConfig(); config.sourceRoots.add(project.toString());
        profile = ProjectProfile.pathFor(project);
        ProjectProfile.save(profile, config, new SettingsShare());
        log = Files.copy(Path.of("src/test/resources/sample.yml"), project.resolve("run.yml"));
        graph = Files.writeString(project.resolve("processor.graphml"),
                "<graphml xmlns='http://graphml.graphdrawing.org/xmlns' xmlns:jGraph='http://www.jgraph.com/'>"
                + "<key id='vertex_label' for='node' attr.name='nodeData' attr.type='string'/>"
                + "<graph edgedefault='undirected'><node id='child'><data key='vertex_label'>"
                + "<jGraph:ShapeNode><jGraph:Geometry height='70' width='160' x='20' y='20'/>"
                + "<jGraph:label text='id:child&#10;class:com.acme.Child'/><jGraph:Style properties='NODE'/>"
                + "</jGraph:ShapeNode></data></node></graph></graphml>");
        design = Files.writeString(project.resolve("design.xml"), "<beans><bean id='child' class='com.acme.Child'/></beans>");
        diagnostics = Files.writeString(project.resolve("fluxtion-validation.json"), "{\"contractVersion\":\"1.0\",\"valid\":true,\"diagnosticReport\":{\"diagnosticsVersion\":\"1.0\",\"diagnostics\":[]}}");
        frame = edt(MainFrame::new);
        executor = field(frame, "actionExecutor", ActionExecutor.class);
        act("open", Map.of("project", profile.toString()));
    }
    private void openEvidence() throws Exception {
        act("open", Map.of("log", log.toString(), "format", "yaml"));
        await(c -> c.containsKey("log") && !Boolean.TRUE.equals(map(c.get("graphPairing")).get("loading")));
        act("open", Map.of("graphml", graph.toString()));
        act("open", Map.of("design", design.toString()));
        act("open", Map.of("diagnostics", diagnostics.toString()));
    }
    /** Optional operator evidence from the real frame; disabled in normal tests. */
    private void capture(String name, Map<String,Object> context) throws Exception {
        String location = System.getProperty("journey.evidence");
        if (location == null) return;
        Path dir = Files.createDirectories(Path.of(location));
        edt(() -> { frame.setVisible(true); frame.validate(); return null; });
        edt(() -> {
            var image = new java.awt.image.BufferedImage(frame.getWidth(), frame.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try { frame.paint(graphics); } finally { graphics.dispose(); }
            javax.imageio.ImageIO.write(image, "png", dir.resolve(name + ".png").toFile());
            return null;
        });
        Files.writeString(dir.resolve(name + ".json"), telamin.fluxtion.audit.analyser.analyser.llm.Json.write(context) + "\n");
    }

    private void act(String verb, Map<String,Object> params) {
        var result = executor.render(verb, params); assertTrue(result.ok(), result.toString());
    }
    private Map<String,Object> context() { return executor.render("context", Map.of()).payload(); }
    private Map<String,Object> await(Predicate<Map<String,Object>> done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        Map<String,Object> c;
        do { c = context(); if (done.test(c)) return c; Thread.sleep(20); } while(System.nanoTime() < deadline);
        fail("Timed out: " + c); return c;
    }
    private void drainRecovery() throws Exception {
        CountDownLatch drained = new CountDownLatch(1);
        edt(() -> { field(frame,"recovery",SessionRecoveryController.class).closeThen(drained::countDown); return null; });
        assertTrue(drained.await(10,TimeUnit.SECONDS));
    }
    private void stop() throws Exception {
        if (frame != null) { drainRecovery(); disposeFrame(); }
        if (originalHome != null) System.setProperty("user.home", originalHome);
    }
    private void disposeFrame() throws Exception {
        edt(() -> {
            for (String name : List.of("designFollowTimer", "followTimer", "mcpIndicatorTimer", "projectSaveDebounce")) {
                var timer = field(frame,name,javax.swing.Timer.class); if(timer != null) timer.stop();
            }
            frame.dispose(); return null;
        });
    }
    private static Map<?,?> map(Object o) { return o instanceof Map<?,?> m ? m : Map.of(); }
    private static <T> T field(Object o, String name, Class<T> type) throws Exception {
        var f = o.getClass().getDeclaredField(name); f.setAccessible(true); return type.cast(f.get(o));
    }
    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get();
    }
}
