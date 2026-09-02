package com.acme.app;

import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** R8 (EDGE): On DISPATCH whose weightKg > carrier's maxWeightKg. */
public class OverweightDecider extends EventLogNode {

    @NoTriggerReference private final OrderStore orderStore;
    @NoTriggerReference private final CarrierStore carrierStore;

    public OverweightDecider(OrderStore orderStore, CarrierStore carrierStore) {
        this.orderStore = orderStore;
        this.carrierStore = carrierStore;
    }

    @OnEventHandler
    public boolean onDispatch(Dispatch event) {
        Carrier carrier = carrierStore.getCarrier(event.carrierId());

        if (event.weightKg() > carrier.maxWeightKg()) {
            DecisionCollector.emit("OVERWEIGHT", event.orderId());
            auditLog.info("orderId", event.orderId()).info("overweight", true);
            return true;
        }

        auditLog.info("orderId", event.orderId()).info("overweight", false);
        return false;  // not overweight
    }
}
