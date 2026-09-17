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
            onEdt(() -> cut.set(javax.swing.SwingUtilities.convertRectangle(overlay, overlay.cutOut(),
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
