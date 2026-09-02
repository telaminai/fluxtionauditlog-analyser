package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.Vol;
public class Var extends EventLogNode {
    private final RkRate rate; private final Exposure exposure; private final Vol vol;
    public transient double value;
    public Var(RkRate rate, Exposure exposure, Vol vol) {
        this.rate = rate; this.exposure = exposure; this.vol = vol; }
    @OnTrigger public boolean calc() {
        value = exposure.value * vol.value * rate.rate;
        auditLog.info("stage", "risk.var").info("value", value); return true; }
}
