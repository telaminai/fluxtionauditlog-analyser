package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Band intervals from a condition series (M28.6). The input is the condition's EXTRACTED series, so
 * the NaN-is-omitted rule has already been applied — unknown records are simply absent, and a band
 * runs from the first sample where the condition held to the next where it measurably did not.
 */
class ConditionBandsTest {

    private static Series series(long[] xs, double[] ys) {
        Series s = new Series("c");
        for (int i = 0; i < xs.length; i++) s.add(xs[i], ys[i]);
        return s;
    }

    @Test
    void truthySpansBecomeIntervals() {
        // false, true, true, false, true — two bands: [200,400] and [500,500]
        List<long[]> iv = ConditionBands.intervals(series(
                new long[]{100, 200, 300, 400, 500},
                new double[]{0, 1, 1, 0, 1}));
        assertEquals(2, iv.size());
        assertArrayEquals(new long[]{200, 400}, iv.get(0), "closes at the first measurably-false sample");
        assertArrayEquals(new long[]{500, 500}, iv.get(1), "still open at the end → closes at the last sample");
    }

    @Test
    void unknownSamplesNeitherOpenNorCloseABand() {
        // the extractor omits NaN samples, so a gap between t=200 (true) and t=600 (false) bridges
        List<long[]> iv = ConditionBands.intervals(series(
                new long[]{200, 600},
                new double[]{1, 0}));
        assertEquals(1, iv.size());
        assertArrayEquals(new long[]{200, 600}, iv.get(0));
    }

    @Test
    void neverTrueMeansNoBands() {
        assertTrue(ConditionBands.intervals(series(new long[]{1, 2}, new double[]{0, 0})).isEmpty());
        assertTrue(ConditionBands.intervals(new Series("empty")).isEmpty());
    }

    @Test
    void anyNonZeroFiniteValueIsTruthy() {
        // conditions evaluate to 1.0/0.0, but a band expr may be any formula — nonzero holds
        List<long[]> iv = ConditionBands.intervals(series(
                new long[]{10, 20, 30},
                new double[]{-2.5, 0, 7}));
        assertEquals(2, iv.size());
        assertArrayEquals(new long[]{10, 20}, iv.get(0));
    }
}
