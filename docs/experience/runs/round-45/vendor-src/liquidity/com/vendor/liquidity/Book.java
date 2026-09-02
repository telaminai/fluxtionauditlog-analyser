package com.vendor.liquidity;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.Depth;
public class Book extends EventLogNode {
    private final LqTick tick; private final Depth depth;
    public transient double value;
    public Book(LqTick tick, Depth depth) { this.tick = tick; this.depth = depth; }
    @OnTrigger public boolean calc() {
        value = depth.value * tick.size;
        auditLog.info("stage", "liquidity.book").info("value", value); return true; }
}
