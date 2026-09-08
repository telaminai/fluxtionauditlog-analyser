package com.benchv;

import com.bench.MarketTick;

/**
 * Round 62 §13 — the library equivalent of {@link LaneGraph}: same node classes, topological order
 * computed once at wiring time, dispatch through a pre-built array. It cannot bind statically because
 * its wiring is data.
 */
public class LaneLibrary {

    public interface Step {
        void fire();
    }

    public final LaneGraph.Src src = new LaneGraph.Src();
    public final LaneGraph.C[] tails;
    private final Step[] plan;

    public LaneLibrary(int lanes) {
        tails = new LaneGraph.C[lanes];
        plan = new Step[lanes * 3];
        for (int i = 0; i < lanes; i++) {
            LaneGraph.A a = new LaneGraph.A(src, 0.5 + i * 0.01);
            LaneGraph.B b = new LaneGraph.B(a);
            LaneGraph.C c = new LaneGraph.C(b);
            tails[i] = c;
            plan[i * 3] = a::calc;
            plan[i * 3 + 1] = b::calc;
            plan[i * 3 + 2] = c::calc;
        }
    }

    public void onEvent(Object e) {
        if (e instanceof MarketTick) {
            src.onTick((MarketTick) e);
            for (int i = 0; i < plan.length; i++) {
                plan[i].fire();
            }
        }
    }

    public double checksum() {
        double t = 0.0;
        for (LaneGraph.C c : tails) { t += c.value + c.hits; }
        return t;
    }
}
