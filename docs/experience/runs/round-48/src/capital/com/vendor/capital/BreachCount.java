package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
/** STATEFUL, and below the supervisor. */
public class BreachCount extends EventLogNode {
    private final LimitApi detector;
    public int breaches;
    public BreachCount(LimitApi detector) { this.detector = detector; }
    @OnTrigger public boolean calc() {
        breaches++;
        auditLog.info("stage", "capital.breachCount").info("value", (double) breaches); return true; }
}
