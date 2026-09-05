package com.benchv;
import com.bench.MarketTick;
/** PROPOSED CODEGEN MODE: node STATE hoisted into the processor as primitive fields, node
 *  bodies emitted as private methods over those fields. Same dependency order, same arithmetic,
 *  same semantics as BaseProcessor — but one object instead of eleven.
 *  Trade: node instances are no longer addressable/observable. */
public class FlatStateProcessor {
    // TickIn                       // Mid / Spread / Ewma / Vol / Notional / Exposure / Limit / Charge / Buffer
    private double bid, ask;
    public  double mid, spread, ewma, vol, notional, exposure, charge, buffer;
    public  double limit = 108_000.0;
    public  long   breaches, updates;
    private long   ewmaN;

    private void tickIn(MarketTick t){ bid = t.bid; ask = t.ask; }
    private void calcMid()     { mid = (bid + ask) * 0.5; }
    private void calcEwma()    { ewma = ewmaN++ == 0 ? mid : 0.3 * mid + 0.7 * ewma; }
    private void calcSpread()  { spread = ask - bid; }
    private void calcNotional(){ notional = mid * 1000.0 - spread; }
    private void calcVol()     { double d = mid - ewma; vol = d < 0 ? -d : d; }
    private void calcExposure(){ exposure = notional * (1.0 + vol * 0.001); }
    private void calcLimit()   { if (exposure > limit) breaches++; }
    private void calcCharge()  { charge = exposure * 0.08; }
    private void calcBuffer()  { buffer = charge * 1.25; updates++; }

    public void handleEvent(MarketTick e) {
        tickIn(e);
        calcMid(); calcEwma(); calcSpread(); calcNotional(); calcVol();
        calcExposure(); calcLimit(); calcCharge(); calcBuffer();
    }
}
