package com.vendor;
public class Liquidity {
    private final MarketData md; private final Pricing px;
    private double book, score, size = 1.0;
    public Liquidity(MarketData md, Pricing px) { this.md = md; this.px = px; }

    public void onTick(Events.Tick t) {
        size  = t.ask() - t.bid();
        book  = md.depth() * size;
        score = px.adjusted() / 10 + book / 1000;
        Audit.log("liquidity.book", book);
        Audit.log("liquidity.score", score);
    }
    public double book()  { return book; }
    public double score() { return score; }
}
