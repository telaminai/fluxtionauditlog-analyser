package com.vendor.risk;
import com.vendor.marketdata.MarketData;
import com.vendor.liquidity.Liquidity;
public class Risk {
    public final RkTrade trade; public final RkRate rate;
    public final Notional notional; public final Exposure exposure; public final Var var;
    public final LimitDetector limitDetector; public final Streak streak;
    public Risk(MarketData md, Liquidity lq) {
        this.trade = new RkTrade(); this.rate = new RkRate();
        this.notional = new Notional(trade, md.mid);
        this.exposure = new Exposure(notional, lq.score);
        this.var = new Var(rate, exposure, md.vol);
        this.limitDetector = new LimitDetector(exposure);
        this.streak = new Streak(exposure, limitDetector);
    }
    public Risk(RkTrade trade, RkRate rate, Notional notional, Exposure exposure, Var var,
                LimitDetector limitDetector, Streak streak) {
        this.trade = trade; this.rate = rate; this.notional = notional; this.exposure = exposure;
        this.var = var; this.limitDetector = limitDetector; this.streak = streak;
    }
}
