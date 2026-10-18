package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;

import javax.swing.JSplitPane;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/**
 * Owner hands-on test (2026-10-18b, the v2 integration jar): in the sample project the hosted assistant's Java source
 * spotlights were refused — "Java source anchor is no longer visible in its bound document" — whenever the Topology tab
 * was showing, so the code was never lit beside the graph. The source root was in force all along; the embedded source
 * pane had no area. Two layouts, both replayed here through the action socket on the REAL sample (its own profile,
 * graph and log, opened by the start page's entrance):
 * <ul>
 *   <li>a wide side column: the embedded viewer opens in Split, and the half a spotlight lands in had height 0
 *       ({@link SourcePanel#giveSplitPaneRoom});</li>
 *   <li>the default narrow column: the graph/source divider sat past the right edge, so the pane had width 0
 *       ({@link TopologyPanel#sourceDivider}).</li>
 * </ul>
 */
class SampleProjectSpotlightFrameTest {

    static void openSample(MainFrame frame) throws Exception {
        onEdt(() -> {
            try {
                var m = MainFrame.class.getDeclaredMethod("openSampleProject");
                m.setAccessible(true);
                m.invoke(frame);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        StartPromptFrameTest.awaitDemo(frame);
        onEdt(() -> { });
    }

    /** The side column (assistant, source, topology) widened to the owner's layout: the inner split's divider moved left. */
    static void widenSideColumn(MainFrame frame, int divider) throws Exception {
        onEdt(() -> {
            Component c = (Component) field(frame, "sideTabs");
            while (c != null && !(c instanceof JSplitPane)) c = c.getParent();
            assertNotNull(c, "the side tabs sit in a split");
            ((JSplitPane) c).setDividerLocation(divider);
            frame.validate();
        });
        onEdt(() -> { });
    }

    static Map<String, Object> t(String target, String caption) {
        return Map.of("target", target, "caption", caption);
    }

    static Map<String, Object> lit(ActionResult r, String target) {
        @SuppressWarnings("unchecked")
        var all = (List<Map<String, Object>>) r.payload().get("lit");
        return all.stream().filter(e -> target.equals(e.get("target"))).findFirst()
                .orElseThrow(() -> new AssertionError(r.toMap().toString()));
    }

    static void assertLitOnScreen(ActionResult r, String target) {
        assertTrue(r.ok(), r.toMap().toString());
        @SuppressWarnings("unchecked")
        var bounds = (Map<String, Object>) lit(r, target).get("bounds");
        assertNotNull(bounds, target + " has bounds: " + r.toMap());
        assertTrue(((Number) bounds.get("width")).intValue() > 30 && ((Number) bounds.get("height")).intValue() > 0,
                target + " is on screen: " + bounds);
    }

    /**
     * ★ The owner's exact sequence: {@code topology {select riskMonitor, source: true}}, {@code goto 6}, then the node,
     * its Java line and the two records in ONE spotlight — and the generated processor's dispatch line beside the graph.
     * Witness: make {@code giveSplitPaneRoom} return false — the node half keeps height 0 and the set is refused.
     */
    @Test
    @DisplayName("★ sample, wide side column: the transcript's node + Java line + records spotlight lights in Topology")
    void theTranscriptsSourceSpotlightLightsBesideTheGraph(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            openSample(f.frame);
            widenSideColumn(f.frame, 150);
            assertTrue(f.ex.render("topology", Map.of("select", "riskMonitor", "scope", "neighbours", "source", true)).ok());
            assertTrue(f.ex.render("goto", Map.of("recordIndex", 6)).ok());
            String line = "source:java:com.acme.demo.node.Nodes:line:103";
            ActionResult set = f.ex.render("spotlight", Map.of("targets", List.of(
                    t("topology:node:riskMonitor", "riskMonitor checks the limit"),
                    t(line, "the breach re-dispatches here"),
                    t("records:row:6", "the original cycle"),
                    t("records:row:7", "the queued event's own cycle"))));
            assertLitOnScreen(set, line);
            assertEquals("topology-source", lit(set, line).get("destination"));
            assertLitOnScreen(set, "topology:node:riskMonitor");

            // a second call seconds later, as an assistant's is: the first call's layout has settled
            onEdt(() -> { }); onEdt(() -> { });
            String dispatch = "source:java:" + DemoAssets.PROCESSOR + ":line:337";
            ActionResult generated = f.ex.render("spotlight", Map.of("targets", List.of(
                    t("topology:node:orderTracker", "the handler"), t(dispatch, "the generated dispatch calls it"))));
            assertLitOnScreen(generated, dispatch);
            assertEquals("topology-source", lit(generated, dispatch).get("destination"));
        }
    }

    /**
     * ★ The default (narrow) side column: a Java line lit while the Topology tab shows. Witness: restore the unclamped
     * {@code Math.max(240, getWidth() / 2)} divider — the source pane is 0 px wide and the spotlight is refused.
     */
    @Test
    @DisplayName("★ sample, default side column: a Java spotlight in the Topology tab gets a pane with width")
    void aJavaSpotlightInTheNarrowTopologyTabIsVisible(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            openSample(f.frame);
            assertTrue(f.ex.render("spotlight", Map.of("target", "tab:topology")).ok());
            onEdt(() -> { });
            for (String target : List.of("source:java:com.acme.demo.node.Nodes", "source:java:com.acme.demo.node.Nodes:line:103")) {
                ActionResult r = f.ex.render("spotlight", Map.of("targets", List.of(
                        t("topology:node:riskMonitor", "the node"), t(target, "its code"))));
                assertTrue(r.ok(), r.toMap().toString());
                assertEquals("topology-source", lit(r, target).get("destination"));
                assertNotNull(lit(r, target).get("bounds"), r.toMap().toString());
            }
        }
    }

    /**
     * ★ The sample's GraphML is spotlightable: its nodes, the pairing verdict and the project's graph row, after the
     * start page's open loaded it. Witness: drop {@code topologyPanel.load(DemoAssets.graphml())} from
     * {@code openSampleProject} — no node resolves.
     */
    @Test
    @DisplayName("★ sample: the GraphML's nodes, verdict and project row light")
    void theSamplesGraphIsSpotlightable(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            openSample(f.frame);
            ActionResult graph = f.ex.render("spotlight", Map.of("targets", List.of(
                    t("topology:node:priceListener", "the handler"), t("topology:node:quotePublisher", "downstream"),
                    t("topology:verdict", "how the graph fits the log"))));
            assertTrue(graph.ok(), graph.toMap().toString());
            assertNotNull(lit(graph, "topology:node:priceListener").get("bounds"));
            ActionResult row = f.ex.render("spotlight", Map.of("target", "project:graph", "caption", "the GraphML in force"));
            assertTrue(row.ok(), row.toMap().toString());
        }
    }
    /** A topology-viewer pane read that blocks until released — the read a {@code goto} starts, landing late. */
    static final class HeldReads implements AutoCloseable {
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicInteger held = new java.util.concurrent.atomic.AtomicInteger();
        HeldReads(MainFrame frame) throws Exception {
            onEdt(() -> {
                SourcePanel viewer = topology(frame).sourceViewer();
                var real = viewer.reader;
                viewer.reader = (lookup, fqn) -> {
                    held.incrementAndGet();
                    try { release.await(30, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return real.apply(lookup, fqn);
                };
            });
        }
        @Override public void close() { release.countDown(); }
    }

    static TopologyPanel topology(MainFrame frame) { return (TopologyPanel) field(frame, "topologyPanel"); }

    static long spotlightTicket(MainFrame frame) throws Exception {
        long[] t = new long[1];
        onEdt(() -> t[0] = (long) field(frame, "javaSpotlightTicket"));
        return t[0];
    }

    /** Waits for {@code done} or the deadline, polling on the EDT; the condition, not the time, ends the wait. */
    static void await(java.util.function.BooleanSupplier done, String what) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        boolean[] ok = new boolean[1];
        while (true) {
            onEdt(() -> ok[0] = done.getAsBoolean());
            if (ok[0]) return;
            if (System.nanoTime() > end) throw new AssertionError("timed out waiting for " + what);
            Thread.onSpinWait();
        }
    }

