package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class CappedSpread extends EventLogNode implements SpreadApi {
    private final PxRate rate; private final AdjustedApi adjusted;
    public transient double value;
    public CappedSpread(PxRate rate, AdjustedApi adjusted) { this.rate = rate; this.adjusted = adjusted; }
    public double spread() { return value; }
    @OnTrigger public boolean calc() {
        value = Math.min(adjusted.adjusted() * rate.rate(), 250.0);
        auditLog.info("stage", "pricing.spread").info("value", value); return true; }
}
