package com.vendor.pricing;
import com.vendor.Events;
/**
 * pricing 2.0 - ADDITIVE. Still the adapter for the shared RATE event; now ALSO reacts to the
 * shared CONFIG event, for the spread multiplier. No signature changed, nothing removed.
 */
public class PxRate {
    public double rate = 1.0;
    public double mult = 1.0;                       // new in 2.0
    public boolean onRate(Events.Rate r) {
        rate = r.rate();
        com.vendor.Audit.log("pricing.rateIn", rate); return true; }
    public boolean onConfig(Events.Config c) {   // new in 2.0
        if (!"spreadMult".equals(c.key())) return false;
        mult = c.value();
        com.vendor.Audit.log("pricing.multIn", mult); return true; }
}
