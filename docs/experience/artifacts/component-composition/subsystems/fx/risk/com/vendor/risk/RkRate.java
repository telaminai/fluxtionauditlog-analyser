package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Events;
/** risk's own adapter for the shared RATE event - independent of pricing's. */
public class RkRate extends EventLogNode {
    public transient double rate = 1.0;
    @OnEventHandler public boolean onRate(Events.Rate r) {
        rate = r.rate();
        auditLog.info("stage", "risk.rateIn").info("value", rate); return true; }
}
