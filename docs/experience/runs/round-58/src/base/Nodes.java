package com.benchv;
import com.bench.MarketTick;
import com.telamin.fluxtion.runtime.annotations.*;
/** TRUE BASE CASE nodes: void trigger methods via failBuildIfMissingBooleanReturn=false.
 *  No boolean return means no dirty flag and no guard -- every node fires every event. */
public class Nodes {
    public static class TickIn { public double bid, ask;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTick(MarketTick t){ bid=t.bid; ask=t.ask; } }
    public static class Mid { private final TickIn t; public double value; public Mid(TickIn t){this.t=t;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ value=(t.bid+t.ask)*0.5; } }
    public static class Spread { private final TickIn t; public double value; public Spread(TickIn t){this.t=t;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ value=t.ask-t.bid; } }
    public static class Ewma { private final Mid m; public double value; private long n; public Ewma(Mid m){this.m=m;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ value = n++==0 ? m.value : 0.3*m.value+0.7*value; } }
    public static class Vol { private final Mid m; private final Ewma e; public double value; public Vol(Mid m,Ewma e){this.m=m;this.e=e;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ double d=m.value-e.value; value = d<0?-d:d; } }
    public static class Notional { private final Mid m; private final Spread s; public double value; public Notional(Mid m,Spread s){this.m=m;this.s=s;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ value=m.value*1000.0-s.value; } }
    public static class Exposure { private final Notional n; private final Vol v; public double value; public Exposure(Notional n,Vol v){this.n=n;this.v=v;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ value=n.value*(1.0+v.value*0.001); } }
    public static class Limit { private final Exposure e; public double limit=108_000.0; public long breaches; public Limit(Exposure e){this.e=e;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ if(e.value>limit) breaches++; } }
    public static class Charge { private final Exposure e; public double value; public Charge(Exposure e){this.e=e;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ value=e.value*0.08; } }
    public static class Buffer { private final Charge c; public double value; public long updates; public Buffer(Charge c){this.c=c;}
        @OnTrigger(failBuildIfMissingBooleanReturn = false) public void calc(){ value=c.value*1.25; updates++; } }
}
