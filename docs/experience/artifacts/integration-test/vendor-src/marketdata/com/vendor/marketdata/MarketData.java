package com.vendor.marketdata;
/**
 * The published face of the marketdata subsystem.
 * Two constructors on purpose: the no-arg one is what a consumer declares, and it builds the
 * subtree; the all-fields one is what the generator needs so it can reconstruct the subtree in
 * the generated processor.
 */
public class MarketData {
    public final MdTick tick; public final MdConfig config;
    public final Mid mid; public final Depth depth; public final Vol vol;
    public MarketData() {
        this.tick = new MdTick(); this.config = new MdConfig();
        this.mid = new Mid(tick); this.depth = new Depth(tick); this.vol = new Vol(config, mid);
    }
    public MarketData(MdTick tick, MdConfig config, Mid mid, Depth depth, Vol vol) {
        this.tick = tick; this.config = config; this.mid = mid; this.depth = depth; this.vol = vol;
    }
}
