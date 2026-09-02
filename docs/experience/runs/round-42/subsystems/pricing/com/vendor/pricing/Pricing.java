package com.vendor.pricing;
import com.vendor.marketdata.MarketData;
public class Pricing {
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final PxRate   rate = new PxRate();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Adjusted adjusted;
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Spread   spread;
    public Pricing(MarketData md) {
        adjusted = new Adjusted(md.mid, md.depth);
        spread   = new Spread(rate, adjusted);
    }
}
