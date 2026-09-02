package com.vendor.marketdata;
import com.vendor.contract.*;
/**
 * Entry point: mid, depth, volatility and a smoothed mid.
 * It IMPLEMENTS the contracts it provides, so a consumer wires this bean straight into any component
 * that asks for MidApi/DepthApi/VolApi/EwmaApi — the internal nodes never appear in the bean file.
 */
public class MarketDataPlus {
    public final MdTick tick; public final MdConfig config;
    public final Mid mid; public final Depth depth; public final Vol vol; public final Ewma ewma;
    public MarketDataPlus() {
        this.tick = new MdTick(); this.config = new MdConfig();
        this.mid = new Mid(tick); this.depth = new Depth(tick);
        this.vol = new Vol(config, mid); this.ewma = new Ewma(mid); }
    public MarketDataPlus(MdTick tick, MdConfig config, Mid mid, Depth depth, Vol vol, Ewma ewma) {
        this.tick = tick; this.config = config; this.mid = mid;
        this.depth = depth; this.vol = vol; this.ewma = ewma; }
}
