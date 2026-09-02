package com.vendor.capital;
import com.vendor.risk.Risk;
public class Capital {
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final CpTrade  trade  = new CpTrade();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final CpConfig config = new CpConfig();
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Charge   charge;
    @com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore public final Buffer   buffer;
    public Capital(Risk risk) {
        charge = new Charge(config, risk.exposure);
        buffer = new Buffer(trade, charge, risk.var);
    }
}
