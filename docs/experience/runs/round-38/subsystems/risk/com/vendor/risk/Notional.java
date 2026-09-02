package com.vendor.risk;
import com.vendor.Stage; import com.vendor.marketdata.Mid;
public class Notional implements Stage {
    private final Mid mid; private double value;
    public Notional(Mid mid) { this.mid = mid; }
    public String name() { return "risk.notional"; }
    public void evaluate() { value = mid.value() * 1000; }
    public double value() { return value; }
}
