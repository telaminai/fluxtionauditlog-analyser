package com.vendor;
/**
 * Idiomatic component. One class per subsystem. The ordering of the figures is INTERNAL — the
 * author writes mid before vol and ewma because they read it — and no consumer ever sees it.
 */
public class MarketData {
    private double mid, depth, vol, ewma, volFactor = 1.0;
    private int samples;

    public void onTick(Events.Tick t) {
        mid   = (t.bid() + t.ask()) / 2;
        depth = (t.ask() - t.bid()) * 100;
        vol   = mid * volFactor / 100;
        ewma  = samples++ == 0 ? mid : 0.3 * mid + 0.7 * ewma;
        Audit.log("marketdata.mid", mid);
        Audit.log("marketdata.depth", depth);
        Audit.log("marketdata.vol", vol);
        Audit.log("marketdata.ewma", ewma);
    }
    /** @return true if this component owns the key and acted on it. */
    public boolean onConfig(Events.Config c) {
        if (!"volFactor".equals(c.key())) return false;
        volFactor = c.value();
        vol = mid * volFactor / 100;
        Audit.log("marketdata.vol", vol);
        return true;
    }
    public double mid()  { return mid; }
    public double depth(){ return depth; }
    public double vol()  { return vol; }
    public double ewma() { return ewma; }
}
