package com.vendor.pricing;
import com.vendor.contract.*;
/** Entry point: adds the spread with the smoothing factor applied; owns the RATE event */
public class PricingSmoothed {
    public final PxRate rate; public final Adjusted adjusted; public final SmoothedSpread spread;
    public PricingSmoothed(MidApi mid, DepthApi depth) {
        this.rate = new PxRate();
        this.adjusted = new Adjusted(mid, depth);
        this.spread = new SmoothedSpread(rate, adjusted); }
    public PricingSmoothed(PxRate rate, Adjusted adjusted, SmoothedSpread spread) {
        this.rate = rate; this.adjusted = adjusted; this.spread = spread; }
}
