package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
/** STATEFUL, and two levels below the arrest. */
public class AlertCount extends EventLogNode {
    private final Alert alert;
    public int alerts;
    public AlertCount(Alert alert) { this.alert = alert; }
    @OnTrigger public boolean calc() {
        alerts++;
        auditLog.info("stage", "capital.alertCount").info("value", (double) alerts);
        return true; }
}
