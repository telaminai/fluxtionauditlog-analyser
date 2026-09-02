package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class Notional extends EventLogNode implements NotionalApi {
    private final RkTrade trade; private final MidApi mid;
    public transient double value;
    public Notional(RkTrade trade, MidApi mid) { this.trade = trade; this.mid = mid; }
    public double notional() { return value; }
    @OnTrigger public boolean calc() {
        value = mid.mid() * trade.qty;
        auditLog.info("stage", "risk.notional").info("value", value); return true; }
}
