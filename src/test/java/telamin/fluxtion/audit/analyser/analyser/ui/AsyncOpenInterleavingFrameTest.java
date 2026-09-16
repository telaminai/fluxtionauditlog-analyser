package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.spi.AuditLogReader;
import telamin.fluxtion.audit.analyser.analyser.spi.ReaderRegistry;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The finish-first review's three M44.3 blockers, as display cases. A latch-controlled reader makes the
 * interleavings deterministic (the reviewer's method): the load ENTERS, the test does something while it is
 * pending, then RELEASES it. Skipped where there is no display; the CI ui-frame job runs it on one.
 */
class AsyncOpenInterleavingFrameTest {

    // ---- fixtures ----------------------------------------------------------------------------------

    static String log(String node) {
        return "---\n#00:00:01.000 [t] INFO L\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n"
                + "    - " + node + ": { v: 1}\n---\n";
    }

    static String graph(String node) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" "
                + "xmlns:jGraph=\"http://www.jgraph.com/\">\n"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>\n"
                + "<graph edgedefault=\"undirected\">\n<node id=\"" + node + "\"><data key=\"vertex_label\">"
                + "<jGraph:ShapeNode><jGraph:Geometry height=\"70\" width=\"160\" x=\"20\" y=\"20\"/>"
                + "<jGraph:label text=\"id:" + node + "&#10;class:com.acme." + node + "\"/>"
                + "<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>\n</graph></graphml>\n";
    }

    /** A reader that blocks until released, then yields one record (or fails). Format {@code test-slow}. */
    static final class DelayedReader implements AuditLogReader {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final boolean fail;
        final String node;
        DelayedReader(boolean fail, String node) { this.fail = fail; this.node = node; }
        @Override public String formatId() { return "test-slow"; }
        @Override public String displayName() { return "test slow reader"; }
        @Override public boolean canOpen(Path source) { return source.toString().endsWith(".slow"); }
        @Override public TimeBase timeBase() { return TimeBase.wallClockMillisUtc(); }
        @Override public Capabilities capabilities() { return new Capabilities(false, false, true); }
        @Override public void read(Path source, Consumer<String> out) throws java.io.IOException {
            entered.countDown();
            try {
                if (!release.await(20, TimeUnit.SECONDS)) throw new java.io.IOException("test timeout");
            } catch (InterruptedException e) { throw new java.io.IOException(e); }
            if (fail) throw new java.io.IOException("delayed failure");
            out.accept(log(node));
        }
        void awaitEntered() throws InterruptedException { assertTrue(entered.await(20, TimeUnit.SECONDS), "the load never started"); }
    }

    /** A real frame under an isolated home, with a delayed reader registered through the frame's own registry. */
    static final class Frame implements AutoCloseable {
        final MainFrame frame;
        final ActionExecutor ex;
        final DialogWatchdog dialogs = new DialogWatchdog();
        private final String home;
        Frame(Path tmp, DelayedReader... readers) throws Exception {
            home = System.getProperty("user.home");
            System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
            AtomicReference<MainFrame> ref = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> {
                MainFrame f = new MainFrame();
                for (DelayedReader r : readers) registry(f).register(r);
                ref.set(f);
            });
            frame = ref.get();
            ex = (ActionExecutor) field(frame, "actionExecutor");
        }
        @Override public void close() throws Exception {
            dialogs.stop();
            System.setProperty("user.home", home);
            SwingUtilities.invokeAndWait(frame::dispose);
        }
        String status() throws Exception { return ((javax.swing.JLabel) field(frame, "status")).getText(); }
        String processorLog() throws Exception {
            Object session = field(frame, "session");
            return session == null ? null
                    : ((telamin.fluxtion.audit.analyser.analyser.session.SessionDriver) session).processor().openLog.logPath();
        }
    }

    // ---- review B1 ---------------------------------------------------------------------------------

    @Test
    void b1_aHumanRecentGraphmlDuringAPendingSocketLoad_doesNotMakeTheArrivalModal(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path graphB = Files.writeString(tmp.resolve("b.graphml"), graph("nodeB"));
        Path slowA = Files.writeString(tmp.resolve("a.slow"), "slow");
        DelayedReader reader = new DelayedReader(false, "nodeA");
        try (Frame f = new Frame(tmp, reader)) {
            onEdt(() -> render(f.ex, "open", Map.of("graphml", graphB.toString())));
            onEdt(() -> render(f.ex, "open", Map.of("log", slowA.toString(), "format", "test-slow")));   // socket
            reader.awaitEntered();
            onEdt(() -> clickRecentGraphml(f.frame, graphB));                                            // a PERSON, mid-load
            reader.release.countDown();
            awaitLoaded(f.ex);
            assertEquals(0, f.dialogs.seen(), "the arrival is the socket's operation; its audience is not the person's");
            onEdt(() -> assertNull(pairing(f.ex).get("graph"), "the mismatching graph was closed by the arrival"));
        }
    }

    @Test
    void b1_control_aHumanArrivalStillWarnsAsADialog(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path graphB = Files.writeString(tmp.resolve("b.graphml"), graph("nodeB"));
        Path slowA = Files.writeString(tmp.resolve("a.slow"), "slow");
        DelayedReader reader = new DelayedReader(false, "nodeA");
        try (Frame f = new Frame(tmp, reader)) {
            onEdt(() -> f.frame.openFile(slowA, OpenRequest.HUMAN));                                     // a person
            reader.awaitEntered();
            onEdt(() -> render(f.ex, "open", Map.of("graphml", graphB.toString())));                    // socket, mid-load
            reader.release.countDown();
            awaitLoaded(f.ex);
            assertEquals(1, f.dialogs.seen(), "a person's arrival that closes a mismatching graph warns as a dialog");
        }
    }

    // ---- review B2 ---------------------------------------------------------------------------------

    private static Path project(Path tmp, String name) throws Exception {
        Path settings = tmp.resolve(name).resolve(".analyser").resolve("project.fluxtion-settings");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "sourceRoot.0=" + tmp.resolve(name).resolve("src") + "\n");
        return settings;
    }

    @Test
    void b2_aProjectSwitchDuringAPendingLoad_retiresIt(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path slowA = Files.writeString(tmp.resolve("a.slow"), "slow");
        Path settings = project(tmp, "P");
        DelayedReader reader = new DelayedReader(false, "nodeA");
        try (Frame f = new Frame(tmp, reader)) {
            onEdt(() -> render(f.ex, "open", Map.of("log", slowA.toString(), "format", "test-slow")));
            reader.awaitEntered();
            onEdt(() -> render(f.ex, "open", Map.of("project", settings.toString())));
            reader.release.countDown();
            awaitStatusStartsWith(f, "Discarded ");
            onEdt(() -> {
                Map<String, Object> ctx = render(f.ex, "context", Map.of());
                assertNull(find(ctx, "inFlight"), "nothing is outstanding after the switch: " + find(ctx, "inFlight"));
                assertFalse(pairing(f.ex).containsKey("loading"), "and the load is not reported as in flight");
            });
            assertNull(f.processorLog(), "the superseded load opened nothing");
        }
    }

    @Test
    void b2_control_reopeningTheActiveProjectIsANoOp_theLoadSurvives(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path slowA = Files.writeString(tmp.resolve("a.slow"), "slow");
        Path settings = project(tmp, "P");
        DelayedReader reader = new DelayedReader(false, "nodeA");
        try (Frame f = new Frame(tmp, reader)) {
            onEdt(() -> render(f.ex, "open", Map.of("project", settings.toString())));
            onEdt(() -> render(f.ex, "open", Map.of("log", slowA.toString(), "format", "test-slow")));
            reader.awaitEntered();
            onEdt(() -> render(f.ex, "open", Map.of("project", settings.toString())));                  // same project
            reader.release.countDown();
            awaitLoaded(f.ex);
            assertEquals(slowA.toString(), f.processorLog(), "a no-op project request does not supersede the load");
            onEdt(() -> assertNull(find(render(f.ex, "context", Map.of()), "inFlight")));
        }
    }

    @Test
    void b2_control_aBadProjectPathIsRefusedBeforeTheGate_theLoadSurvives(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path slowA = Files.writeString(tmp.resolve("a.slow"), "slow");
        DelayedReader reader = new DelayedReader(false, "nodeA");
        try (Frame f = new Frame(tmp, reader)) {
            onEdt(() -> render(f.ex, "open", Map.of("log", slowA.toString(), "format", "test-slow")));
            reader.awaitEntered();
            onEdt(() -> assertFalse(f.ex.render("open", new java.util.LinkedHashMap<>(Map.of("project",
                    tmp.resolve("nowhere/.analyser/project.fluxtion-settings").toString()))).ok(), "a bad path is an error"));
            reader.release.countDown();
            awaitLoaded(f.ex);
            assertEquals(slowA.toString(), f.processorLog());
            onEdt(() -> assertNull(find(render(f.ex, "context", Map.of()), "inFlight")));
        }
    }

    // ---- review B3 ---------------------------------------------------------------------------------

    @Test
    void b3_aSupersededFailure_isRecordedButNeverShown(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path slowA = Files.writeString(tmp.resolve("a.slow"), "slow");
        Path logB = Files.writeString(tmp.resolve("b.yaml"), log("nodeB"));
        DelayedReader failing = new DelayedReader(true, "nodeA");
        try (Frame f = new Frame(tmp, failing)) {
            onEdt(() -> f.frame.openFile(slowA, OpenRequest.HUMAN));                                     // a person, will fail
            failing.awaitEntered();
            onEdt(() -> render(f.ex, "open", Map.of("log", logB.toString())));                          // newer, succeeds
            awaitLoaded(f.ex);
            String statusAfterB = f.status();
            failing.release.countDown();                                                                // A's failure lands late
            Thread.sleep(400);
            onEdt(() -> { });
            assertEquals(0, f.dialogs.seen(), "a superseded failure is not the current operation's failure");
            assertEquals(statusAfterB, f.status(), "and it does not overwrite the newer operation's status");
            assertEquals(logB.toString(), f.processorLog());
            onEdt(() -> assertNull(find(render(f.ex, "context", Map.of()), "inFlight")));
        }
    }

    @Test
    void b3_control_anAcceptedHumanFailureStillWarns(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path slowA = Files.writeString(tmp.resolve("a.slow"), "slow");
        DelayedReader failing = new DelayedReader(true, "nodeA");
        try (Frame f = new Frame(tmp, failing)) {
            onEdt(() -> f.frame.openFile(slowA, OpenRequest.HUMAN));
            failing.awaitEntered();
            failing.release.countDown();
            awaitStatusStartsWith(f, "Failed to load ");
            assertEquals(1, f.dialogs.seen(), "the person who asked is told");
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    static ReaderRegistry registry(MainFrame f) { return (ReaderRegistry) field(f, "readerRegistry"); }

    static Object field(Object o, String name) {
        try {
            var fld = o.getClass().getDeclaredField(name);
            fld.setAccessible(true);
            return fld.get(o);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    static Map<String, Object> render(ActionExecutor ex, String verb, Map<String, Object> params) {
        ActionResult r = ex.render(verb, new java.util.LinkedHashMap<>(params));
        assertTrue(r.ok(), verb + " " + params + " → " + r.toMap());
        return r.toMap();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> pairing(ActionExecutor ex) {
        return (Map<String, Object>) find(render(ex, "context", Map.of()), "graphPairing");
    }

    static Object find(Object node, String key) {
        if (node instanceof Map<?, ?> m) {
            if (m.containsKey(key)) return m.get(key);
            for (Object v : m.values()) { Object hit = find(v, key); if (hit != null) return hit; }
        } else if (node instanceof List<?> l) {
            for (Object v : l) { Object hit = find(v, key); if (hit != null) return hit; }
        }
        return null;
    }

    static void awaitLoaded(ActionExecutor ex) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        AtomicReference<Map<String, Object>> last = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline) {
            onEdt(() -> last.set(pairing(ex)));
            if (last.get() != null && !last.get().containsKey("loading")) return;
            Thread.sleep(50);
        }
        fail("load still in flight after 20s: " + last.get());
    }

    static void awaitStatusStartsWith(Frame f, String prefix) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            String s = f.status();
            if (s != null && s.startsWith(prefix)) return;
            Thread.sleep(50);
        }
        fail("status never started with '" + prefix + "'; last: " + f.status());
    }

    static void clickRecentGraphml(MainFrame f, Path graph) {
        javax.swing.JMenu menu = (javax.swing.JMenu) field(f, "recentGraphmlMenu");
        for (int i = 0; i < menu.getItemCount(); i++) {
            javax.swing.JMenuItem item = menu.getItem(i);
            if (item != null && graph.toString().equals(item.getText())) { item.doClick(); return; }
        }
        fail("no Recent GraphML item for " + graph);
    }

    static void onEdt(Runnable r) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { r.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() instanceof AssertionError e) throw e;
        if (failure.get() != null) throw new RuntimeException(failure.get());
    }

    /** Counts distinct dialogs and disposes them, so a modal fails a test instead of hanging it. */
    static final class DialogWatchdog {
        private final java.util.Set<java.awt.Window> seen =
                java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));
        private volatile boolean running = true;
        DialogWatchdog() {
            Thread t = new Thread(() -> {
                while (running) {
                    for (java.awt.Window w : java.awt.Window.getWindows()) {
                        if (w instanceof javax.swing.JDialog d && d.isShowing() && seen.add(d)) SwingUtilities.invokeLater(d::dispose);
                    }
                    try { Thread.sleep(25); } catch (InterruptedException e) { return; }
                }
            }, "dialog-watchdog");
            t.setDaemon(true); t.start();
        }
        int seen() { return seen.size(); }
        void stop() { running = false; }
    }
}
