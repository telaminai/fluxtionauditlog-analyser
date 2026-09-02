package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
/** pricing 2.0 - same constructor as 1.0. Now applies the spread multiplier. */
public class Spread extends EventLogNode {
    private final PxRate rate; private final Adjusted adjusted;
    public transient double value;
    public Spread(PxRate rate, Adjusted adjusted) { this.rate = rate; this.adjusted = adjusted; }
    @OnTrigger public boolean calc() {
        value = adjusted.value * rate.rate * rate.mult;
        auditLog.info("stage", "pricing.spread").info("value", value); return true; }
}
