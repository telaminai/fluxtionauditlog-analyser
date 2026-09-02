package com.vendor.risk;
import com.vendor.marketdata.Vol;
public class Var {
    private final RkRate rate; private final Exposure exposure; private final Vol vol;
    public double value;
    public Var(RkRate rate, Exposure exposure, Vol vol) {
        this.rate = rate; this.exposure = exposure; this.vol = vol; }
    public boolean calc() {
        value = exposure.value * vol.value * rate.rate;
        com.vendor.Audit.log("risk.var", value); return true; }
}
