package com.bench;
import com.telamin.fluxtion.runtime.annotations.*;
public class Buffer {
    private final Charge c;
    public double value; public long updates;
    public Buffer(Charge c) { this.c = c; }
    @OnTrigger public boolean calc() { value = c.value * 1.25; updates++; return true; }
}
