package com.vendor.capital;
import com.vendor.contract.*;
/** Entry point: everything CapitalCore has, plus breach alerting and the reported counts. */
public class CapitalRegulated {
    public final CpTrade trade; public final CpConfig config;
    public final Charge charge; public final Buffer buffer; public final Fee fee;
    public final BreachCount breachCount; public final Alert alert; public final AlertCount alertCount;
    public CapitalRegulated(ExposureApi exposure, VarApi var, LimitApi limits) {
        this.trade = new CpTrade(); this.config = new CpConfig();
        this.charge = new Charge(config, exposure);
        this.buffer = new Buffer(trade, charge, var);
        this.fee = new Fee(exposure);
        this.breachCount = new BreachCount(limits);
        this.alert = new Alert(limits, charge);
        this.alertCount = new AlertCount(alert); }
    public CapitalRegulated(CpTrade trade, CpConfig config, Charge charge, Buffer buffer, Fee fee,
                            BreachCount breachCount, Alert alert, AlertCount alertCount) {
        this.trade = trade; this.config = config; this.charge = charge; this.buffer = buffer;
        this.fee = fee; this.breachCount = breachCount; this.alert = alert; this.alertCount = alertCount; }
}
