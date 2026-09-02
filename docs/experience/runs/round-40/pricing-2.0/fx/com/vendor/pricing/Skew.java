package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.Vol;
/** pricing 2.0 - NEW public stage. Cross-vendor: it reads marketdata's Vol. */
public class Skew extends EventLogNode {
    private final Vol vol; private final Adjusted adjusted;
    public transient double value;
    public Skew(Vol vol, Adjusted adjusted) { this.vol = vol; this.adjusted = adjusted; }
    @OnTrigger public boolean calc() {
        value = vol.value * adjusted.value / 100;
        auditLog.info("stage", "pricing.skew").info("value", value); return true; }
}
