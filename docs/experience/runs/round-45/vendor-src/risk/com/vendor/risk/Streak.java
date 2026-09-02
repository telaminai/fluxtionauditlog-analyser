package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
/**
 * STATEFUL and RUN-COUNT SENSITIVE: consecutive breaches. It hangs off Exposure rather than the
 * detector, because it must see the clean events too in order to reset. Run it when exposure did
 * not change and the streak is wrong in both directions.
 */
public class Streak extends EventLogNode {
    private final Exposure exposure;
    @NoTriggerReference private final LimitDetector limits;   // data only - never a trigger
    public int streak; public int longest;
    public Streak(Exposure exposure, LimitDetector limits) {
        this.exposure = exposure; this.limits = limits; }
    @OnTrigger public boolean calc() {
        if (exposure.value > limits.limit) { streak++; longest = Math.max(longest, streak); }
        else streak = 0;
        auditLog.info("stage", "risk.streak").info("value", (double) streak);
        return true; }
}
