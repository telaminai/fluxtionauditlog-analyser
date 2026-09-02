package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Events;
/** capital's own adapter for the shared TRADE event. */
public class CpTrade extends EventLogNode {
    public transient double qty = 1.0;
    @OnEventHandler public boolean onTrade(Events.Trade t) {
        qty = t.qty();
        auditLog.info("stage", "capital.tradeIn").info("value", qty); return true; }
}
