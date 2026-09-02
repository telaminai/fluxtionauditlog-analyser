package com.bench2;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
public class Vol extends EventLogNode {
    private final Mid mid; private final Ewma ewma;
    public double value;
    public Vol(Mid mid, Ewma ewma) { this.mid = mid; this.ewma = ewma; }
    @OnTrigger public boolean calc() {
        double d = mid.value - ewma.value; value = d < 0 ? -d : d; auditLog.info("vol", value); return true; }
}
