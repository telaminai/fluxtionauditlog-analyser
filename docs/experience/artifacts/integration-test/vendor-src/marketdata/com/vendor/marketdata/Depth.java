package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
public class Depth extends EventLogNode {
    private final MdTick tick;
    public transient double value;
    public Depth(MdTick tick) { this.tick = tick; }
    @OnTrigger public boolean calc() {
        value = (tick.ask - tick.bid) * 100;
        auditLog.info("stage", "marketdata.depth").info("value", value); return true; }
}
