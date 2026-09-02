package com.vendor.capital;
import com.vendor.risk.Var;
public class Buffer {
    private final CpTrade trade; private final Charge charge; private final Var var;
    public double value;
    public Buffer(CpTrade trade, Charge charge, Var var) {
        this.trade = trade; this.charge = charge; this.var = var; }
    public boolean calc() {
        value = charge.value + var.value * trade.qty / 100;
        com.vendor.Audit.log("capital.buffer", value); return true; }
}
