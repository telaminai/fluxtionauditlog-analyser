package com.vendor.pricing;
import com.vendor.contract.*;
/** Entry point: adds the spread including the desk hedging overlay; owns the RATE event */
public class PricingHedged {
    public final PxRate rate; public final Adjusted adjusted; public final HedgedSpread spread;
    public PricingHedged(MidApi mid, DepthApi depth) {
        this.rate = new PxRate();
        this.adjusted = new Adjusted(mid, depth);
        this.spread = new HedgedSpread(rate, adjusted); }
    public PricingHedged(PxRate rate, Adjusted adjusted, HedgedSpread spread) {
        this.rate = rate; this.adjusted = adjusted; this.spread = spread; }
}
