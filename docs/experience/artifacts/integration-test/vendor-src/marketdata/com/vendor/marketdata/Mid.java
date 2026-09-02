package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
public class Mid extends EventLogNode {
    private final MdTick tick;
    public transient double value;
    public Mid(MdTick tick) { this.tick = tick; }
    @OnTrigger public boolean calc() {
        value = (tick.bid + tick.ask) / 2;
        auditLog.info("stage", "marketdata.mid").info("value", value); return true; }
}
