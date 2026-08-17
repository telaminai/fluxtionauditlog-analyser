package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The W0 mirror evaluator (spec-expr-conditionals-windows M28.2): per-scan, position-keyed. The
 * semantics themselves are pinned by {@link ExprTest} running entirely through evaluators; this class
 * pins the properties the refactor exists for.
 */
class EvaluatorTest {

    @Test
    void statelessExpressionsProveIt() {
        // stateSlotCount()==0 is the checkable proof a formula carries no window state — every
        // pre-M28.3 expression must satisfy it
        for (String expr : new String[]{"1 + 2 * 3", "abs(-1)", "min(4, 2)",
                "if(1 > 0, 7, 9)", "and(1, or(0, 1))", "not(0)"}) {
            assertEquals(0, Expr.parse(expr, Set.of()).newEvaluator().stateSlotCount(),
                    expr + " is stateless and must compile with zero state slots");
        }
    }

    @Test
    void eachScanGetsItsOwnEvaluator() {
        // one AST, two scans — the mirrors must be distinct objects, because window state (M28.3)
        // lives in the mirror and a shared one would bleed history across scans
        Expr e = Expr.parse("1 + 2", Set.of());
        assertNotSame(e.newEvaluator(), e.newEvaluator());
    }

    // ---- rolling windows (M28.3) ---------------------------------------------------------------

    private static final GraphKey X = new GraphKey("a", "x");

    /** Feed the samples in order (NaN = a record whose sample is non-finite) and collect results. */
    private static double[] feed(String expr, double... samples) {
        Evaluator ev = Expr.parse(expr, Set.of(X)).newEvaluator();
        double[] out = new double[samples.length];
        for (int i = 0; i < samples.length; i++) out[i] = ev.eval(i, Map.of(X, samples[i]));
        return out;
    }

    @Test
    void lagIsTheValueNSamplesAgo_andNeedsAFullWindow() {
        double[] r = feed("lag(a.x, 2)", 10, 20, 30, 40);
        assertTrue(Double.isNaN(r[0]) && Double.isNaN(r[1]), "NaN until 2 samples precede the current one");
        assertEquals(10.0, r[2], 1e-9);
        assertEquals(20.0, r[3], 1e-9);
    }

    @Test
    void deltaSpansAGapRatherThanForgettingThePast() {
        // D-W2: a non-finite sample leaves state unchanged — the next delta measures across the gap
        double[] r = feed("delta(a.x)", 1, Double.NaN, 3);
        assertTrue(Double.isNaN(r[0]), "no previous sample yet");
        assertTrue(Double.isNaN(r[1]), "lag/delta anchor to the current sample — none here");
        assertEquals(2.0, r[2], 1e-9, "3 - 1: the gap widened the step, it did not erase the past");
    }

    @Test
    void meanSlidesAndRequiresAFullWindow() {
        double[] r = feed("mean(a.x, 3)", 3, 6, 9, 12);
        assertTrue(Double.isNaN(r[0]) && Double.isNaN(r[1]), "under-filled window → no point");
        assertEquals(6.0, r[2], 1e-9);
        assertEquals(9.0, r[3], 1e-9, "oldest sample evicted");
    }

    @Test
    void rollingMinAndMaxEvictCorrectly() {
        double[] mn = feed("rollingMin(a.x, 2)", 5, 3, 7, 9);
        assertEquals(3.0, mn[1], 1e-9);
        assertEquals(3.0, mn[2], 1e-9);
        assertEquals(7.0, mn[3], 1e-9, "the 3 left the window");
        double[] mx = feed("rollingMax(a.x, 2)", 5, 3, 7, 2);
        assertEquals(5.0, mx[1], 1e-9);
        assertEquals(7.0, mx[2], 1e-9);
        assertEquals(7.0, mx[3], 1e-9);
    }

    @Test
    void sumIsTheWindowTotal() {
        double[] r = feed("sum(a.x, 2)", 1, 2, 3);
        assertEquals(3.0, r[1], 1e-9);
        assertEquals(5.0, r[2], 1e-9);
    }

