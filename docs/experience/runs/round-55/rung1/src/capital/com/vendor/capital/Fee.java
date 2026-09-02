package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
/** Position fixed at build time; the function is replaceable at runtime through a service. */
public class Fee extends EventLogNode {
    private final ExposureApi exposure;
    private FeeStrategy strategy = FeeStrategies.DEFAULT;
    public transient double value;
    public Fee(ExposureApi exposure) { this.exposure = exposure; }
    @ServiceRegistered public void feeStrategy(FeeStrategy s, String name) {
        this.strategy = s;
        auditLog.info("strategySwapped", s.name()); }
    @OnTrigger public boolean calc() {
        value = strategy.fee(exposure.exposure());
        auditLog.info("stage", "capital.fee").info("value", value); return true; }
}
