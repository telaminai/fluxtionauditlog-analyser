package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two chart-drawing defects found together, both about the bottom of the plot.
 *
 * <p>Swing painting is untested by rule 4, so the DECISIONS are extracted and pinned here — which is
 * the point, because in both cases the arithmetic was the bug rather than the drawing.
 */
class ChartDrawsItsClosingValueTest {

    // ---- #49: the last value is held to the window edge ----------------------------------------

    private static final int PLOT_X = 56, PLOT_W = 1000;
    private static final int EDGE = PLOT_X + PLOT_W;

    @Test
    @DisplayName("A step series holds its last value out to the window edge")
    void theClosingValueIsHeld() {
        assertEquals(EDGE, ChartPanel.stepHoldEnd(ChartPanel.Style.STEP, true, 900, PLOT_X, PLOT_W),
                "without this the closing value is a bare vertical stroke with no horizontal run — "
                        + "the number a reader most wants, drawn as the least visible thing on the chart");
    }

    @Test
    @DisplayName("…including when the window is pinned far past the last record")
    void aPinnedWindowIsFilledToItsEdge() {
        // the reported case: pinned 40s beyond the last point, so the last point sits well short of
        // the edge. The pinned remainder was drawn blank, which reads exactly like "no data".
        assertEquals(EDGE, ChartPanel.stepHoldEnd(ChartPanel.Style.STEP, true, 600, PLOT_X, PLOT_W));
    }

    @Test
    @DisplayName("Nothing is drawn when the last point already reaches the edge")
    void noZeroLengthHold() {
        assertNull(ChartPanel.stepHoldEnd(ChartPanel.Style.STEP, true, EDGE, PLOT_X, PLOT_W));
        assertNull(ChartPanel.stepHoldEnd(ChartPanel.Style.STEP, true, EDGE + 5, PLOT_X, PLOT_W),
                "a point clipped beyond the edge must not drag a line backwards");
    }

    @Test
    @DisplayName("An empty series holds nothing")
    void nothingToHold() {
        assertNull(ChartPanel.stepHoldEnd(ChartPanel.Style.STEP, false, 900, PLOT_X, PLOT_W));
    }

    /**
     * STEP only, and deliberately. A LINE interpolates between points it actually has; running one
     * flat past the last point would assert a hold the style does not claim, which is the same class
     * of misrepresentation that made stairs the default for market data in the first place.
     */
    @Test
    @DisplayName("LINE and POINTS assert nothing past their last point")
    void onlyStepHolds() {
        assertNull(ChartPanel.stepHoldEnd(ChartPanel.Style.LINE, true, 900, PLOT_X, PLOT_W));
        assertNull(ChartPanel.stepHoldEnd(ChartPanel.Style.POINTS, true, 900, PLOT_X, PLOT_W));
    }

    // ---- #48: the axis labels survive a pinned note ---------------------------------------------

    @Test
    @DisplayName("The x-axis labels sit under the plot, above where the footer starts")
    void labelsAreNotInTheFooter() {
        int plotY = 14, plotH = 600;
        int footerTop = plotY + plotH + ChartPanel.B;   // where the explanation block is placed

        int baseline = ChartPanel.axisLabelBaseline(plotY, plotH);

        assertTrue(baseline > plotY + plotH, "below the plot frame, not inside it: " + baseline);
        assertTrue(baseline < footerTop,
                "ABOVE the footer. These used to be drawn at the component's bottom edge, which is "
                        + "inside the footer whenever one exists — and the footer fills its background, "
                        + "so pinning one note silently erased the time axis. A note is pinned TO A "
                        + "MOMENT IN TIME: losing the axis to it defeats the feature. baseline="
                        + baseline + " footerTop=" + footerTop);
    }

    @Test
    @DisplayName("The label position does not depend on whether there IS a footer")
    void sameWithAndWithoutANote() {
        // plotH already shrinks to make room for a footer, so the baseline follows the plot and needs
        // no knowledge of the notes — which is exactly what the old `h - 6` got wrong
        assertEquals(ChartPanel.axisLabelBaseline(14, 600) - ChartPanel.axisLabelBaseline(14, 500), 100);
    }

    @Test
    @DisplayName("The label band fits inside the margin reserved for it")
    void theBandIsBigEnough() {
        assertTrue(ChartPanel.LABEL_DROP < ChartPanel.B,
                "LABEL_DROP must sit inside B, or the labels land in the footer again");
    }

    // ---- PR #51 review: the same decisions, through the paint that uses them ---------------------
    //
    // Every test above pins an extracted helper. Removing the CALL to it (`holdToWindowEdge`, either path) or
    // putting the labels back at `h - 6` left all of them green — the fix could be deleted and nothing noticed.
    // These paint the real component offscreen (headless: toImage needs no display) and read the pixels.

