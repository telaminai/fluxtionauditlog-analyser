package com.vendor.pricing;
import com.vendor.Events;
/** pricing's own adapter for the shared RATE event. */
public class PxRate {
    public double rate = 1.0;
    public boolean onRate(Events.Rate r) {
        rate = r.rate();
        com.vendor.Audit.log("pricing.rateIn", rate); return true; }
}
