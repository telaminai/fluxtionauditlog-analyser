package com.vendor.pricing;
import com.vendor.Stage; import com.vendor.marketdata.Depth; import com.vendor.marketdata.Mid;
public class Adjusted implements Stage {
    private final Mid mid; private final Depth depth; private double value;
    public Adjusted(Mid mid, Depth depth) { this.mid = mid; this.depth = depth; }
    public String name() { return "pricing.adjusted"; }
    public void evaluate() { value = mid.value() * (1 + depth.value() / 10_000); }
    public double value() { return value; }
}
