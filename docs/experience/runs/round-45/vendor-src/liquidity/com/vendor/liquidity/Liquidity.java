package com.vendor.liquidity;
import com.vendor.marketdata.MarketData;
import com.vendor.pricing.Pricing;
public class Liquidity {
    public final LqTick tick; public final Book book; public final Score score;
    public Liquidity(MarketData md, Pricing px) {
        this.tick = new LqTick();
        this.book = new Book(tick, md.depth);
        this.score = new Score(px.adjusted, book);
    }
    public Liquidity(LqTick tick, Book book, Score score) {
        this.tick = tick; this.book = book; this.score = score;
    }
}
