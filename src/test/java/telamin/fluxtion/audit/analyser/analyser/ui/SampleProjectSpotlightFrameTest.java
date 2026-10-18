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

    static ActionResult sampleSpotlight(Frame f, Map<String, Object> params) throws Exception {
        // topology {source:true} starts real asynchronous pane reads. Wait for those requested navigations,
        // not merely an empty EDT turn, before asserting that a separate spotlight keeps the same view.
        awaitSampleSources(f.frame);
        return f.ex.render("spotlight", params);
    }

    static void awaitSampleSources(MainFrame frame) throws Exception {
        long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < until) {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            onEdt(() -> ready.set(!((SourcePanel) field(frame, "sourcePanel")).reading()
                    && !((TopologyPanel) field(frame, "topologyPanel")).sourceViewer().reading()));
            if (ready.get()) { onEdt(frame::validate); onEdt(() -> { }); return; }
            Thread.sleep(5);
        }
        fail("the sample's requested source panes did not finish reading");
    }

    /** Queue a spotlight capture before an earlier pane read installs, and that install before the spotlight applies.
     * A single empty EDT turn cannot wait for background reads: the old fixture produces the reported supersede.
     * All reads are real; only their completion ordering is held. */
    @Test
    void theSampleJourneyWaitsForItsEarlierPaneRead(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var releaseRead = new java.util.concurrent.CountDownLatch(1);
        var edtHeld = new java.util.concurrent.CountDownLatch(1);
        var releaseEdt = new java.util.concurrent.CountDownLatch(1);
        try (var f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
            openSample(f.frame);
            awaitSampleSources(f.frame);
            widenSideColumn(f.frame, 150);
            SourcePanel pane = ((TopologyPanel) field(f.frame, "topologyPanel")).sourceViewer();
            onEdt(() -> pane.reader = (lookup, fqn) -> {
                var document = lookup.freshDocument(fqn);
                if (fqn.contains("Nodes")) {
                    entered.countDown();
                    try {
                        if (!releaseRead.await(4, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("reader not released");
                    } catch (InterruptedException e) { throw new AssertionError(e); }
                }
                return document;
            });
            assertTrue(f.ex.render("topology", Map.of("select", "riskMonitor", "source", true)).ok());
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS), "the real node read started");
            var read = (java.util.concurrent.Future<?>) field(field(pane, "nodePane"), "pendingRead");
            javax.swing.SwingUtilities.invokeLater(() -> {
                edtHeld.countDown();
                try {
                    if (!releaseEdt.await(4, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("EDT not released");
                } catch (InterruptedException e) { throw new AssertionError(e); }
            });
            assertTrue(edtHeld.await(3, java.util.concurrent.TimeUnit.SECONDS));
            String line = "source:java:com.acme.demo.node.Nodes:line:103";
            var request = new java.util.concurrent.FutureTask<ActionResult>(() -> sampleSpotlight(f, Map.of("target", line)));
            Thread caller = new Thread(request, "sample-review-caller");
            caller.start();
            long until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (caller.getState() != Thread.State.WAITING && caller.getState() != Thread.State.TIMED_WAITING
                    && System.nanoTime() < until) Thread.sleep(1);
            assertTrue(caller.getState() == Thread.State.WAITING || caller.getState() == Thread.State.TIMED_WAITING,
                    "the call queued its first EDT observation");
            releaseRead.countDown();
            until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (!read.isDone() && System.nanoTime() < until) Thread.sleep(1);
            assertTrue(read.isDone(), "the earlier pane install is now queued behind that observation");
            releaseEdt.countDown();
            assertLitOnScreen(request.get(5, java.util.concurrent.TimeUnit.SECONDS), line);
        } finally {
            releaseRead.countDown();
            releaseEdt.countDown();
        }
    }

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
            ActionResult set = sampleSpotlight(f, Map.of("targets", List.of(
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
            ActionResult generated = sampleSpotlight(f, Map.of("targets", List.of(
                    t("topology:node:orderTracker", "the handler"), t(dispatch, "the generated dispatch calls it"))));
            assertLitOnScreen(generated, dispatch);
            assertEquals("topology-source", lit(generated, dispatch).get("destination"));
            if (System.getProperty("sampleSpotlight.capture") != null) {
                onEdt(() -> {
                    var image = new java.awt.image.BufferedImage(f.frame.getWidth(), f.frame.getHeight(),
                            java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics();
                    try { f.frame.paint(graphics); }
                    finally { graphics.dispose(); }
                    try { javax.imageio.ImageIO.write(image, "png", Path.of(System.getProperty("sampleSpotlight.capture")).toFile()); }
                    catch (java.io.IOException e) { throw new AssertionError(e); }
                });
            }
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
                ActionResult r = sampleSpotlight(f, Map.of("targets", List.of(
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
}
