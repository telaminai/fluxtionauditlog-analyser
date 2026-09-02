package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
/** Supervises the limit. It PROPAGATES only on a breach. */
public class LimitDetector extends EventLogNode implements LimitApi {
    private final ExposureApi exposure;
    public transient double limitValue = 250_000;
    public double breachedExposure() { return exposure.exposure(); }
    public double limit() { return limitValue; }
    public LimitDetector(ExposureApi exposure) { this.exposure = exposure; }
    @OnTrigger public boolean calc() {
        if (exposure.exposure() <= limitValue) return false;
        auditLog.info("stage", "risk.limitDetector").info("value", exposure.exposure()); return true; }
}