    @Test
    void structurallyEqualSubtreesOwnTheirOwnState() {
        // THE review test (W0): value-equal AST nodes must not share a state cell — a node-keyed map
        // would advance one shared window twice per record and this stops holding
        double[] doubled = feed("delta(a.x) + delta(a.x)", 1, 4, 9);
        double[] single = feed("2 * delta(a.x)", 1, 4, 9);
        assertEquals(single[1], doubled[1], 1e-9, "delta(x) + delta(x) == 2 * delta(x)");
        assertEquals(single[2], doubled[2], 1e-9);
        assertEquals(2, Expr.parse("delta(a.x) + delta(a.x)", Set.of(X)).newEvaluator().stateSlotCount(),
                "two positions, two slots");
    }

    @Test
    void gateTheOutputVersusGateTheInput() {
        // the two idioms from the spec's table — the docs' most load-bearing paragraph, pinned here.
        // Samples: 1, 100, 2, 200 with condition "x > 50".
        // gate the OUTPUT: mean over ALL samples, shown only while the condition holds
        double[] out = feed("if(a.x > 50, mean(a.x, 2))", 1, 100, 2, 200);
        assertTrue(Double.isNaN(out[2]), "condition false → not shown");
        assertEquals(101.0, out[3], 1e-9, "mean of ALL of the last 2 samples (2, 200)");
        // gate the INPUT: mean over ONLY the samples where the condition held
        double[] in = feed("mean(if(a.x > 50, a.x), 2)", 1, 100, 2, 200);
        assertTrue(Double.isNaN(in[1]), "only one breaching sample so far — window under-filled");
        assertEquals(150.0, in[3], 1e-9, "mean of the last 2 BREACHING samples (100, 200)");
        assertEquals(150.0, in[3], 1e-9);
        // and the D-W2 composition payoff: on a non-breaching record the aggregate HOLDS, not resets
        double[] hold = feed("mean(if(a.x > 50, a.x), 2)", 100, 200, 2, 2);
        assertEquals(150.0, hold[2], 1e-9, "false-condition samples leave the window untouched");
        assertEquals(150.0, hold[3], 1e-9);
    }

    @Test
    void windowedExpressionsDeclareTheirState() {
        assertEquals(1, Expr.parse("mean(a.x, 5)", Set.of(X)).newEvaluator().stateSlotCount());
        assertEquals(0, Expr.parse("a.x + 1", Set.of(X)).newEvaluator().stateSlotCount());
    }

    // ---- time windows (M28.4) --------------------------------------------------------------------

    /** Feed (time, sample) pairs in order and collect results. */
    private static double[] feedTimed(String expr, long[] times, double... samples) {
        Evaluator ev = Expr.parse(expr, Set.of(X)).newEvaluator();
        double[] out = new double[samples.length];
        for (int i = 0; i < samples.length; i++) out[i] = ev.eval(times[i], Map.of(X, samples[i]));
        return out;
    }

    @Test
    void timeWindowsPruneByTheCurrentRecordsClock() {
        // samples at t=0s, 2s, 63s with a "1m" window: the first two fall out by the third
        double[] r = feedTimed("mean(a.x, \"1m\")", new long[]{0, 2_000, 63_000}, 10, 20, 90);
        assertEquals(10.0, r[0], 1e-9, "a time window has no fill requirement — one sample answers");
        assertEquals(15.0, r[1], 1e-9);
        assertEquals(90.0, r[2], 1e-9, "everything older than now − 1m fell out");
    }

    @Test
    void timeWindowedMinPrunesOldExtremes() {
        double[] r = feedTimed("rollingMin(a.x, \"5s\")", new long[]{0, 1_000, 7_000}, 3, 8, 9);
        assertEquals(3.0, r[1], 1e-9);
        assertEquals(9.0, r[2], 1e-9, "the 3 (t=0) and 8 (t=1s) are outside now − 5s");
    }

