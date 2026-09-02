package com.bench;
import com.telamin.fluxtion.runtime.annotations.*;
public class Vol {
    private final Mid mid; private final Ewma ewma;
    public double value;
    public Vol(Mid mid, Ewma ewma) { this.mid = mid; this.ewma = ewma; }
    @OnTrigger public boolean calc() {
        double d = mid.value - ewma.value; value = d < 0 ? -d : d; return true; }
}
