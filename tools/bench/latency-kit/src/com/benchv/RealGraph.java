package com.benchv;

import com.bench.LimitEvent;
import com.bench.MarketTick;
import com.bench.TradeEvent;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;

/**
 * Round 62 §11 — the realistic shape. <b>The same node classes are used by both arms.</b> Only the
 * wiring and the dispatch differ: Fluxtion resolves both at build time, the library resolves both from
 * data at runtime.
 *
 * <p>Nodes are deliberately LIGHT — a few flops each, as in a real graph. §9 used 4×4 matrices, which
 * are too heavy for either arm to inline, and that is exactly why §9 could not show the wiring
 * difference it set out to measure.
 *
 * <p>Two nodes take a strategy from injected configuration ({@link Smoother}). The strategy is a
 * NODE, not transient state: FLX-1001 rejected the first version because a transient field is not a
 * mapped dependency, so the builder could not find a constructor for the fields it could see. The
 * compiler was right — an injected collaborator the graph depends on is an edge, and declaring it as
 * hidden state is a category error. This is the second semantic correction the framework has made in
 * this round.
 */
public class RealGraph {

    /** The strategy a deployment chooses. Build-time for the generator, runtime wiring for a library. */
    public interface Smoother {
        double smooth(double prev, double next);
    }

    public static final class Ewma implements Smoother {
        private final double alpha;
        public Ewma(double alpha) { this.alpha = alpha; }
        @Override public double smooth(double prev, double next) { return alpha * next + (1 - alpha) * prev; }
    }

    public static final class Median3 implements Smoother {
        private double a, b;
        @Override public double smooth(double prev, double next) {
            double x = a; a = b; b = next;
            return x + b > 2 * prev ? prev : 0.5 * (x + b);
        }
    }

    // ---- event-handling roots -----------------------------------------------------------
    public static final class TickIn {
        public transient double bid, ask;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTick(MarketTick t) { bid = t.bid; ask = t.ask; }
    }

    public static final class TradeIn {
        public transient double px, qty; public transient int side;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTrade(TradeEvent t) { px = t.price; qty = t.qty; side = t.side; }
    }

    public static final class LimitIn {
        public transient double value = 108_000.0;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onLimit(LimitEvent e) { value = e.newLimit; }
    }

    // ---- derived nodes: real edges ------------------------------------------------------
    public static final class Mid {
        private final TickIn t; public transient double value;
        public Mid(TickIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = (t.bid + t.ask) * 0.5; }
    }

    public static final class Spread {
        private final TickIn t; public transient double value;
        public Spread(TickIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = t.ask - t.bid; }
    }

    /** Configuration-driven: which Smoother is a deployment decision. */
    public static final class SmoothMid {
        private final Mid m; private final Smoother s; public transient double value;
        public SmoothMid(Mid m, Smoother s) { this.m = m; this.s = s; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = s.smooth(value, m.value); }
    }

    public static final class SmoothSpread {
        private final Spread sp; private final Smoother s; public transient double value;
        public SmoothSpread(Spread sp, Smoother s) { this.sp = sp; this.s = s; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = s.smooth(value, sp.value); }
    }

    public static final class Vol {
        private final Mid m; private final SmoothMid sm; public transient double value;
        public Vol(Mid m, SmoothMid sm) { this.m = m; this.sm = sm; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { double d = m.value - sm.value; value = d < 0 ? -d : d; }
    }

    public static final class Position {
        private final TradeIn t; public transient double net, traded; public transient long fills;
        public Position(TradeIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { net += t.side * t.qty; traded += t.px * t.qty; fills++; }
    }

    public static final class Notional {
        private final SmoothMid sm; private final SmoothSpread ss; public transient double value;
        public Notional(SmoothMid sm, SmoothSpread ss) { this.sm = sm; this.ss = ss; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = sm.value * 1000.0 - ss.value; }
    }

    /** Fan-in from both paths. */
    public static final class Exposure {
        private final Notional n; private final Vol v; private final Position p; public transient double value;
        public Exposure(Notional n, Vol v, Position p) { this.n = n; this.v = v; this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = n.value * (1.0 + v.value * 0.001) + p.net * 10.0; }
    }

    public static final class Breach {
        private final Exposure e; private final LimitIn l; public transient long count;
        public Breach(Exposure e, LimitIn l) { this.e = e; this.l = l; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { if (e.value > l.value) count++; }
    }

    public static final class Charge {
        private final Exposure e; public transient double value;
        public Charge(Exposure e) { this.e = e; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = e.value * 0.08; }
    }

    public static final class Buffer {
        private final Charge c; public transient double value; public transient long updates;
        public Buffer(Charge c) { this.c = c; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = c.value * 1.25; updates++; }
    }
}
