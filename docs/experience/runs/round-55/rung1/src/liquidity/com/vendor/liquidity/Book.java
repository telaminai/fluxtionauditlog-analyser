package com.vendor.liquidity;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class Book extends EventLogNode implements BookApi {
    private final LqTick tick; private final DepthApi depth;
    public transient double value;
    public Book(LqTick tick, DepthApi depth) { this.tick = tick; this.depth = depth; }
    public double book() { return value; }
    @OnTrigger public boolean calc() {
        value = depth.depth() * tick.size;
        auditLog.info("stage", "liquidity.book").info("value", value); return true; }
}
