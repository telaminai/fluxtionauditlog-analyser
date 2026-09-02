package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.Mid;
public class Notional extends EventLogNode {
    private final RkTrade trade; private final Mid mid;
    public transient double value;
    public Notional(RkTrade trade, Mid mid) { this.trade = trade; this.mid = mid; }
    @OnTrigger public boolean calc() {
        value = mid.value * trade.qty;
        auditLog.info("stage", "risk.notional").info("value", value); return true; }
}
