package com.bench;
import com.telamin.fluxtion.runtime.annotations.*;
public class Mid {
    private final TickIn t;
    public double value;
    public Mid(TickIn t) { this.t = t; }
    @OnTrigger public boolean calc() { value = (t.bid + t.ask) * 0.5; return true; }
}
