package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.marketdata.Mid;
/** risk subsystem, EARLY stage. Requires only marketdata.Mid — so it runs before pricing. */
public class Notional extends EventLogNode {
    private final Mid mid;
    public transient double value;
    public Notional(Mid mid) { this.mid = mid; }
    @OnTrigger public boolean calc() {
        value = mid.value * 1000;
        auditLog.info("stage", "risk.notional").info("value", value);
        return true;
    }
}
