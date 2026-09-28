package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * M69 S2 (spec-spotlight-walks.md §3.6; review R3; W-A6): a chart DREW only when its last paint says so for the
 * size, data and window it has now. {@code lastEmptyMessage() == null} is not that fact: it is also what an unpainted
 * chart, a stale paint, and a changed series look like. This needs a real, showing component.
 */
class ChartDrawnFactFrameTest {

    private static telamin.fluxtion.audit.analyser.analyser.graph.Series series(long offset) {
        return series(offset, 0);
    }

    /** Same timestamps, different values when {@code bump} differs: the window stays put, only the data changes. */
    private static telamin.fluxtion.audit.analyser.analyser.graph.Series series(long offset, int bump) {
        var s = new telamin.fluxtion.audit.analyser.analyser.graph.Series("v");
        for (int i = 0; i < 40; i++) s.add(offset + 1_000L * i, (i % 5) + bump);
        return s;
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> c) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Exception> err = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { out.set(c.call()); } catch (Exception e) { err.set(e); }
        });
        if (err.get() != null) throw err.get();
        return out.get();
    }

    @Test
    @DisplayName("W-A6: drawn after a good paint; stale after a resize or a new series, until it paints again; no room names its size")
    void theDrawnFactFollowsThePaint() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        JFrame frame = onEdt(() -> new JFrame("drawn fact"));
        ChartPanel chart = onEdt(ChartPanel::new);
        try {
            onEdt(() -> {                            // the frame is shown FIRST, empty: showing may paint synchronously
                frame.getContentPane().setPreferredSize(new Dimension(900, 420));
                frame.pack();
                frame.setVisible(true);
                return null;
            });
            ChartPanel.DrawnFact beforePaint = onEdt(() -> {
                chart.setStyle(ChartPanel.Style.STEP);
                chart.setSeries(java.util.List.of(series(0)));
                frame.getContentPane().add(chart, BorderLayout.CENTER);
                frame.validate();
                // an unpainted chart has no outcome: asking must answer "not settled", never throw
                return assertDoesNotThrow(chart::drawnFact, "asking before the first paint must not fail");
            });
            assertFalse(beforePaint.settled(), "an unpainted chart is not settled: " + beforePaint);
            assertNull(onEdt(chart::lastEmptyMessage), "control: the null message that R3 says proves nothing");

            ChartPanel.DrawnFact good = onEdt(() -> {
                chart.paintImmediately(0, 0, chart.getWidth(), chart.getHeight());
                return chart.drawnFact();
            });
            assertTrue(good.drawn() && good.settled(), good.toString());

            ChartPanel.DrawnFact afterNewSeries = onEdt(() -> {
                // same times, new values: the window does not move, so only the data revision can say it is stale
                chart.setSeries(java.util.List.of(series(0, 7)));
                return chart.drawnFact();           // the message is still null from the good paint
            });
            assertNull(onEdt(chart::lastEmptyMessage), "the stale paint left no message");
            assertFalse(afterNewSeries.settled(), "new data is not drawn until it paints: " + afterNewSeries);

            ChartPanel.DrawnFact shrunk = onEdt(() -> {
                chart.paintImmediately(0, 0, chart.getWidth(), chart.getHeight());
                // a window cannot shrink this far on every platform, so the component is sized directly: well below the
                // plot's minimum, the case #56 reports at a laptop-sized window
                chart.setSize(60, 40);
                return chart.drawnFact();           // resized after a good paint, before its repaint
            });
            assertFalse(shrunk.settled(), "a resize after a good paint must not count as drawn: " + shrunk);

            ChartPanel.DrawnFact noRoom = onEdt(() -> {
                chart.paintImmediately(0, 0, chart.getWidth(), chart.getHeight());
                return chart.drawnFact();
            });
            assertTrue(noRoom.settled() && !noRoom.drawn(), noRoom.toString());
            assertTrue(noRoom.reason().contains("no room") && noRoom.reason().contains("widen"), noRoom.reason());
        } finally {
            onEdt(() -> { frame.dispose(); return null; });
        }
    }

    @Test
    @DisplayName("W-A6: a window with no samples, and a chart with no series, give their own reasons — never 'widen'")
    void emptyWindowsSayWhy() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        JFrame frame = onEdt(() -> new JFrame("drawn fact"));
        ChartPanel chart = onEdt(ChartPanel::new);
        try {
            onEdt(() -> {
                chart.setSeries(java.util.List.of(series(0)));
                chart.setPreferredSize(new Dimension(900, 420));
                frame.getContentPane().add(chart);
                frame.pack();
                frame.setVisible(true);
                frame.validate();
                chart.setViewWindow(10_000_000L, 20_000_000L);   // far outside the series
                chart.paintImmediately(0, 0, chart.getWidth(), chart.getHeight());
                return null;
            });
            ChartPanel.DrawnFact outside = onEdt(chart::drawnFact);
            assertFalse(outside.drawn());
            assertTrue(outside.settled());
            assertFalse(outside.reason().contains("widen"), "room is not the problem: " + outside.reason());

            ChartPanel.DrawnFact none = onEdt(() -> {
                chart.clear();
                chart.paintImmediately(0, 0, chart.getWidth(), chart.getHeight());
                return chart.drawnFact();
            });
            assertFalse(none.drawn());
            assertFalse(none.reason().contains("widen"), none.reason());
        } finally {
            onEdt(() -> { frame.dispose(); return null; });
        }
    }
}
