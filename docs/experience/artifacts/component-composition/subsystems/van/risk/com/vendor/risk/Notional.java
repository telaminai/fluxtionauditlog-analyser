package com.vendor.risk;
import com.vendor.marketdata.Mid;
public class Notional {
    private final RkTrade trade; private final Mid mid;
    public double value;
    public Notional(RkTrade trade, Mid mid) { this.trade = trade; this.mid = mid; }
    public boolean calc() {
        value = mid.value * trade.qty;
        com.vendor.Audit.log("risk.notional", value); return true; }
}
