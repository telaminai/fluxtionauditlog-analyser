package com.vendor.capital;
import com.vendor.risk.Risk;
public class Capital {
    public final CpTrade trade; public final CpConfig config;
    public final Charge charge; public final Buffer buffer;
    public Capital(Risk risk) {
        this.trade = new CpTrade(); this.config = new CpConfig();
        this.charge = new Charge(config, risk.exposure);
        this.buffer = new Buffer(trade, charge, risk.var);
    }
    public Capital(CpTrade trade, CpConfig config, Charge charge, Buffer buffer) {
        this.trade = trade; this.config = config; this.charge = charge; this.buffer = buffer;
    }
}
