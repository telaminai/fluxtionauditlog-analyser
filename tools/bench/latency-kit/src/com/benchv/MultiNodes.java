package com.benchv;

import com.bench.LimitEvent;
import com.bench.MarketTick;
import com.bench.TradeEvent;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;

/**
 * Three event types with three DIFFERENT paths through one graph, so the benchmark measures
 * type dispatch and partial propagation rather than a single straight line.
 *
 * <ul>
 *   <li>{@code MarketTick} → TickIn → Mid/Spread → Ewma/Vol/Notional → Exposure → …</li>
 *   <li>{@code TradeEvent} → TradeIn → Position → Exposure → …  (Mid, Spread, Ewma, Vol and
 *       Notional are NOT on this path and keep their previous values)</li>
 *   <li>{@code LimitEvent} → LimitIn → Limit, and nothing downstream of Limit exists</li>
 * </ul>
 *
 * <p>Every trigger is void ({@code failBuildIfMissingBooleanReturn = false}), so there is no dirty
 * filtering: every node ON THE PATH fires, every time. The hand-rolled equivalent has to reproduce
 * that subset and that order exactly — which is what the correctness harness checks before any
 * timing is believed.
 */
public class MultiNodes {

    public static class TickIn {
        public double bid, ask;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTick(MarketTick t) { bid = t.bid; ask = t.ask; }
    }

    public static class TradeIn {
        public double price, qty;
        public int side;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTrade(TradeEvent t) { price = t.price; qty = t.qty; side = t.side; }
    }

    public static class LimitIn {
        public double value = 108_000.0;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onLimit(LimitEvent e) { value = e.newLimit; }
    }

    public static class Mid {
        private final TickIn t; public double value;
        public Mid(TickIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = (t.bid + t.ask) * 0.5; }
    }

    public static class Spread {
        private final TickIn t; public double value;
        public Spread(TickIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = t.ask - t.bid; }
    }

    public static class Ewma {
        private final Mid m; public double value; private long n;
        public Ewma(Mid m) { this.m = m; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = n++ == 0 ? m.value : 0.3 * m.value + 0.7 * value; }
    }

    public static class Vol {
        private final Mid m; private final Ewma e; public double value;
        public Vol(Mid m, Ewma e) { this.m = m; this.e = e; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { double d = m.value - e.value; value = d < 0 ? -d : d; }
    }

    public static class Notional {
        private final Mid m; private final Spread s; public double value;
        public Notional(Mid m, Spread s) { this.m = m; this.s = s; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = m.value * 1000.0 - s.value; }
    }

    /** Trade-path only: running position and traded notional. */
    public static class Position {
        private final TradeIn t; public double net, traded; public long fills;
        public Position(TradeIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { net += t.side * t.qty; traded += t.price * t.qty; fills++; }
    }

    /** Where the two paths converge — reached from BOTH TickIn and TradeIn. */
    public static class Exposure {
        private final Notional n; private final Vol v; private final Position p; public double value;
        public Exposure(Notional n, Vol v, Position p) { this.n = n; this.v = v; this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = n.value * (1.0 + v.value * 0.001) + p.net * 10.0; }
    }

    /** Reached from the tick path, the trade path AND the control path. */
    public static class Limit {
        private final Exposure e; private final LimitIn l; public long breaches;
        public Limit(Exposure e, LimitIn l) { this.e = e; this.l = l; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { if (e.value > l.value) breaches++; }
    }

    public static class Charge {
        private final Exposure e; public double value;
        public Charge(Exposure e) { this.e = e; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = e.value * 0.08; }
    }

    public static class Buffer {
        private final Charge c; public double value; public long updates;
        public Buffer(Charge c) { this.c = c; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = c.value * 1.25; updates++; }
    }
}
