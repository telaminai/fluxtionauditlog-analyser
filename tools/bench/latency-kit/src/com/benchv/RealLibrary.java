package com.benchv;

import com.bench.LimitEvent;
import com.bench.MarketTick;
import com.bench.TradeEvent;

/**
 * Round 62 §11 — the hand-written library, <b>written the way a competent engineer would</b>, using
 * the same node classes as the generated arm.
 *
 * <p>It does everything a good implementation does: the topological order is computed once at wiring
 * time, not per event; dispatch is a switch on the event type into a pre-built subscriber array, not a
 * map lookup; there is no reflection anywhere on the event path.
 *
 * <p>What it cannot do is <b>bind statically</b>. Its wiring is data — which nodes exist, which
 * subscribe to what, which strategy each uses — so every node is reached through a {@code Step}
 * reference the compiler cannot resolve. That is not a flaw in the implementation; it is what "wiring
 * decided at runtime" means, and it is the thing a generator turns into a constant.
 */
public class RealLibrary {

    /** What a wiring-driven framework must reduce every node to. */
    public interface Step {
        void fire();
    }

    private final Step[] onTick;
    private final Step[] onTrade;
    private final Step[] onLimit;

    public final RealGraph.TickIn tickIn = new RealGraph.TickIn();
    public final RealGraph.TradeIn tradeIn = new RealGraph.TradeIn();
    public final RealGraph.LimitIn limitIn = new RealGraph.LimitIn();
    public final RealGraph.Mid mid = new RealGraph.Mid(tickIn);
    public final RealGraph.Spread spread = new RealGraph.Spread(tickIn);
    public final RealGraph.SmoothMid smoothMid;
    public final RealGraph.SmoothSpread smoothSpread;
    public final RealGraph.Vol vol;
    public final RealGraph.Position position = new RealGraph.Position(tradeIn);
    public final RealGraph.Notional notional;
    public final RealGraph.Exposure exposure;
    public final RealGraph.Breach breach;
    public final RealGraph.Charge charge;
    public final RealGraph.Buffer buffer;

    public RealLibrary(RealGraph.Smoother midSmoother, RealGraph.Smoother spreadSmoother) {
        smoothMid = new RealGraph.SmoothMid(mid, midSmoother);
        smoothSpread = new RealGraph.SmoothSpread(spread, spreadSmoother);
        vol = new RealGraph.Vol(mid, smoothMid);
        notional = new RealGraph.Notional(smoothMid, smoothSpread);
        exposure = new RealGraph.Exposure(notional, vol, position);
        breach = new RealGraph.Breach(exposure, limitIn);
        charge = new RealGraph.Charge(exposure);
        buffer = new RealGraph.Buffer(charge);

        // topological order per event type, computed ONCE at wiring time
        onTick = new Step[]{mid::calc, smoothMid::calc, spread::calc, smoothSpread::calc, vol::calc,
                notional::calc, exposure::calc, charge::calc, buffer::calc, breach::calc};
        onTrade = new Step[]{position::calc, exposure::calc, charge::calc, buffer::calc, breach::calc};
        onLimit = new Step[]{breach::calc};
    }

    public void onEvent(Object e) {
        Step[] plan;
        if (e instanceof MarketTick) {
            tickIn.onTick((MarketTick) e);
            plan = onTick;
        } else if (e instanceof TradeEvent) {
            tradeIn.onTrade((TradeEvent) e);
            plan = onTrade;
        } else if (e instanceof LimitEvent) {
            limitIn.onLimit((LimitEvent) e);
            plan = onLimit;
        } else {
            return;
        }
        for (int i = 0; i < plan.length; i++) {
            plan[i].fire();
        }
    }
}
