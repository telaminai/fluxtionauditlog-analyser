package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;

import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.find;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * M64 acceptance 5 — on a REAL frame, {@code spotlight {target: "topology:node:<id>"}} lights that node,
 * and the image the {@code screenshot} verb writes has the cut-out over the node's bounds: <b>checked by
 * pixel sampling inside and outside the cut-out, not by eye</b>.
 *
 * <p>This is the test that found the spec's one wrong assumption. {@code spec-spotlight.md} said the
 * screenshot "already paints the glass pane because it captures the window the app painted"; the verb
 * paints the CONTENT PANE, which the glass pane is not part of, so the first version of this test saw an
 * undimmed image. The verb now composites a live spotlight, and this holds it there.
 *
 * <p>Skipped where there is no display; the CI {@code ui-frame} job runs it on one.
 */
class SpotlightFrameTest {

    private static final String GRAPH = "src/test/resources/topology/demo-quote-processor.graphml";
    private static final String NODE = "priceListener";
    private static final String OTHER = "quotePublisher";
    private static final String SERIES_LOG = "src/main/resources/demo/demo-quote-series.yaml";

    private static BufferedImage shoot(AsyncOpenInterleavingFrameTest.Frame f, Path to) throws Exception {
        onEdt(() -> render(f.ex, "screenshot", Map.of("path", to.toString())));
        return ImageIO.read(to.toFile());
    }

    private static int brightness(BufferedImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }

