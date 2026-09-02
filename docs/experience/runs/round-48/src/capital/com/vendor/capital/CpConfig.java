package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class CpConfig extends EventLogNode {
    public transient double pct = 0.08;
    @OnEventHandler public boolean onConfig(Events.Config c) {
        if (!"chargePct".equals(c.key())) return false;
        pct = c.value();
        auditLog.info("stage", "capital.configIn").info("value", pct); return true; }
}
