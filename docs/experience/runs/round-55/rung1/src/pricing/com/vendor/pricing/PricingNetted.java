package com.vendor.pricing;
import com.vendor.contract.*;
/** Entry point: adds the spread net of the standing inventory offset; owns the RATE event */
public class PricingNetted {
    public final PxRate rate; public final Adjusted adjusted; public final NettedSpread spread;
    public PricingNetted(MidApi mid, DepthApi depth) {
        this.rate = new PxRate();
        this.adjusted = new Adjusted(mid, depth);
        this.spread = new NettedSpread(rate, adjusted); }
    public PricingNetted(PxRate rate, Adjusted adjusted, NettedSpread spread) {
        this.rate = rate; this.adjusted = adjusted; this.spread = spread; }
}
