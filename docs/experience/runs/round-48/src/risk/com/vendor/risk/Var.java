package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.vendor.contract.*;
public class Var extends EventLogNode implements VarApi {
    private final RkRate rate; private final ExposureApi exposure; private final VolApi vol;
    public transient double value;
    public Var(RkRate rate, @AssignToField("exposure") ExposureApi exposure, @AssignToField("vol") VolApi vol) {
        this.rate = rate; this.exposure = exposure; this.vol = vol; }
    public double var() { return value; }
    @OnTrigger public boolean calc() {
        value = exposure.exposure() * vol.vol() * rate.value;
        auditLog.info("stage", "risk.var").info("value", value); return true; }
}
