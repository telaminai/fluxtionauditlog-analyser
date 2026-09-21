package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChartAnnotationLayoutTest {
    private static ChartPanel chart() {
        var panel = new ChartPanel(); panel.setSize(900, 500);
        var series = new Series("n.value"); series.add(0, 1); series.add(1000, 2);
        panel.setSeries(List.of(series));
        return panel;
    }
    @Test void commentaryIsBelowThePlotAndOverflowIsAvailableOnHover() {
        var panel = chart();
        String explanation = "Long explanation with enough words to wrap. ".repeat(100);
        panel.setNotes(new ChartNotes(explanation, List.of(new ChartNotes.Note(500, "moment", null))));
        panel.toImage();
        var box = panel.explanationBounds(); var plot = panel.plotBounds();
        assertTrue(box.height > 0); assertTrue(plot.height > 100);
        assertTrue(box.y >= plot.y + plot.height, "commentary must never cover data");
        assertTrue(box.y + box.height <= panel.getHeight());
        var event = new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, box.x + 10, box.y + 10, 0, false);
        assertTrue(panel.getToolTipText(event).contains(explanation), "overflow must not silently discard commentary");
    }
    @Test void adjacentAndCoincidentNotePinsShareDisjointRangesOutsideThePlot() {
        var panel = chart(); var notes = new ArrayList<ChartNotes.Note>();
        for (int i = 0; i < 25; i++) notes.add(new ChartNotes.Note(500 + i / 2, "note " + i, null));
        notes.add(new ChartNotes.Note(900, "separate", null));
        panel.setNotes(new ChartNotes("", notes)); panel.toImage();
        var pins = panel.notePins(); assertTrue(pins.size() >= 2); assertTrue(pins.size() < notes.size());
        assertEquals(notes.size(), pins.stream().mapToInt(p -> p.last() - p.first() + 1).sum());
        for (int i = 0; i < pins.size(); i++) {
            var bounds = pins.get(i).bounds();
            assertFalse(bounds.intersects(panel.plotBounds()), "note pins must be outside the data rectangle");
            for (int j = 0; j < i; j++) assertFalse(bounds.intersects(pins.get(j).bounds()), "nearby columns must not overlap");
        }
        for (int i = 1; i <= notes.size(); i++) assertNotNull(panel.noteBounds(i), "every note remains targetable");
    }
    @Test void legendReservesSpaceAndIsPartOfExport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var graph = new GraphPanel();
            var chart = graph.chartPanel(); chart.setSize(900, 500);
            var series = new Series("n.value"); series.add(0, 1); series.add(1000, 2); chart.setSeries(List.of(series));
            try {
                var position = GraphPanel.class.getDeclaredMethod("positionLegendOverlay"); position.setAccessible(true); position.invoke(graph);
                var field = GraphPanel.class.getDeclaredField("legendScroll"); field.setAccessible(true);
                var legend = (JScrollPane)field.get(graph); legend.doLayout();
                var image = chart.toImage();
                assertTrue(legend.getWidth() > 0);
                assertFalse(chart.plotBounds().intersects(legend.getBounds()), "legend must not cover data");
                // A bright child proves exports include child components, not just paintComponent.
                var witness = new JPanel(); witness.setBackground(Color.MAGENTA); witness.setBounds(legend.getX(), 300, 20, 20);
                chart.add(witness); image = chart.toImage();
                assertEquals(Color.MAGENTA.getRGB(), image.getRGB(witness.getX() + 5, 305));
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
    }
}
