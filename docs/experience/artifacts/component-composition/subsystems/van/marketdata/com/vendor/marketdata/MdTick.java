package com.vendor.marketdata;
import com.vendor.Events;
/** marketdata's own adapter for the shared TICK event. */
public class MdTick {
    public double bid, ask;
    public boolean onTick(Events.Tick t) {
        bid = t.bid(); ask = t.ask();
        com.vendor.Audit.log("marketdata.tickIn", ask - bid); return true; }
}
