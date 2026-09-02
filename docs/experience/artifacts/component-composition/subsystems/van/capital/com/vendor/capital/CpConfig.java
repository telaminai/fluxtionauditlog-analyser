package com.vendor.capital;
import com.vendor.Events;
/** capital's own adapter for the shared CONFIG event; only chargePct concerns it. */
public class CpConfig {
    public double pct = 0.08;
    public boolean onConfig(Events.Config c) {
        if (!"chargePct".equals(c.key())) return false;
        pct = c.value();
        com.vendor.Audit.log("capital.configIn", pct); return true; }
}
