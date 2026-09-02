package com.vendor.marketdata;
public class MarketData {
    public final MdTick tick; public final MdConfig config;
    public final Mid mid; public final Depth depth; public final Vol vol; public final Ewma ewma;
    public MarketData() {
        this.tick = new MdTick(); this.config = new MdConfig();
        this.mid = new Mid(tick); this.depth = new Depth(tick);
        this.vol = new Vol(config, mid); this.ewma = new Ewma(mid);
    }
    public MarketData(MdTick tick, MdConfig config, Mid mid, Depth depth, Vol vol, Ewma ewma) {
        this.tick = tick; this.config = config; this.mid = mid;
        this.depth = depth; this.vol = vol; this.ewma = ewma;
    }
}
