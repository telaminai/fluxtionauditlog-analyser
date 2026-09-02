package com.vendor.pricing;
import com.vendor.marketdata.MarketData;
public class Pricing {
    public final PxRate   rate = new PxRate();
    public final Adjusted adjusted;
    public final Spread   spread;
    public Pricing(MarketData md) {
        adjusted = new Adjusted(md.mid, md.depth);
        spread   = new Spread(rate, adjusted);
    }
}
