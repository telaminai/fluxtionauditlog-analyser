package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
public class Spread extends EventLogNode {
    private final PxRate rate; private final Adjusted adjusted;
    public transient double value;
    public Spread(PxRate rate, Adjusted adjusted) { this.rate = rate; this.adjusted = adjusted; }
    @OnTrigger public boolean calc() {
        value = adjusted.value * rate.rate;
        auditLog.info("stage", "pricing.spread").info("value", value); return true; }
}
