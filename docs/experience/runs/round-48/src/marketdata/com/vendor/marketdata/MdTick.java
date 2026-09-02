package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class MdTick extends EventLogNode {
    public transient double bid, ask;
    @OnEventHandler public boolean onTick(Events.Tick t) {
        bid = t.bid(); ask = t.ask();
        auditLog.info("stage", "marketdata.tickIn").info("value", ask - bid); return true; }
}
