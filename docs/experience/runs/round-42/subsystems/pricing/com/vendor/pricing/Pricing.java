package com.vendor.pricing;
import com.vendor.marketdata.MarketData;
public class Pricing {
    public final PxRate rate; public final Adjusted adjusted; public final Spread spread;
    public Pricing(MarketData md) {
        this.rate = new PxRate();
        this.adjusted = new Adjusted(md.mid, md.depth);
        this.spread = new Spread(rate, adjusted);
    }
    public Pricing(PxRate rate, Adjusted adjusted, Spread spread) {
        this.rate = rate; this.adjusted = adjusted; this.spread = spread;
    }
}
