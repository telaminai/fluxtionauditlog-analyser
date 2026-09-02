package com.plain;
import com.bench.MarketTick;

/** Hand-written, but replicating the GENERATED dispatch shape exactly: per-node objects,
 *  isDirty flags, guardCheck methods, a dispatch method. Isolates "the shape Fluxtion emits"
 *  from "the framework services Fluxtion also runs". */
public class PlainGuarded {
    public static class TickIn  { public double bid, ask; boolean onTick(MarketTick t){ bid=t.bid; ask=t.ask; return true; } }
    public static class Mid     { public double v; final TickIn t; Mid(TickIn t){this.t=t;} boolean calc(){ v=(t.bid+t.ask)*0.5; return true; } }
    public static class Spread  { public double v; final TickIn t; Spread(TickIn t){this.t=t;} boolean calc(){ v=t.ask-t.bid; return true; } }
    public static class Ewma    { public double v; long n; final Mid m; Ewma(Mid m){this.m=m;} boolean calc(){ v=n++==0?m.v:0.3*m.v+0.7*v; return true; } }
    public static class Vol     { public double v; final Mid m; final Ewma e; Vol(Mid m,Ewma e){this.m=m;this.e=e;} boolean calc(){ double d=m.v-e.v; v=d<0?-d:d; return true; } }
    public static class Notional{ public double v; final Mid m; final Spread s; Notional(Mid m,Spread s){this.m=m;this.s=s;} boolean calc(){ v=m.v*1000.0-s.v; return true; } }
    public static class Exposure{ public double v; final Notional n; final Vol vo; Exposure(Notional n,Vol vo){this.n=n;this.vo=vo;} boolean calc(){ v=n.v*(1.0+vo.v*0.001); return true; } }
    public static class Limit   { public double lim=108_000.0; public long breaches; final Exposure e; Limit(Exposure e){this.e=e;} boolean calc(){ if(e.v<=lim) return false; breaches++; return true; } }
    public static class Charge  { public double v; final Exposure e; Charge(Exposure e){this.e=e;} boolean calc(){ v=e.v*0.08; return true; } }
    public static class Buffer  { public double v; public long updates; final Charge c; Buffer(Charge c){this.c=c;} boolean calc(){ v=c.v*1.25; updates++; return true; } }

    public final TickIn tickIn = new TickIn();
    public final Mid mid = new Mid(tickIn); public final Spread spread = new Spread(tickIn);
    public final Ewma ewma = new Ewma(mid); public final Vol vol = new Vol(mid, ewma);
    public final Notional notional = new Notional(mid, spread);
    public final Exposure exposure = new Exposure(notional, vol);
    public final Limit limit = new Limit(exposure);
    public final Charge charge = new Charge(exposure);
    public final Buffer buffer = new Buffer(charge);

    boolean isDirty_tickIn, isDirty_mid, isDirty_spread, isDirty_ewma, isDirty_vol,
            isDirty_notional, isDirty_exposure, isDirty_limit, isDirty_charge;

    private boolean guard_mid(){ return isDirty_tickIn; }
    private boolean guard_spread(){ return isDirty_tickIn; }
    private boolean guard_ewma(){ return isDirty_mid; }
    private boolean guard_vol(){ return isDirty_mid | isDirty_ewma; }
    private boolean guard_notional(){ return isDirty_mid | isDirty_spread; }
    private boolean guard_exposure(){ return isDirty_notional | isDirty_vol; }
    private boolean guard_limit(){ return isDirty_exposure; }
    private boolean guard_charge(){ return isDirty_limit; }
    private boolean guard_buffer(){ return isDirty_charge; }

    public void onTick(MarketTick t) {
        isDirty_tickIn = tickIn.onTick(t);
        if (guard_mid())      isDirty_mid      = mid.calc();
        if (guard_ewma())     isDirty_ewma     = ewma.calc();
        if (guard_spread())   isDirty_spread   = spread.calc();
        if (guard_notional()) isDirty_notional = notional.calc();
        if (guard_vol())      isDirty_vol      = vol.calc();
        if (guard_exposure()) isDirty_exposure = exposure.calc();
        if (guard_limit())    isDirty_limit    = limit.calc();
        if (guard_charge())   isDirty_charge   = charge.calc();
        if (guard_buffer())                      buffer.calc();
        afterEvent();
    }
    private void afterEvent() {
        isDirty_tickIn=false; isDirty_mid=false; isDirty_spread=false; isDirty_ewma=false;
        isDirty_vol=false; isDirty_notional=false; isDirty_exposure=false; isDirty_limit=false; isDirty_charge=false;
    }
}
