package com.vendor.capital;
import com.vendor.Events;
/** capital's own adapter for the shared TRADE event. */
public class CpTrade {
    public double qty = 1.0;
    public boolean onTrade(Events.Trade t) {
        qty = t.qty();
        com.vendor.Audit.log("capital.tradeIn", qty); return true; }
}
