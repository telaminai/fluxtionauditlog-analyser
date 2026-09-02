package com.vendor.pricing;
import com.vendor.marketdata.Vol;
/** pricing 2.0 - NEW public stage. Cross-vendor: it reads marketdata's Vol. */
public class Skew {
    private final Vol vol; private final Adjusted adjusted;
    public double value;
    public Skew(Vol vol, Adjusted adjusted) { this.vol = vol; this.adjusted = adjusted; }
    public boolean calc() {
        value = vol.value * adjusted.value / 100;
        com.vendor.Audit.log("pricing.skew", value); return true; }
}