    private static final int W = 900, H = 420;

    private static telamin.fluxtion.audit.analyser.analyser.graph.Series steps(int points, int trailingGap) {
        var s = new telamin.fluxtion.audit.analyser.analyser.graph.Series("v");
        for (int i = 0; i < points; i++) s.add(1_000L * i, i < points - trailingGap ? (i % 7 == 0 ? 5 : 3) : Double.NaN);
        return s;
    }

    private static ChartPanel chart(telamin.fluxtion.audit.analyser.analyser.graph.Series s,
                                    telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes notes) {
        ChartPanel c = new ChartPanel();
        c.setStyle(ChartPanel.Style.STEP);
        c.setSeries(java.util.List.of(s));
        if (notes != null) c.setNotes(notes);
        c.setSize(W, H);
        c.doLayout();
        return c;
    }

    /**
     * #49's reported case: the window PINNED past the last record, so the last point sits well short of the right
     * edge. Unpinned, the window ends at the last point and the series reaches the edge without any hold — a test
     * of that case passes with the hold deleted.
     */
    private static ChartPanel pinnedPastTheData(telamin.fluxtion.audit.analyser.analyser.graph.Series s) {
        ChartPanel c = chart(s, null);
        long first = s.minX(), last = s.maxX();
        c.setViewWindow(first, last + (last - first) / 4);
        return c;
    }

    /** Series-coloured pixels in the plot's last few columns — where only the closing hold can be. */
    private static int seriesPixelsAtTheRightEdge(ChartPanel c) {
        java.awt.image.BufferedImage img = c.toImage(W, H);
        java.awt.Rectangle plot = c.plotBounds();
        java.awt.Color want = ChartPanel.paletteColor(0);
        int hits = 0;
        for (int x = plot.x + plot.width - 8; x < plot.x + plot.width; x++) {
            for (int y = plot.y; y < plot.y + plot.height; y++) {
                java.awt.Color p = new java.awt.Color(img.getRGB(x, y));
                if (Math.abs(p.getRed() - want.getRed()) < 40 && Math.abs(p.getGreen() - want.getGreen()) < 40
                        && Math.abs(p.getBlue() - want.getBlue()) < 40) hits++;
            }
        }
        return hits;
    }

    @Test
    @DisplayName("PAINTED: the closing step reaches the right edge — on the exact path and the decimated one")
    void theHoldIsPainted() {
        assertTrue(seriesPixelsAtTheRightEdge(pinnedPastTheData(steps(200, 0))) > 0, "exact path (few points)");
        assertTrue(seriesPixelsAtTheRightEdge(pinnedPastTheData(steps(20_000, 0))) > 0,
                "decimated path (more than three points per pixel column)");
    }

    @Test
    @DisplayName("PAINTED: a series ending in a gap holds nothing, however dense it is")
    void aTrailingGapIsNotHeld() {
        assertEquals(0, seriesPixelsAtTheRightEdge(pinnedPastTheData(steps(200, 20))), "exact path");
        assertEquals(0, seriesPixelsAtTheRightEdge(pinnedPastTheData(steps(20_000, 2_000))),
                "decimated path — it used to hold the last finite value straight through the gap");
    }

    /** Non-background pixels in the band just under the plot's left corner, where the first time label is drawn. */
    private static int inkUnderThePlot(ChartPanel c) {
        java.awt.image.BufferedImage img = c.toImage(W, H);
        java.awt.Rectangle plot = c.plotBounds();
        int top = plot.y + plot.height + 2, bottom = plot.y + plot.height + ChartPanel.LABEL_DROP + 3;
        java.awt.Color bg = new java.awt.Color(img.getRGB(plot.x + plot.width / 2, (top + bottom) / 2));
        int ink = 0;
        for (int x = plot.x; x < plot.x + 150; x++) {
            for (int y = top; y < bottom; y++) {
                java.awt.Color p = new java.awt.Color(img.getRGB(x, y));
                if (Math.abs(p.getRed() - bg.getRed()) + Math.abs(p.getGreen() - bg.getGreen())
                        + Math.abs(p.getBlue() - bg.getBlue()) > 90) ink++;
            }
        }
        return ink;
    }

    @Test
    @DisplayName("PAINTED: the time labels are drawn under the plot with a note and its footer present")
    void theAxisLabelsArePaintedWithANote() {
        var notes = telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.EMPTY
                .withExplanation("why this chart exists")
                .plus(new telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.Note(50_000L, "a moment", null));
        assertTrue(inkUnderThePlot(chart(steps(200, 0), null)) > 0,
                "the time labels belong just under the plot frame, with or without a note");
        assertTrue(inkUnderThePlot(chart(steps(200, 0), notes)) > 0,
                "with a note and its footer the labels must still be there — #48 was that they were not");
    }
}
