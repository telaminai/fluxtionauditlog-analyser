package com.vendor.risk;
import com.vendor.contract.*;
/** Entry point: adds limit supervision and a breach streak. */
public class RiskSupervised {
    public final RkTrade trade; public final RkRate rate;
    public final Notional notional; public final Exposure exposure; public final Var var;
    public final LimitDetector limitDetector; public final Streak streak;
    public RiskSupervised(MidApi mid, VolApi vol, ScoreApi score) {
        this.trade = new RkTrade(); this.rate = new RkRate();
        this.notional = new Notional(trade, mid);
        this.exposure = new Exposure(notional, score);
        this.var = new Var(rate, exposure, vol);
        this.limitDetector = new LimitDetector(exposure);
        this.streak = new Streak(exposure, limitDetector); }
    public RiskSupervised(RkTrade trade, RkRate rate, Notional notional, Exposure exposure,
                          Var var, LimitDetector limitDetector, Streak streak) {
        this.trade = trade; this.rate = rate; this.notional = notional; this.exposure = exposure;
        this.var = var; this.limitDetector = limitDetector; this.streak = streak; }
}
