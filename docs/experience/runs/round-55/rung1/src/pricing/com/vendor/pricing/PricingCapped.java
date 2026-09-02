package com.vendor.pricing;
import com.vendor.contract.*;
/** Entry point: adds the spread capped at the venue ceiling; owns the RATE event */
public class PricingCapped {
    public final PxRate rate; public final Adjusted adjusted; public final CappedSpread spread;
    public PricingCapped(MidApi mid, DepthApi depth) {
        this.rate = new PxRate();
        this.adjusted = new Adjusted(mid, depth);
        this.spread = new CappedSpread(rate, adjusted); }
    public PricingCapped(PxRate rate, Adjusted adjusted, CappedSpread spread) {
        this.rate = rate; this.adjusted = adjusted; this.spread = spread; }
}
