package com.vendor.risk;
import com.vendor.marketdata.MarketData;
import com.vendor.liquidity.Liquidity;
public class Risk {
    public final RkTrade  trade = new RkTrade();
    public final RkRate   rate  = new RkRate();
    public final Notional notional;
    public final Exposure exposure;
    public final Var      var;
    public Risk(MarketData md, Liquidity lq) {
        notional = new Notional(trade, md.mid);
        exposure = new Exposure(notional, lq.score);
        var      = new Var(rate, exposure, md.vol);
    }
}
