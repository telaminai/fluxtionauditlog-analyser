package com.vendor.liquidity;
import com.vendor.marketdata.MarketData;
import com.vendor.pricing.Pricing;
public class Liquidity {
    public final LqTick tick = new LqTick();
    public final Book   book;
    public final Score  score;
    public Liquidity(MarketData md, Pricing px) {
        book  = new Book(tick, md.depth);
        score = new Score(px.adjusted, book);
    }
}