    static java.util.concurrent.CompletableFuture<ActionResult> spotlightAsync(Frame f, List<Map<String, Object>> targets) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> f.ex.render("spotlight", Map.of("targets", targets)));
    }

    /**
     * ★ The flake's cause, made deterministic: {@code goto} starts a read of the generated processor in the Topology
     * viewer; a Java spotlight asked while that read is still outstanding must be applied AFTER it lands — not
     * captured before it and then bounced "superseded; retry" (or knocked out) when the read installs and scrolls.
     * Witness: make {@code MainFrame.whenSourceReadsSettle} run its argument at once — the spotlight answers while
     * the read is held, and the read's landing moves the view the spotlight was bound to.
     */
    @Test
    @DisplayName("★ a Java spotlight asked while goto's source read is outstanding applies after it lands")
    void aJavaSpotlightWaitsForTheSourceReadAPreviousVerbStarted(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            openSample(f.frame);
            widenSideColumn(f.frame, 150);
            assertTrue(f.ex.render("topology", Map.of("select", "riskMonitor", "scope", "neighbours", "source", true)).ok());
            onEdt(() -> { });
            await(() -> !topology(f.frame).sourceViewer().reading(), "the select's own reads");
            SourcePanel viewer = topology(f.frame).sourceViewer();
            try (var held = new HeldReads(f.frame)) {
                assertTrue(f.ex.render("goto", Map.of("recordIndex", 6)).ok());
                await(() -> held.held.get() > 0, "goto's processor read to start");
                long before = spotlightTicket(f.frame);
                String line = "source:java:com.acme.demo.node.Nodes:line:103";
                var answer = spotlightAsync(f, List.of(t("topology:node:riskMonitor", "the node"), t(line, "its code")));
                await(() -> answer.isDone() || (long) field(f.frame, "javaSpotlightTicket") > before, "the spotlight to be asked");
                await(() -> answer.isDone() || viewer.reading(), "the spotlight to be asked");
                held.release.countDown();
                ActionResult r = answer.get(20, java.util.concurrent.TimeUnit.SECONDS);
                assertLitOnScreen(r, line);
                Object[] bound = new Object[1];
                onEdt(() -> bound[0] = viewer.spotlightViewState());
                await(() -> !viewer.reading(), "every source read to land");
                onEdt(() -> { }); onEdt(() -> { });
                Object[] settled = new Object[1];
                onEdt(() -> settled[0] = viewer.spotlightViewState());
                assertEquals(bound[0], settled[0], "nothing asked BEFORE the spotlight moved the view AFTER it answered");
            }
        }
    }

    /**
     * ★ The assistant's ordinary pace, ungated: {@code topology {select, source: true}}, {@code goto}, then at once a
     * Java spotlight, then at once a second — each answers lit, none "superseded".
     */
    @Test
    @DisplayName("★ select + goto then two Java spotlights back to back all light")
    void selectThenTwoJavaSpotlightsBackToBackAllLight(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            openSample(f.frame);
            widenSideColumn(f.frame, 150);
            assertTrue(f.ex.render("topology", Map.of("select", "riskMonitor", "scope", "neighbours", "source", true)).ok());
            String line = "source:java:com.acme.demo.node.Nodes:line:103";
            assertLitOnScreen(f.ex.render("spotlight", Map.of("targets", List.of(t(line, "first")))), line);
            assertTrue(f.ex.render("goto", Map.of("recordIndex", 6)).ok());
            assertLitOnScreen(f.ex.render("spotlight", Map.of("targets", List.of(t(line, "second")))), line);
            String dispatch = "source:java:" + DemoAssets.PROCESSOR + ":line:337";
            assertLitOnScreen(f.ex.render("spotlight", Map.of("targets", List.of(t(dispatch, "third")))), dispatch);
        }
    }

    /**
     * ★ A GENUINE supersede still says so: a spotlight waiting on an outstanding read is replaced by a newer one,
     * which lights; the first answers "superseded".
     */
    @Test
    @DisplayName("★ a newer spotlight still supersedes one that is waiting")
    void aNewerSpotlightStillSupersedesAWaitingOne(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (var f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            openSample(f.frame);
            widenSideColumn(f.frame, 150);
            assertTrue(f.ex.render("topology", Map.of("select", "riskMonitor", "scope", "neighbours", "source", true)).ok());
            onEdt(() -> { });
            await(() -> !topology(f.frame).sourceViewer().reading(), "the select's own reads");
            String line = "source:java:com.acme.demo.node.Nodes:line:103";
            String dispatch = "source:java:" + DemoAssets.PROCESSOR + ":line:337";
            try (var held = new HeldReads(f.frame)) {
                assertTrue(f.ex.render("goto", Map.of("recordIndex", 6)).ok());
                await(() -> held.held.get() > 0, "goto's processor read to start");
                long before = spotlightTicket(f.frame);
                var first = spotlightAsync(f, List.of(t(line, "first")));
                await(() -> (long) field(f.frame, "javaSpotlightTicket") > before, "the first spotlight to be asked");
                var second = spotlightAsync(f, List.of(t(dispatch, "second")));
                await(() -> (long) field(f.frame, "javaSpotlightTicket") > before + 1, "the second spotlight to be asked");
                held.release.countDown();
                ActionResult older = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
                assertFalse(older.ok(), older.toMap().toString());
                assertTrue(String.valueOf(older.toMap().get("error")).contains("superseded"), older.toMap().toString());
                assertLitOnScreen(second.get(20, java.util.concurrent.TimeUnit.SECONDS), dispatch);
            }
        }
    }
}
