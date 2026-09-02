package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.Exposure;
public class Charge extends EventLogNode {
    private final CpConfig cfg; private final Exposure exposure;
    public transient double value;
    public Charge(CpConfig cfg, Exposure exposure) { this.cfg = cfg; this.exposure = exposure; }
    @OnTrigger public boolean calc() {
        value = exposure.value * cfg.pct;
        auditLog.info("stage", "capital.charge").info("value", value); return true; }
}
