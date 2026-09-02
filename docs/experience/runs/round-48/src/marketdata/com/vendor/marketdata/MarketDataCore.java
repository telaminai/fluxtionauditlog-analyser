package com.vendor.marketdata;
import com.vendor.contract.*;
/** Entry point: mid and depth only. */
public class MarketDataCore {
    public final MdTick tick; public final Mid mid; public final Depth depth;
    public MarketDataCore() {
        this.tick = new MdTick(); this.mid = new Mid(tick); this.depth = new Depth(tick); }
    public MarketDataCore(MdTick tick, Mid mid, Depth depth) {
        this.tick = tick; this.mid = mid; this.depth = depth; }
}
