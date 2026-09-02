package com.bench2;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
public class Mid extends EventLogNode {
    private final TickIn t;
    public double value;
    public Mid(TickIn t) { this.t = t; }
    @OnTrigger public boolean calc() { value = (t.bid + t.ask) * 0.5; auditLog.info("mid", value); return true; }
}
