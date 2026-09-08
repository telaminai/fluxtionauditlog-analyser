package com.benchv;

import com.bench.MarketTick;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;

/**
 * Round 62 §13 — <b>light</b> nodes at scale, which is the case §9 and §12 between them left open.
 *
 * <p>§9 measured 50 nodes but each was a 4×4 matrix multiply — too heavy for either arm to inline, so
 * the advantage was capped and inverted. §12 measured light nodes but only 14 of them. The open
 * question is whether the 9.45× at 14 light nodes survives to 50, or whether the inlining budget
 * arrives somewhere in between.
 *
 * <p>Shape: one event source fanning out to N independent lanes of three chained nodes each, a
 * realistic derived-value graph. Every node is a handful of flops.
 */
public class LaneGraph {

    public static final class Src {
        public transient double bid, ask;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTick(MarketTick t) { bid = t.bid; ask = t.ask; }
    }

    /** First in a lane: reads the source. */
    public static final class A {
        private final Src s; private final double k; public transient double value;
        public A(Src s, double k) { this.s = s; this.k = k; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = (s.bid + s.ask) * k; }
    }

    /** Middle of a lane. */
    public static final class B {
        private final A a; public transient double value;
        public B(A a) { this.a = a; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = a.value * 1.5 - 0.25; }
    }

    /** End of a lane. */
    public static final class C {
        private final B b; public transient double value; public transient long hits;
        public C(B b) { this.b = b; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { double v = b.value; value = v > 0 ? v * 0.8 : -v; hits++; }
    }
}
