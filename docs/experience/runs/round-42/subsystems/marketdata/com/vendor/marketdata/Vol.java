package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
public class Vol extends EventLogNode {
    private final MdConfig cfg; private final Mid mid;
    public transient double value;
    public Vol(MdConfig cfg, Mid mid) { this.cfg = cfg; this.mid = mid; }
    @OnTrigger public boolean calc() {
        value = mid.value * cfg.factor / 100;
        auditLog.info("stage", "marketdata.vol").info("value", value); return true; }
}
