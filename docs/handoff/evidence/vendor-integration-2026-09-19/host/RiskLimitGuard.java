package com.example.myapp.node;

import com.example.myapp.event.RiskAlert;

/**
 * Host-side reaction to the vendor risk engine (com.acmerisk.RiskEngine, from the acme-risk jar).
 * Publishes a RiskAlert only when the breach state CHANGES, not on every recalculation.
 */
public class RiskLimitGuard extends com.telamin.fluxtion.runtime.audit.EventLogNode {

    public RiskLimitGuard(com.acmerisk.RiskEngine arg0) {
        this.acmeRisk = arg0;
    }

    private final com.acmerisk.RiskEngine acmeRisk;

    private boolean lastBreached;

    @javax.annotation.processing.Generated("fluxtion-starter")
public com.telamin.fluxtion.runtime.output.SinkPublisher<com.example.myapp.event.RiskAlert> sinkRiskAlerts = new com.telamin.fluxtion.runtime.output.SinkPublisher<>("riskAlerts");

    @com.telamin.fluxtion.runtime.annotations.OnTrigger
@javax.annotation.processing.Generated("fluxtion-starter")
public boolean onRiskLimitGuard() {
    boolean breached = acmeRisk.isBreached();
    boolean changed = breached != lastBreached;
    lastBreached = breached;
    auditLog.info("breached", breached);
    auditLog.info("alert", changed);
    if (changed) {
        sinkRiskAlerts.publish(new RiskAlert(breached, acmeRisk.getTotalVar(), acmeRisk.getVarLimit()));
    }
    return changed;
}
}
