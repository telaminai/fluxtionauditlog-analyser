package com.vendor.capital;
import com.vendor.risk.Risk;
public class Capital {
    public final CpTrade trade; public final CpConfig config;
    public final Charge charge; public final Buffer buffer;
    public final Fee fee; public final BreachCount breachCount;

    /** what a CONSUMER declares: only this subsystem's external dependencies. */
    public Capital(Risk risk) {
        this.trade = new CpTrade(); this.config = new CpConfig();
        this.charge = new Charge(config, risk.exposure);
        this.buffer = new Buffer(trade, charge, risk.var);
        this.fee = new Fee(risk.exposure);
        this.breachCount = new BreachCount(risk.exposure);
    }
    /** what the GENERATOR needs: every node-typed field. */
    public Capital(CpTrade trade, CpConfig config, Charge charge, Buffer buffer,
                   Fee fee, BreachCount breachCount) {
        this.trade = trade; this.config = config; this.charge = charge;
        this.buffer = buffer; this.fee = fee; this.breachCount = breachCount;
    }
}
