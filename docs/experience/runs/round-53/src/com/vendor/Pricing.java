package com.vendor;
public class Pricing {
    private final MarketData md;
    private double adjusted, spread, rate = 1.0;
    public Pricing(MarketData md) { this.md = md; }

    public void onTick(Events.Tick t) { recompute(); }
    public void onRate(Events.Rate r) { rate = r.rate(); recompute(); }
    private void recompute() {
        adjusted = md.mid() * (1 + md.depth() / 10_000);
        spread   = adjusted * rate;
        Audit.log("pricing.adjusted", adjusted);
        Audit.log("pricing.spread", spread);
    }
    public double adjusted() { return adjusted; }
    public double spread()   { return spread; }
}
