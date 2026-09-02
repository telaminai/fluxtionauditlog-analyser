package com.vendor.capital;
import com.vendor.risk.Risk;
public class Capital {
    public final CpTrade trade; public final CpConfig config;
    public final Charge charge; public final Buffer buffer; public final Fee fee;
    public final BreachCount breachCount; public final Alert alert; public final AlertCount alertCount;
    public Capital(Risk risk) {
        this.trade = new CpTrade(); this.config = new CpConfig();
        this.charge = new Charge(config, risk.exposure);
        this.buffer = new Buffer(trade, charge, risk.var);
        this.fee = new Fee(risk.exposure);
        this.breachCount = new BreachCount(risk.limitDetector);
        this.alert = new Alert(risk.limitDetector, charge);
        this.alertCount = new AlertCount(alert);
    }
    public Capital(CpTrade trade, CpConfig config, Charge charge, Buffer buffer, Fee fee,
                   BreachCount breachCount, Alert alert, AlertCount alertCount) {
        this.trade = trade; this.config = config; this.charge = charge; this.buffer = buffer;
        this.fee = fee; this.breachCount = breachCount; this.alert = alert; this.alertCount = alertCount;
    }
}
