package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class RkRate extends EventLogNode {
    public transient double value = 1.0;
    @OnEventHandler public boolean onRate(Events.Rate r) {
        value = r.rate();
        auditLog.info("stage", "risk.rateIn").info("value", value); return true; }
}
