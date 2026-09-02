package com.vendor.liquidity;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class LqTick extends EventLogNode {
    public transient double size = 1.0;
    @OnEventHandler public boolean onTick(Events.Tick t) {
        size = t.ask() - t.bid();
        auditLog.info("stage", "liquidity.tickIn").info("value", size); return true; }
}
