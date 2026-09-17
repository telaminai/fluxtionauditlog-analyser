package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import javax.imageio.ImageIO;
import javax.swing.JMenu;
import javax.swing.SwingUtilities;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;

/**
 * M64.10 — a graph target may NAME its chart ({@code graph:<name>:series:<label>}); lighting it selects that chart.
 * A target on the selected chart that lives on another one is refused naming the selected chart AND the charts
 * that have it. M64.11 — {@code menu:<Menu>} and {@code menu:<Menu>:<item>}: the reveal opens the menu, the item is
 * cut out inside the window (a lightweight popup, under the glass pane), the screenshot verb paints the popup into
 * the shot, a press on the lit item chooses it, and the menu closing puts the spotlights out. Real frame, xvfb in CI.
 */
class NamedGraphAndMenuSpotlightFrameTest {

    private static final String SERIES_LOG = "src/main/resources/demo/demo-quote-series.yaml";

    /**
     * The shipped app runs FlatLaf ({@code Main} installs it). The bare test rig gets the platform default — on macOS
     * that is Aqua, whose {@code ScreenPopupFactory} shows EVERY popup as a heavyweight window, above the glass pane,
     * which no user of this app ever sees. Install FlatLaf first, as the app does, so the popup can be lightweight.
     */
    private static javax.swing.LookAndFeel flatLafLikeTheApp() throws Exception {
        javax.swing.LookAndFeel[] previous = new javax.swing.LookAndFeel[1];
        SwingUtilities.invokeAndWait(() -> {
            previous[0] = javax.swing.UIManager.getLookAndFeel();
            if (!(previous[0] instanceof com.formdev.flatlaf.FlatLaf)) {
                try { javax.swing.UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf()); }
                catch (Exception e) { throw new IllegalStateException(e); }
            }
        });
        return previous[0];
    }

    /** Other display tests in this JVM sample pixels under the platform LAF: put it back. */
    private static void restoreLaf(javax.swing.LookAndFeel previous) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { if (previous != null && javax.swing.UIManager.getLookAndFeel() != previous) javax.swing.UIManager.setLookAndFeel(previous); }
            catch (Exception e) { throw new IllegalStateException(e); }
        });
    }

    private static void show(Frame f, Path exchange) throws Exception {
        onEdt(() -> {
            AppConfig config = (AppConfig) field(f.frame, "config");
            config.assistantExports = true;
            config.assistantExportDir = exchange.toString();
            f.frame.setSize(1300, 850);
            f.frame.setVisible(true);
            f.frame.validate();
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> lit(Frame f) {
        Object lit = find(find(render(f.ex, "context", Map.of()), "spotlight"), "lit");
        return lit == null ? List.of() : (List<Map<String, Object>>) lit;
    }

    private static void awaitLoaded(Frame f) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            final Object[] records = new Object[1];
            onEdt(() -> records[0] = find(find(render(f.ex, "context", Map.of()), "log"), "records"));
            if (records[0] != null) return;
            Thread.sleep(100);
        }
        fail("the log did not finish loading in 30 s");
    }

    /** The verb's answer as it is — {@code render} asserts ok, and these cases expect a refusal. */
    private static Map<String, Object> attempt(Frame f, String verb, Map<String, Object> params) {
        return f.ex.render(verb, new java.util.LinkedHashMap<>(params)).toMap();
    }

    private static void pump() throws Exception {
        for (int i = 0; i < 3; i++) SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void aGraphTargetThatNamesItsChart_selectsThatChart_andTheSelectedChartRefusalNamesWhereItIs(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        javax.swing.LookAndFeel previous = flatLafLikeTheApp();
        try (Frame f = new Frame(tmp)) {
            show(f, Files.createDirectories(tmp.resolve("exchange")));
            onEdt(() -> render(f.ex, "open", Map.of("log", Path.of(SERIES_LOG).toAbsolutePath().toString())));
            awaitLoaded(f);
            onEdt(() -> {
                assertEquals(true, render(f.ex, "graph", Map.of("newTab", true, "name", "Spread",
                        "series", List.of("quotePublisher.spread"))).get("ok"));
                assertEquals(true, render(f.ex, "graph", Map.of("newTab", true, "name", "Orders",
                        "series", List.of("riskMonitor.liveOrders"))).get("ok"));
            });
            Thread.sleep(600);                                                   // let both extractions land
            GraphTabs tabs = (GraphTabs) field(f.frame, "graphTabs");
            onEdt(() -> assertEquals("Orders", tabs.selectedGraphName(), "control: the last chart drawn is the selected one"));

            // the SELECTED chart lacks the series: the refusal names the selected chart and where the series IS
            onEdt(() -> {
                Map<String, Object> r = attempt(f, "spotlight", Map.of("target", "graph:series:quotePublisher.spread"));
                assertEquals(false, r.get("ok"));
                String why = String.valueOf(r.get("error"));
                assertTrue(why.contains("the selected graph ('Orders')"), why);
                assertTrue(why.contains("It is on [Spread]") && why.contains("graph:Spread:series:quotePublisher.spread"),
                        "and says how to name it: " + why);
                assertEquals("Orders", tabs.selectedGraphName(), "a refused call selected nothing");
            });
            // the NAMED chart: selected first, then lit
            onEdt(() -> {
                Map<String, Object> r = render(f.ex, "spotlight", Map.of("target", "graph:Spread:series:quotePublisher.spread",
                        "caption", "the spread series"));
                assertEquals(true, r.get("ok"), r::toString);
                assertEquals("Spread", tabs.selectedGraphName(), "naming the chart selected it (a reveal)");
                assertEquals(List.of("graph:Spread:series:quotePublisher.spread"),
                        lit(f).stream().map(m -> m.get("target")).toList());
            });
            // an unknown chart name: refused, naming the open charts; nothing lit changes
            onEdt(() -> {
                Map<String, Object> r = attempt(f, "spotlight", Map.of("target", "graph:Nope:note:1"));
                assertEquals(false, r.get("ok"));
                assertTrue(String.valueOf(r.get("error")).contains("no graph named 'Nope'")
                        && String.valueOf(r.get("error")).contains("Spread"), String.valueOf(r.get("error")));
                assertEquals(1, lit(f).size(), "the standing spotlight is untouched by a refused call");
            });
        } finally {
            restoreLaf(previous);
        }
    }

    @Test
    void aMenuItemIsLitInsideTheWindow_paintedIntoTheShot_chosenByAPress_andOutWhenTheMenuCloses(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path exchange = Files.createDirectories(tmp.resolve("exchange"));
        javax.swing.LookAndFeel previous = flatLafLikeTheApp();
        try (Frame f = new Frame(tmp)) {
            show(f, exchange);
            onEdt(() -> render(f.ex, "open", Map.of("log", Path.of(SERIES_LOG).toAbsolutePath().toString())));
            awaitLoaded(f);
            JMenu file = (JMenu) f.frame.getJMenuBar().getComponent(0);
            assertEquals("File", file.getText());

            // 1. light an item: the reveal OPENS the menu; the item is measured inside the window
            final java.awt.Rectangle[] itemBounds = new java.awt.Rectangle[1];
            onEdt(() -> {
                Map<String, Object> r = render(f.ex, "spotlight", Map.of("target", "menu:File:Close log", "caption", "closes the log"));
                assertEquals(true, r.get("ok"), r::toString);
                assertTrue(file.getPopupMenu().isShowing(), "lighting a menu item opened the menu");
                assertEquals(f.frame, SwingUtilities.getWindowAncestor(file.getPopupMenu()), "as a LIGHTWEIGHT popup, inside the window");
                assertEquals("menu:File:Close log", lit(f).get(0).get("target"));
                Map<String, Object> one = ((List<Map<String, Object>>) find(r, "lit")).get(0);   // the verb's echo carries bounds
                Map<String, Object> b = (Map<String, Object>) one.get("bounds");
                itemBounds[0] = new java.awt.Rectangle(((Number) b.get("x")).intValue(), ((Number) b.get("y")).intValue(),
                        ((Number) b.get("width")).intValue(), ((Number) b.get("height")).intValue());
                assertTrue(itemBounds[0].width > 40 && itemBounds[0].height > 10, "a real item rectangle: " + itemBounds[0]);
            });

            // 2. the screenshot verb paints the open popup into the shot: the item's pixels are the popup's, not the
            //    dimmed content beneath — compare with the same pixel in a shot with nothing lit and no menu open
            Path withMenu = exchange.resolve("with-menu.png");
            onEdt(() -> assertEquals(true, render(f.ex, "screenshot", Map.of("path", withMenu.toString())).get("ok")));
            BufferedImage lit = ImageIO.read(withMenu.toFile());
            int cx = itemBounds[0].x + itemBounds[0].width / 2, cy = itemBounds[0].y + itemBounds[0].height / 2;
            int inside = lit.getRGB(cx, cy);
            int outside = lit.getRGB(Math.min(lit.getWidth() - 5, itemBounds[0].x + itemBounds[0].width + 200), cy);
            assertNotEquals(inside, outside, "inside the cut-out is the popup, outside is dimmed content");

            // 3. a press ON the lit item chooses it: the log closes, the menu closes, the spotlight goes out
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            // the echo's bounds are in content-pane (screenshot) coordinates; the overlay is the glass pane, which
            // also covers the menu bar — so the press point is converted, as a real press would arrive
            Point at = SwingUtilities.convertPoint(f.frame.getContentPane(), new Point(cx, cy), overlay);
            EventQueue q = Toolkit.getDefaultToolkit().getSystemEventQueue();
            q.postEvent(new MouseEvent(overlay, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, at.x, at.y, 1, false, MouseEvent.BUTTON1));
            pump();
            Thread.sleep(300);
            pump();
            onEdt(() -> {
                assertEquals(List.of(), lit(f), "the press put the spotlight out");
                assertFalse(file.getPopupMenu().isShowing(), "and the menu closed");
                assertEquals(0, f.dialogs.seen(), "no dialog stood in the way");
                assertNull(find(render(f.ex, "context", Map.of()), "records"), "and Close log was CHOSEN: no log is open now");
            });

            // 4. a lit menu that closes for any other reason takes its spotlight with it
            onEdt(() -> assertEquals(true, render(f.ex, "spotlight", Map.of("target", "menu:File")).get("ok")));
            onEdt(() -> assertEquals(1, lit(f).size()));
            onEdt(() -> javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath());   // what Escape or a click elsewhere does
            pump();
            onEdt(() -> assertEquals(List.of(), lit(f), "menu closed → nothing left pointing at where it was"));

            // 5. an unknown item is refused naming the menu's items; an unknown menu names the menus
            onEdt(() -> {
                Map<String, Object> r = attempt(f, "spotlight", Map.of("target", "menu:File:Nope"));
                assertEquals(false, r.get("ok"));
                assertTrue(String.valueOf(r.get("error")).contains("no item 'Nope' in the File menu"), String.valueOf(r.get("error")));
                assertFalse(file.getPopupMenu().isShowing(), "a refused item does not leave the menu open");
                Map<String, Object> r2 = attempt(f, "spotlight", Map.of("target", "menu:Nope"));
                assertTrue(String.valueOf(r2.get("error")).contains("no menu 'Nope'"), String.valueOf(r2.get("error")));
            });
        } finally {
            restoreLaf(previous);
        }
    }
}
