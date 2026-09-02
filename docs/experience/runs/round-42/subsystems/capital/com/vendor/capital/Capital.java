package com.vendor.capital;
import com.vendor.risk.Risk;
public class Capital {
    public final CpTrade  trade  = new CpTrade();
    public final CpConfig config = new CpConfig();
    public final Charge   charge;
    public final Buffer   buffer;
    public Capital(Risk risk) {
        charge = new Charge(config, risk.exposure);
        buffer = new Buffer(trade, charge, risk.var);
    }
}
