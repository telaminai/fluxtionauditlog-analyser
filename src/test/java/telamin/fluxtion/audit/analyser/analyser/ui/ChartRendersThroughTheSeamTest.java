package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;
import telamin.fluxtion.audit.analyser.analyser.ui.render.RecordingSurface;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #53 review: #48 and #49 through {@link ChartPanel#renderTo} — the chart as it is actually drawn.
 *
 * <p>The render package's tests call {@code PlotPainter} directly, with a geometry and a footer the TEST builds.
 * That pins the painter and not the chart: removing ChartPanel's call to the painter, dropping the decimated path's
 * closing hold, or handing the labels a footer-deep drop all left every test green (mutation witnesses on 96379547).
 * Here the panel lays itself out, draws onto a recorder, and the assertions compare what IT drew — its own footer
 * fill, its own plot rectangle.
 */
class ChartRendersThroughTheSeamTest {

    private static final int W = 900, H = 420;
    private static final long T0 = 1_767_258_000_000L;

    private static Series steps(int points) {
        Series s = new Series("v");
        for (int i = 0; i < points; i++) s.add(T0 + 1_000L * i, i % 7 == 0 ? 5 : 3);
        return s;
    }

    /** The window pinned a quarter past the last sample — #49's case, where only the hold can reach the edge. */
    private static ChartPanel chart(Series s, ChartNotes notes) {
        ChartPanel c = new ChartPanel();
        c.setStyle(ChartPanel.Style.STEP);
        c.setSeries(List.of(s));
        if (notes != null) c.setNotes(notes);
        c.setSize(W, H);
        c.doLayout();
        long last = s.maxX();
        c.setViewWindow(s.minX(), last + (last - s.minX()) / 4);
        return c;
    }

    private static RecordingSurface render(ChartPanel c) {
        RecordingSurface surface = new RecordingSurface();
        c.renderTo(surface, W, H);
        return surface;
    }

    /** A horizontal line ending at the plot's right edge, drawn under the plot clip: the closing hold. */
    private static boolean holdReachesTheEdge(ChartPanel c, RecordingSurface surface) {
        Rectangle plot = c.plotBounds();
        return surface.lines().stream().anyMatch(l -> l.isHorizontal()
                && Math.max(l.x1(), l.x2()) == plot.x + plot.width
                && Math.min(l.x1(), l.x2()) < plot.x + plot.width - 20
                && plot.equals(surface.clipOf(l)));
    }

    @Test
    @DisplayName("#49 through renderTo: the closing step reaches the edge — exact path")
    void theHoldIsDrawnOnTheExactPath() {
        ChartPanel c = chart(steps(200), null);
        RecordingSurface surface = render(c);
        assertTrue(holdReachesTheEdge(c, surface), "no closing hold reached the plot's right edge: " + surface.lines().size() + " lines");
    }

    @Test
    @DisplayName("#49 through renderTo: …and on the decimated path, which ChartPanel still draws itself")
    void theHoldIsDrawnOnTheDecimatedPath() {
        ChartPanel c = chart(steps(20_000), null);   // more than three points per pixel column
        RecordingSurface surface = render(c);
        assertTrue(holdReachesTheEdge(c, surface), "the decimated path drew no closing hold");
    }

    @Test
    @DisplayName("The series is drawn at all — inside the plot, under its clip")
    void theSeriesIsDrawn() {
        ChartPanel c = chart(steps(200), null);
        RecordingSurface surface = render(c);
        Rectangle plot = c.plotBounds();
        long steps = surface.lines().stream().filter(l -> l.isVertical() && plot.equals(surface.clipOf(l))
                && l.x1() > plot.x && l.x1() < plot.x + plot.width).count();
        assertTrue(steps > 10, "the step series' risers are missing — " + steps + " found");
    }

    @Test
    @DisplayName("#48 through renderTo: the time labels miss the footer the chart actually fills")
    void theLabelsMissTheFooterItFills() {
        var notes = ChartNotes.EMPTY.withExplanation("why this chart exists")
                .plus(new ChartNotes.Note(T0 + 50_000L, "a moment", null));
        ChartPanel c = chart(steps(200), notes);
        RecordingSurface surface = render(c);
        Rectangle footer = c.explanationBounds();
        assertTrue(footer.height > 0, "precondition: a note gives the chart a footer");
        assertTrue(surface.fills().stream().anyMatch(f -> f.bounds().equals(footer)),
                "precondition: the chart FILLS its footer — that fill is what erased the labels");
        double[] view = c.viewX();
        List<String> labels = List.of(TimeFormat.utc((long) view[0]), TimeFormat.utc((long) view[1]));
        var drawn = surface.texts().stream().filter(t -> labels.contains(t.text())).toList();
        assertEquals(2, drawn.size(), "both window labels are drawn: " + surface.texts());
        for (var label : drawn) {
            assertFalse(label.bounds().intersects(footer),
                    "the label " + label.text() + " at " + label.bounds() + " is inside the footer " + footer);
            assertTrue(label.bounds().y >= c.plotBounds().y + c.plotBounds().height,
                    "and it sits below the plot frame: " + label.bounds());
        }
    }
}
