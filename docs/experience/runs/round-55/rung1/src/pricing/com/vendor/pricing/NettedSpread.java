package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class NettedSpread extends EventLogNode implements SpreadApi {
    private final PxRate rate; private final AdjustedApi adjusted;
    public transient double value;
    public NettedSpread(PxRate rate, AdjustedApi adjusted) { this.rate = rate; this.adjusted = adjusted; }
    public double spread() { return value; }
    @OnTrigger public boolean calc() {
        value = (adjusted.adjusted() - 0.5) * rate.rate();
        auditLog.info("stage", "pricing.spread").info("value", value); return true; }
}
