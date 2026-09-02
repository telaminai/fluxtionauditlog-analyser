package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.risk.Var;
public class Buffer extends EventLogNode {
    private final CpTrade trade; private final Charge charge; private final Var var;
    public transient double value;
    public Buffer(CpTrade trade, Charge charge, Var var) {
        this.trade = trade; this.charge = charge; this.var = var; }
    @OnTrigger public boolean calc() {
        value = charge.value + var.value * trade.qty / 100;
        auditLog.info("stage", "capital.buffer").info("value", value); return true; }
}
