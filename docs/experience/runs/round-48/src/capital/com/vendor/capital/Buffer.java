package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.vendor.contract.*;
public class Buffer extends EventLogNode {
    private final CpTrade trade; private final ChargeApi charge; private final VarApi var;
    public transient double value;
    public Buffer(CpTrade trade, @AssignToField("charge") ChargeApi charge, @AssignToField("var") VarApi var) {
        this.trade = trade; this.charge = charge; this.var = var; }
    @OnTrigger public boolean calc() {
        value = charge.charge() + var.var() * trade.qty / 100;
        auditLog.info("stage", "capital.buffer").info("value", value); return true; }
}
