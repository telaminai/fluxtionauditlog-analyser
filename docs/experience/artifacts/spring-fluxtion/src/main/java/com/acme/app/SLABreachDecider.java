package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** R9 (EDGE): On PICKDONE, if time since RELEASE > 3600000ms. */
public class SLABreachDecider extends EventLogNode {

    @NoTriggerReference private final OrderStore orderStore;
    private static final long SLA_THRESHOLD_MS = 3600000;  // 1 hour

    public SLABreachDecider(OrderStore orderStore) {
        this.orderStore = orderStore;
    }

    @OnEventHandler
    public boolean onPickDone(PickDone event) {
        Long releaseTimestamp = orderStore.getReleaseTimestamp(event.orderId());
        if (releaseTimestamp == null) {
            return false;  // order was never released
        }

        long timeSinceRelease = event.timestampMs() - releaseTimestamp;
        if (timeSinceRelease > SLA_THRESHOLD_MS) {
            DecisionCollector.emit("SLA_BREACH", event.orderId());
            auditLog.info("orderId", event.orderId()).info("timeSinceRelease", timeSinceRelease)
                    .info("breach", true);
            return true;
        }

        auditLog.info("orderId", event.orderId()).info("timeSinceRelease", timeSinceRelease)
                .info("breach", false);
        return false;  // SLA not breached
    }
}
