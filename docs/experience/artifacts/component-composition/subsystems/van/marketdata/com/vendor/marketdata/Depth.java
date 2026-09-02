package com.vendor.marketdata;
public class Depth {
    private final MdTick tick;
    public double value;
    public Depth(MdTick tick) { this.tick = tick; }
    public boolean calc() {
        value = (tick.ask - tick.bid) * 100;
        com.vendor.Audit.log("marketdata.depth", value); return true; }
}
