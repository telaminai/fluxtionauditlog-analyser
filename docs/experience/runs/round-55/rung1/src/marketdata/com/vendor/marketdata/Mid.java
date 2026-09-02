package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class Mid extends EventLogNode implements MidApi {
    private final MdTick tick;
    public transient double value;
    public Mid(MdTick tick) { this.tick = tick; }
    public double mid() { return value; }
    @OnTrigger public boolean calc() {
        value = (tick.bid + tick.ask) / 2;
        auditLog.info("stage", "marketdata.mid").info("value", value); return true; }
}
