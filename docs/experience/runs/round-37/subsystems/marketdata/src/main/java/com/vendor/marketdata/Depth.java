package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Tick;
/** marketdata subsystem, stage 2. */
public class Depth extends EventLogNode {
    public transient double value;
    @OnEventHandler public boolean onTick(Tick t) {
        value = (t.ask() - t.bid()) * 100;
        auditLog.info("stage", "marketdata.depth").info("value", value);
        return true;
    }
}
