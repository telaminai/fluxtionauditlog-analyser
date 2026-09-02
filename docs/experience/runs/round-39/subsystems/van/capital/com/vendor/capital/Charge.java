package com.vendor.capital;
import com.vendor.risk.Exposure;
public class Charge {
    private final CpConfig cfg; private final Exposure exposure;
    public double value;
    public Charge(CpConfig cfg, Exposure exposure) { this.cfg = cfg; this.exposure = exposure; }
    public boolean calc() {
        value = exposure.value * cfg.pct;
        com.vendor.Audit.log("capital.charge", value); return true; }
}