    @Test
    void theScreenshotVerbsImageHasTheCutOutOverTheNode_dimOutside_untouchedInside(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path exchange = Files.createDirectories(tmp.resolve("exchange"));
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                AppConfig config = (AppConfig) field(f.frame, "config");
                config.assistantExports = true;                       // the screenshot verb is opt-in and confined (B1)
                config.assistantExportDir = exchange.toString();
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
            });
            onEdt(() -> render(f.ex, "open", Map.of("graphml", Path.of(GRAPH).toAbsolutePath().toString())));
            onEdt(() -> render(f.ex, "spotlight", Map.of("target", "tab:topology")));   // bring the canvas on screen
            onEdt(() -> render(f.ex, "spotlight", Map.of("clear", true)));
            Thread.sleep(400);                                        // let the canvas take its first fit-to-view

            BufferedImage before = shoot(f, exchange.resolve("before.png"));

            AtomicReference<Map<String, Object>> lit = new AtomicReference<>();
            onEdt(() -> lit.set(render(f.ex, "spotlight",
                    Map.of("target", "topology:node:" + NODE, "caption", "this node receives every price first"))));
            Thread.sleep(300);                                        // the deferred re-measure, then a repaint
            onEdt(() -> assertEquals("topology:node:" + NODE,
                    find(find(render(f.ex, "context", Map.of()), "spotlight"), "target"),
                    "context reports what is lit, so the tutor can check before it shoots"));

            BufferedImage after = shoot(f, exchange.resolve("after.png"));

            // where the app SAYS it lit, in the coordinates of this very image
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            AtomicReference<java.awt.Rectangle> cut = new AtomicReference<>();
            onEdt(() -> cut.set(javax.swing.SwingUtilities.convertRectangle(overlay, overlay.cutOutOf("topology:node:" + NODE),
                    f.frame.getContentPane())));
            java.awt.Rectangle hole = cut.get();
            assertNotNull(hole);
            assertTrue(hole.width > 20 && hole.height > 10, "a real node box: " + hole);

            int cx = hole.x + hole.width / 2, cy = hole.y + hole.height / 2;
            assertEquals(before.getRGB(cx, cy), after.getRGB(cx, cy),
                    "INSIDE the cut-out the image is untouched — the node is shown as it is, not tinted");

            // OUTSIDE: the corner of the content pane diagonally away from the cut-out and its caption
            int ox = cx < after.getWidth() / 2 ? after.getWidth() - 12 : 12;
            int oy = cy < after.getHeight() / 2 ? after.getHeight() - 12 : 12;
            assertFalse(hole.contains(ox, oy));
            assertTrue(brightness(after, ox, oy) < brightness(before, ox, oy) * 0.85,
                    "OUTSIDE the cut-out the image is dimmed: " + brightness(before, ox, oy) + " → "
                            + brightness(after, ox, oy) + " at (" + ox + "," + oy + ")");

            // and a view-changing verb puts it out on the real frame too
            onEdt(() -> f.ex.render("topology", Map.of("select", NODE)));
            onEdt(() -> assertNull(find(render(f.ex, "context", Map.of()), "spotlight"),
                    "a verb that changes the view ends the spotlight; context reports nothing after it"));
        }
    }

    /**
     * M64.6 — two nodes lit TOGETHER on a real frame: both cut-outs untouched in the screenshot, the ground
     * between them dimmed, each numbered in {@code context}; then a set with one bad member lights nothing
     * new, and {@code add} / {@code clear + target} change exactly one.
     */
    @Test
    @SuppressWarnings("unchecked")
    void twoNodesLitTogether_bothCutOutInTheScreenshot_numbered_andASetIsAllOrNothing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path exchange = Files.createDirectories(tmp.resolve("exchange"));
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                AppConfig config = (AppConfig) field(f.frame, "config");
                config.assistantExports = true;
                config.assistantExportDir = exchange.toString();
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
            });
            onEdt(() -> render(f.ex, "open", Map.of("graphml", Path.of(GRAPH).toAbsolutePath().toString())));
            onEdt(() -> render(f.ex, "spotlight", Map.of("target", "tab:topology")));
            onEdt(() -> render(f.ex, "spotlight", Map.of("clear", true)));
            Thread.sleep(400);
            BufferedImage before = shoot(f, exchange.resolve("before.png"));

            String first = "topology:node:" + NODE, second = "topology:node:" + OTHER;
            AtomicReference<Map<String, Object>> echo = new AtomicReference<>();
            onEdt(() -> echo.set(render(f.ex, "spotlight", Map.of("targets", java.util.List.of(
                    Map.of("target", first, "caption", "every price arrives here"),
                    Map.of("target", second, "caption", "and leaves here"))))));
            Thread.sleep(300);

            AtomicReference<Object> lit = new AtomicReference<>();
            onEdt(() -> lit.set(find(find(render(f.ex, "context", Map.of()), "spotlight"), "lit")));
            java.util.List<Map<String, Object>> items = (java.util.List<Map<String, Object>>) lit.get();
            assertEquals(java.util.List.of(1, 2), items.stream().map(m -> m.get("n")).toList(), "numbered in the order asked");
            assertEquals(java.util.List.of(first, second), items.stream().map(m -> m.get("target")).toList());

            BufferedImage after = shoot(f, exchange.resolve("after.png"));
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            AtomicReference<java.awt.Rectangle[]> cuts = new AtomicReference<>();
            onEdt(() -> cuts.set(new java.awt.Rectangle[]{
                    javax.swing.SwingUtilities.convertRectangle(overlay, overlay.cutOutOf(first), f.frame.getContentPane()),
                    javax.swing.SwingUtilities.convertRectangle(overlay, overlay.cutOutOf(second), f.frame.getContentPane())}));
            for (java.awt.Rectangle hole : cuts.get()) {
                // sampled at the right-hand edge's middle: the number badge sits on the TOP-LEFT corner
                int x = hole.x + hole.width - 12, y = hole.y + hole.height / 2;
                assertEquals(before.getRGB(x, y), after.getRGB(x, y), "INSIDE each cut-out the image is untouched: " + hole);
            }
            assertFalse(cuts.get()[0].intersects(cuts.get()[1]), "two different nodes, two holes");
            int ox = 12, oy = after.getHeight() - 12;
            assertTrue(brightness(after, ox, oy) < brightness(before, ox, oy) * 0.85, "and the rest is dimmed ONCE, not twice");
            int single = brightnessAfterLightingOne(f, exchange, ox, oy, first, second);
            assertEquals(single, brightness(after, ox, oy), 6, "two spotlights dim the ground exactly as much as one");

            // a set with one bad member is refused WHOLE — and what was lit is still lit
            AtomicReference<ActionResult> refused = new AtomicReference<>();
            onEdt(() -> refused.set(f.ex.render("spotlight", new java.util.LinkedHashMap<>(Map.of("targets",
                    java.util.List.of("status", "topology:node:noSuchNode"))))));
            assertFalse(refused.get().ok());
            assertTrue(String.valueOf(refused.get().toMap()).contains("'topology:node:noSuchNode': "), String.valueOf(refused.get().toMap()));
            onEdt(() -> assertEquals(2, ((java.util.List<?>) find(find(render(f.ex, "context", Map.of()), "spotlight"), "lit")).size()));

            // add keeps; clear + target puts out exactly one, and the other keeps its number
            onEdt(() -> render(f.ex, "spotlight", Map.of("target", "status", "caption", "the pairing verdict", "add", true)));
            onEdt(() -> render(f.ex, "spotlight", Map.of("clear", true, "target", first)));
            onEdt(() -> lit.set(find(find(render(f.ex, "context", Map.of()), "spotlight"), "lit")));
            items = (java.util.List<Map<String, Object>>) lit.get();
            assertEquals(java.util.List.of(2, 3), items.stream().map(m -> m.get("n")).toList(),
                    "putting one out does not renumber the rest");
        }
    }

    private static int brightnessAfterLightingOne(AsyncOpenInterleavingFrameTest.Frame f, Path exchange, int x, int y,
                                                  String first, String second) throws Exception {
        onEdt(() -> render(f.ex, "spotlight", Map.of("target", first)));
        Thread.sleep(200);
        int one = brightness(shoot(f, exchange.resolve("one.png")), x, y);
        onEdt(() -> render(f.ex, "spotlight", Map.of("targets", java.util.List.of(first, second))));
        Thread.sleep(200);
        return one;
    }

    /**
     * Review of M64.6, F2 — the reviewer's two reproductions, on a real frame with a real log. A call that is
     * WRONG (a misspelt member; a seventh) used to reveal its {@code records:row} first, through goto's path,
     * which relaxed the filter and changed the selection — so a REFUSED request erased the investigation scope.
     */
    @Test
    void aRefusedCall_leavesTheFilter_theSelection_andTheStandingSpotlightsExactlyAsTheyWere(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
            });
            onEdt(() -> render(f.ex, "open", Map.of("log", Path.of(SERIES_LOG).toAbsolutePath().toString())));
            long deadline = System.currentTimeMillis() + 20_000;
            AtomicReference<Object> records = new AtomicReference<>();
            while (records.get() == null && System.currentTimeMillis() < deadline) {
                onEdt(() -> records.set(find(find(render(f.ex, "context", Map.of()), "log"), "records")));
                Thread.sleep(50);
            }
            assertNotNull(records.get(), "the series log never loaded");

            onEdt(() -> render(f.ex, "filter", Map.of("text", "nothing-matches-review-probe")));
            onEdt(() -> render(f.ex, "spotlight", Map.of("targets", java.util.List.of("status", "toolbar:flag"))));
            AtomicReference<String> before = new AtomicReference<>();
            onEdt(() -> before.set(scope(f)));
            assertTrue(before.get().contains("nothing-matches-review-probe"), before.get());

            // 1. a misspelt member beside a row
            AtomicReference<ActionResult> refused = new AtomicReference<>();
            onEdt(() -> refused.set(f.ex.render("spotlight", new java.util.LinkedHashMap<>(Map.of("targets",
                    java.util.List.of("records:row:15", "not-a-target"))))));
            assertFalse(refused.get().ok());
            onEdt(() -> assertEquals(before.get(), scope(f),
                    "a call refused for a misspelt member touched NOTHING: filter, selection, standing spotlights"));

            // 2. a seventh, by add, that is a row
            onEdt(() -> render(f.ex, "spotlight", Map.of("targets", java.util.List.of("status", "toolbar:open",
                    "toolbar:flag", "toolbar:explain", "toolbar:follow", "records"))));
            AtomicReference<String> six = new AtomicReference<>();
            onEdt(() -> six.set(scope(f)));
            onEdt(() -> refused.set(f.ex.render("spotlight", new java.util.LinkedHashMap<>(Map.of(
                    "target", "records:row:10", "add", true)))));
            assertFalse(refused.get().ok());
            assertTrue(String.valueOf(refused.get().toMap()).contains("at most 6"), String.valueOf(refused.get().toMap()));
            onEdt(() -> assertEquals(six.get(), scope(f), "nor did a seventh: the bound is judged before the row is revealed"));

            // 3. re-review R5: a row this log does not have. goto CLAMPS an index, so this used to relax the filter
            //    and select the LAST record, then refuse without mentioning either.
            onEdt(() -> refused.set(f.ex.render("spotlight", new java.util.LinkedHashMap<>(Map.of("target", "records:row:99999")))));
            assertFalse(refused.get().ok());
            assertTrue(String.valueOf(refused.get().toMap()).contains("there is no record 99999"), String.valueOf(refused.get().toMap()));
            onEdt(() -> assertEquals(six.get(), scope(f), "an impossible row touched nothing either — it is never handed to goto"));

            // control: a VALID row call is still allowed to reveal — that is what D-SP6 permits
            onEdt(() -> render(f.ex, "spotlight", Map.of("target", "records:row:15")));
            onEdt(() -> assertFalse(scope(f).contains("nothing-matches-review-probe"),
                    "a valid row is revealed the way goto reveals one — the filter is relaxed for it"));
        }
    }

    /** The three things a refused call must not move, as context states them. */
    private static String scope(AsyncOpenInterleavingFrameTest.Frame f) {
        Map<String, Object> ctx = render(f.ex, "context", Map.of());
        return "filter=" + find(ctx, "filter") + " selection=" + find(ctx, "selection") + " spotlight=" + find(ctx, "spotlight");
    }

    @Test
    void anUnknownNodeIsRefused_andLightsNothing(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                f.frame.validate();
            });
            onEdt(() -> render(f.ex, "open", Map.of("graphml", Path.of(GRAPH).toAbsolutePath().toString())));
            AtomicReference<ActionResult> refused = new AtomicReference<>();
            onEdt(() -> refused.set(f.ex.render("spotlight", Map.of("target", "topology:node:noSuchNode"))));

            assertFalse(refused.get().ok(), "never a spotlight on nothing");
            assertTrue(String.valueOf(refused.get().toMap()).contains("not in the graph"), String.valueOf(refused.get().toMap()));
            onEdt(() -> assertNull(find(render(f.ex, "context", Map.of()), "spotlight")));
        }
    }
}
