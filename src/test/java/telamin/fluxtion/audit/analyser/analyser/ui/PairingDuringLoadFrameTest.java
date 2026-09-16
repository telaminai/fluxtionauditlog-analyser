package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Review B1 / F3 on a REAL frame, driven through its own action executor with EDT sequencing: open log A and
 * graph A, then start log B and open graph B in one EDT turn (so B's load cannot have landed), read context in
 * that same turn, then wait for the load and read it again. Skipped where there is no display (CI is
 * headless): this is the reviewer's reproduction, pinned so it cannot regress silently on a developer machine.
 */
class PairingDuringLoadFrameTest {

    private static String log(String node) {
        return "---\n#00:00:01.000 [t] INFO L\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n"
                + "    - " + node + ": { v: 1}\n---\n";
    }

    private static String graph(String node) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\" "
                + "xmlns:jGraph=\"http://www.jgraph.com/\">\n"
                + "<key id=\"vertex_label\" for=\"node\" attr.name=\"nodeData\" attr.type=\"string\"/>\n"
                + "<graph edgedefault=\"undirected\">\n<node id=\"" + node + "\"><data key=\"vertex_label\">"
                + "<jGraph:ShapeNode><jGraph:Geometry height=\"70\" width=\"160\" x=\"20\" y=\"20\"/>"
                + "<jGraph:label text=\"id:" + node + "&#10;class:com.acme." + node + "\"/>"
                + "<jGraph:Style properties=\"NODE\"/></jGraph:ShapeNode></data></node>\n</graph></graphml>\n";
    }

    @Test
    @SuppressWarnings("unchecked")
    void aGraphOpenedWhileTheNextLogLoads_contextSaysPending_thenJudgesTheNewPair(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a display: a real MainFrame is constructed");
        Path logA = Files.writeString(tmp.resolve("a.yaml"), log("nodeA"));
        Path logB = Files.writeString(tmp.resolve("b.yaml"), log("nodeB"));
        Path graphA = Files.writeString(tmp.resolve("a.graphml"), graph("nodeA"));
        Path graphB = Files.writeString(tmp.resolve("b.graphml"), graph("nodeB"));

        String home = System.getProperty("user.home");
        System.setProperty("user.home", Files.createDirectories(tmp.resolve("home")).toString());
        AtomicReference<MainFrame> frame = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> frame.set(new MainFrame()));
            ActionExecutor ex = executorOf(frame.get());

            // A/A, in a fresh window with NO project opened (review F3: the session driver must not depend on one)
            onEdt(() -> render(ex, "open", Map.of("log", logA.toString())));
            awaitLoaded(ex);
            onEdt(() -> render(ex, "open", Map.of("graphml", graphA.toString())));
            Map<String, Object> pairAA = awaitVerdict(ex);
            assertEquals(Boolean.TRUE, pairAA.get("applies"), "A/A: " + pairAA);
            assertEquals(1, pairAA.get("declaredByGraph"));

            // start B, open graph B and read context — all in ONE EDT turn, so B's load cannot have landed
            AtomicReference<Map<String, Object>> during = new AtomicReference<>();
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> {
                render(ex, "open", Map.of("log", logB.toString()));
                echo.set(render(ex, "open", Map.of("graphml", graphB.toString())));
                during.set(pairing(ex));
            });
            Map<String, Object> g = (Map<String, Object>) ((Map<String, Object>) echo.get().get("opened")).get("graphml");
            assertEquals(ActionExecutor.PAIRING_PENDING, g.get("pairing"), "echo: " + g);
            Map<String, Object> d = during.get();
            assertTrue(String.valueOf(d.get("pairing")).startsWith("pending"), "context during the load: " + d);
            assertEquals(Boolean.TRUE, d.get("loading"));
            assertFalse(d.containsKey("applies"), "review B1: graph A's verdict must not be attached to graph B: " + d);
            assertFalse(d.containsKey("declaredByGraph"), d.toString());

            // and once B lands, the pair is B/B — judged, not left null (review F3)
            Map<String, Object> pairBB = awaitVerdict(ex);
            assertEquals(Boolean.TRUE, pairBB.get("applies"), "B/B: " + pairBB);
            assertEquals(1, pairBB.get("declaredByGraph"));
            assertEquals(1, pairBB.get("loggedNodes"));
            assertTrue(String.valueOf(pairBB.get("graphPath")).endsWith("b.graphml"), pairBB.toString());
        } finally {
            System.setProperty("user.home", home);
            if (frame.get() != null) SwingUtilities.invokeAndWait(() -> frame.get().dispose());
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static ActionExecutor executorOf(MainFrame f) throws Exception {
        var field = MainFrame.class.getDeclaredField("actionExecutor");
        field.setAccessible(true);
        return (ActionExecutor) field.get(f);
    }

    private static Map<String, Object> render(ActionExecutor ex, String verb, Map<String, Object> params) {
        ActionResult r = ex.render(verb, new java.util.LinkedHashMap<>(params));
        assertTrue(r.ok(), verb + " " + params + " → " + r.toMap());
        return r.toMap();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pairing(ActionExecutor ex) {
        Map<String, Object> ctx = render(ex, "context", Map.of());
        return (Map<String, Object>) find(ctx, "graphPairing");
    }

    private static Object find(Object node, String key) {
        if (node instanceof Map<?, ?> m) {
            if (m.containsKey(key)) return m.get(key);
            for (Object v : m.values()) { Object hit = find(v, key); if (hit != null) return hit; }
        } else if (node instanceof List<?> l) {
            for (Object v : l) { Object hit = find(v, key); if (hit != null) return hit; }
        }
        return null;
    }

    /** Poll context on the EDT until the pairing carries a verdict (the background load landed). */
    private static Map<String, Object> awaitVerdict(ActionExecutor ex) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        AtomicReference<Map<String, Object>> last = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline) {
            onEdt(() -> last.set(pairing(ex)));
            Map<String, Object> p = last.get();
            if (p != null && p.containsKey("applies")) return p;
            Thread.sleep(50);
        }
        fail("no pairing verdict within 20s; last context.graphPairing = " + last.get());
        return null;
    }

    /** Poll until no load is in flight (context.graphPairing carries no `loading`). */
    private static void awaitLoaded(ActionExecutor ex) throws Exception {
        long deadline = System.currentTimeMillis() + 20_000;
        AtomicReference<Map<String, Object>> last = new AtomicReference<>();
        while (System.currentTimeMillis() < deadline) {
            onEdt(() -> last.set(pairing(ex)));
            if (last.get() != null && !last.get().containsKey("loading")) return;
            Thread.sleep(50);
        }
        fail("load still in flight after 20s: " + last.get());
    }

    private static void onEdt(Runnable r) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { r.run(); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() instanceof AssertionError e) throw e;
        if (failure.get() != null) throw new RuntimeException(failure.get());
    }
}
