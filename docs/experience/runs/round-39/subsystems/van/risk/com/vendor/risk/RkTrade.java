package com.vendor.risk;
import com.vendor.Events;
/** risk's own adapter for the shared TRADE event. */
public class RkTrade {
    public double qty = 1.0;
    public boolean onTrade(Events.Trade t) {
        qty = t.qty();
        com.vendor.Audit.log("risk.tradeIn", qty); return true; }
}
