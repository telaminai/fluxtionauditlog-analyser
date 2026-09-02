package com.vendor.pricing;
import com.vendor.contract.*;
/** Entry point: the adjusted price only. */
public class PricingSpot {
    public final Adjusted adjusted;
    public PricingSpot(MidApi mid, DepthApi depth) { this.adjusted = new Adjusted(mid, depth); }
    public PricingSpot(Adjusted adjusted) { this.adjusted = adjusted; }
}
