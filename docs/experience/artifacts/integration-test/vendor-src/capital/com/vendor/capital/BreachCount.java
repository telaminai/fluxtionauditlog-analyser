package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.Exposure;
/** STATEFUL: counts how many times exposure has crossed the limit. Reconstruct it and the count resets. */
public class BreachCount extends EventLogNode {
    private final Exposure exposure;
    public int breaches;
    public double limit = 10_000;
    public BreachCount(Exposure exposure) { this.exposure = exposure; }
    @OnTrigger public boolean calc() {
        if (exposure.value <= limit) return false;          // ARREST: nothing downstream runs
        breaches++;
        auditLog.info("stage", "capital.breachCount").info("value", (double) breaches);
        return true; }
}
