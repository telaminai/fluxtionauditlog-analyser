package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
/** STATEFUL and count-sensitive. */
public class Ewma extends EventLogNode implements EwmaApi {
    private final MidApi mid;
    public double value; public int samples;
    private static final double ALPHA = 0.3;
    public Ewma(MidApi mid) { this.mid = mid; }
    public double ewma() { return value; }
    @OnTrigger public boolean calc() {
        value = samples == 0 ? mid.mid() : ALPHA * mid.mid() + (1 - ALPHA) * value;
        samples++;
        auditLog.info("stage", "marketdata.ewma").info("value", value); return true; }
}
