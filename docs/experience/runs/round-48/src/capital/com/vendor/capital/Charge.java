package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class Charge extends EventLogNode implements ChargeApi {
    private final CpConfig cfg; private final ExposureApi exposure;
    public transient double value;
    public Charge(CpConfig cfg, ExposureApi exposure) { this.cfg = cfg; this.exposure = exposure; }
    public double charge() { return value; }
    @OnTrigger public boolean calc() {
        value = exposure.exposure() * cfg.pct;
        auditLog.info("stage", "capital.charge").info("value", value); return true; }
}
