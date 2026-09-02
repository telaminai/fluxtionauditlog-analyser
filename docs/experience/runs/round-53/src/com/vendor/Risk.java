package com.vendor;
/**
 * Idiomatic component. Exposure, VaR and the breach streak, ordered internally. The limit check is
 * exposed as a query — a coarse component cannot hand another component a propagation signal, so a
 * downstream component has to ASK.
 */
public class Risk {
    private final MarketData md; private final Liquidity lq;
    private double notional, exposure, var, qty = 1.0, rate = 1.0;
    private final double limit = 250_000;
    private int streak, longest;

    public Risk(MarketData md, Liquidity lq) { this.md = md; this.lq = lq; }

    public void onTick(Events.Tick t)  { recompute(); }
    public void onTrade(Events.Trade t){ qty = t.qty(); recompute(); }
    public void onRate(Events.Rate r)  { rate = r.rate(); recompute(); }

    /**
     * Recompute derived figures because an INPUT changed, not because an event arrived.
     * Advances no counter. Call this when a neighbour's figures moved without an event we consume.
     */
    public void refresh() { compute(false); }

    private void recompute() { compute(true); }

    private void compute(boolean eventArrived) {
        notional = md.mid() * qty;
        exposure = notional * (1 + lq.score() / 1000);
        var      = exposure * md.vol() * rate;
        Audit.log("risk.notional", notional);
        Audit.log("risk.exposure", exposure);
        if (eventArrived) {
            if (exposure > limit) { streak++; longest = Math.max(longest, streak); }
            else streak = 0;
            Audit.log("risk.streak", streak);
        }
        if (exposure > limit) Audit.log("risk.limitDetector", exposure);
        Audit.log("risk.var", var);
    }
    public double exposure() { return exposure; }
    public double var()      { return var; }
    public int    streak()   { return streak; }
    /** A downstream component must ASK; there is no signal to hand it. */
    public boolean limitBreached() { return exposure > limit; }
}
