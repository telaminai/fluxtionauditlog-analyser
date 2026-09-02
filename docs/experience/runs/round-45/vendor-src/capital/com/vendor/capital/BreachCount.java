package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.LimitDetector;
/** STATEFUL and below the arrest: it counts breaches, and only breaches. */
public class BreachCount extends EventLogNode {
    private final LimitDetector detector;
    public int breaches;
    public BreachCount(LimitDetector detector) { this.detector = detector; }
    @OnTrigger public boolean calc() {
        breaches++;
        auditLog.info("stage", "capital.breachCount").info("value", (double) breaches);
        return true; }
}
