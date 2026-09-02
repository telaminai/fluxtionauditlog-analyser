package com.vendor.risk;
import com.vendor.contract.*;
/** Entry point: exposure and value-at-risk. No limit supervision. */
public class RiskBasic {
    public final RkTrade trade; public final RkRate rate;
    public final Notional notional; public final Exposure exposure; public final Var var;
    public RiskBasic(MidApi mid, VolApi vol, ScoreApi score) {
        this.trade = new RkTrade(); this.rate = new RkRate();
        this.notional = new Notional(trade, mid);
        this.exposure = new Exposure(notional, score);
        this.var = new Var(rate, exposure, vol); }
    public RiskBasic(RkTrade trade, RkRate rate, Notional notional, Exposure exposure, Var var) {
        this.trade = trade; this.rate = rate; this.notional = notional;
        this.exposure = exposure; this.var = var; }
}
