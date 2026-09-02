package com.bench;
import com.telamin.fluxtion.runtime.annotations.*;
public class Spread {
    private final TickIn t;
    public double value;
    public Spread(TickIn t) { this.t = t; }
    @OnTrigger public boolean calc() { value = t.ask - t.bid; return true; }
}
