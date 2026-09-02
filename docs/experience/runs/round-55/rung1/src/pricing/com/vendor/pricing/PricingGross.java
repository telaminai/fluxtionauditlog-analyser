package com.vendor.pricing;
import com.vendor.contract.*;
/** Entry point: adds the spread gross of execution fees; owns the RATE event */
public class PricingGross {
    public final PxRate rate; public final Adjusted adjusted; public final GrossSpread spread;
    public PricingGross(MidApi mid, DepthApi depth) {
        this.rate = new PxRate();
        this.adjusted = new Adjusted(mid, depth);
        this.spread = new GrossSpread(rate, adjusted); }
    public PricingGross(PxRate rate, Adjusted adjusted, GrossSpread spread) {
        this.rate = rate; this.adjusted = adjusted; this.spread = spread; }
}
