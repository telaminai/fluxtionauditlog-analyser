package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M32.1 — the snap SEARCH is pure and pinned here; the tooltip that renders it is on the eyeball
 * list. View: x ∈ [0, 1000] → 100px at plotX=0; y ∈ [0, 100] → 100px at plotY=0 (top).
 */
class SnapSearchTest {

    private static Series series(String label, long[] xs, double[] ys) {
        Series s = new Series(label);
        for (int i = 0; i < xs.length; i++) s.add(xs[i], ys[i]);
        return s;
    }

    private static SnapSearch.Hit hit(List<Series> all, int mx, int my, int radius) {
        return SnapSearch.nearest(all, l -> false, 0, 1000, 0, 100, 0, 0,
                0, 0, 100, 100, mx, my, radius);
    }

    @Test
    void snapsToTheNearestSampleWithinTheRadius() {
        Series a = series("a", new long[]{100, 500, 900}, new double[]{50, 80, 20});
        // sample (500, 80) → px (50, 20). Cursor at (52, 23): distance ~3.6
        SnapSearch.Hit h = hit(List.of(a), 52, 23, 12);
        assertNotNull(h);
        assertEquals("a", h.label());
        assertEquals(500L, h.x());
        assertEquals(80.0, h.y(), 1e-9);
    }

    @Test
    void noCandidateInRadiusMeansNoHit_theCallerFallsBackToCoordinates() {
        Series a = series("a", new long[]{100}, new double[]{50});
        assertNull(hit(List.of(a), 90, 90, 12), "sample px (10,50) is far from cursor (90,90)");
    }

    @Test
    void theNearerOfTwoSeriesWins() {
        Series a = series("a", new long[]{500}, new double[]{80});   // px (50, 20)
        Series b = series("b", new long[]{500}, new double[]{75});   // px (50, 25)
        SnapSearch.Hit h = hit(List.of(a, b), 50, 24, 12);
        assertEquals("b", h.label());
    }

    @Test
    void nanSamplesAreNeverSnapTargets() {
        Series a = series("a", new long[]{500, 510}, new double[]{Double.NaN, 80});
        SnapSearch.Hit h = hit(List.of(a), 50, 20, 12);
        assertEquals(510L, h.x(), "the NaN gap point is not a sample");
    }

    @Test
    void rightAxisSeriesSnapInTheirOwnScale() {
        // right scale 0..1000: value 800 → px y = 20 on the right transform, would be far off on left
        Series r = series("rhs", new long[]{500}, new double[]{800});
        SnapSearch.Hit h = SnapSearch.nearest(List.of(r), l -> true, 0, 1000, 0, 100, 0, 1000,
                0, 0, 100, 100, 50, 20, 12);
        assertNotNull(h, "the snap must measure distance in the series' OWN scale");
        assertEquals(800.0, h.y(), 1e-9);
    }

    @Test
    void denseSeriesAreFlaggedDecimated_andColumnMinMaxAnswersHonestly() {
        long[] xs = new long[1000];
        double[] ys = new double[1000];
        for (int i = 0; i < 1000; i++) {
            xs[i] = i;
            ys[i] = i % 2 == 0 ? 10 : 90;
        }
        Series dense = series("dense", xs, ys);
        // plotW=100 → 1000 > 3*100 → decimated
        SnapSearch.Hit h = SnapSearch.nearest(List.of(dense), l -> false, 0, 1000, 0, 100, 0, 0,
                0, 0, 100, 100, 50, 10, 12);   // near the 90-valued band (py = 10)
        assertNotNull(h);
        assertTrue(h.decimated(), "snapping one sample of a decimated series would pretend it is the truth");
        double[] mm = SnapSearch.columnMinMax(dense, 490, 510);
        assertEquals(10.0, mm[0], 1e-9);
        assertEquals(90.0, mm[1], 1e-9);
    }
}
