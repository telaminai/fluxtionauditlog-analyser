package com.acme;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.MdTick;
/** Consumer-side stateful node, used by the rebuild test. */
public class Counter extends EventLogNode {
    private final MdTick tick;
    public int count;
    public Counter(MdTick tick) { this.tick = tick; }
    @OnTrigger public boolean calc() {
        count++;
        auditLog.info("stage", "acme.counter").info("value", (double) count);
        return true; }
}
