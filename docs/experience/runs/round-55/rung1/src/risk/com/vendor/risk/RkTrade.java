package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class RkTrade extends EventLogNode {
    public transient double qty = 1.0;
    @OnEventHandler public boolean onTrade(Events.Trade t) {
        qty = t.qty();
        auditLog.info("stage", "risk.tradeIn").info("value", qty); return true; }
}
