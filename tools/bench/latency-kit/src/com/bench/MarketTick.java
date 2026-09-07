package com.bench;
/** The event. A plain POJO — Fluxtion does not require it to extend anything. */
public class MarketTick {
    public double bid, ask;
    public long seq;
    public MarketTick set(double bid, double ask, long seq) {
        this.bid = bid; this.ask = ask; this.seq = seq; return this;
    }
}
