package com.bench;
import com.telamin.fluxtion.runtime.annotations.*;
public class Notional {
    private final Mid mid; private final Spread sp;
    public double value;
    public Notional(Mid mid, Spread sp) { this.mid = mid; this.sp = sp; }
    @OnTrigger public boolean calc() { value = mid.value * 1000.0 - sp.value; return true; }
}
