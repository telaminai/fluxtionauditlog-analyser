package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Events;
/** pricing's own adapter for the shared RATE event. */
public class PxRate extends EventLogNode {
    public transient double rate = 1.0;
    @OnEventHandler public boolean onRate(Events.Rate r) {
        rate = r.rate();
        auditLog.info("stage", "pricing.rateIn").info("value", rate); return true; }
}
