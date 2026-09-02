package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Events;
/**
 * pricing 2.0 - ADDITIVE. Still the adapter for the shared RATE event; now ALSO reacts to the
 * shared CONFIG event, for the spread multiplier. No signature changed, nothing removed.
 */
public class PxRate extends EventLogNode {
    public transient double rate = 1.0;
    public transient double mult = 1.0;                       // new in 2.0
    @OnEventHandler public boolean onRate(Events.Rate r) {
        rate = r.rate();
        auditLog.info("stage", "pricing.rateIn").info("value", rate); return true; }
    @OnEventHandler public boolean onConfig(Events.Config c) {   // new in 2.0
        if (!"spreadMult".equals(c.key())) return false;
        mult = c.value();
        auditLog.info("stage", "pricing.multIn").info("value", mult); return true; }
}
