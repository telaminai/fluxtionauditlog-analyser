package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.MarkerSeries;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The marker legend (tracker M32.9) — the half of the spec's §C line that did not ship with v1.5.0.
 *
 * <p>The chart drew glyphs and the legend named only the value series, so nothing on screen said what
 * an orange triangle meant — visible in the milestone's own docs image. These pin the parts that are
 * pure logic; the Swing wiring stays an eyeball item under rule 4.
 */
class MarkerLegendTest {

    private static MarkerSeries series(String label, String glyph, int points) {
        List<MarkerSeries.MarkerPoint> pts = new java.util.ArrayList<>();
        for (int i = 0; i < points; i++) {
            pts.add(new MarkerSeries.MarkerPoint(1000L + i, 1.0 + i, "ord-" + i, i));
        }
        return new MarkerSeries(label, glyph, pts, null);
    }

    @Test
    void aRowNamesTheSeriesAndHowManyEventsItHas() {
        assertEquals("order live  (166)", GraphPanel.markerLegendText(series("order live", "triangleUp", 166)));
    }

    /**
     * The count is TOTAL points, not drawn glyphs. Drawn glyphs collapse with zoom under D-M3's column
     * aggregation, so a zoom-dependent legend number would change while the data did not — and the ×N
     * badges already disclose density on the plot, where it matters.
     */
    @Test
    void theCountIsTheDataNotTheDrawing() {
        MarkerSeries dense = series("fills", "circle", 5000);
        assertTrue(GraphPanel.markerLegendText(dense).endsWith("(5000)"),
                "the legend answers 'how many are there', not 'how many fitted on screen'");
    }

    /**
     * A series that resolved to nothing still gets a row. A dangling {@code y} pin or a {@code when}
     * that never fired is a fact the reader needs; a silently absent row is exactly the failure D-M2's
     * loud-degrade rule exists to prevent.
     */
    @Test
    void aSeriesThatResolvedToNothingStillAppears() {
        assertEquals("hedge fills  (0)", GraphPanel.markerLegendText(series("hedge fills", "x", 0)));
    }

    /** The legend swatch is the chart's OWN painter, so key and plot cannot drift apart (D-M1). */
    @Test
    void everyGlyphShapePaintsWithoutFallingBackOrThrowing() {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (String glyph : MarkerSeries.GLYPHS) {
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            assertDoesNotThrow(() -> ChartPanel.paintGlyph(g, glyph, 8, 8), "glyph: " + glyph);
            g.dispose();
        }
    }

    /** Markers have their own palette (post-review R7) — the legend must read from THAT one. */
    @Test
    void theMarkerPaletteIsSeparateFromTheSeriesPalette() {
        assertNotEquals(ChartPanel.paletteColor(0), ChartPanel.markerPaletteColor(0),
                "a marker swatch drawn in a series colour would mislabel which line it belongs to");
        assertEquals(ChartPanel.markerPaletteColor(0), ChartPanel.markerPaletteColor(4),
                "the palette wraps, so a fifth marker series still gets a colour");
    }
}
