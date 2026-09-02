package com.vendor.marketdata;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
public class Vol extends EventLogNode implements VolApi {
    private final MdConfig cfg; private final MidApi mid;
    public transient double value;
    public Vol(MdConfig cfg, MidApi mid) { this.cfg = cfg; this.mid = mid; }
    public double vol() { return value; }
    @OnTrigger public boolean calc() {
        value = mid.mid() * cfg.factor / 100;
        auditLog.info("stage", "marketdata.vol").info("value", value); return true; }
}
