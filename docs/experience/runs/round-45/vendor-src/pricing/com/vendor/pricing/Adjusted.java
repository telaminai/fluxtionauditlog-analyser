package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.*;
public class Adjusted extends EventLogNode {
    private final Mid mid; private final Depth depth;
    public transient double value;
    public Adjusted(Mid mid, Depth depth) { this.mid = mid; this.depth = depth; }
    @OnTrigger public boolean calc() {
        value = mid.value * (1 + depth.value / 10_000);
        auditLog.info("stage", "pricing.adjusted").info("value", value); return true; }
}
