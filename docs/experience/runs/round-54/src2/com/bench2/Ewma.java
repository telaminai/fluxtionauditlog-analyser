package com.bench2;
import com.telamin.fluxtion.runtime.annotations.*;
public class Ewma {
    private final Mid mid;
    public double value; private long n;
    public Ewma(Mid mid) { this.mid = mid; }
    @OnTrigger public boolean calc() {
        value = n++ == 0 ? mid.value : 0.3 * mid.value + 0.7 * value; return true; }
}
