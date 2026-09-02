package com.vendor.risk;
import com.vendor.marketdata.MarketData;
import com.vendor.liquidity.Liquidity;
public class Risk {
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final RkTrade  trade = new RkTrade();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final RkRate   rate  = new RkRate();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Notional notional;
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Exposure exposure;
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Var      var;
    public Risk(MarketData md, Liquidity lq) {
        notional = new Notional(trade, md.mid);
        exposure = new Exposure(notional, lq.score);
        var      = new Var(rate, exposure, md.vol);
    }
}
