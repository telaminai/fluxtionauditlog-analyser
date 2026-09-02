package com.vendor.risk;
import com.vendor.Events;
/** risk's own adapter for the shared RATE event - independent of pricing's. */
public class RkRate {
    public double rate = 1.0;
    public boolean onRate(Events.Rate r) {
        rate = r.rate();
        com.vendor.Audit.log("risk.rateIn", rate); return true; }
}
