package telamin.fluxtion.audit.analyser.analyser.graph;

import telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment.Axis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Which axis a series is measured against — the thing that makes incommensurable series comparable. */
class AxisAssignmentTest {

    private static Series series(String label, double... values) {
        Series s = new Series(label);
        long t = 0;
        for (double v : values) {
            s.add(t += 1000, v);
        }
        return s;
    }

    @Test
    void everythingIsOnTheLeftByDefault() {
        AxisAssignment axes = new AxisAssignment();
        assertEquals(Axis.LEFT, axes.axisOf("anything"));
        assertFalse(axes.hasRightAxis(), "no second scale to draw");
    }

    @Test
    void aSeriesCanBeMovedToTheRightAndBack() {
        AxisAssignment axes = new AxisAssignment().plusRight("stock");
        assertEquals(Axis.RIGHT, axes.axisOf("stock"));
        assertEquals(Axis.LEFT, axes.axisOf("revenue"));
        assertTrue(axes.hasRightAxis());
        assertFalse(axes.minusRight("stock").hasRightAxis());
    }

    @Test
    void assignmentsAreImmutable() {
        AxisAssignment original = new AxisAssignment();
        original.plusRight("stock");
        assertFalse(original.hasRightAxis(), "a saved graph's assignment must not change under it");
    }

    @Test
    void anOrderOfMagnitudeApartIsSuggestedForTheRight() {
        // revenue 0..2000, stock 18..22 — the exact case that produced a flat smear on one axis
        var suggestion = AxisAssignment.suggestFor(List.of(
                series("revenue", 0, 500, 1200, 2000),
                series("stock", 18, 22, 19, 21)));
        assertTrue(suggestion.isRight("stock"));
        assertFalse(suggestion.isRight("revenue"));
    }

    @Test
    void comparableSeriesAreLeftAlone() {
        var suggestion = AxisAssignment.suggestFor(List.of(
                series("bid", 100, 101, 99), series("ask", 102, 103, 101)));
        assertFalse(suggestion.hasRightAxis(),
                "splitting a bid from an ask would break the comparison the reader came for");
    }

    @Test
    void aSingleSeriesIsNeverSplitOffFromItself() {
        assertFalse(AxisAssignment.suggestFor(List.of(series("only", 1, 1000))).hasRightAxis());
    }

    @Test
    void aFlatSeriesIsJudgedByMagnitudeNotByItsZeroSpan() {
        // a constant 5000 against a 0..10 series: zero span must not read as "no scale of its own"
        var suggestion = AxisAssignment.suggestFor(List.of(
                series("constant", 5000, 5000, 5000), series("small", 0, 5, 10)));
        assertTrue(suggestion.hasRightAxis(), "one of them needs its own scale");
    }

    @Test
    void emptySeriesDoNotThrow() {
        assertFalse(AxisAssignment.suggestFor(List.of(series("empty"))).hasRightAxis());
        assertFalse(AxisAssignment.suggestFor(List.of()).hasRightAxis());
    }
}
