package com.acme.probe;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.MdTick;
/** A STATEFUL node: it accumulates. Reconstructing it would reset the count to zero. */
public class TickCount extends EventLogNode {
    private final MdTick tick;
    public int count;                       // deliberately NOT transient - this is the state under test
    public TickCount(MdTick tick) { this.tick = tick; }
    @OnTrigger public boolean calc() {
        count++;
        auditLog.info("stage", "probe.tickCount").info("value", (double) count);
        return true; }
}
