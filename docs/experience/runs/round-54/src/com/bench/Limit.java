package com.bench;
import com.telamin.fluxtion.runtime.annotations.*;
/** A detector: it ARRESTS unless breached, so the tail of the graph is conditional. */
public class Limit {
    private final Exposure e;
    public double limit = 108_000.0;
    public long breaches;
    public Limit(Exposure e) { this.e = e; }
    @OnTrigger public boolean calc() {
        if (e.value <= limit) return false;
        breaches++; return true; }
}
