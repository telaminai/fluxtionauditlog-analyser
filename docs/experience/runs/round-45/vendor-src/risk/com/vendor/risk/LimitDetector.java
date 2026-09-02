package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
/**
 * ARRESTS the path below it unless exposure is over the limit. Whether it arrests depends on the
 * VALUE, not on the event type - so which subgraph is live changes from event to event.
 */
public class LimitDetector extends EventLogNode {
    private final Exposure exposure;
    public double limit = 250_000;
    public LimitDetector(Exposure exposure) { this.exposure = exposure; }
    @OnTrigger public boolean calc() {
        if (exposure.value <= limit) return false;         // nothing below this runs
        auditLog.info("stage", "risk.limitDetector").info("value", exposure.value);
        return true; }
}
