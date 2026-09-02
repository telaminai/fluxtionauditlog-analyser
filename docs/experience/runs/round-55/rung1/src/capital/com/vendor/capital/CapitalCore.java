package com.vendor.capital;
import com.vendor.contract.*;
/** Entry point: capital charge, buffer and fee. No breach reporting. */
public class CapitalCore {
    public final CpTrade trade; public final CpConfig config;
    public final Charge charge; public final Buffer buffer; public final Fee fee;
    public CapitalCore(ExposureApi exposure, VarApi var) {
        this.trade = new CpTrade(); this.config = new CpConfig();
        this.charge = new Charge(config, exposure);
        this.buffer = new Buffer(trade, charge, var);
        this.fee = new Fee(exposure); }
    public CapitalCore(CpTrade trade, CpConfig config, Charge charge, Buffer buffer, Fee fee) {
        this.trade = trade; this.config = config; this.charge = charge;
        this.buffer = buffer; this.fee = fee; }
}
