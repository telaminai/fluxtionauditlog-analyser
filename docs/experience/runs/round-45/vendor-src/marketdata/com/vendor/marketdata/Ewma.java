package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
/** STATEFUL and COUNT-SENSITIVE: an extra update skews it, a missed one lags it. */
public class Ewma extends EventLogNode {
    private final Mid mid;
    public double value; public int samples;
    private static final double ALPHA = 0.3;
    public Ewma(Mid mid) { this.mid = mid; }
    @OnTrigger public boolean calc() {
        value = samples == 0 ? mid.value : ALPHA * mid.value + (1 - ALPHA) * value;
        samples++;
        auditLog.info("stage", "marketdata.ewma").info("value", value);
        return true; }
}
