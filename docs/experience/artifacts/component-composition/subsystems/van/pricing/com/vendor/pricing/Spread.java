package com.vendor.pricing;
public class Spread {
    private final PxRate rate; private final Adjusted adjusted;
    public double value;
    public Spread(PxRate rate, Adjusted adjusted) { this.rate = rate; this.adjusted = adjusted; }
    public boolean calc() {
        value = adjusted.value * rate.rate;
        com.vendor.Audit.log("pricing.spread", value); return true; }
}
