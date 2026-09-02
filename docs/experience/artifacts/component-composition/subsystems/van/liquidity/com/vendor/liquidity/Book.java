package com.vendor.liquidity;
import com.vendor.marketdata.Depth;
public class Book {
    private final LqTick tick; private final Depth depth;
    public double value;
    public Book(LqTick tick, Depth depth) { this.tick = tick; this.depth = depth; }
    public boolean calc() {
        value = depth.value * tick.size;
        com.vendor.Audit.log("liquidity.book", value); return true; }
}
