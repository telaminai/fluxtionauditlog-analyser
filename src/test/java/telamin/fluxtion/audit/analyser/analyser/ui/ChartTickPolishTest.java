package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChartTickPolishTest {
    @Test void valueLabelsLandOnRoundValuesIncludingZeroWhenTheRangeIsNearIt() {
        Series counts = new Series("counts");
        counts.add(1_003, 10);
        counts.add(61_003, 20);
        ChartPanel chart = new ChartPanel();
        chart.setSeries(List.of(counts));
        double[] range = ChartAxisWindowTest.range(chart, "v");
        assertTrue(range[0] < 0 && range[1] > 20, "zero must be visible without clipping the highest point");
        assertEquals(List.of(0.0, 5.0, 10.0, 15.0, 20.0), ChartPanel.valueTicks(range[0], range[1]),
                "10 and 20 with padding should produce round 5-unit ticks, not fractional quarters");

        Series baseline = new Series("baseline");
        baseline.add(1_003, 1_000_000);
        baseline.add(61_003, 1_000_100);
        chart.setSeries(List.of(baseline));
        assertTrue(ChartAxisWindowTest.range(chart, "v")[0] > 900_000,
                "forcing zero onto a narrow million-unit range would flatten the data");

        Series negatives = new Series("negatives");
        negatives.add(1_003, -20);
        negatives.add(61_003, -10);
        chart.setSeries(List.of(negatives));
        double[] negativeRange = ChartAxisWindowTest.range(chart, "v");
        assertTrue(negativeRange[0] < -20 && negativeRange[1] > 0,
                "a nearby zero should be visible for negative values too");
    }

    @Test void timeGridUsesUtcAlignedIntervalsWhileEventTimesStayExact() {
        List<Long> ticks = ChartPanel.timeTicks(1_003, 61_003, 400);
        assertEquals(List.of(15_000L, 30_000L, 45_000L, 60_000L), ticks,
                "the grid aligns to round wall-clock intervals, not quarters of an arbitrary data window");
        assertFalse(ticks.contains(1_003L), "a real event's time is not moved to a tick");
    }
}
