package com.vendor.marketdata;
public class Mid {
    private final MdTick tick;
    public double value;
    public Mid(MdTick tick) { this.tick = tick; }
    public boolean calc() {
        value = (tick.bid + tick.ask) / 2;
        com.vendor.Audit.log("marketdata.mid", value); return true; }
}
