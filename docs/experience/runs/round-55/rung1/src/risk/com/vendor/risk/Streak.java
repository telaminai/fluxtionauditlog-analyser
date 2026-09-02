package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.vendor.contract.*;
/** STATEFUL: consecutive breaches, and it resets. */
public class Streak extends EventLogNode {
    private final ExposureApi exposure;
    @NoTriggerReference private final LimitApi limits;
    public int streak; public int longest;
    public Streak(@AssignToField("exposure") ExposureApi exposure, @AssignToField("limits") LimitApi limits) { this.exposure = exposure; this.limits = limits; }
    @OnTrigger public boolean calc() {
        if (exposure.exposure() > limits.limit()) { streak++; longest = Math.max(longest, streak); }
        else streak = 0;
        auditLog.info("stage", "risk.streak").info("value", (double) streak); return true; }
}
