package com.plain;
import com.bench.MarketTick;

/** The component-library shape: separate objects, hand-wired, called in dependency order.
 *  This is what the vendor-jar arms of rounds 49-53 look like. */
public class PlainComponents {
    public static class Mid      { public double v; void calc(double b, double a){ v=(b+a)*0.5; } }
    public static class Spread   { public double v; void calc(double b, double a){ v=a-b; } }
    public static class Ewma     { public double v; long n; void calc(double mid){ v = n++==0?mid:0.3*mid+0.7*v; } }
    public static class Vol      { public double v; void calc(double mid,double ew){ double d=mid-ew; v=d<0?-d:d; } }
    public static class Notional { public double v; void calc(double mid,double sp){ v=mid*1000.0-sp; } }
    public static class Exposure { public double v; void calc(double no,double vo){ v=no*(1.0+vo*0.001); } }
    public static class Charge   { public double v; void calc(double ex){ v=ex*0.08; } }
    public static class Buffer   { public double v; public long updates; void calc(double ch){ v=ch*1.25; updates++; } }

    public final Mid mid = new Mid(); public final Spread spread = new Spread();
    public final Ewma ewma = new Ewma(); public final Vol vol = new Vol();
    public final Notional notional = new Notional(); public final Exposure exposure = new Exposure();
    public final Charge charge = new Charge(); public final Buffer buffer = new Buffer();
    public double limit = 108_000.0; public long breaches;

    public void onTick(MarketTick t) {
        mid.calc(t.bid, t.ask);
        ewma.calc(mid.v);
        spread.calc(t.bid, t.ask);
        notional.calc(mid.v, spread.v);
        vol.calc(mid.v, ewma.v);
        exposure.calc(notional.v, vol.v);
        if (exposure.v <= limit) return;
        breaches++;
        charge.calc(exposure.v);
        buffer.calc(charge.v);
    }
}
