package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class PxRate extends EventLogNode implements RateApi {
    public transient double value = 1.0;
    public double rate() { return value; }
    @OnEventHandler public boolean onRate(Events.Rate r) {
        value = r.rate();
        auditLog.info("stage", "pricing.rateIn").info("value", value); return true; }
}
