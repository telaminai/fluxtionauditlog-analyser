package com.vendor.capital;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.contract.*;
/** STATEFUL, two levels below the supervisor. */
public class AlertCount extends EventLogNode {
    private final AlertApi alert;
    public int alerts;
    public AlertCount(AlertApi alert) { this.alert = alert; }
    @OnTrigger public boolean calc() {
        alerts++;
        auditLog.info("stage", "capital.alertCount").info("value", (double) alerts); return true; }
}
