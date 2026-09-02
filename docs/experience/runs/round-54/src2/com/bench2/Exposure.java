package com.bench2;
import com.telamin.fluxtion.runtime.annotations.*;
public class Exposure {
    private final Notional n; private final Vol v;
    public double value;
    public Exposure(Notional n, Vol v) { this.n = n; this.v = v; }
    @OnTrigger public boolean calc() { value = n.value * (1.0 + v.value * 0.001); return true; }
}
