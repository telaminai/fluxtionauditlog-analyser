package com.bench;
/** A MUTABLE event, allocated once and refilled per tick. No garbage per event. */
public class MarketTick {
    public double bid, ask;
    public long seq;
    public MarketTick set(double bid, double ask, long seq) {
        this.bid = bid; this.ask = ask; this.seq = seq; return this;
    }
}
