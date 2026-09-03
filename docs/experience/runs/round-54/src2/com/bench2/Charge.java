package com.bench2;
import com.telamin.fluxtion.runtime.annotations.*;
public class Charge {
    private final Limit l;
    @NoTriggerReference private final Exposure e;
    public double value;
    public Charge(Limit l, Exposure e) { this.l = l; this.e = e; }
    @OnTrigger public boolean calc() { value = e.value * 0.08; return true; }
}
