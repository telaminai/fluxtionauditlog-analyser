package com.vendor.pricing;
/** pricing 2.0 - same constructor as 1.0. Now applies the spread multiplier. */
public class Spread {
    private final PxRate rate; private final Adjusted adjusted;
    public double value;
    public Spread(PxRate rate, Adjusted adjusted) { this.rate = rate; this.adjusted = adjusted; }
    public boolean calc() {
        value = adjusted.value * rate.rate * rate.mult;
        com.vendor.Audit.log("pricing.spread", value); return true; }
}
