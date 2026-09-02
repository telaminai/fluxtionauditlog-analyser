package com.vendor.pricing;
import com.vendor.marketdata.*;
public class Adjusted {
    private final Mid mid; private final Depth depth;
    public double value;
    public Adjusted(Mid mid, Depth depth) { this.mid = mid; this.depth = depth; }
    public boolean calc() {
        value = mid.value * (1 + depth.value / 10_000);
        com.vendor.Audit.log("pricing.adjusted", value); return true; }
}
