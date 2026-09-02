package com.vendor.marketdata;
import com.vendor.Events;
/** marketdata's own adapter for the shared CONFIG event; only volFactor concerns it. */
public class MdConfig {
    public double factor = 1.0;
    public boolean onConfig(Events.Config c) {
        if (!"volFactor".equals(c.key())) return false;
        factor = c.value();
        com.vendor.Audit.log("marketdata.configIn", factor); return true; }
}
