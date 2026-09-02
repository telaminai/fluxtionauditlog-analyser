package com.vendor.pricing;
import com.vendor.contract.*;
/** Entry point: the adjusted price and the spread. Owns the RATE event for pricing. */
public class PricingFull {
    public final PxRate rate; public final Adjusted adjusted; public final Spread spread;
    public PricingFull(MidApi mid, DepthApi depth) {
        this.rate = new PxRate();
        this.adjusted = new Adjusted(mid, depth);
        this.spread = new Spread(rate, adjusted); }
    public PricingFull(PxRate rate, Adjusted adjusted, Spread spread) {
        this.rate = rate; this.adjusted = adjusted; this.spread = spread; }
}
