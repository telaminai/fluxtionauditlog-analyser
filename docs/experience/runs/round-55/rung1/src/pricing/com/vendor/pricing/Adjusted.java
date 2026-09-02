package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.vendor.contract.*;
public class Adjusted extends EventLogNode implements AdjustedApi {
    private final MidApi mid; private final DepthApi depth;
    public transient double value;
    public Adjusted(@AssignToField("mid") MidApi mid, @AssignToField("depth") DepthApi depth) { this.mid = mid; this.depth = depth; }
    public double adjusted() { return value; }
    @OnTrigger public boolean calc() {
        value = mid.mid() * (1 + depth.depth() / 10_000);
        auditLog.info("stage", "pricing.adjusted").info("value", value); return true; }
}
