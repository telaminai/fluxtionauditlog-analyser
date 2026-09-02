package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.Exposure;
/**
 * Structure is static, behaviour is dynamic: this node's POSITION in the dispatch is fixed at build
 * time, and the function it applies is replaceable at runtime through a registered service.
 */
public class Fee extends EventLogNode {
    private final Exposure exposure;
    private FeeStrategy strategy = new FeeStrategy() {
        public double fee(double e) { return e * 0.01; }
        public String name() { return "default-1pct"; }
    };
    public transient double value;
    public Fee(Exposure exposure) { this.exposure = exposure; }

    @ServiceRegistered public void feeStrategy(FeeStrategy s, String name) {
        this.strategy = s;
        auditLog.info("strategySwapped", s.name());
    }
    @OnTrigger public boolean calc() {
        value = strategy.fee(exposure.value);
        auditLog.info("stage", "capital.fee").info("value", value).info("strategy", strategy.name());
        return true; }
}
