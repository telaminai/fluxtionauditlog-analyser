package com.vendor.liquidity;
import com.vendor.Events;
/** liquidity's own adapter for the shared TICK event - it does not reuse marketdata's. */
public class LqTick {
    public double size = 1.0;
    public boolean onTick(Events.Tick t) {
        size = t.ask() - t.bid();
        com.vendor.Audit.log("liquidity.tickIn", size); return true; }
}