    @Test
    void rateIsChangePerWindow_normalisedByTheSpanActuallyCovered() {
        double[] r = feedTimed("rate(a.x, \"1m\")", new long[]{0, 30_000, 60_500}, 100, 130, 190);
        assertTrue(Double.isNaN(r[0]), "a rate from one observation is unknown, not zero");
        assertEquals(60.0, r[1], 1e-9, "+30 over the 30s actually covered = 60 per minute");
        assertEquals(60.0 * 60_000 / 30_500, r[2], 1e-6, "t=0 aged out; +60 over the 30.5s covered");
    }

    @Test
    void rateDoesNotUnderstateOnAPartlyCoveredWindow() {
        // x rises exactly 1.0/s, sampled every 10s — the true rate is 60 per minute at EVERY point.
        // Un-normalised (newest − oldest) read 10 at t=10s and never exceeded 50 in steady state,
        // because the window is open at the old end: the retained span is always short by one
        // sampling interval, a permanent low bias of (T−Δ)/T that never converges.
        long[] times = new long[10];
        double[] vals = new double[10];
        for (int i = 0; i < 10; i++) {
            times[i] = i * 10_000L;
            vals[i] = i * 10.0;
        }
        double[] r = feedTimed("rate(a.x, \"1m\")", times, vals);
        assertTrue(Double.isNaN(r[0]), "one observation has no rate");
        for (int i = 1; i < 10; i++) {
            assertEquals(60.0, r[i], 1e-9,
                    "a constant 1.0/s must read 60 per minute at t=" + (i * 10) + "s, filling or full");
        }
    }

    @Test
    void rateNeedsElapsedTime() {
        // samples sharing one timestamp: change over zero elapsed time is unknown, not infinite
        double[] r = feedTimed("rate(a.x, \"1m\")", new long[]{5_000, 5_000, 5_000}, 1, 2, 3);
        assertTrue(Double.isNaN(r[2]), "no elapsed time — no rate");
    }

    @Test
    void durationLiteralsAreWindowsOnly_andRateRefusesACount() {
        assertThrows(IllegalArgumentException.class, () -> Expr.parse("\"5m\" + 1", Set.of()),
                "a duration is not a value");
        assertThrows(IllegalArgumentException.class, () -> Expr.parse("rate(a.x, 5)", Set.of(X)),
                "a rate is change per TIME — a count window is refused");
        assertThrows(IllegalArgumentException.class, () -> Expr.parse("lag(a.x, \"5m\")", Set.of(X)),
                "N samples ago is a count — a time window is refused");
        assertThrows(IllegalArgumentException.class, () -> Expr.parse("mean(a.x, \"5 furlongs\")", Set.of(X)));
    }

    @Test
    void windowSizeMustBeAPositiveIntegerLiteral() {
        for (String bad : new String[]{"mean(a.x)", "mean(a.x, 2.5)", "mean(a.x, 0)",
                "mean(a.x, -3)", "lag(a.x, a.x)", "delta(a.x, 2)"}) {
            assertThrows(IllegalArgumentException.class, () -> Expr.parse(bad, Set.of(X)), bad);
        }
    }

    @Test
    void evaluatorMatchesTheOldEngineOnTheOddCases() {
        // the exact edge semantics the old Expr.eval carried, now answered by the mirror
        Evaluator ev = Expr.parse("1 / 0", Set.of()).newEvaluator();
        assertTrue(Double.isNaN(ev.eval(0, Map.of())), "div-by-zero is no-point, not Infinity");
        GraphKey k = new GraphKey("a", "x");
        assertTrue(Double.isNaN(Expr.parse("a.x + 1").newEvaluator().eval(0, Map.of())),
                "missing ref propagates NaN");
        assertEquals(3.0, Expr.parse("a.x + 1").newEvaluator().eval(0, Map.of(k, 2.0)), 1e-9);
    }
}
