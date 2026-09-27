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
}
