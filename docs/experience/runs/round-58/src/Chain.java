package com.benchi;
import com.bench.MarketTick;
/** The pricing chain three ways: concrete types, behind interfaces with ONE implementor,
 *  and behind interfaces with THREE implementors reachable. Identical arithmetic in all. */
public class Chain {
    public interface ITickIn { void onTick(MarketTick t); double bid(); double ask(); }
    public interface IMid { void calc(); double value(); }
    public interface ISpread { void calc(); double value(); }
    public interface IEwma { void calc(); double value(); }
    public interface IVol { void calc(); double value(); }
    public interface INotional { void calc(); double value(); }
    public interface IExposure { void calc(); double value(); }
    public interface ILimit { void calc(); long breaches(); }
    public interface ICharge { void calc(); double value(); }
    public interface IBuffer { void calc(); double value(); long updates(); }
    public static final class TickInA implements ITickIn { private double b,a;
        public void onTick(MarketTick t){ b=t.bid; a=t.ask; } public double bid(){return b;} public double ask(){return a;} }
    public static final class MidA implements IMid { private double v; private final ITickIn t; public MidA(ITickIn t){this.t=t;}
        public void calc(){ v=(t.bid()+t.ask())*0.5; } public double value(){return v;} }
    public static final class SpreadA implements ISpread { private double v; private final ITickIn t; public SpreadA(ITickIn t){this.t=t;}
        public void calc(){ v=t.ask()-t.bid(); } public double value(){return v;} }
    public static final class EwmaA implements IEwma { private double v; private final IMid m; private long n; public EwmaA(IMid m){this.m=m;}
        public void calc(){ v = n++==0 ? m.value() : 0.3*m.value()+0.7*v; } public double value(){return v;} }
    public static final class VolA implements IVol { private double v; private final IMid m; private final IEwma e; public VolA(IMid m,IEwma e){this.m=m;this.e=e;}
        public void calc(){ double d=m.value()-e.value(); v = d<0?-d:d; } public double value(){return v;} }
    public static final class NotionalA implements INotional { private double v; private final IMid m; private final ISpread s; public NotionalA(IMid m,ISpread s){this.m=m;this.s=s;}
        public void calc(){ v=m.value()*1000.0-s.value(); } public double value(){return v;} }
    public static final class ExposureA implements IExposure { private double v; private final INotional n; private final IVol vo; public ExposureA(INotional n,IVol vo){this.n=n;this.vo=vo;}
        public void calc(){ v=n.value()*(1.0+vo.value()*0.001); } public double value(){return v;} }
    public static final class ChargeA implements ICharge { private double v; private final IExposure e; public ChargeA(IExposure e){this.e=e;}
        public void calc(){ v=e.value()*0.08; } public double value(){return v;} }
    public static final class LimitA implements ILimit { private final IExposure e; private long br; private final double lim=108_000.0;
        public LimitA(IExposure e){this.e=e;} public void calc(){ if(e.value()>lim) br++; } public long breaches(){return br;} }
    public static final class BufferA implements IBuffer { private final ICharge c; private double v; private long u;
        public BufferA(ICharge c){this.c=c;} public void calc(){ v=c.value()*1.25; u++; } public double value(){return v;} public long updates(){return u;} }
    public static final class TickInB implements ITickIn { private double b,a;
        public void onTick(MarketTick t){ b=t.bid; a=t.ask; } public double bid(){return b;} public double ask(){return a;} }
    public static final class MidB implements IMid { private double v; private final ITickIn t; public MidB(ITickIn t){this.t=t;}
        public void calc(){ v=(t.bid()+t.ask())*0.5; } public double value(){return v;} }
    public static final class SpreadB implements ISpread { private double v; private final ITickIn t; public SpreadB(ITickIn t){this.t=t;}
        public void calc(){ v=t.ask()-t.bid(); } public double value(){return v;} }
    public static final class EwmaB implements IEwma { private double v; private final IMid m; private long n; public EwmaB(IMid m){this.m=m;}
        public void calc(){ v = n++==0 ? m.value() : 0.3*m.value()+0.7*v; } public double value(){return v;} }
    public static final class VolB implements IVol { private double v; private final IMid m; private final IEwma e; public VolB(IMid m,IEwma e){this.m=m;this.e=e;}
        public void calc(){ double d=m.value()-e.value(); v = d<0?-d:d; } public double value(){return v;} }
    public static final class NotionalB implements INotional { private double v; private final IMid m; private final ISpread s; public NotionalB(IMid m,ISpread s){this.m=m;this.s=s;}
        public void calc(){ v=m.value()*1000.0-s.value(); } public double value(){return v;} }
    public static final class ExposureB implements IExposure { private double v; private final INotional n; private final IVol vo; public ExposureB(INotional n,IVol vo){this.n=n;this.vo=vo;}
        public void calc(){ v=n.value()*(1.0+vo.value()*0.001); } public double value(){return v;} }
    public static final class ChargeB implements ICharge { private double v; private final IExposure e; public ChargeB(IExposure e){this.e=e;}
        public void calc(){ v=e.value()*0.08; } public double value(){return v;} }
    public static final class LimitB implements ILimit { private final IExposure e; private long br; private final double lim=108_000.0;
        public LimitB(IExposure e){this.e=e;} public void calc(){ if(e.value()>lim) br++; } public long breaches(){return br;} }
    public static final class BufferB implements IBuffer { private final ICharge c; private double v; private long u;
        public BufferB(ICharge c){this.c=c;} public void calc(){ v=c.value()*1.25; u++; } public double value(){return v;} public long updates(){return u;} }
    public static final class TickInC implements ITickIn { private double b,a;
        public void onTick(MarketTick t){ b=t.bid; a=t.ask; } public double bid(){return b;} public double ask(){return a;} }
    public static final class MidC implements IMid { private double v; private final ITickIn t; public MidC(ITickIn t){this.t=t;}
        public void calc(){ v=(t.bid()+t.ask())*0.5; } public double value(){return v;} }
    public static final class SpreadC implements ISpread { private double v; private final ITickIn t; public SpreadC(ITickIn t){this.t=t;}
        public void calc(){ v=t.ask()-t.bid(); } public double value(){return v;} }
    public static final class EwmaC implements IEwma { private double v; private final IMid m; private long n; public EwmaC(IMid m){this.m=m;}
        public void calc(){ v = n++==0 ? m.value() : 0.3*m.value()+0.7*v; } public double value(){return v;} }
    public static final class VolC implements IVol { private double v; private final IMid m; private final IEwma e; public VolC(IMid m,IEwma e){this.m=m;this.e=e;}
        public void calc(){ double d=m.value()-e.value(); v = d<0?-d:d; } public double value(){return v;} }
    public static final class NotionalC implements INotional { private double v; private final IMid m; private final ISpread s; public NotionalC(IMid m,ISpread s){this.m=m;this.s=s;}
        public void calc(){ v=m.value()*1000.0-s.value(); } public double value(){return v;} }
    public static final class ExposureC implements IExposure { private double v; private final INotional n; private final IVol vo; public ExposureC(INotional n,IVol vo){this.n=n;this.vo=vo;}
        public void calc(){ v=n.value()*(1.0+vo.value()*0.001); } public double value(){return v;} }
    public static final class ChargeC implements ICharge { private double v; private final IExposure e; public ChargeC(IExposure e){this.e=e;}
        public void calc(){ v=e.value()*0.08; } public double value(){return v;} }
    public static final class LimitC implements ILimit { private final IExposure e; private long br; private final double lim=108_000.0;
        public LimitC(IExposure e){this.e=e;} public void calc(){ if(e.value()>lim) br++; } public long breaches(){return br;} }
    public static final class BufferC implements IBuffer { private final ICharge c; private double v; private long u;
        public BufferC(ICharge c){this.c=c;} public void calc(){ v=c.value()*1.25; u++; } public double value(){return v;} public long updates(){return u;} }
    /** interface-typed fields — the shape under test */
    public static final class IfaceProcessor {
        final ITickIn tickIn; final IMid mid; final ISpread spread; final IEwma ewma; final IVol vol;
        final INotional notional; final IExposure exposure; final ILimit limit; final ICharge charge; final IBuffer buffer;
        public IfaceProcessor(ITickIn t,IMid m,ISpread s,IEwma e,IVol v,INotional n,IExposure x,ILimit l,ICharge c,IBuffer b){
            tickIn=t; mid=m; spread=s; ewma=e; vol=v; notional=n; exposure=x; limit=l; charge=c; buffer=b; }
        public void handleEvent(MarketTick e){
            tickIn.onTick(e); mid.calc(); ewma.calc(); spread.calc(); notional.calc();
            vol.calc(); exposure.calc(); limit.calc(); charge.calc(); buffer.calc(); }
        public double buf(){ return buffer.value(); }
        public long breaches(){ return limit.breaches(); }
        public long updates(){ return buffer.updates(); }
    }
    public static IfaceProcessor buildA(){
        TickInA t=new TickInA(); MidA m=new MidA(t); SpreadA s=new SpreadA(t); EwmaA e=new EwmaA(m);
        VolA v=new VolA(m,e); NotionalA n=new NotionalA(m,s); ExposureA x=new ExposureA(n,v);
        LimitA l=new LimitA(x); ChargeA c=new ChargeA(x); BufferA b=new BufferA(c);
        return new IfaceProcessor(t,m,s,e,v,n,x,l,c,b); }
    public static IfaceProcessor buildB(){
        TickInB t=new TickInB(); MidB m=new MidB(t); SpreadB s=new SpreadB(t); EwmaB e=new EwmaB(m);
        VolB v=new VolB(m,e); NotionalB n=new NotionalB(m,s); ExposureB x=new ExposureB(n,v);
        LimitB l=new LimitB(x); ChargeB c=new ChargeB(x); BufferB b=new BufferB(c);
        return new IfaceProcessor(t,m,s,e,v,n,x,l,c,b); }
    public static IfaceProcessor buildC(){
        TickInC t=new TickInC(); MidC m=new MidC(t); SpreadC s=new SpreadC(t); EwmaC e=new EwmaC(m);
        VolC v=new VolC(m,e); NotionalC n=new NotionalC(m,s); ExposureC x=new ExposureC(n,v);
        LimitC l=new LimitC(x); ChargeC c=new ChargeC(x); BufferC b=new BufferC(c);
        return new IfaceProcessor(t,m,s,e,v,n,x,l,c,b); }
}
