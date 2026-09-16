package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Finish-first review F5: {@code ChartNotesTest} pins the mapping, not the CALL SITE — reintroducing the
 * caller's {@code (long)} casts would pass it. This paints a real panel at fractional bounds and reads the
 * rule's pixel column back from the image, against the panel's own {@code xToPx}. Headless: a panel is
 * constructible and paints into an image without a display.
 */
class ChartNotesCallBoundaryTest {

    @Test
    void theRuleIsPaintedInTheSeriesColumnAtFractionalBounds() throws Exception {
        Series s = new Series("nodeA.price");
        for (int i = 0; i <= 17; i++) s.add(1000L + i, 1.0 + i);
        ChartPanel panel = new ChartPanel();
        panel.setSize(1200, 500);
        panel.setSeries(List.of(s));
        panel.setViewWindow(1000L, 1017L);
        panel.zoomIn();                                        // fractional bounds, as a wheel zoom leaves them
        panel.setNotes(new ChartNotes("", List.of(new ChartNotes.Note(1008L, "tick", null))));
        BufferedImage img = panel.toImage();                   // paints, and sets the plot rect fields

        double vx0 = (double) field(panel, "vx0"), vx1 = (double) field(panel, "vx1");
        int plotX = (int) field(panel, "plotX"), plotY = (int) field(panel, "plotY"), plotW = (int) field(panel, "plotW");
        assertNotEquals(Math.floor(vx0), vx0, "control: the bounds really are fractional after the zoom: " + vx0);
        int expected = plotX + (int) Math.round((1008 - vx0) / (vx1 - vx0) * plotW);   // ChartPanel.xToPx

        // the rule is a 1px dashed vertical line drawn from plotY with a {3,4} dash: row plotY+1 is inked
        int y = plotY + 1;
        int background = img.getRGB(plotX + 2, y);
        java.util.List<Integer> inked = new java.util.ArrayList<>();
        for (int x = plotX; x < plotX + plotW; x++) {
            if (img.getRGB(x, y) != background) inked.add(x);
        }
        assertTrue(inked.contains(expected),
                "the rule must be painted at the series' column " + expected + "; inked columns: " + inked);
        assertFalse(inked.contains(expected + 36) || inked.contains(expected - 36),
                "and not a millisecond's width away, which is where the truncated bounds put it");
    }

    private static Object field(Object o, String name) throws Exception {
        var f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }
}
