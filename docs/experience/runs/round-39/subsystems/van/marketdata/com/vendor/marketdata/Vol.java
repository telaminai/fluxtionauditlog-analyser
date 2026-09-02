package com.vendor.marketdata;
public class Vol {
    private final MdConfig cfg; private final Mid mid;
    public double value;
    public Vol(MdConfig cfg, Mid mid) { this.cfg = cfg; this.mid = mid; }
    public boolean calc() {
        value = mid.value * cfg.factor / 100;
        com.vendor.Audit.log("marketdata.vol", value); return true; }
}
