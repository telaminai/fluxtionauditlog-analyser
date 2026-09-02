package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class MdConfig extends EventLogNode {
    public transient double factor = 1.0;
    @OnEventHandler public boolean onConfig(Events.Config c) {
        if (!"volFactor".equals(c.key())) return false;
        factor = c.value();
        auditLog.info("stage", "marketdata.configIn").info("value", factor); return true; }
}
