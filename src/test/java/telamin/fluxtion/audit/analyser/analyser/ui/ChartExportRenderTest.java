package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.2 (spec-evidence-integrity D-E8, acceptance 9). The G14 recovery packet's PDF showed a chart saying "No data
 * under the current filter" over a series with one point: the chart tab was a sliver, and the export painted it at
 * that size. Asserted on what the chart SAYS (its empty message), not on pixels.
 */
class ChartExportRenderTest {

    private static ChartPanel onePoint() {
        ChartPanel c = new ChartPanel();
        Series s = new Series("rootNode.price");
        s.add(1_000, 40.0);                      // the collapsed log's one point
        c.setSeries(List.of(s));
        return c;
    }

    @Test
    @DisplayName("the packet's case: a chart whose tab is a sliver renders its point when exported at page size")
    void aNarrowChartExportsItsPlot() {
        // witness: toImage(w,h) painting at the component's own size again
        ChartPanel c = onePoint();
        c.setSize(4, 300);                        // a tab that is not showing
        var img = c.toImage(1200, 600);
        assertEquals(1200, img.getWidth());
        assertNull(c.lastEmptyMessage(), "the plot was drawn: " + c.lastEmptyMessage());
        assertEquals(4, c.getWidth(), "and the live component's size is restored");
    }

    @Test
    @DisplayName("a plot with no room says so — it does not blame the filter over a series that has data")
    void noRoomIsNotNoData() {
        // witness: emptyPlotMessage returning the filter sentence for a starved plot
        ChartPanel c = onePoint();
        c.setSize(4, 300);
        var img = new java.awt.image.BufferedImage(4, 300, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        c.paint(g);
        g.dispose();
        assertNotNull(c.lastEmptyMessage());
        assertFalse(c.lastEmptyMessage().contains("No data"), c.lastEmptyMessage());
        assertTrue(c.lastEmptyMessage().contains("no room"), c.lastEmptyMessage());
    }

    @Test
    @DisplayName("the three empty reasons, told apart")
    void threeReasons() {
        assertTrue(ChartPanel.emptyPlotMessage(true, true, 500, 300).startsWith("No numeric series selected"));
        assertTrue(ChartPanel.emptyPlotMessage(false, true, 500, 300).startsWith("No data under the current filter"));
        assertTrue(ChartPanel.emptyPlotMessage(false, false, 3, 300).contains("no room"));
    }

    @Test
    @DisplayName("a series with no points in view still says no data — the true case keeps its sentence")
    void realEmptinessIsStillNoData() {
        ChartPanel c = new ChartPanel();
        c.setSeries(List.of(new Series("empty")));
        c.toImage(1200, 600);
        assertNotNull(c.lastEmptyMessage());
        assertTrue(c.lastEmptyMessage().startsWith("No data under the current filter"), c.lastEmptyMessage());
    }
}
