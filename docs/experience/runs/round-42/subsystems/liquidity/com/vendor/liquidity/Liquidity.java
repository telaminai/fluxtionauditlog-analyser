package com.vendor.liquidity;
import com.vendor.marketdata.MarketData;
import com.vendor.pricing.Pricing;
public class Liquidity {
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final LqTick tick = new LqTick();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Book   book;
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Score  score;
    public Liquidity(MarketData md, Pricing px) {
        book  = new Book(tick, md.depth);
        score = new Score(px.adjusted, book);
    }
}
