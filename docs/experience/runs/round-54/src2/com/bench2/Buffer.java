package com.bench2;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
public class Buffer extends EventLogNode {
    private final Charge c;
    public double value; public long updates;
    public Buffer(Charge c) { this.c = c; }
    @OnTrigger public boolean calc() { value = c.value * 1.25; updates++; auditLog.info("buffer", value); return true; }
}
