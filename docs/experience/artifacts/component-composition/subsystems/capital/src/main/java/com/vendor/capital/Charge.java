package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.Exposure;
/** capital subsystem. Requires risk.Exposure. */
public class Charge extends EventLogNode {
    private final Exposure exposure;
    public transient double value;
    public Charge(Exposure exposure) { this.exposure = exposure; }
    @OnTrigger public boolean calc() {
        value = exposure.value * 0.08;
        auditLog.info("stage", "capital.charge").info("value", value);
        return true;
    }
}
