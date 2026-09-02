package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Tick;
/** marketdata subsystem, stage 1. */
public class Mid extends EventLogNode {
    public transient double value;
    @OnEventHandler public boolean onTick(Tick t) {
        value = (t.bid() + t.ask()) / 2;
        auditLog.info("stage", "marketdata.mid").info("value", value);
        return true;
    }
}
