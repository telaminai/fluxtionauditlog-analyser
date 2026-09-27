package telamin.fluxtion.audit.analyser.analyser.ui.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two chart defects, asserted against what is DRAWN rather than against the arithmetic behind it.
 *
 * <p>These were first pinned by hoisting two decisions out of the paint method into pure functions.
 * That caught the arithmetic and could not have caught either defect as a user met it: one was a
 * line that stopped short, the other was a filled rectangle painted over some text. Both are
 * relationships BETWEEN drawing operations, and no pure function has two operations to compare.
 *
 * <p>Through the {@link Surface} seam the application and these tests run the same painter, so what
 * is asserted here is what lands on screen.
 */
class ChartDrawsItsClosingValueTest {

    /** A plot 1000px wide showing one minute, values 0..100. */
    private static final PlotGeometry GEOM =
            new PlotGeometry(56, 14, 1000, 600, 1_000_000d, 1_060_000d, 0d, 100d);

    private static Series series(long[] times, double[] values) {
        Series s = new Series("mid");
        for (int i = 0; i < times.length; i++) s.add(times[i], values[i]);
        return s;
    }

    // ---- #49 — the closing value is held to the window edge -------------------------------------

    @Test
    @DisplayName("A step series draws a horizontal line from its last sample to the right edge")
    void theClosingValueIsHeldToTheEdge() {
        // last sample two thirds across; a third of the window has no further data
        Series s = series(new long[]{1_000_000L, 1_020_000L, 1_040_000L}, new double[]{80, 60, 20});
        var surface = new RecordingSurface();

        PlotPainter.series(surface, GEOM, s, PlotPainter.Style.STEP, false);

        int lastX = GEOM.xToPx(1_040_000L);
        int lastY = GEOM.yToPx(20);
        assertTrue(surface.lines().stream().anyMatch(l ->
                        l.isHorizontal() && l.y1() == lastY && l.x1() == lastX && l.x2() == GEOM.right()),
                "no hold from the last sample to the right edge. Without it the closing value is a "
                        + "bare vertical stroke and a third of the window reads as 'no data'. Lines:\n  "
                        + surface.lines());
    }

    @Test
    @DisplayName("The drawn line reaches the right edge exactly — no gap, no overrun")
    void theLineReachesTheEdge() {
        Series s = series(new long[]{1_000_000L, 1_030_000L}, new double[]{50, 25});
        var surface = new RecordingSurface();

        PlotPainter.series(surface, GEOM, s, PlotPainter.Style.STEP, false);

        int rightmost = surface.lines().stream()
                .mapToInt(l -> Math.max(l.x1(), l.x2())).max().orElseThrow();
        assertEquals(GEOM.right(), rightmost, "the drawing should end at the plot's right edge");
    }

    @Test
    @DisplayName("LINE and POINTS assert nothing past their last sample")
    void onlyStepHolds() {
        Series s = series(new long[]{1_000_000L, 1_030_000L}, new double[]{50, 25});
        int lastX = GEOM.xToPx(1_030_000L);

        for (PlotPainter.Style style : List.of(PlotPainter.Style.LINE, PlotPainter.Style.POINTS)) {
            var surface = new RecordingSurface();
            PlotPainter.series(surface, GEOM, s, style, false);
            assertTrue(surface.lines().stream().noneMatch(l -> Math.max(l.x1(), l.x2()) > lastX),
                    style + " drew past its last sample: " + surface.lines());
        }
    }

    @Test
    @DisplayName("A series already at the edge draws no zero-length hold")
    void noZeroLengthHold() {
        Series s = series(new long[]{1_000_000L, 1_060_000L}, new double[]{50, 25});
        var surface = new RecordingSurface();

        PlotPainter.series(surface, GEOM, s, PlotPainter.Style.STEP, false);

        assertTrue(surface.lines().stream().noneMatch(l -> l.x1() == l.x2() && l.y1() == l.y2()),
                "a degenerate line was drawn: " + surface.lines());
    }

    @Test
    @DisplayName("An empty series draws nothing at all")
    void nothingToDraw() {
        var surface = new RecordingSurface();

        PlotPainter.series(surface, GEOM, new Series("empty"), PlotPainter.Style.STEP, false);

        assertEquals(List.of(), surface.marks());
    }

    @Test
    @DisplayName("A NaN breaks the line rather than plotting through it")
    void gapsBreakTheLine() {
        Series s = series(new long[]{1_000_000L, 1_020_000L, 1_040_000L},
                new double[]{80, Double.NaN, 20});
        var surface = new RecordingSurface();

        PlotPainter.series(surface, GEOM, s, PlotPainter.Style.STEP, false);

        int gapX = GEOM.xToPx(1_020_000L);
        assertTrue(surface.lines().stream().noneMatch(l ->
                        l.isHorizontal() && l.x1() < gapX && l.x2() > gapX),
                "a line crossed the gap: " + surface.lines());
    }

    // ---- #48 — a pinned note must not cost the time axis ----------------------------------------

    /** Where the explanation footer sits: below the plot, past the reserved label band. */
    private static Rectangle footer(int band) {
        return new Rectangle(GEOM.x(), GEOM.bottom() + band, GEOM.width(), 90);
    }

    @Test
    @DisplayName("The axis labels are not inside the footer the explanation block fills")
    void labelsSurviveAPinnedNote() {
        var surface = new RecordingSurface();

        PlotPainter.axisLabels(surface, GEOM, 16, t -> "09:00:" + t % 60);

        Rectangle footer = footer(44);
        for (var text : surface.texts()) {
            assertFalse(text.bounds().intersects(footer),
                    "axis label " + text.text() + " at " + text.bounds() + " is inside the footer "
                            + footer + ". The footer FILLS its background, so pinning one note used to "
                            + "erase the time axis — and a note is pinned TO A MOMENT IN TIME.");
        }
    }

    @Test
    @DisplayName("Both ends of the window are labelled, below the plot")
    void bothEndsAreLabelled() {
        var surface = new RecordingSurface();

        PlotPainter.axisLabels(surface, GEOM, 16, t -> "t" + t);

        var texts = surface.texts();
        assertEquals(2, texts.size(), "start and end: " + texts);
        for (var t : texts) {
            assertTrue(t.bounds().y > GEOM.bottom(), t.text() + " is not below the plot frame");
        }
        assertEquals("t1000000", texts.get(0).text());
        assertEquals(GEOM.x(), texts.get(0).x(), "the start label is flush with the left edge");
    }

    @Test
    @DisplayName("The end label is right-aligned INSIDE the plot, not overflowing it")
    void theEndLabelIsRightAligned() {
        var surface = new RecordingSurface();

        PlotPainter.axisLabels(surface, GEOM, 16, t -> "2026-01-01 09:00:00.360");

        var end = surface.texts().get(1);
        assertEquals(GEOM.right(), end.x() + end.width(),
                "its right edge should meet the plot's right edge");
        assertTrue(end.x() > GEOM.x(), "and it should not start before the plot does");
    }
}
